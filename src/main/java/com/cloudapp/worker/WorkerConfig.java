package com.cloudapp.worker;

public class WorkerConfig {
    // ---------- WORKER QUEUES ----------

// תור משימות שה-Manager שולח ל-Workers
public static final String WORKER_TASK_QUEUE_URL =
        "https://sqs.us-east-1.amazonaws.com/123456789012/worker-tasks"; // TODO

// תור תוצאות שה-Workers שולחים אל ה-Manager
public static final String WORKER_RESULT_QUEUE_URL =
        "https://sqs.us-east-1.amazonaws.com/123456789012/worker-results"; // TODO

// ---------- WORKER EC2 ----------

public static final String WORKER_AMI_ID = "ami-0abcdef0123456789"; // TODO
public static final String WORKER_INSTANCE_TYPE = "t2.micro";
public static final String WORKER_SECURITY_GROUP_ID = "sg-0abcdef0123456789"; // TODO
public static final String WORKER_KEY_NAME = "your-keypair-name"; // TODO
public static final String WORKER_IAM_INSTANCE_PROFILE_ARN =
        "arn:aws:iam::123456789012:instance-profile/WorkerRole"; // TODO

public static final String WORKER_TAG_KEY_ROLE = "Role";
public static final String WORKER_TAG_VALUE_WORKER = "Worker";
}
