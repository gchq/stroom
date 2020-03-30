/*
 * Copyright 2017 Crown Copyright
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

import stroom.node.api.NodeInfo;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.security.shared.PermissionNames;
import stroom.task.api.TaskCallback;
import stroom.task.api.TaskCallbackAdaptor;
import stroom.task.api.TaskHandler;
import stroom.task.api.TaskManager;
import stroom.task.api.TaskTerminatedException;
import stroom.task.shared.FindTaskCriteria;
import stroom.task.shared.FindTaskProgressCriteria;
import stroom.task.shared.Task;
import stroom.task.shared.TaskId;
import stroom.task.shared.TaskProgress;
import stroom.task.shared.ThreadPool;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;
import stroom.util.logging.LogUtil;
import stroom.util.pipeline.scope.PipelineScopeRunnable;
import stroom.util.servlet.SessionIdProvider;
import stroom.util.shared.ResultPage;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Singleton
class TaskManagerImpl implements TaskManager {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TaskManagerImpl.class);

    private final TaskHandlerRegistry taskHandlerRegistry;
    private final NodeInfo nodeInfo;
    private final SessionIdProvider sessionIdProvider;
    private final SecurityContext securityContext;
    private final PipelineScopeRunnable pipelineScopeRunnable;
    private final ExecutorProviderImpl executorProvider;
    private final Map<TaskId, TaskState> currentTasks = new ConcurrentHashMap<>(1024, 0.75F, 1024);
    private final AtomicBoolean stop = new AtomicBoolean();

    @Inject
    TaskManagerImpl(final TaskHandlerRegistry taskHandlerRegistry,
                    final NodeInfo nodeInfo,
                    final SessionIdProvider sessionIdProvider,
                    final SecurityContext securityContext,
                    final PipelineScopeRunnable pipelineScopeRunnable,
                    final ExecutorProviderImpl executorProvider) {
        this.taskHandlerRegistry = taskHandlerRegistry;
        this.nodeInfo = nodeInfo;
        this.sessionIdProvider = sessionIdProvider;
        this.securityContext = securityContext;
        this.pipelineScopeRunnable = pipelineScopeRunnable;
        this.executorProvider = executorProvider;

        // When we are running unit tests we need to make sure that all Stroom
        // threads complete and are shutdown between tests.
        ExternalShutdownController.addTerminateHandler(TaskManagerImpl.class, this::shutdown);
    }


    @Override
    public synchronized void startup() {
        LOGGER.info("startup()");
        stop.set(false);
        executorProvider.setStop(false);
    }

    /**
     * Tells tasks to terminate and waits for all tasks to terminate before
     * cleaning up the executors.
     */
    @Override
    public synchronized void shutdown() {
        LOGGER.info("shutdown()");
        stop.set(true);
        executorProvider.setStop(true);

        try {
            // Wait for all tasks to stop executing.
            boolean waiting = true;
            while (waiting) {
                // Stop all of the current tasks.
                currentTasks.values().forEach(TaskState::terminate);

                final int currentCount = executorProvider.getCurrentTaskCount();
                waiting = currentCount > 0;
                if (waiting) {
                    // Output some debug to list the tasks that are executing
                    // and queued.
                    LOGGER.info("shutdown() - Waiting for {} tasks to complete. {}", currentCount, currentTasks.values().stream()
                            .map(TaskState::toString)
                            .collect(Collectors.joining(", ")));

                    // Wait 1 second.
                    Thread.sleep(1000);
                }
            }

            // Shut down all executors.
            executorProvider.shutdownExecutors();
        } catch (final InterruptedException e) {
            LOGGER.error(e.getMessage(), e);

            // Continue to interrupt this thread.
            Thread.currentThread().interrupt();
        }

        executorProvider.setStop(false);
        stop.set(false);
        LOGGER.info("shutdown() - Complete");
    }

    /**
     * Execute a task synchronously.
     *
     * @param task The task to execute.
     * @return The result of the task execution.
     */
    @Override
    public <R> R exec(final Task<R> task) {
        return createSupplier(task).get();
    }

    /**
     * Execute a task asynchronously without expecting to handle any result via
     * a callback.
     *
     * @param task The task to execute asynchronously.
     */
    @Override
    public <R> void execAsync(final Task<R> task) {
        execAsync(task, null, task.getThreadPool());
    }

    @Override
    public <R> void execAsync(final Task<R> task, final ThreadPool threadPool) {
        execAsync(task, null, threadPool);
    }

    @Override
    public <R> void execAsync(final Task<R> task, final TaskCallback<R> callback) {
        execAsync(task, callback, task.getThreadPool());
    }

    /**
     * Execute a task asynchronously with a callback to receive results.
     *
     * @param task The task to execute asynchronously.
     * @param c    The callback that will receive results from the task
     *             execution.
     */
    @Override
    public <R> void execAsync(final Task<R> task, TaskCallback<R> c, final ThreadPool threadPool) {
        if (c == null) {
            c = new AsyncTaskCallback<>();
        }
        final TaskCallback<R> callback = c;

        try {
            // Now we have a task scoped runnable we will execute it in a new thread.
            final Executor executor = executorProvider.get(threadPool);
            final Runnable taskRunnable = createRunnable(task, callback);
            try {
                // We might run out of threads and get a can't fork
                // exception from the thread pool.
                executor.execute(taskRunnable);

            } catch (final Throwable t) {
                LOGGER.error(() -> "exec() - Unexpected Exception (" + task.getClass().getSimpleName() + ")", t);
                throw new RuntimeException(t.getMessage(), t);
            }

        } catch (final Throwable t) {
            try {
                callback.onFailure(t);
            } catch (final Throwable t2) {
                // Ignore.
            }
            throw t;
        }
    }

    private Set<TaskState> getAncestorTaskSet(final TaskId parentTask) {
        // Get the parent task thread if there is one.
        final Set<TaskState> ancestorTaskSet = new HashSet<>();
        TaskId ancestor = parentTask;
        while (ancestor != null) {
            TaskState ancestorTaskState = currentTasks.get(ancestor);
            if (ancestorTaskState != null) {
                ancestorTaskSet.add(ancestorTaskState);
            }
            ancestor = ancestor.getParentId();
        }
        return ancestorTaskSet;
    }

    <R> Supplier<R> createSupplier(final Task<R> task) {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        final UserIdentity userIdentity = getUserIdentity();

        final SyncTaskCallback<R> callback = new SyncTaskCallback<>();
        final Supplier<R> supplier = () -> {
            // Get the task handler that will deal with this task.
            final TaskHandler<Task<R>, R> taskHandler = taskHandlerRegistry.findHandler(task);
            taskHandler.exec(task, callback);

            if (callback.getThrowable() != null) {
                if (callback.getThrowable() instanceof RuntimeException) {
                    throw (RuntimeException) callback.getThrowable();
                }
                throw new RuntimeException(callback.getThrowable().getMessage(), callback.getThrowable());
            }

            return callback.getResult();
        };

        return wrapSupplier(task.getId(), getTaskName(task),  userIdentity, supplier, logExecutionTime);
    }

    <R> Runnable createRunnable(final Task<R> task, TaskCallback<R> callback) {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        final UserIdentity userIdentity = getUserIdentity();

        final Supplier<Void> supplier = () -> {
            // Get the task handler that will deal with this task.
            final TaskHandler<Task<R>, R> taskHandler = taskHandlerRegistry.findHandler(task);
            taskHandler.exec(task, callback);
            return null;
        };

        return () -> {
            try {
                final Supplier<Void> wrappedSupplier = wrapSupplier(task.getId(), getTaskName(task),  userIdentity, supplier, logExecutionTime);
                wrappedSupplier.get();
            } catch (final Throwable t) {
                try {
                    callback.onFailure(t);
                } catch (final Throwable t2) {
                    LOGGER.debug(t2::getMessage, t2);
                }
            }
        };
    }

    UserIdentity getUserIdentity() {
        final UserIdentity userIdentity = securityContext.getUserIdentity();
        if (userIdentity == null) {
            throw new NullPointerException("Null user identity");
        }
        return userIdentity;
    }

    private String getTaskName(final Task<?> task) {
        if (task.getTaskName() != null) {
            return task.getTaskName();
        }

        return getTaskName();
    }

    String getTaskName() {
        final String name = CurrentTaskState.currentName();
        if (name != null) {
            return name;
        }

        return "Unnamed Task";
    }

    <R> Supplier<R> wrapSupplier(final TaskId taskId, final String taskName, final UserIdentity userIdentity, final Supplier<R> supplier, final LogExecutionTime logExecutionTime) {
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
            if (taskId == null) {
                throw new IllegalStateException("All tasks must have a pre-allocated id");
            }
            if (taskName == null) {
                throw new IllegalStateException("All tasks must have a name");
            }
            if (userIdentity == null) {
                throw new IllegalStateException("Null user identity: " + taskName);
            }

            // Get the parent task thread if there is one.
            final Set<TaskState> ancestorTaskSet = getAncestorTaskSet(taskId.getParentId());

            final Thread currentThread = Thread.currentThread();
            final String oldThreadName = currentThread.getName();

            currentThread.setName(oldThreadName + " - " + taskName);

            final TaskState taskState = new TaskState(taskId, userIdentity);
            taskState.setThread(currentThread);
            taskState.setName(taskName);

            try {
                // Let every ancestor know this descendant task is being executed.
                ancestorTaskSet.forEach(ancestorTask -> ancestorTask.addChild(taskState));

                currentTasks.put(taskId, taskState);
                LOGGER.debug(() -> "execAsync()->exec() - " + taskName + " took " + logExecutionTime.toString());

                if (stop.get() || currentThread.isInterrupted()) {
                    throw new TaskTerminatedException(stop.get());
                }

                result = securityContext.asUserResult(userIdentity, () -> pipelineScopeRunnable.scopeResult(() -> {
                    CurrentTaskState.pushState(taskState);
                    try {
                        return LOGGER.logDurationIfDebugEnabled(supplier, () -> taskName);
                    } finally {
                        CurrentTaskState.popState();
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
                currentTasks.remove(taskId);

                // Let every ancestor know this descendant task has completed.
                ancestorTaskSet.forEach(ancestorTask -> ancestorTask.removeChild(taskState));

                taskState.setThread(null);
                currentThread.setName(oldThreadName);
            }

            return result;
        };
    }

    @Override
    public ResultPage<TaskProgress> terminate(final FindTaskCriteria criteria, final boolean kill) {
        return securityContext.secureResult(PermissionNames.MANAGE_TASKS_PERMISSION, () -> {
            // This can change a little between servers
            final long timeNowMs = System.currentTimeMillis();

            final List<TaskProgress> taskProgressList = new ArrayList<>();

            if (criteria != null && criteria.isConstrained()) {
                final Iterator<TaskState> iter = currentTasks.values().iterator();

                final List<TaskState> terminateList = new ArrayList<>();

                // Loop over all of the tasks that this node knows about and see if
                // it should be terminated.
                iter.forEachRemaining(taskThread -> {
                    final TaskId taskId = taskThread.getTaskId();

                    // Terminate it?
                    if (kill || !taskThread.isTerminated()) {
                        if (criteria.isMatch(taskId, taskThread.getSessionId())) {
                            terminateList.add(taskThread);
                        }
                    }
                });

                // Now terminate the relevant tasks.
                doTerminated(kill, timeNowMs, taskProgressList, terminateList);
            }

            return ResultPage.createUnboundedList(taskProgressList);
        });
    }

    private void doTerminated(final boolean kill, final long timeNowMs, final List<TaskProgress> taskProgressList,
                              final List<TaskState> itemsToKill) {
        LOGGER.trace(() -> LogUtil.message("doTerminated() - itemsToKill.size() {}", itemsToKill.size()));

        for (final TaskState taskState : itemsToKill) {
            final TaskId taskId = taskState.getTaskId();
            // First try and terminate the task.
            if (!taskState.isTerminated()) {
                LOGGER.trace(() -> LogUtil.message("terminating task {}", taskId));
                taskState.terminate();
            }

            // If we are forced to kill then kill the associated thread.
            if (kill) {
                LOGGER.trace(() -> LogUtil.message("killing task {} on thread {}", taskId, taskState.getThreadName()));
                taskState.kill();
            }
            final TaskProgress taskProgress = buildTaskProgress(timeNowMs, taskState, taskId);
            taskProgressList.add(taskProgress);
        }
    }

    @Override
    public ResultPage<TaskProgress> find(final FindTaskProgressCriteria findTaskProgressCriteria) {
        final boolean sessionMatch = findTaskProgressCriteria != null &&
                findTaskProgressCriteria.getSessionId() != null &&
                findTaskProgressCriteria.getSessionId().equals(sessionIdProvider.get());

        if (sessionMatch) {
            // Always allow a user to see tasks for their own session if tasks for the current session have been requested.
            return doFind(findTaskProgressCriteria);
        } else {
            return securityContext.secureResult(PermissionNames.MANAGE_TASKS_PERMISSION, () ->
                    doFind(findTaskProgressCriteria));
        }
    }

    private ResultPage<TaskProgress> doFind(final FindTaskProgressCriteria findTaskProgressCriteria) {
        LOGGER.debug("getTaskProgressMap()");
        // This can change a little between servers.
        final long timeNowMs = System.currentTimeMillis();

        final List<TaskProgress> taskProgressList = new ArrayList<>();

        final Iterator<TaskState> iter = currentTasks.values().iterator();
        iter.forEachRemaining(taskThread -> {
            final TaskId taskId = taskThread.getTaskId();
            final TaskProgress taskProgress = buildTaskProgress(timeNowMs, taskThread, taskId);

            // Only add this task progress if it matches the supplied criteria.
            if (findTaskProgressCriteria == null ||
                    findTaskProgressCriteria.getSessionId() == null ||
                    findTaskProgressCriteria.getSessionId().equals(taskThread.getSessionId())) {
                taskProgressList.add(taskProgress);
            }
        });

        return ResultPage.createUnboundedList(taskProgressList);
    }

    private TaskProgress buildTaskProgress(final long timeNowMs, final TaskState taskState, final TaskId taskId) {
        final TaskProgress taskProgress = new TaskProgress();
        taskProgress.setId(taskId);
        taskProgress.setTaskName(taskState.getName());
        taskProgress.setUserName(taskState.getUserId());
        taskProgress.setThreadName(taskState.getThreadName());
        taskProgress.setTaskInfo(taskState.getInfo());
        taskProgress.setSubmitTimeMs(taskState.getSubmitTimeMs());
        taskProgress.setTimeNowMs(timeNowMs);
        taskProgress.setNodeName(nodeInfo.getThisNodeName());
        return taskProgress;
    }

    @Override
    public boolean isTerminated(final TaskId taskId) {
        final TaskState taskState = currentTasks.get(taskId);
        if (taskState != null) {
            return taskState.isTerminated();
        }
        return true;
    }

    @Override
    public void terminate(final TaskId taskId) {
        securityContext.secure(PermissionNames.MANAGE_TASKS_PERMISSION, () -> {
            final TaskState taskState = currentTasks.get(taskId);
            if (taskState != null) {
                taskState.terminate();
            }
        });
    }

    @Override
    public String toString() {
        final List<TaskState> monitorList = new ArrayList<>(currentTasks.values());
        final String serverTasks = TaskThreadInfoUtil.getInfo(monitorList);

        final StringBuilder sb = new StringBuilder();
        if (serverTasks.length() > 0) {
            sb.append("Server Tasks:\n");
            sb.append(serverTasks);
            sb.append("\n");
        }

        return sb.toString();
    }

    private static class AsyncTaskCallback<R> extends TaskCallbackAdaptor<R> {
    }

    private static class SyncTaskCallback<R> extends TaskCallbackAdaptor<R> {
        private R result;
        private Throwable throwable;

        @Override
        public void onSuccess(final R result) {
            this.result = result;
        }

        @Override
        public void onFailure(final Throwable throwable) {
            this.throwable = throwable;
        }

        public R getResult() {
            return result;
        }

        public Throwable getThrowable() {
            return throwable;
        }
    }
}
