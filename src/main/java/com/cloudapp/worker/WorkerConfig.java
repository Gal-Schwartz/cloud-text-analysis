package com.cloudapp.worker;

import software.amazon.awssdk.regions.Region;

public class WorkerConfig {

    // ---------- AWS REGION ----------
    public static final Region REGION = Region.US_EAST_1; 

    // ---------- S3 ----------
    public static final String BUCKET_NAME = "cloud-text-analysis"; // אותו באקט כמו ב-Local/Manager
    public static final String OUTPUT_PREFIX = "worker-output/";  

    // ---------- WORKER QUEUES ----------
    // תור משימות שה-Manager שולח ל-Workers
    public static final String WORKER_TASK_QUEUE_URL =
            "https://sqs.us-east-1.amazonaws.com/675475782430/worker-tasks"; 

    // תור תוצאות שה-Workers שולחים אל ה-Manager
    public static final String WORKER_RESULT_QUEUE_URL =
            "https://sqs.us-east-1.amazonaws.com/675475782430/worker-results"; 

    // ---------- WORKER EC2 ----------
    // (אלה משמשים את ה-Manager כשמרים Workers, ה-Worker עצמו לא צריך אותם בזמן ריצה)
    public static final String WORKER_AMI_ID = "ami-0abcdef0123456789"; // TODO
    public static final String WORKER_INSTANCE_TYPE = "t2.micro";
    public static final String WORKER_SECURITY_GROUP_ID = "sg-03d44604c58553b54"; 
    public static final String WORKER_KEY_NAME = "mykey"; 
    public static final String WORKER_IAM_INSTANCE_PROFILE_ARN =
            "arn:aws:iam::675475782430:role/LabRole"; 

    public static final String WORKER_TAG_KEY_ROLE = "Role";
    public static final String WORKER_TAG_VALUE_WORKER = "Worker";
}
