package com.cloudapp.worker;

import software.amazon.awssdk.regions.Region;

public class WorkerConfig {

    // ---------- AWS REGION ----------
    public static final Region REGION = Region.US_EAST_1; 

    // ---------- S3 ----------
    public static final String BUCKET_NAME = "cloud-text-analysis"; 
    public static final String OUTPUT_PREFIX = "worker-output/";  

    // ---------- WORKER QUEUES ----------
    public static final String WORKER_TASK_QUEUE_URL =
            "https://sqs.us-east-1.amazonaws.com/675475782430/worker-tasks"; 

    public static final String WORKER_RESULT_QUEUE_URL =
            "https://sqs.us-east-1.amazonaws.com/675475782430/worker-results"; 

}
