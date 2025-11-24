package com.cloudapp.manager;

import com.cloudapp.common.SummaryResponse;
import com.cloudapp.common.TaskRequest;
import com.cloudapp.common.WorkerResultMessage;
import com.cloudapp.common.WorkerTaskMessage;
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

public class ManagerApplication {

    private static final Logger logger = LoggerFactory.getLogger(ManagerApplication.class);

    private final S3Client s3;
    private final SqsClient sqs;
    private final Ec2Client ec2;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HtmlSummaryBuilder htmlSummaryBuilder = new HtmlSummaryBuilder();

    // taskId -> TaskState
    private final ConcurrentMap<String, TaskState> tasks = new ConcurrentHashMap<>();

    // Termination flag 
    private volatile boolean terminateRequested = false;

    // Thread pools
    private final ExecutorService taskExecutor = Executors.newCachedThreadPool();

    public ManagerApplication(Region region) {
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
        ManagerApplication manager = new ManagerApplication(ManagerConfig.REGION);
        try {
            manager.run();
        } catch (Exception e) {
            logger.error("Manager crashed", e);
        } finally {
            manager.shutdown();
        }
    }

    public void shutdown() {
        try {
            taskExecutor.shutdown();
        } catch (Exception ignored) {}
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

    public void run() {
        logger.info("Manager started, listening for tasks from local applications...");

        Thread localRequestsThread = new Thread(this::pollLocalRequests, "LocalRequestsPoller");
        Thread workerResultsThread = new Thread(this::pollWorkerResults, "WorkerResultsPoller");

        localRequestsThread.start();
        workerResultsThread.start();

        try {
            localRequestsThread.join();
            workerResultsThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        logger.info("Manager main loop finished.");
    }

    // ==================== POLLING FROM LOCAL (TASK REQUESTS) ====================

    private void pollLocalRequests() {
        while (!terminateRequested) {
            try {
                ReceiveMessageRequest recvReq = ReceiveMessageRequest.builder()
                        .queueUrl(ManagerConfig.MANAGER_REQUEST_QUEUE_URL)
                        .maxNumberOfMessages(5)
                        .waitTimeSeconds(20)  // long polling
                        .visibilityTimeout(60)
                        .build();

                ReceiveMessageResponse recvRes = sqs.receiveMessage(recvReq);
                List<Message> messages = recvRes.messages();

                if (messages == null || messages.isEmpty()) {
                    continue;
                }

                for (Message msg : messages) {
                    String body = msg.body();
                    TaskRequest request;
                    try {
                        request = objectMapper.readValue(body, TaskRequest.class);
                    } catch (Exception e) {
                        logger.error("Failed to parse TaskRequest: {}", body, e);
                        deleteMessage(ManagerConfig.MANAGER_REQUEST_QUEUE_URL, msg);
                        continue;
                    }

                    if (terminateRequested && !request.isTerminateAfterFinish()) {
                        logger.warn("Received new task after terminate requested. Ignoring taskId={}", request.getTaskId());
                        deleteMessage(ManagerConfig.MANAGER_REQUEST_QUEUE_URL, msg);
                        continue;
                    }

                    logger.info("Received new task from local. taskId={}, inputKey={}, n={}, terminateAfterFinish={}",
                            request.getTaskId(), request.getInputS3Key(), request.getN(), request.isTerminateAfterFinish());

                    final Message msgToDelete = msg;
                    taskExecutor.submit(() -> {
                        try {
                            handleTaskRequest(request);
                            // Delete the TaskRequest message after starting processing
                            deleteMessage(ManagerConfig.MANAGER_REQUEST_QUEUE_URL, msgToDelete);
                        } catch (Exception e) {
                            logger.error("Error while handling taskId={}", request.getTaskId(), e);
                        }
                    });

                    if (request.isTerminateAfterFinish()) {
                        terminateRequested = true;
                        logger.info("Terminate requested by taskId={}", request.getTaskId());
                    }
                }

            } catch (Exception e) {
                logger.error("Exception in pollLocalRequests loop", e);
            }
        }

        logger.info("Stopped polling local requests (terminateRequested=true)");
    }

    private void handleTaskRequest(TaskRequest request) throws IOException {
        String taskId = request.getTaskId();

        // 1. Download the input file from S3
        List<InputLine> inputLines = downloadAndParseInputFile(request.getInputS3Key());

        if (inputLines.isEmpty()) {
            logger.warn("Task {} has no input URLs. Sending empty summary.", taskId);
            TaskState emptyState = new TaskState(taskId, request.getResponseQueueUrl(),
                    request.isTerminateAfterFinish(), 0);
            tasks.put(taskId, emptyState);
            sendSummaryAndCleanup(emptyState);
            return;
        }

        // 2. Create TaskState
        TaskState state = new TaskState(
                taskId,
                request.getResponseQueueUrl(),
                request.isTerminateAfterFinish(),
                inputLines.size()
        );
        tasks.put(taskId, state);

        // 3. Send tasks to Workers as WorkerTaskMessage
        for (InputLine line : inputLines) {
            WorkerTaskMessage workerTask = new WorkerTaskMessage(
                    taskId,
                    line.url,
                    line.analysisType
            );
            String body = objectMapper.writeValueAsString(workerTask);

            SendMessageRequest sendReq = SendMessageRequest.builder()
                    .queueUrl(ManagerConfig.WORKER_TASK_QUEUE_URL)
                    .messageBody(body)
                    .build();

            sqs.sendMessage(sendReq);
        }

        logger.info("Task {}: sent {} worker tasks to queue {}", taskId, inputLines.size(),
                ManagerConfig.WORKER_TASK_QUEUE_URL);

        // 4. Ensure enough Workers are running
        ensureEnoughWorkers(inputLines.size(), request.getN());
    }

    // Representation of one line in the input file
    private static class InputLine {
        final String analysisType;
        final String url;

        InputLine(String analysisType, String url) {
            this.analysisType = analysisType;
            this.url = url;
        }
    }

    private List<InputLine> downloadAndParseInputFile(String inputS3Key) throws IOException {
        GetObjectRequest getReq = GetObjectRequest.builder()
                .bucket(ManagerConfig.BUCKET_NAME)
                .key(inputS3Key)
                .build();

        try (ResponseInputStream<GetObjectResponse> s3Object = s3.getObject(getReq);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(s3Object, StandardCharsets.UTF_8))) {

            List<InputLine> result = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split("\t");
                if (parts.length != 2) {
                    logger.warn("Invalid input line in {}: {}", inputS3Key, line);
                    continue;
                }
                String analysisType = parts[0].trim(); // "POS" | "CONSTITUENCY" | "DEPENDENCY"
                String url = parts[1].trim();
                result.add(new InputLine(analysisType, url));
            }
            return result;
        }
    }

