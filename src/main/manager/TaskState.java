package cloud.assignment.manager;

import cloud.assignment.manager.dto.WorkerResultMessage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class TaskState {

    private final String taskId;
    private final String responseQueueUrl;
    private final boolean terminateAfterFinish;

    private final int totalItems;
    private final AtomicInteger completedCount = new AtomicInteger(0);

    // per URL: WorkerResultMessage
    private final Map<String, WorkerResultMessage> resultsByUrl = new ConcurrentHashMap<>();

    public TaskState(String taskId,
                     String responseQueueUrl,
                     boolean terminateAfterFinish,
                     int totalItems) {
        this.taskId = taskId;
        this.responseQueueUrl = responseQueueUrl;
        this.terminateAfterFinish = terminateAfterFinish;
        this.totalItems = totalItems;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getResponseQueueUrl() {
        return responseQueueUrl;
    }

    public boolean isTerminateAfterFinish() {
        return terminateAfterFinish;
    }

    public int getTotalItems() {
        return totalItems;
    }

    public int getCompletedCount() {
        return completedCount.get();
    }

    public Map<String, WorkerResultMessage> getResultsByUrl() {
        return resultsByUrl;
    }

    public void addResult(WorkerResultMessage result) {
        // מניחים שה-URL הוא ייחודי בתוך המשימה
        resultsByUrl.put(result.getUrl(), result);
        completedCount.incrementAndGet();
    }

    public boolean isDone() {
        return completedCount.get() >= totalItems;
    }
}
