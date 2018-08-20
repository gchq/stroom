package stroom.streamtask;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import javax.inject.Singleton;

@Singleton
public class ProcessConfig implements BatchDeleteConfig {
    private boolean assignTasks = true;
    private boolean createTasks = true;
    private String deleteAge = "1d";
    private String deletePurgeAge = "7d";
    private int deleteBatchSize = 1000;
    private boolean fillTaskQueue = true;
    private int queueSize = 1000;

    @JsonPropertyDescription("Should the master node assign tasks to workers when tasks are requested?")
    public boolean isAssignTasks() {
        return assignTasks;
    }

    public void setAssignTasks(final boolean assignTasks) {
        this.assignTasks = assignTasks;
    }

    @JsonPropertyDescription("Should the master node create new tasks for stream processor filters?")
    public boolean isCreateTasks() {
        return createTasks;
    }

    public void setCreateTasks(final boolean createTasks) {
        this.createTasks = createTasks;
    }

    @JsonPropertyDescription("How long to keep tasks on the database for before deleting them (if they are complete)")
    public String getDeleteAge() {
        return deleteAge;
    }

    public void setDeleteAge(final String deleteAge) {
        this.deleteAge = deleteAge;
    }

    @JsonPropertyDescription("How long a process task is left logically deleted before it is deleted from the database")
    public String getDeletePurgeAge() {
        return deletePurgeAge;
    }

    public void setDeletePurgeAge(final String deletePurgeAge) {
        this.deletePurgeAge = deletePurgeAge;
    }

    @JsonPropertyDescription("How many process tasks we want to try and delete in a single batch")
    public int getDeleteBatchSize() {
        return deleteBatchSize;
    }

    public void setDeleteBatchSize(final int deleteBatchSize) {
        this.deleteBatchSize = deleteBatchSize;
    }

    @JsonPropertyDescription("Should the master node fill the task queue ready for workers to fetch tasks?")
    public boolean isFillTaskQueue() {
        return fillTaskQueue;
    }

    public void setFillTaskQueue(final boolean fillTaskQueue) {
        this.fillTaskQueue = fillTaskQueue;
    }

    @JsonPropertyDescription("Maximum number of tasks to cache ready for processing per processor filter")
    public int getQueueSize() {
        return queueSize;
    }

    public void setQueueSize(final int queueSize) {
        this.queueSize = queueSize;
    }
}
