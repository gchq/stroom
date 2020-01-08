package stroom.task.server;

import stroom.security.ProcessingUserIdentity;
import stroom.security.shared.UserIdentity;
import stroom.util.shared.Task;
import stroom.util.shared.VoidResult;
import stroom.util.task.ServerTask;

public final class GenericServerTask extends ServerTask<VoidResult> {
    private final String message;
    private volatile transient Runnable runnable;

    private GenericServerTask(final Task<?> parentTask, final UserIdentity userIdentity,
                              final String taskName, final String message) {
        super(parentTask, userIdentity);
        this.message = message;
        setTaskName(taskName);
    }

    public static GenericServerTask create(final String taskName, final String message) {
        return new GenericServerTask(null, ProcessingUserIdentity.INSTANCE, taskName, message);
    }

    public static GenericServerTask create(final Task<?> parentTask,
                                           final String taskName, final String message) {
        if (parentTask == null) {
            return new GenericServerTask(null, ProcessingUserIdentity.INSTANCE, taskName, message);
        }

        return new GenericServerTask(parentTask, parentTask.getUserIdentity(), taskName, message);
    }

    public static GenericServerTask create(final Task<?> parentTask, final UserIdentity userIdentity,
                                           final String taskName, final String message) {
        return new GenericServerTask(parentTask, userIdentity, taskName, message);
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
