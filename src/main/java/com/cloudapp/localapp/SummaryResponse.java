package com.cloudapp.localapp;

public class SummaryResponse {

    private String taskId;
    private String summaryS3Key;
    private String error; // default null if no error

    public SummaryResponse() {
    }

    public SummaryResponse(String taskId, String summaryS3Key, String error) {
        this.taskId = taskId;
        this.summaryS3Key = summaryS3Key;
        this.error = error;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getSummaryS3Key() {
        return summaryS3Key;
    }

    public String getError() {
        return error;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public void setSummaryS3Key(String summaryS3Key) {
        this.summaryS3Key = summaryS3Key;
    }

    public void setError(String error) {
        this.error = error;
    }
}
