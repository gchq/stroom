package stroom.task.server;

import stroom.util.shared.Task;
import stroom.util.shared.VoidResult;
import stroom.util.task.ServerTask;

public final class GenericServerTask extends ServerTask<VoidResult> {
    private final String message;
    private volatile transient Runnable runnable;

    private GenericServerTask(final Task<?> parentTask, final String sessionId, final String userName,
                              final String taskName, final String message) {
        super(parentTask, sessionId, userName);
        this.message = message;
        setTaskName(taskName);
    }

    public static GenericServerTask create(final String taskName, final String message) {
        return new GenericServerTask(null, null, ServerTask.INTERNAL_PROCESSING_USER, taskName, message);
    }

    public static GenericServerTask create(final Task<?> parentTask,
                                           final String taskName, final String message) {
        if (parentTask == null) {
            return new GenericServerTask(null, null, ServerTask.INTERNAL_PROCESSING_USER, taskName, message);
        }

        return new GenericServerTask(parentTask, parentTask.getSessionId(), parentTask.getUserId(), taskName, message);
    }

    public static GenericServerTask create(final Task<?> parentTask, final String sessionId, final String userName,
                                           final String taskName, final String message) {
        return new GenericServerTask(parentTask, sessionId, userName, taskName, message);
    }

    Runnable getRunnable() {
        return runnable;
    }

    public void setRunnable(final Runnable runnable) {
        this.runnable = runnable;
    }

    String getMessage() {
        return message;
    }
}
