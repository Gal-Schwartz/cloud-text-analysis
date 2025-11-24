package com.cloudapp.common;  

public class TaskRequest {

    private String taskId;
    private String inputS3Key;
    private int n;
    private boolean terminateAfterFinish;
    private String responseQueueUrl;

    public TaskRequest() {
    }

    public TaskRequest(String taskId,
                       String inputS3Key,
                       int n,
                       boolean terminateAfterFinish,
                       String responseQueueUrl) {
        this.taskId = taskId;
        this.inputS3Key = inputS3Key;
        this.n = n;
        this.terminateAfterFinish = terminateAfterFinish;
        this.responseQueueUrl = responseQueueUrl;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getInputS3Key() {
        return inputS3Key;
    }

    public int getN() {
        return n;
    }

    public boolean isTerminateAfterFinish() {
        return terminateAfterFinish;
    }

    public String getResponseQueueUrl() {
        return responseQueueUrl;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public void setInputS3Key(String inputS3Key) {
        this.inputS3Key = inputS3Key;
    }

    public void setN(int n) {
        this.n = n;
    }

    public void setTerminateAfterFinish(boolean terminateAfterFinish) {
        this.terminateAfterFinish = terminateAfterFinish;
    }

    public void setResponseQueueUrl(String responseQueueUrl) {
        this.responseQueueUrl = responseQueueUrl;
    }
}