    // ==================== WORKERS MANAGEMENT ====================

    private void ensureEnoughWorkers(int messagesCount, int n) {
        // How many Workers are needed for this task (ceil(messagesCount / n))
        int required = (messagesCount + n - 1) / n;
        logger.info("ensureEnoughWorkers: messagesCount={}, n={}, requiredWorkers={}",
                messagesCount, n, required);

        // Count how many Workers are active (tag: Role=Worker, state in {pending, running})
        DescribeInstancesRequest describeReq = DescribeInstancesRequest.builder()
                .filters(
                        Filter.builder()
                                .name("tag:" +  ManagerConfig.WORKER_TAG_KEY_ROLE)
                                .values(ManagerConfig.WORKER_TAG_VALUE_WORKER)
                                .build(),
                        Filter.builder()
                                .name("instance-state-name")
                                .values("pending", "running")
                                .build()
                )
                .build();

        DescribeInstancesResponse describeRes = ec2.describeInstances(describeReq);
        int active = 0;
        for (Reservation res : describeRes.reservations()) {
            active += res.instances().size();
        }

        int toLaunch = Math.max(0, required - active);

        // Do not exceed the total limit of 19 instances (as per the instructions)
        int maxTotal = 19;
        int currentTotal = countAllInstances();
        int maxAdditional = Math.max(0, maxTotal - currentTotal);

        toLaunch = Math.min(toLaunch, maxAdditional);

        if (toLaunch <= 0) {
            logger.info("No need to launch new workers. active={}, required={}, currentTotal={}",
                    active, required, currentTotal);
            return;
        }

        logger.info("Launching {} new worker instances", toLaunch);
        launchWorkers(toLaunch);
    }

