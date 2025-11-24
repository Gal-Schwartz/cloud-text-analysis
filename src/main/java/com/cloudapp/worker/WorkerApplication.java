package com.cloudapp.worker;

import com.cloudapp.manager.WorkerResultMessage;
import com.cloudapp.manager.WorkerTaskMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public class WorkerApplication {

    private static final Logger logger = LoggerFactory.getLogger(WorkerApplication.class);

    private final SqsClient sqs;
    private final S3Client s3;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ParserRunner parserRunner = new ParserRunner();

    public WorkerApplication() {
        this.sqs = SqsClient.builder()
                .region(WorkerConfig.REGION)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        this.s3 = S3Client.builder()
                .region(WorkerConfig.REGION)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    public static void main(String[] args) {
        WorkerApplication app = new WorkerApplication();
        try {
            app.run();
        } catch (Exception e) {
            logger.error("Worker crashed", e);
        } finally {
            app.close();
        }
    }

    public void close() {
        try {
            sqs.close();
        } catch (Exception ignored) {}
        try {
            s3.close();
        } catch (Exception ignored) {}
    }

    public void run() {
        logger.info("Worker started. Listening for tasks on {}", WorkerConfig.WORKER_TASK_QUEUE_URL);

        while (true) {
            try {
                ReceiveMessageRequest recvReq = ReceiveMessageRequest.builder()
                        .queueUrl(WorkerConfig.WORKER_TASK_QUEUE_URL)
                        .maxNumberOfMessages(5)
                        .waitTimeSeconds(20)   // long polling
                        .visibilityTimeout(300) // זמן מספיק לעיבוד טקסט
                        .build();

                ReceiveMessageResponse recvRes = sqs.receiveMessage(recvReq);
                List<Message> messages = recvRes.messages();

                if (messages == null || messages.isEmpty()) {
                    continue;
                }

                for (Message msg : messages) {
                    handleTaskMessage(msg);
                }

            } catch (Exception e) {
                logger.error("Error in worker main loop", e);
            }
        }
    }

    private void handleTaskMessage(Message msg) {
        String body = msg.body();
        WorkerTaskMessage task;
        try {
            task = objectMapper.readValue(body, WorkerTaskMessage.class);
        } catch (Exception e) {
            logger.error("Failed to parse WorkerTaskMessage: {}", body, e);
            deleteMessage(WorkerConfig.WORKER_TASK_QUEUE_URL, msg);
            return;
        }

        logger.info("Received worker task: taskId={}, url={}, analysisType={}",
                task.getTaskId(), task.getUrl(), task.getAnalysisType());

        String outputS3Key = null;
        String error = null;

        try {
            // 1. הרצת ה-Parser ל-URL אחד
            File outputFile = parserRunner.process(task.getUrl(), task.getAnalysisType());

            // 2. העלאת הפלט ל-S3
            outputS3Key = uploadOutputToS3(task.getTaskId(), outputFile);

            logger.info("Uploaded worker output to s3://{}/{}",
                    WorkerConfig.BUCKET_NAME, outputS3Key);

        } catch (Exception e) {
            logger.error("Error while processing worker taskId={}", task.getTaskId(), e);
            error = "Worker error: " + e.getMessage();
        }

        try {
            // 3. שליחת WorkerResultMessage ל-Manager
            WorkerResultMessage resultMsg = new WorkerResultMessage(
                    task.getTaskId(),
                    task.getUrl(),
                    task.getAnalysisType(),
                    outputS3Key,
                    error
            );

            String resultBody = objectMapper.writeValueAsString(resultMsg);

            SendMessageRequest sendReq = SendMessageRequest.builder()
                    .queueUrl(WorkerConfig.WORKER_RESULT_QUEUE_URL)
                    .messageBody(resultBody)
                    .build();

            sqs.sendMessage(sendReq);

            logger.info("Sent worker result for taskId={} to {}", task.getTaskId(),
                    WorkerConfig.WORKER_RESULT_QUEUE_URL);

        } catch (Exception e) {
            logger.error("Failed to send worker result for taskId={}", task.getTaskId(), e);
        } finally {
            // 4. מוחקים את הודעת המשימה מהתור (כדי לא לטפל בה שוב)
            deleteMessage(WorkerConfig.WORKER_TASK_QUEUE_URL, msg);
        }
    }

    private String uploadOutputToS3(String taskId, File file) {
        String fileName = file.getName();
        String key = WorkerConfig.OUTPUT_PREFIX
                + taskId + "/"
                + UUID.randomUUID() + "_" + fileName;

        PutObjectRequest putReq = PutObjectRequest.builder()
                .bucket(WorkerConfig.BUCKET_NAME)
                .key(key)
                .build();

        s3.putObject(putReq, RequestBody.fromFile(file));
        return key;
    }

    private void deleteMessage(String queueUrl, Message msg) {
        DeleteMessageRequest delReq = DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(msg.receiptHandle())
                .build();
        sqs.deleteMessage(delReq);
    }
}
