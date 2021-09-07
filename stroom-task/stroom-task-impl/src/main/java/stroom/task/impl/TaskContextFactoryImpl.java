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

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
class TaskContextFactoryImpl implements TaskContextFactory, TaskContext {

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
    public Runnable context(final String taskName,
                            final Consumer<TaskContext> consumer) {
        return createFromConsumer(null, securityContext.getUserIdentity(), taskName, consumer);
    }

    @Override
    public Runnable childContext(final TaskContext parentContext,
                                 final String taskName,
                                 final Consumer<TaskContext> consumer) {
        final TaskContext parent = resolveParent(parentContext);
        return createFromConsumer(getTaskId(parent), getUserIdentity(parent), taskName, consumer);
    }

    @Override
    public <R> Supplier<R> contextResult(final String taskName, final Function<TaskContext, R> function) {
        return createFromFunction(null, securityContext.getUserIdentity(), taskName, function);
    }

    @Override
    public <R> Supplier<R> childContextResult(final TaskContext parentContext,
                                              final String taskName,
                                              final Function<TaskContext, R> function) {
        final TaskContext parent = resolveParent(parentContext);
        return createFromFunction(getTaskId(parent), getUserIdentity(parent), taskName, function);
    }

    private TaskContext resolveParent(final TaskContext parentContext) {
        if (parentContext instanceof TaskContextFactoryImpl) {
            return CurrentTaskContext.currentContext();
        }
        return parentContext;
    }

    private TaskId getTaskId(final TaskContext taskContext) {
        if (taskContext != null) {
            return taskContext.getTaskId();
        }
        return null;
    }

    private UserIdentity getUserIdentity(final TaskContext taskContext) {
        if (taskContext instanceof TaskContextImpl) {
            return ((TaskContextImpl) taskContext).getUserIdentity();
        }
        return securityContext.getUserIdentity();
    }

    private Runnable createFromConsumer(final TaskId parentTaskId,
                                        final UserIdentity userIdentity,
                                        final String taskName,
                                        final Consumer<TaskContext> consumer) {
        final Supplier<Void> supplierOut = createFromFunction(parentTaskId, userIdentity, taskName, taskContext -> {
            consumer.accept(taskContext);
            return null;
        });
        return supplierOut::get;
    }

    private <R> Supplier<R> createFromFunction(final TaskId parentTaskId,
                                               final UserIdentity userIdentity,
                                               final String taskName,
                                               final Function<TaskContext, R> function) {
        return wrap(parentTaskId, userIdentity, taskName, function);
    }

    private <R> Supplier<R> wrap(final TaskId parentTaskId,
                                 final UserIdentity userIdentity,
                                 final String taskName,
                                 final Function<TaskContext, R> function) {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        final TaskId taskId = TaskIdFactory.create(parentTaskId);
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
                LOGGER.debug(() -> "execAsync()->exec() - " + taskName + " took " + logExecutionTime);

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
                        LOGGER.debug(() -> t.getMessage() + " (" + taskName + ")", t);
                    }

                } catch (final Throwable t2) {
                    LOGGER.debug(t2::getMessage, t2);
                }

                throw t;

            } finally {
                taskRegistry.remove(taskId);

                // Let every ancestor know this descendant task has completed.
                ancestorTaskSet.forEach(ancestorTask -> ancestorTask.removeChild(subTaskContext));

                try {
                    subTaskContext.setThread(null);
                } finally {
                    currentThread.setName(oldThreadName);
                }
            }

            return result;
        };
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

    @Override
    public void info(final Supplier<String> messageSupplier) {
        final TaskContextImpl taskContext = CurrentTaskContext.currentContext();
        if (taskContext != null) {
            taskContext.info(messageSupplier);
        }
    }

    @Override
    public TaskId getTaskId() {
        final TaskContextImpl taskContext = CurrentTaskContext.currentContext();
        if (taskContext != null) {
            return taskContext.getTaskId();
        }
        return null;
    }
}
