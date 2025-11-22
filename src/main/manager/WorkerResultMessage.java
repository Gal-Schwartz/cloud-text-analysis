package cloud.assignment.manager.dto;

public class WorkerResultMessage {

    private String taskId;          // איזו משימה (batch) זה שייך
    private String url;             // ה-URL המקורי של קובץ הטקסט
    private String analysisType;    // POS | CONSTITUENCY | DEPENDENCY
    private String outputS3Key;     // איפה ה-Worker שמר את הפלט ב-S3 (אם הצליח)
    private String error;           // תיאור שגיאה, אם נכשל (null אם הכול טוב)

    public WorkerResultMessage() {
    }

    public WorkerResultMessage(String taskId,
                               String url,
                               String analysisType,
                               String outputS3Key,
                               String error) {
        this.taskId = taskId;
        this.url = url;
        this.analysisType = analysisType;
        this.outputS3Key = outputS3Key;
        this.error = error;
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

    public String getOutputS3Key() {
        return outputS3Key;
    }

    public String getError() {
        return error;
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

    public void setOutputS3Key(String outputS3Key) {
        this.outputS3Key = outputS3Key;
    }

    public void setError(String error) {
        this.error = error;
    }
}
