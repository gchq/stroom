package stroom.task.server;

import stroom.util.shared.Task;
import stroom.util.shared.VoidResult;
import stroom.util.task.ServerTask;

public final class GenericServerTask extends ServerTask<VoidResult> {
    private final String message;
    private volatile transient Runnable runnable;

    public GenericServerTask(final Task<?> parentTask, final String sessionId, final String userName,
                             final String taskName, final String message) {
        super(parentTask, sessionId, userName);
        this.message = message;
        setTaskName(taskName);
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
