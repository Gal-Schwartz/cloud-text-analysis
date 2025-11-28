package com.cloudapp.manager;

import software.amazon.awssdk.regions.Region;

public class ManagerConfig {
    // ---------- AWS REGION ----------
    public static final Region REGION = Region.US_EAST_1; 

    // ---------- S3 ----------
    public static final String BUCKET_NAME = "cloud-text-analysis";
    public static final String SUMMARY_PREFIX = "summaries/";

    // ---------- SQS ----------
    public static final String MANAGER_REQUEST_QUEUE_URL =
            "https://sqs.us-east-1.amazonaws.com/675475782430/manager-requests";


    // ---------- WORKER EC2 ----------
    // (These are used by the Manager when launching Workers, the Worker itself does not need them at runtime)
    public static final String WORKER_AMI_ID = "ami-0c1c02bdf63b28881"; 
    public static final String WORKER_INSTANCE_TYPE = "t3.medium";
    public static final String WORKER_SECURITY_GROUP_ID = "sg-03d44604c58553b54"; 
    public static final String WORKER_KEY_NAME = "mykey"; 
    public static final String WORKER_IAM_INSTANCE_PROFILE_ARN =
            "arn:aws:iam::675475782430:instance-profile/LabInstanceProfile"; 

    // ---------- WORKER QUEUES ----------
    // Task queue that the Manager sends to Workers
    public static final String WORKER_TASK_QUEUE_URL =
            "https://sqs.us-east-1.amazonaws.com/675475782430/worker-tasks"; 

    // Results queue that the Workers send to the Manager
    public static final String WORKER_RESULT_QUEUE_URL =
            "https://sqs.us-east-1.amazonaws.com/675475782430/worker-results"; 

    public static final String WORKER_TAG_KEY_ROLE = "Role";
    public static final String WORKER_TAG_VALUE_WORKER = "Worker";
}
