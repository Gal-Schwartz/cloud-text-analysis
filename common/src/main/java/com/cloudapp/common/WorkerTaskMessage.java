package com.cloudapp.common;

public class WorkerTaskMessage {

    private String taskId;        // Local task ID
    private String url;           // URL of the text file
    private String analysisType;  // "POS" | "CONSTITUENCY" | "DEPENDENCY"

    public WorkerTaskMessage() {
    }

    public WorkerTaskMessage(String taskId, String url, String analysisType) {
        this.taskId = taskId;
        this.url = url;
        this.analysisType = analysisType;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getUrl() {
        return url;
    }

    public String getAnalysisType() {
        return analysisType;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setAnalysisType(String analysisType) {
        this.analysisType = analysisType;
    }
}
