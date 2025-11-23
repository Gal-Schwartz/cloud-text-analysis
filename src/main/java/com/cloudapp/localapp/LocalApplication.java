package com.cloudapp.localapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

public class LocalApplication {

    private static final Logger logger = LoggerFactory.getLogger(LocalApplication.class);

    private final S3Client s3;
    private final SqsClient sqs;
    private final Ec2Client ec2;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LocalApplication(Region region) {
        this.s3 = S3Client.builder()
                .region(region)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
        this.sqs = SqsClient.builder()
                .region(region)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
        this.ec2 = Ec2Client.builder()
                .region(region)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    public static void main(String[] args) {
        if (args.length < 3 || args.length > 4) {
            System.err.println("Usage: java -jar local-app.jar <inputFileName> <outputFileName> <n> [terminate]");
            System.exit(1);
        }

        String inputFilePath = args[0];
        String outputFilePath = args[1];
        int n;
        try {
            n = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            System.err.println("n must be an integer");
            System.exit(1);
            return;
        }

        boolean terminate = args.length == 4 && "terminate".equalsIgnoreCase(args[3]);

        LocalApplication app = new LocalApplication(LocalAppConfig.REGION);
        try {
            app.run(inputFilePath, outputFilePath, n, terminate);
        } catch (Exception e) {
            logger.error("Local application failed", e);
            System.exit(2);
        } finally {
            app.close();
        }
    }

    public void close() {
        try {
            s3.close();
        } catch (Exception ignored) {}
        try {
            sqs.close();
        } catch (Exception ignored) {}
        try {
            ec2.close();
        } catch (Exception ignored) {}
    }

    public void run(String inputFilePath, String outputFilePath, int n, boolean terminateAfterFinish) throws Exception {
        logger.info("Starting local application. input={}, output={}, n={}, terminate={}",
                inputFilePath, outputFilePath, n, terminateAfterFinish);

        // 0. ליצור / לוודא קיום תור תשובות אישי ללוקאל הזה, ולקבל את ה-URL שלו
        String responseQueueUrl = ensureLocalResponseQueue();
        logger.info("Using local response queue: {}", responseQueueUrl);

        // 1. Ensure manager is running
        String managerInstanceId = ensureManagerRunning();
        logger.info("Using manager instance: {}", managerInstanceId);

        // 2. Upload input file to S3
        String taskId = UUID.randomUUID().toString();
        String inputKey = uploadInputFile(taskId, inputFilePath);
        logger.info("Uploaded input file to S3: s3://{}/{}", LocalAppConfig.BUCKET_NAME, inputKey);

        // 3. Send task request message to manager
        sendTaskRequest(taskId, inputKey, n, terminateAfterFinish, responseQueueUrl);
        logger.info("Sent task request to manager. taskId={}", taskId);

        // 4. Wait for summary response (מהתור האישי של הלוקאל)
        SummaryResponse response = waitForSummary(taskId, responseQueueUrl);
        if (response == null) {
            throw new IllegalStateException("Did not receive response for taskId=" + taskId);
        }
        if (response.getError() != null) {
            throw new RuntimeException("Manager reported error: " + response.getError());
        }

        logger.info("Received summary response. summaryS3Key={}", response.getSummaryS3Key());

        // 5. Download summary HTML from S3
        downloadSummaryToLocal(response.getSummaryS3Key(), outputFilePath);
        logger.info("Saved summary to local file: {}", outputFilePath);
    }

    // ---------- NEW: ENSURE LOCAL RESPONSE QUEUE EXISTS ----------

    /**
     * יוצר (או מאמת) את תור התשובות האישי של ה-Local, ומחזיר את ה-URL שלו.
     * SQS createQueue הוא אידמפוטנטי (אם התור כבר קיים עם אותו שם ואותן הגדרות,
     * הוא יחזיר את אותו URL).
     */
    private String ensureLocalResponseQueue() {
        String queueName = LocalAppConfig.LOCAL_RESPONSE_QUEUE_NAME;

        CreateQueueRequest createReq = CreateQueueRequest.builder()
                .queueName(queueName)
                .build();

        CreateQueueResponse createRes = sqs.createQueue(createReq);
        String queueUrl = createRes.queueUrl();
        logger.info("Local response queue ready. name={}, url={}", queueName, queueUrl);
        return queueUrl;
    }

    // ---------- STEP 1: ENSURE MANAGER RUNNING ----------

    private String ensureManagerRunning() {
        DescribeInstancesRequest describeReq = DescribeInstancesRequest.builder()
                .filters(
                        Filter.builder()
                                .name("tag:" + LocalAppConfig.MANAGER_TAG_KEY_ROLE)
                                .values(LocalAppConfig.MANAGER_TAG_VALUE_MANAGER)
                                .build(),
                        Filter.builder()
                                .name("instance-state-name")
                                .values("pending", "running")
                                .build()
                )
                .build();

        DescribeInstancesResponse describeRes = ec2.describeInstances(describeReq);
        for (Reservation reservation : describeRes.reservations()) {
            for (Instance instance : reservation.instances()) {
                logger.info("Found existing manager instance: {} (state={})",
                        instance.instanceId(), instance.state().nameAsString());
                return instance.instanceId();
            }
        }

        logger.info("No running manager found. Launching new manager instance...");

        String userDataScript = buildManagerUserDataScript();
        String userDataBase64 = Base64.getEncoder().encodeToString(userDataScript.getBytes());

        RunInstancesRequest runReq = RunInstancesRequest.builder()
                .imageId(LocalAppConfig.MANAGER_AMI_ID)
                .instanceType(InstanceType.fromValue(LocalAppConfig.MANAGER_INSTANCE_TYPE))
                .minCount(1)
                .maxCount(1)
                .securityGroupIds(LocalAppConfig.MANAGER_SECURITY_GROUP_ID)
                .keyName(LocalAppConfig.MANAGER_KEY_NAME)
                .iamInstanceProfile(IamInstanceProfileSpecification.builder()
                        .arn(LocalAppConfig.MANAGER_IAM_INSTANCE_PROFILE_ARN)
                        .build())
                .userData(userDataBase64)
                .tagSpecifications(
                        TagSpecification.builder()
                                .resourceType(ResourceType.INSTANCE)
                                .tags(
                                        software.amazon.awssdk.services.ec2.model.Tag.builder()
                                                .key(LocalAppConfig.MANAGER_TAG_KEY_ROLE)
                                                .value(LocalAppConfig.MANAGER_TAG_VALUE_MANAGER)
                                                .build(),
                                        software.amazon.awssdk.services.ec2.model.Tag.builder()
                                                .key("Name")
                                                .value("ManagerInstance")
                                                .build()
                                )
                                .build()
                )
                .build();

        RunInstancesResponse runRes = ec2.runInstances(runReq);
        String instanceId = runRes.instances().get(0).instanceId();
        logger.info("Launched new manager instance: {}", instanceId);
        return instanceId;
    }

    private String buildManagerUserDataScript() {
        return "#!/bin/bash\n" +
                "cd /home/ubuntu/app\n" +
                "java -jar manager.jar > manager.log 2>&1 &\n";
    }

    // ---------- STEP 2: UPLOAD INPUT FILE TO S3 ----------

    private String uploadInputFile(String taskId, String inputFilePath) {
        File file = new File(inputFilePath);
        if (!file.exists()) {
            throw new IllegalArgumentException("Input file does not exist: " + inputFilePath);
        }

        String fileName = file.getName();
        String key = LocalAppConfig.INPUT_PREFIX + taskId + "_" + fileName;

        PutObjectRequest putReq = PutObjectRequest.builder()
                .bucket(LocalAppConfig.BUCKET_NAME)
                .key(key)
                .build();

        s3.putObject(putReq, RequestBody.fromFile(file));
        return key;
    }

    // ---------- STEP 3: SEND TASK REQUEST TO MANAGER ----------

    private void sendTaskRequest(String taskId,
                                 String inputS3Key,
                                 int n,
                                 boolean terminateAfterFinish,
                                 String responseQueueUrl) throws IOException {

        TaskRequest request = new TaskRequest(
                taskId,
                inputS3Key,
                n,
                terminateAfterFinish,
                responseQueueUrl
        );

        String body = objectMapper.writeValueAsString(request);

        SendMessageRequest sendReq = SendMessageRequest.builder()
                .queueUrl(LocalAppConfig.MANAGER_REQUEST_QUEUE_URL)
                .messageBody(body)
                .build();

        sqs.sendMessage(sendReq);
    }

    // ---------- STEP 4: WAIT FOR SUMMARY RESPONSE FROM PERSONAL QUEUE ----------

    private SummaryResponse waitForSummary(String taskId, String responseQueueUrl) throws IOException {
        long deadline = System.currentTimeMillis() + LocalAppConfig.RESPONSE_WAIT_TIMEOUT_MS;

        while (System.currentTimeMillis() < deadline) {
            ReceiveMessageRequest recvReq = ReceiveMessageRequest.builder()
                    .queueUrl(responseQueueUrl)
                    .maxNumberOfMessages(5)
                    .waitTimeSeconds(20) // long polling
                    .visibilityTimeout(30)
                    .build();

            ReceiveMessageResponse recvRes = sqs.receiveMessage(recvReq);
            List<Message> messages = recvRes.messages();

            if (messages == null || messages.isEmpty()) {
                continue;
            }

            for (Message msg : messages) {
                String body = msg.body();
                SummaryResponse response;
                try {
                    response = objectMapper.readValue(body, SummaryResponse.class);
                } catch (Exception e) {
                    logger.warn("Failed to parse summary response message. body={}", body, e);
                    // שגיאה בפענוח – אפשר למחוק או להשאיר, תלוי במדיניות; נשאיר כדי לא לאבד מידע
                    continue;
                }

                if (taskId.equals(response.getTaskId())) {
                    // זו התשובה שלנו → מוחקים ומחזירים
                    deleteMessage(responseQueueUrl, msg);
                    return response;
                } else {
                    // במקרה הזה, מאחר שזה תור אישי לכל Local, זה לא אמור לקרות.
                    logger.warn("Received response for different taskId on same local queue. expected={}, got={}",
                            taskId, response.getTaskId());
                    // אפשר למחוק אם את יודעת שלא תרצי אותה שוב:
                    // deleteMessage(responseQueueUrl, msg);
                }
            }
        }

        logger.error("Timeout while waiting for summary for taskId={}", taskId);
        return null;
    }

    private void deleteMessage(String queueUrl, Message msg) {
        DeleteMessageRequest delReq = DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(msg.receiptHandle())
                .build();
        sqs.deleteMessage(delReq);
    }

    // ---------- STEP 5: DOWNLOAD SUMMARY FROM S3 ----------

    private void downloadSummaryToLocal(String summaryKey, String outputFilePath) throws IOException {
        GetObjectRequest getReq = GetObjectRequest.builder()
                .bucket(LocalAppConfig.BUCKET_NAME)
                .key(summaryKey)
                .build();

        ResponseInputStream<GetObjectResponse> s3Object = s3.getObject(getReq);

        File outFile = Paths.get(outputFilePath).toFile();
        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = s3Object.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
        }
        s3Object.close();
    }
}