    private int countAllInstances() {
        DescribeInstancesRequest req = DescribeInstancesRequest.builder()
                .build();

        DescribeInstancesResponse res = ec2.describeInstances(req);
        int count = 0;
        for (Reservation r : res.reservations()) {
            for (Instance i : r.instances()) {
                String state = i.state().nameAsString();
                if (!"terminated".equalsIgnoreCase(state) &&
                        !"shutting-down".equalsIgnoreCase(state)) {
                    count++;
                }
            }
        }
        return count;
    }

    private void launchWorkers(int count) {
        String userDataScript = buildWorkerUserDataScript();
        String userDataBase64 = Base64.getEncoder().encodeToString(userDataScript.getBytes(StandardCharsets.UTF_8));

        RunInstancesRequest runReq = RunInstancesRequest.builder()
                .imageId(ManagerConfig.WORKER_AMI_ID)
                .instanceType(InstanceType.fromValue(ManagerConfig.WORKER_INSTANCE_TYPE))
                .minCount(count)
                .maxCount(count)
                .securityGroupIds(ManagerConfig.WORKER_SECURITY_GROUP_ID)
                .keyName(ManagerConfig.WORKER_KEY_NAME)
                .iamInstanceProfile(IamInstanceProfileSpecification.builder()
                        .arn(ManagerConfig.WORKER_IAM_INSTANCE_PROFILE_ARN)
                        .build())
                .userData(userDataBase64)
                .tagSpecifications(
                        TagSpecification.builder()
                                .resourceType(ResourceType.INSTANCE)
                                .tags(
                                        software.amazon.awssdk.services.ec2.model.Tag.builder()
                                                .key(ManagerConfig.WORKER_TAG_KEY_ROLE)
                                                .value(ManagerConfig.WORKER_TAG_VALUE_WORKER)
                                                .build(),
                                        software.amazon.awssdk.services.ec2.model.Tag.builder()
                                                .key("Name")
                                                .value("WorkerInstance")
                                                .build()
                                )
                                .build()
                )
                .build();

        RunInstancesResponse resp = ec2.runInstances(runReq);
        for (Instance inst : resp.instances()) {
            logger.info("Launched worker instance: {}", inst.instanceId());
        }
    }

    private String buildWorkerUserDataScript() {
        // Must match the location of the Worker jar in the AMI
        return "#!/bin/bash\n" +
                "cd /home/ubuntu/app\n" +
                "java -jar worker.jar > worker.log 2>&1 &\n";
    }

    // ==================== POLLING FROM WORKERS (RESULTS) ====================

    private void pollWorkerResults() {
        while (true) {
            try {
                ReceiveMessageRequest recvReq = ReceiveMessageRequest.builder()
                        .queueUrl(ManagerConfig.WORKER_RESULT_QUEUE_URL)
                        .maxNumberOfMessages(10)
                        .waitTimeSeconds(20)
                        .visibilityTimeout(60)
                        .build();

                ReceiveMessageResponse recvRes = sqs.receiveMessage(recvReq);
                List<Message> messages = recvRes.messages();

                if (messages == null || messages.isEmpty()) {
                    if (terminateRequested && tasks.isEmpty()) {
                        logger.info("No more tasks and terminateRequested=true. Stopping worker results polling.");
                        break;
                    }
                    continue;
                }

                for (Message msg : messages) {
                    String body = msg.body();
                    WorkerResultMessage result;
                    try {
                        result = objectMapper.readValue(body, WorkerResultMessage.class);
                    } catch (Exception e) {
                        logger.error("Failed to parse WorkerResultMessage: {}", body, e);
                        deleteMessage(ManagerConfig.WORKER_RESULT_QUEUE_URL, msg);
                        continue;
                    }

                    handleWorkerResult(result);
                    deleteMessage(ManagerConfig.WORKER_RESULT_QUEUE_URL, msg);
                }

            } catch (Exception e) {
                logger.error("Exception in pollWorkerResults loop", e);
            }
        }

        tryTerminateAllWorkersAndSelf();
    }

