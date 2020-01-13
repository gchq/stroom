package stroom.task.api;

import stroom.task.shared.Task;
import stroom.util.shared.VoidResult;

public final class GenericServerTask extends ServerTask<VoidResult> {
    private final String message;
    private volatile transient Runnable runnable;

    private GenericServerTask(final Task<?> parentTask,
                              final String taskName,
                              final String message) {
        super(parentTask);
        this.message = message;
        setTaskName(taskName);
    }

    public static GenericServerTask create(final String taskName,
                                           final String message) {
        return new GenericServerTask(null, taskName, message);
    }

    public static GenericServerTask create(final Task<?> parentTask,
                                           final String taskName,
                                           final String message) {
        return new GenericServerTask(parentTask, taskName, message);
    }

    public Runnable getRunnable() {
        return runnable;
    }

    public void setRunnable(final Runnable runnable) {
        this.runnable = runnable;
    }

    public String getMessage() {
        return message;
    }
}
