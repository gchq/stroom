package stroom.task.impl;

import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskTerminatedException;
import stroom.task.shared.TaskId;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;
import stroom.util.pipeline.scope.PipelineScopeRunnable;

import javax.inject.Inject;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

class TaskContextFactoryImpl implements TaskContextFactory {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TaskContextFactoryImpl.class);

    private final SecurityContext securityContext;
    private final PipelineScopeRunnable pipelineScopeRunnable;
    private final TaskRegistry taskRegistry;
    private final AtomicBoolean stop = new AtomicBoolean();

    @Inject
    TaskContextFactoryImpl(final SecurityContext securityContext,
                           final PipelineScopeRunnable pipelineScopeRunnable,
                           final TaskRegistry taskRegistry) {
        this.securityContext = securityContext;
        this.pipelineScopeRunnable = pipelineScopeRunnable;
        this.taskRegistry = taskRegistry;
    }

    @Override
    public Runnable context(final String taskName, final Consumer<TaskContext> consumer) {
        return createFromConsumer(null, taskName, consumer);
    }

    @Override
    public Runnable context(final TaskContext parentContext, final String taskName, final Consumer<TaskContext> consumer) {
        Objects.requireNonNull(parentContext, "Null parent context");
        return createFromConsumer(parentContext, taskName, consumer);
    }

    @Override
    public <R> Supplier<R> contextResult(final String taskName, final Function<TaskContext, R> function) {
        return createFromFunction(null, taskName, function);
    }

    @Override
    public <R> Supplier<R> contextResult(final TaskContext parentContext, final String taskName, final Function<TaskContext, R> function) {
        Objects.requireNonNull(parentContext, "Null parent context");
        return createFromFunction(parentContext, taskName, function);
    }

    private Runnable createFromConsumer(final TaskContext parentContext, final String taskName, final Consumer<TaskContext> consumer) {
        final Supplier<Void> supplierOut = createFromFunction(parentContext, taskName, taskContext -> {
            consumer.accept(taskContext);
            return null;
        });
        return supplierOut::get;
    }

    private <R> Supplier<R> createFromFunction(final TaskContext parentContext, final String taskName, final Function<TaskContext, R> function) {
        return wrap(parentContext, taskName, function);
    }

    @Override
    public TaskContext currentContext() {
        return CurrentTaskContext.currentContext();
    }

    private <R> Supplier<R> wrap(final TaskContext parentContext, final String taskName, final Function<TaskContext, R> function) {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        final TaskId parentTaskId = getParentTaskId(parentContext);
        final TaskId taskId = TaskIdFactory.create(parentTaskId);
        final UserIdentity userIdentity = getUserIdentity(parentContext);
        final TaskContextImpl subTaskContext = new TaskContextImpl(taskId, taskName, userIdentity);

        return () -> {
            R result;

            // Make sure this thread is not interrupted.
            if (Thread.interrupted()) {
                LOGGER.warn("This thread was previously interrupted");
            }
            // Do not execute the task if we are no longer supposed to be running.
            if (stop.get()) {
                throw new TaskTerminatedException(stop.get());
            }
            if (taskName == null) {
                throw new IllegalStateException("All tasks must have a name");
            }
            if (userIdentity == null) {
                throw new IllegalStateException("Null user identity: " + taskName);
            }

            // Get the parent task thread if there is one.
            final Set<TaskContextImpl> ancestorTaskSet = getAncestorTaskSet(parentTaskId);

            final Thread currentThread = Thread.currentThread();
            final String oldThreadName = currentThread.getName();

            currentThread.setName(oldThreadName + " - " + taskName);

            subTaskContext.setThread(currentThread);

            try {
                // Let every ancestor know this descendant task is being executed.
                ancestorTaskSet.forEach(ancestorTask -> ancestorTask.addChild(subTaskContext));

                taskRegistry.put(taskId, subTaskContext);
                LOGGER.debug(() -> "execAsync()->exec() - " + taskName + " took " + logExecutionTime.toString());

                if (stop.get() || currentThread.isInterrupted()) {
                    throw new TaskTerminatedException(stop.get());
                }

                result = securityContext.asUserResult(userIdentity, () -> pipelineScopeRunnable.scopeResult(() -> {
                    CurrentTaskContext.pushContext(subTaskContext);
                    try {
                        return LOGGER.logDurationIfDebugEnabled(() -> function.apply(subTaskContext), () -> taskName);
                    } finally {
                        CurrentTaskContext.popContext();
                    }
                }));

            } catch (final Throwable t) {
                try {
                    if (t instanceof ThreadDeath || t instanceof TaskTerminatedException) {
                        LOGGER.warn(() -> "exec() - Task killed! (" + taskName + ")");
                        LOGGER.debug(() -> "exec() (" + taskName + ")", t);
                    } else {
                        LOGGER.error(() -> t.getMessage() + " (" + taskName + ")", t);
                    }

                } catch (final Throwable t2) {
                    LOGGER.debug(t2::getMessage, t2);
                }

                throw t;

            } finally {
                taskRegistry.remove(taskId);

                // Let every ancestor know this descendant task has completed.
                ancestorTaskSet.forEach(ancestorTask -> ancestorTask.removeChild(subTaskContext));

                subTaskContext.setThread(null);
                currentThread.setName(oldThreadName);
            }

            return result;
        };
    }

    private TaskId getParentTaskId(final TaskContext parentContext) {
        if (parentContext != null) {
            return parentContext.getTaskId();
        }
        return null;
    }

    private UserIdentity getUserIdentity(final TaskContext parentContext) {
        if (parentContext != null) {
            return ((TaskContextImpl) parentContext).getUserIdentity();
        }
        return securityContext.getUserIdentity();
    }

    private Set<TaskContextImpl> getAncestorTaskSet(final TaskId parentTask) {
        // Get the parent task thread if there is one.
        final Set<TaskContextImpl> ancestorTaskSet = new HashSet<>();
        TaskId ancestor = parentTask;
        while (ancestor != null) {
            TaskContextImpl ancestorTaskState = taskRegistry.get(ancestor);
            if (ancestorTaskState != null) {
                ancestorTaskSet.add(ancestorTaskState);
            }
            ancestor = ancestor.getParentId();
        }
        return ancestorTaskSet;
    }

    void setStop(final boolean stop) {
        this.stop.set(stop);
    }
}