    private void handleWorkerResult(WorkerResultMessage result) {
        TaskState state = tasks.get(result.getTaskId());
        if (state == null) {
            logger.warn("Received worker result for unknown taskId={}", result.getTaskId());
            return;
        }

        state.addResult(result);
        logger.info("Task {}: received result for url={}, {}/{} done",
                state.getTaskId(),
                result.getUrl(),
                state.getCompletedCount(),
                state.getTotalItems());

        if (state.isDone()) {
            logger.info("Task {} completed. Building summary...", state.getTaskId());
            try {
                sendSummaryAndCleanup(state);
            } catch (Exception e) {
                logger.error("Failed to build/send summary for taskId={}", state.getTaskId(), e);
            } finally {
                tasks.remove(state.getTaskId());
            }
        }
    }

    private void sendSummaryAndCleanup(TaskState state) throws IOException {
        // 1. Build HTML
        String html = htmlSummaryBuilder.buildHtml(
                state.getResultsByUrl().values(),
                ManagerConfig.BUCKET_NAME
        );

        // 2. Upload HTML to S3
        String summaryKey = ManagerConfig.SUMMARY_PREFIX
                + state.getTaskId()
                + "_summary_"
                + Instant.now().toEpochMilli()
                + ".html";

        PutObjectRequest putReq = PutObjectRequest.builder()
                .bucket(ManagerConfig.BUCKET_NAME)
                .key(summaryKey)
                .contentType("text/html; charset=UTF-8")
                .build();

        s3.putObject(putReq, RequestBody.fromString(html, StandardCharsets.UTF_8));

        logger.info("Task {}: summary uploaded to s3://{}/{}",
                state.getTaskId(), ManagerConfig.BUCKET_NAME, summaryKey);

        // 3. Send SummaryResponse to Local as the personal response
        SummaryResponse response = new SummaryResponse(
                state.getTaskId(),
                summaryKey,
                null  
        );

        String body = objectMapper.writeValueAsString(response);

        SendMessageRequest sendReq = SendMessageRequest.builder()
                .queueUrl(state.getResponseQueueUrl())
                .messageBody(body)
                .build();

        sqs.sendMessage(sendReq);

        logger.info("Task {}: summary response sent to local queue {}", state.getTaskId(), state.getResponseQueueUrl());
    }

    // ==================== TERMINATION ====================

    private void tryTerminateAllWorkersAndSelf() {
        logger.info("Terminating worker instances...");

        DescribeInstancesRequest describeReq = DescribeInstancesRequest.builder()
                .filters(
                        Filter.builder()
                                .name("tag:" + ManagerConfig.WORKER_TAG_KEY_ROLE)
                                .values(ManagerConfig.WORKER_TAG_VALUE_WORKER)
                                .build(),
                        Filter.builder()
                                .name("instance-state-name")
                                .values("pending", "running", "stopping", "stopped")
                                .build()
                )
                .build();

        DescribeInstancesResponse describeRes = ec2.describeInstances(describeReq);
        List<String> workerIds = new ArrayList<>();
        for (Reservation r : describeRes.reservations()) {
            for (Instance i : r.instances()) {
                workerIds.add(i.instanceId());
            }
        }

        if (!workerIds.isEmpty()) {
            TerminateInstancesRequest termReq = TerminateInstancesRequest.builder()
                    .instanceIds(workerIds)
                    .build();
            ec2.terminateInstances(termReq);
            logger.info("Requested termination of {} worker instances", workerIds.size());
        } else {
            logger.info("No worker instances to terminate.");
        }

        try {
            String myInstanceId = fetchMyInstanceId();
            if (myInstanceId != null) {
                logger.info("Requesting self-termination for manager instance: {}", myInstanceId);
                TerminateInstancesRequest termSelf = TerminateInstancesRequest.builder()
                        .instanceIds(myInstanceId)
                        .build();
                ec2.terminateInstances(termSelf);
            }
        } catch (Exception e) {
            logger.error("Failed to terminate manager instance", e);
        }
    }

    private String fetchMyInstanceId() {
        try {
            java.net.URL url = new java.net.URL("http://169.254.169.254/latest/meta-data/instance-id");
            try (java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
                return r.readLine();
            }
        } catch (IOException e) {
            logger.error("Failed to fetch manager instance-id from metadata", e);
            return null;
        }
    }

    // ==================== UTIL ====================

    private void deleteMessage(String queueUrl, Message msg) {
        DeleteMessageRequest delReq = DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(msg.receiptHandle())
                .build();
        sqs.deleteMessage(delReq);
    }
}
