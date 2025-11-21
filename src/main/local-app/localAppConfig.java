
import software.amazon.awssdk.regions.Region;

public class LocalAppConfig {

    // ---------- AWS REGION ----------
    public static final Region REGION = Region.US_EAST_1; 

    // ---------- S3 ----------
    public static final String BUCKET_NAME = "your-bucket-name"; // TODO
    public static final String INPUT_PREFIX = "inputs/";
    public static final String SUMMARY_PREFIX = "summaries/";

    // ---------- SQS ----------
    // התור שאליו ה-Local שולח בקשה ל-Manager
    public static final String MANAGER_REQUEST_QUEUE_URL =
            "https://sqs.us-east-1.amazonaws.com/123456789012/manager-requests"; // TODO

    public static final String LOCAL_RESPONSE_QUEUE_NAME = "local-responses-i"; // TODO

    // ---------- EC2 / MANAGER ----------
    public static final String MANAGER_AMI_ID = "ami-0123456789abcdef0"; // TODO
    public static final String MANAGER_INSTANCE_TYPE = "t2.micro";
    public static final String MANAGER_SECURITY_GROUP_ID = "sg-0123456789abcdef0"; // TODO
    public static final String MANAGER_KEY_NAME = "your-keypair-name"; // TODO
    public static final String MANAGER_IAM_INSTANCE_PROFILE_ARN =
            "arn:aws:iam::123456789012:instance-profile/ManagerRole"; // TODO

    public static final String MANAGER_TAG_KEY_ROLE = "Role";
    public static final String MANAGER_TAG_VALUE_MANAGER = "Manager";

    // זמן מקסימלי להמתנה לתשובה (מיליסקונדות)
    public static final long RESPONSE_WAIT_TIMEOUT_MS = 10 * 60 * 1000; // 10 דקות
}
