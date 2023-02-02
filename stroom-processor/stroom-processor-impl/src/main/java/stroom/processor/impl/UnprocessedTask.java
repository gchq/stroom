package stroom.processor.impl;

public class UnprocessedTask {
    private final long taskId;
    private final long metaId;

    public UnprocessedTask(final long taskId, final long metaId) {
        this.taskId = taskId;
        this.metaId = metaId;
    }

    public long getTaskId() {
        return taskId;
    }

    public long getMetaId() {
        return metaId;
    }
}
