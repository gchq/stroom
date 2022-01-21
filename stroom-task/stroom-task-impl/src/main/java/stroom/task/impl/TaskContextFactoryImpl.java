package stroom.task.impl;

import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskTerminatedException;
import stroom.task.api.TerminateHandler;
import stroom.task.api.TerminateHandlerFactory;
import stroom.task.shared.TaskId;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;
import stroom.util.pipeline.scope.PipelineScopeRunnable;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
class TaskContextFactoryImpl implements TaskContextFactory, TaskContext {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TaskContextFactoryImpl.class);

    private static final TerminateHandlerFactory DEFAULT_TERMINATE_HANDLER_FACTORY =
            new ThreadTerminateHandlerFactory();

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
        return createFromConsumer(
                null,
                securityContext.getUserIdentity(),
                taskName,
                DEFAULT_TERMINATE_HANDLER_FACTORY,
                consumer);
    }

    @Override
    public Runnable childContext(final TaskContext parentContext,
                                 final String taskName,
                                 final Consumer<TaskContext> consumer) {
        final TaskContext parent = resolveParent(parentContext);
        return createFromConsumer(
                getTaskId(parent),
                getUserIdentity(parent),
                taskName,
                DEFAULT_TERMINATE_HANDLER_FACTORY,
                consumer);
    }

    @Override
    public <R> Supplier<R> contextResult(final String taskName, final Function<TaskContext, R> function) {
        return createFromFunction(
                null,
                securityContext.getUserIdentity(),
                taskName,
                DEFAULT_TERMINATE_HANDLER_FACTORY,
                function);
    }

    @Override
    public <R> Supplier<R> childContextResult(final TaskContext parentContext,
                                              final String taskName,
                                              final Function<TaskContext, R> function) {
        final TaskContext parent = resolveParent(parentContext);
        return createFromFunction(
                getTaskId(parent),
                getUserIdentity(parent),
                taskName,
                DEFAULT_TERMINATE_HANDLER_FACTORY,
                function);
    }

    @Override
    public Runnable context(final String taskName,
                            final TerminateHandlerFactory terminateHandlerFactory,
                            final Consumer<TaskContext> consumer) {
        return createFromConsumer(
                null,
                securityContext.getUserIdentity(),
                taskName,
                terminateHandlerFactory,
                consumer);
    }

    @Override
    public Runnable childContext(final TaskContext parentContext,
                                 final String taskName,
                                 final TerminateHandlerFactory terminateHandlerFactory,
                                 final Consumer<TaskContext> consumer) {
        final TaskContext parent = resolveParent(parentContext);
        return createFromConsumer(
                getTaskId(parent),
                getUserIdentity(parent),
                taskName,
                terminateHandlerFactory,
                consumer);
    }

    @Override
    public <R> Supplier<R> contextResult(final String taskName,
                                         final TerminateHandlerFactory terminateHandlerFactory,
                                         final Function<TaskContext, R> function) {
        return createFromFunction(
                null,
                securityContext.getUserIdentity(),
                taskName,
                terminateHandlerFactory,
                function);
    }

    @Override
    public <R> Supplier<R> childContextResult(final TaskContext parentContext,
                                              final String taskName,
                                              final TerminateHandlerFactory terminateHandlerFactory,
                                              final Function<TaskContext, R> function) {
        final TaskContext parent = resolveParent(parentContext);
        return createFromFunction(
                getTaskId(parent),
                getUserIdentity(parent),
                taskName,
                terminateHandlerFactory,
                function);
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
                                        final TerminateHandlerFactory terminateHandlerFactory,
                                        final Consumer<TaskContext> consumer) {
        final Supplier<Void> supplierOut = createFromFunction(
                parentTaskId,
                userIdentity,
                taskName,
                terminateHandlerFactory,
                taskContext -> {
                    consumer.accept(taskContext);
                    return null;
                });
        return supplierOut::get;
    }

    private <R> Supplier<R> createFromFunction(final TaskId parentTaskId,
                                               final UserIdentity userIdentity,
                                               final String taskName,
                                               final TerminateHandlerFactory terminateHandlerFactory,
                                               final Function<TaskContext, R> function) {
        return wrap(parentTaskId, userIdentity, taskName, terminateHandlerFactory, function);
    }

    private <R> Supplier<R> wrap(final TaskId parentTaskId,
                                 final UserIdentity userIdentity,
                                 final String taskName,
                                 final TerminateHandlerFactory terminateHandlerFactory,
                                 final Function<TaskContext, R> function) {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        final TaskId taskId = TaskIdFactory.create(parentTaskId);
        final TaskContextImpl subTaskContext = new TaskContextImpl(taskId, taskName, userIdentity, stop);

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
            final Optional<TaskContextImpl> parentTask = getTaskById(parentTaskId);
            final Thread currentThread = Thread.currentThread();
//            final String oldThreadName = currentThread.getName();
//
//            currentThread.setName(oldThreadName + " - " + taskName);

            // Set the thread.
            subTaskContext.setThread(currentThread);

            // Create the termination handler.
            final TerminateHandler terminateHandler = terminateHandlerFactory.create();
            // Set the termination handler.
            subTaskContext.setTerminateHandler(terminateHandler);

            try {
                // Let the parent task know about the child task.
                if (parentTaskId != null) {
                    if (parentTask.isPresent()) {
                        parentTask.get().addChild(subTaskContext);
                    } else {
                        // If we don't have the parent task at this point then terminate the sub-task as the parent must
                        // have already terminated.
                        subTaskContext.terminate();
                    }
                }

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
                        LOGGER.debug(() -> "exec() - Task killed! (" + taskName + ")", t);
                    } else {
                        LOGGER.debug(() -> t.getMessage() + " (" + taskName + ")", t);
                    }

                } catch (final Throwable t2) {
                    LOGGER.debug(t2::getMessage, t2);
                }

                throw t;

            } finally {
                taskRegistry.remove(taskId);

                // Let the parent task know the child task has completed.
                parentTask.ifPresent(parent -> parent.removeChild(subTaskContext));

                try {
                    subTaskContext.setThread(null);
                    subTaskContext.setTerminateHandler(null);
                    terminateHandler.onDestroy();
                } finally {
//                    currentThread.setName(oldThreadName);
                }
            }

            return result;
        };
    }

    private Optional<TaskContextImpl> getTaskById(final TaskId taskId) {
        if (taskId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(taskRegistry.get(taskId));
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

    @Override
    public void reset() {
        final TaskContextImpl taskContext = CurrentTaskContext.currentContext();
        if (taskContext != null) {
            taskContext.reset();
        }
    }
}
