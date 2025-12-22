/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.task.impl;

import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.security.api.exception.AuthenticationException;
import stroom.task.api.SimpleTaskContext;
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

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

@Singleton
class TaskContextFactoryImpl implements TaskContextFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TaskContextFactoryImpl.class);

    private static final TerminateHandlerFactory DEFAULT_TERMINATE_HANDLER_FACTORY =
            new ThreadTerminateHandlerFactory();

    private final SecurityContext securityContext;
    private final PipelineScopeRunnable pipelineScopeRunnable;
    private final TaskRegistry taskRegistry;

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
        final SecurityAttributes securityAttributes = getSecurityAttributes(null);
        return createFromConsumer(
                null,
                securityAttributes,
                taskName,
                DEFAULT_TERMINATE_HANDLER_FACTORY,
                consumer);
    }

    @Override
    public Runnable childContext(final TaskContext parentContext,
                                 final String taskName,
                                 final Consumer<TaskContext> consumer) {
        final SecurityAttributes securityAttributes = getSecurityAttributes(parentContext);
        return createFromConsumer(
                getTaskId(parentContext),
                securityAttributes,
                taskName,
                DEFAULT_TERMINATE_HANDLER_FACTORY,
                consumer);
    }

    @Override
    public <R> Supplier<R> contextResult(final String taskName, final Function<TaskContext, R> function) {
        final SecurityAttributes securityAttributes = getSecurityAttributes(null);
        return createFromFunction(
                null,
                securityAttributes,
                taskName,
                DEFAULT_TERMINATE_HANDLER_FACTORY,
                function);
    }

    @Override
    public <R> Supplier<R> childContextResult(final TaskContext parentContext,
                                              final String taskName,
                                              final Function<TaskContext, R> function) {
        final SecurityAttributes securityAttributes = getSecurityAttributes(parentContext);
        return createFromFunction(
                getTaskId(parentContext),
                securityAttributes,
                taskName,
                DEFAULT_TERMINATE_HANDLER_FACTORY,
                function);
    }

    @Override
    public Runnable context(final String taskName,
                            final TerminateHandlerFactory terminateHandlerFactory,
                            final Consumer<TaskContext> consumer) {
        final SecurityAttributes securityAttributes = getSecurityAttributes(null);
        return createFromConsumer(
                null,
                securityAttributes,
                taskName,
                terminateHandlerFactory,
                consumer);
    }

    @Override
    public Runnable childContext(final TaskContext parentContext,
                                 final String taskName,
                                 final TerminateHandlerFactory terminateHandlerFactory,
                                 final Consumer<TaskContext> consumer) {
        final SecurityAttributes securityAttributes = getSecurityAttributes(parentContext);
        return createFromConsumer(
                getTaskId(parentContext),
                securityAttributes,
                taskName,
                terminateHandlerFactory,
                consumer);
    }

    @Override
    public <R> Supplier<R> contextResult(final String taskName,
                                         final TerminateHandlerFactory terminateHandlerFactory,
                                         final Function<TaskContext, R> function) {
        final SecurityAttributes securityAttributes = getSecurityAttributes(null);
        return createFromFunction(
                null,
                securityAttributes,
                taskName,
                terminateHandlerFactory,
                function);
    }

    @Override
    public <R> Supplier<R> childContextResult(final TaskContext parentContext,
                                              final String taskName,
                                              final TerminateHandlerFactory terminateHandlerFactory,
                                              final Function<TaskContext, R> function) {
        final SecurityAttributes securityAttributes = getSecurityAttributes(parentContext);
        return createFromFunction(
                getTaskId(parentContext),
                securityAttributes,
                taskName,
                terminateHandlerFactory,
                function);
    }

    private TaskId getTaskId(final TaskContext taskContext) {
        if (taskContext != null) {
            return taskContext.getTaskId();
        }
        return null;
    }

    private SecurityAttributes getSecurityAttributes(final TaskContext taskContext) {
        final UserIdentity userIdentity = securityContext.getUserIdentity();
        if (userIdentity != null) {
            return new SecurityAttributes(userIdentity, securityContext.isUseAsRead());
        }

        if (taskContext instanceof final TaskContextImpl context) {
            return new SecurityAttributes(context.getUserIdentity(), context.isUseAsRead());
        }

        throw new AuthenticationException("Security context has no valid user");
    }

    private Runnable createFromConsumer(final TaskId parentTaskId,
                                        final SecurityAttributes securityAttributes,
                                        final String taskName,
                                        final TerminateHandlerFactory terminateHandlerFactory,
                                        final Consumer<TaskContext> consumer) {
        final Supplier<Void> supplierOut = createFromFunction(
                parentTaskId,
                securityAttributes,
                taskName,
                terminateHandlerFactory,
                taskContext -> {
                    consumer.accept(taskContext);
                    return null;
                });
        return supplierOut::get;
    }

    private <R> Supplier<R> createFromFunction(final TaskId parentTaskId,
                                               final SecurityAttributes securityAttributes,
                                               final String taskName,
                                               final TerminateHandlerFactory terminateHandlerFactory,
                                               final Function<TaskContext, R> function) {
        return wrap(parentTaskId, securityAttributes, taskName, terminateHandlerFactory, function);
    }

    private <R> Supplier<R> wrap(final TaskId parentTaskId,
                                 final SecurityAttributes securityAttributes,
                                 final String taskName,
                                 final TerminateHandlerFactory terminateHandlerFactory,
                                 final Function<TaskContext, R> function) {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        final TaskId taskId = TaskIdFactory.create(parentTaskId);
        final TaskContextImpl subTaskContext = new TaskContextImpl(
                taskId,
                taskName,
                securityAttributes.userIdentity(),
                securityAttributes.useAsRead());

        return () -> {
            final R result;

            // Make sure this thread is not interrupted.
            if (Thread.interrupted()) {
                LOGGER.warn("This thread was previously interrupted");
            }
            if (taskName == null) {
                throw new IllegalStateException("All tasks must have a name");
            }
            if (securityAttributes.userIdentity() == null) {
                throw new IllegalStateException("Null user identity: " + taskName);
            }

            // Get the parent task thread if there is one.
            final Optional<TaskContextImpl> parentTask = getTaskById(parentTaskId);
            final Thread currentThread = Thread.currentThread();

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

                final Function<TaskContextImpl, R> pipelineScopeFunction = (tc) ->
                        pipelineScopeRunnable.scopeResult(() -> {
                            CurrentTaskContext.pushContext(tc);
                            try {
                                return LOGGER.logDurationIfDebugEnabled(() -> function.apply(tc),
                                        () -> taskName);
                            } finally {
                                CurrentTaskContext.popContext();
                            }
                        });

                result = securityContext.asUserResult(securityAttributes.userIdentity(), () -> {
                    if (securityAttributes.useAsRead()) {
                        return securityContext.useAsReadResult(() -> pipelineScopeFunction.apply(subTaskContext));
                    } else {
                        return pipelineScopeFunction.apply(subTaskContext);
                    }
                });

            } catch (final Throwable t) {
                try {
                    if (t instanceof TaskTerminatedException) {
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

                subTaskContext.setThread(null);
                subTaskContext.setTerminateHandler(null);

                // Make sure we don't continue to interrupt a thread after the task context is out of scope.
                if (currentThread.isInterrupted()) {
                    LOGGER.debug("Clearing interrupted state");
                    if (Thread.interrupted()) {
                        if (currentThread.isInterrupted()) {
                            try {
                                throw new RuntimeException("Unable to clear interrupted state");
                            } catch (final RuntimeException e) {
                                LOGGER.error(e::getMessage, e);
                            }
                        } else {
                            LOGGER.debug("Cleared interrupted state");
                        }
                    }
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

    @Override
    public TaskContext current() {
        TaskContext taskContext = CurrentTaskContext.currentContext();
        if (taskContext == null) {
            taskContext = new SimpleTaskContext();
        }
        return taskContext;
    }


    // --------------------------------------------------------------------------------


    private record SecurityAttributes(UserIdentity userIdentity, boolean useAsRead) {

    }
}
