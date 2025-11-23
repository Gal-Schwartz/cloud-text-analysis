package com.cloudapp.manager;

public class WorkerTaskMessage {

    private String taskId;         // מזהה המשימה (אותו taskId של ה-Local)
    private String url;           // ה-URL של קובץ הטקסט
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
