package com.cloudapp.localapp;

import software.amazon.awssdk.regions.Region;

public class LocalAppConfig {

    // ---------- AWS REGION ----------
    public static final Region REGION = Region.US_EAST_1; 

    // ---------- S3 ----------
    public static final String BUCKET_NAME = "cloud-text-analysis";
    public static final String INPUT_PREFIX = "inputs/";
    public static final String SUMMARY_PREFIX = "summaries/";

    // ---------- SQS ----------
    public static final String MANAGER_REQUEST_QUEUE_URL =
            "https://sqs.us-east-1.amazonaws.com/675475782430/manager-requests";

    public static final String LOCAL_RESPONSE_QUEUE_NAME = "local-responses-i"; 

    // ---------- EC2 / MANAGER ----------
    public static final String MANAGER_AMI_ID = "ami-0123456789abcdef0"; // TODO
    public static final String MANAGER_INSTANCE_TYPE = "t2.micro";
    public static final String MANAGER_SECURITY_GROUP_ID = "sg-03d44604c58553b54"; 
    public static final String MANAGER_KEY_NAME = "mykey"; 
    public static final String MANAGER_IAM_INSTANCE_PROFILE_ARN =
            "arn:aws:iam::675475782430:role/LabRole"; 

    public static final String MANAGER_TAG_KEY_ROLE = "Role";
    public static final String MANAGER_TAG_VALUE_MANAGER = "Manager";

    // ---------- TIMEOUTS ----------
    public static final long RESPONSE_WAIT_TIMEOUT_MS = 10 * 60 * 1000; // 10 minutes
}
