package stroom.task.server;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.security.ProcessingUserIdentity;
import stroom.security.SecurityContext;
import stroom.security.shared.UserIdentity;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;
import stroom.util.shared.Task;
import stroom.util.spring.StroomScope;
import stroom.util.task.TaskWrapper;

import javax.inject.Inject;
import java.util.function.Supplier;

@Component("taskWrapper")
@Scope(StroomScope.PROTOTYPE)
class TaskWrapperImpl implements TaskWrapper {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TaskWrapperImpl.class);

    private final TaskFactory taskFactory;
    private final Task<?> parentTask;
    private final UserIdentity userIdentity;
    private final String taskName;

    @Inject
    TaskWrapperImpl(final TaskFactory taskFactory, final SecurityContext securityContext) {
        final Task<?> parentTask = CurrentTaskState.currentTask();
        this.taskFactory = taskFactory;
        this.parentTask = parentTask;
        this.userIdentity = getUserIdentity(parentTask, securityContext);
        this.taskName = getTaskName(parentTask, "Generic Task");
    }

    @Override
    public <U> Supplier<U> wrap(final Supplier<U> supplier) {
        if (userIdentity == null) {
            LOGGER.debug(() -> "Task has null user token: " + taskName);
        }

        final LogExecutionTime logExecutionTime = new LogExecutionTime();

        // We might need to be able to retrieve the associate task handler
        // right away so associate it here before we execute as the task
        // will run asynchronously.
        final GenericServerTask task = GenericServerTask.create(parentTask, userIdentity, taskName, null);

        return taskFactory.wrapSupplier(task, supplier, logExecutionTime);
    }

    @Override
    public Runnable wrap(final Runnable runnable) {
        final Supplier<Void> supplierIn = () -> {
            runnable.run();
            return null;
        };
        final Supplier<Void> supplierOut = wrap(supplierIn);
        return supplierOut::get;
    }

    private UserIdentity getUserIdentity(final Task<?> parentTask, final SecurityContext securityContext) {
        if (parentTask != null && parentTask.getUserIdentity() != null) {
            return parentTask.getUserIdentity();
        }

        try {
            final UserIdentity userIdentity = securityContext.getUserIdentity();
            if (userIdentity != null) {
                return userIdentity;
            }
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Error getting user id", e);
        }

        LOGGER.debug(() -> "Using internal processing user");
        return ProcessingUserIdentity.INSTANCE;
    }

    private String getTaskName(final Task<?> parentTask, final String defaultName) {
        if (parentTask != null && parentTask.getTaskName() != null) {
            return parentTask.getTaskName();
        }

        return defaultName;
    }
}