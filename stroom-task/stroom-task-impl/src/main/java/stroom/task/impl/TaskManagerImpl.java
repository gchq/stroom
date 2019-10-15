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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;
import stroom.node.api.NodeInfo;
import stroom.security.api.SecurityContext;
import stroom.security.shared.UserToken;
import stroom.task.api.GenericServerTask;
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
import stroom.util.concurrent.ScalingThreadPoolExecutor;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.pipeline.scope.PipelineScopeRunnable;
import stroom.util.shared.BaseResultList;
import stroom.util.thread.CustomThreadFactory;
import stroom.util.thread.StroomThreadGroup;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Singleton
class TaskManagerImpl implements TaskManager {//}, SupportsCriteriaLogging<FindTaskProgressCriteria> {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskManagerImpl.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(TaskManagerImpl.class);

    private final TaskHandlerRegistry taskHandlerRegistry;
    private final NodeInfo nodeInfo;
    private final SecurityContext securityContext;
    private final PipelineScopeRunnable pipelineScopeRunnable;
    private final AtomicInteger currentAsyncTaskCount = new AtomicInteger();
    private final Map<TaskId, TaskThread> currentTasks = new ConcurrentHashMap<>(1024, 0.75F, 1024);
    private final AtomicBoolean stop = new AtomicBoolean();
    // The thread pools that will be used to execute tasks.
    private final ConcurrentHashMap<ThreadPool, ThreadPoolExecutor> threadPoolMap = new ConcurrentHashMap<>();
    private final ReentrantLock poolCreationLock = new ReentrantLock();

    @Inject
    TaskManagerImpl(final TaskHandlerRegistry taskHandlerRegistry,
                    final NodeInfo nodeInfo,
                    final SecurityContext securityContext,
                    final PipelineScopeRunnable pipelineScopeRunnable) {
        this.taskHandlerRegistry = taskHandlerRegistry;
        this.nodeInfo = nodeInfo;
        this.securityContext = securityContext;
        this.pipelineScopeRunnable = pipelineScopeRunnable;

        // When we are running unit tests we need to make sure that all Stroom
        // threads complete and are shutdown between tests.
        ExternalShutdownController.addTerminateHandler(TaskManagerImpl.class, this::shutdown);
    }

    private Executor getExecutor(final ThreadPool threadPool) {
        ThreadPoolExecutor executor = threadPoolMap.get(threadPool);
        if (executor == null) {
            poolCreationLock.lock();
            try {
                // Don't create a thread pool if we are supposed to be stopping
                if (!stop.get()) {
                    executor = threadPoolMap.get(threadPool);
                    if (executor == null) {
                        // Create a thread factory for the thread pool
                        final ThreadGroup poolThreadGroup = new ThreadGroup(StroomThreadGroup.instance(),
                                threadPool.getName());
                        final CustomThreadFactory taskThreadFactory = new CustomThreadFactory(
                                threadPool.getName() + " #", poolThreadGroup, threadPool.getPriority());

                        // Create the new thread pool for this priority
                        executor = ScalingThreadPoolExecutor.newScalingThreadPool(
                                threadPool.getCorePoolSize(),
                                threadPool.getMaxPoolSize(),
                                threadPool.getMaxQueueSize(),
                                60L,
                                TimeUnit.SECONDS,
                                taskThreadFactory);

                        threadPoolMap.put(threadPool, executor);
                    }
                }
            } finally {
                poolCreationLock.unlock();
            }
        }
        return executor;
    }

    private void shutdownExecutors() {
        poolCreationLock.lock();
        try {
            final Iterator<ThreadPool> iter = threadPoolMap.keySet().iterator();
            iter.forEachRemaining(threadPool -> {
                final ThreadPoolExecutor executor = threadPoolMap.get(threadPool);
                if (executor != null) {
                    executor.shutdown();
                    threadPoolMap.remove(threadPool);
                }
            });
        } finally {
            poolCreationLock.unlock();
        }
    }

    @Override
    public synchronized void startup() {
        LOGGER.info("startup()");
        stop.set(false);
    }

    /**
     * Tells tasks to terminate and waits for all tasks to terminate before
     * cleaning up the executors.
     */
    @Override
    public synchronized void shutdown() {
        LOGGER.info("shutdown()");
        stop.set(true);

        try {
            // Wait for all tasks to stop executing.
            boolean waiting = true;
            while (waiting) {
                // Stop all of the current tasks.
                currentTasks.values().forEach(TaskThread::terminate);

                final int currentCount = currentAsyncTaskCount.get();
                waiting = currentCount > 0;
                if (waiting) {
                    // Output some debug to list the tasks that are executing
                    // and queued.
                    LOGGER.info("shutdown() - Waiting for {} tasks to complete. {}", currentCount, currentTasks.values().stream()
                            .map(t -> t.getTask().getTaskName())
                            .collect(Collectors.joining(", ")));

                    // Wait 1 second.
                    Thread.sleep(1000);
                }
            }

            // Shut down all executors.
            shutdownExecutors();
        } catch (final InterruptedException e) {
            LOGGER.error(e.getMessage(), e);

            // Continue to interrupt this thread.
            Thread.currentThread().interrupt();
        }

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
        // Do not execute the task if we are no longer supposed to be running.
        if (stop.get()) {
            throw new TaskTerminatedException(stop.get());
        }
        if (task.getId() == null) {
            throw new IllegalStateException("All tasks must have a pre-allocated id");
        }

        final SyncTaskCallback<R> callback = new SyncTaskCallback<>();
        pipelineScopeRunnable.scopeRunnable(() -> {
            try {
                doExec(task, callback);
            } catch (final RuntimeException e) {
                try {
                    callback.onFailure(e);
                } catch (final RuntimeException e2) {
                    // Ignore.
                }
            }
        });

        if (callback.getThrowable() != null) {
            if (callback.getThrowable() instanceof RuntimeException) {
                throw (RuntimeException) callback.getThrowable();
            }
            throw new RuntimeException(callback.getThrowable().getMessage(), callback.getThrowable());
        }

        return callback.getResult();
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
            if (task.getId() == null) {
                throw new IllegalStateException("All tasks must have a pre-allocated id");
            }

            // Create the task scope runnable object outside of the executing
            // thread so we can store a reference to the current task scope
            // context.
            // The reference to the current context is stored during
            // construction of this object.
            final Runnable runnable = () -> pipelineScopeRunnable.scopeRunnable(() -> {
                try {
                    doExec(task, callback);

                } catch (final ThreadDeath | TaskTerminatedException e) {
                    try {
                        callback.onFailure(e);
                    } catch (final RuntimeException e2) {
                        // Ignore.
                    }

                    LOGGER.warn("exec() - Task killed! (" + task.getClass().getSimpleName() + ")");
                    LOGGER.debug("exec() (" + task.getClass().getSimpleName() + ")", e);

                } catch (final RuntimeException e) {
                    try {
                        callback.onFailure(e);
                    } catch (final RuntimeException e2) {
                        // Ignore.
                    }

                    LOGGER.error(e.getMessage() + " (" + task.getClass().getSimpleName() + ")", e);

                } finally {
                    // Decrease the count of the number of async tasks.
                    currentAsyncTaskCount.decrementAndGet();
                }
            });

            // Now we have a task scoped runnable we will execute it in a new
            // thread.
            final Executor executor = getExecutor(threadPool);
            if (executor != null) {
                currentAsyncTaskCount.incrementAndGet();

                try {
                    // We might run out of threads and get a can't fork
                    // exception from the thread pool.
                    executor.execute(runnable);

                } catch (final RuntimeException e) {
                    try {
                        LOGGER.error(MarkerFactory.getMarker("FATAL"), "exec() - Unexpected Exception (" + task.getClass().getSimpleName() + ")", e);
                        throw new RuntimeException(e.getMessage(), e);

                    } finally {
                        // Decrease the count of the number of async tasks.
                        currentAsyncTaskCount.decrementAndGet();
                    }
                }
            }

        } catch (final RuntimeException e) {
            try {
                callback.onFailure(e);
            } catch (final RuntimeException e2) {
                // Ignore.
            }
        }
    }

    @Override
    public void execAsync(final Task<?> parentTask, final UserToken userToken, final String taskName, final Runnable runnable, ThreadPool threadPool) {
        if (userToken == null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Task has null user token: " + taskName);
            }
        }

        final GenericServerTask task = GenericServerTask.create(parentTask, userToken, taskName, null);
        if (threadPool == null) {
            threadPool = task.getThreadPool();
        }

        if (task.getId() == null) {
            throw new IllegalStateException("All tasks must have a pre-allocated id");
        }

        // Create the task scope runnable object outside of the executing
        // thread so we can store a reference to the current task scope
        // context.
        // The reference to the current context is stored during
        // construction of this object.
        final Runnable scopedRunnable = () -> pipelineScopeRunnable.scopeRunnable(() -> {
            if (stop.get()) {
                throw new TaskTerminatedException(stop.get());
            }

            // Make sure this thread is not interrupted.
            if (Thread.interrupted()) {
                LOGGER.warn("This thread was previously interrupted");
            }

            // Get the parent task thread if there is one.
            TaskThread parentTaskThread = null;
            if (parentTask != null) {
                parentTaskThread = currentTasks.get(parentTask.getId());
            }

            final Thread currentThread = Thread.currentThread();
            final String oldThreadName = currentThread.getName();
            final TaskThread taskThread = new TaskThread(task);

            currentThread.setName(oldThreadName + " - " + task.getClass().getSimpleName());
            try {
                taskThread.setThread(Thread.currentThread());
                if (parentTaskThread != null) {
                    parentTaskThread.addChild(taskThread);
                }
                currentTasks.put(task.getId(), taskThread);

                securityContext.asUser(userToken, () -> {
                    CurrentTaskState.pushState(taskThread);
                    try {
                        LOGGER.debug("doExec() - exec >> '{}' {}", task.getClass().getName(), task);
                        runnable.run();
                        LOGGER.debug("doExec() - exec << '{}' {}", task.getClass().getName(), task);

                    } finally {
                        CurrentTaskState.popState();
                    }
                });
            } catch (final ThreadDeath t) {
                LOGGER.error("exec() - ThreadDeath (" + task.getClass().getSimpleName() + ")");
                LOGGER.debug("exec() (" + task.getClass().getSimpleName() + ")", t);
                throw t;
            } catch (final TaskTerminatedException t) {
                LOGGER.error("exec() (" + task.getClass().getSimpleName() + ")", t);
                throw t;
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage() + " (" + task.getClass().getSimpleName() + ")", e);
                throw e;
            } finally {
                currentTasks.remove(task.getId());
                if (parentTaskThread != null) {
                    parentTaskThread.removeChild(taskThread);
                }
                taskThread.setThread(null);
                currentThread.setName(oldThreadName);
                // Decrease the count of the number of async tasks.
                currentAsyncTaskCount.decrementAndGet();
            }
        });

        // Now we have a task scoped runnable we will execute it in a new
        // thread.
        final Executor executor = getExecutor(threadPool);
        if (executor != null) {
            currentAsyncTaskCount.incrementAndGet();

            try {
                // We might run out of threads and get a can't fork
                // exception from the thread pool.
                executor.execute(scopedRunnable);

            } catch (final RuntimeException e) {
                try {
                    LOGGER.error(MarkerFactory.getMarker("FATAL"), "exec() - Unexpected Exception (" + task.getClass().getSimpleName() + ")", e);
                    throw e;

                } finally {
                    // Decrease the count of the number of async tasks.
                    currentAsyncTaskCount.decrementAndGet();
                }
            }
        }
    }

    private <R> void doExec(final Task<R> task, final TaskCallback<R> callback) {
        // Get the parent task thread if there is one.
        TaskThread parentTaskThread = null;
        if (task.getParentTask() != null) {
            parentTaskThread = currentTasks.get(task.getParentTask().getId());
        }

        final Thread currentThread = Thread.currentThread();
        final String oldThreadName = currentThread.getName();
        final TaskThread taskThread = new TaskThread(task);

        currentThread.setName(oldThreadName + " - " + task.getClass().getSimpleName());
        try {
            UserToken userToken = task.getUserToken();
            if (userToken == null) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Task has null user token: " + task.getClass().getSimpleName());
                }
            }

            // Make sure this thread is not interrupted.
            if (Thread.interrupted()) {
                LOGGER.warn("This thread was previously interrupted");
            }

            taskThread.setThread(Thread.currentThread());
            if (parentTaskThread != null) {
                parentTaskThread.addChild(taskThread);
            }
            currentTasks.put(task.getId(), taskThread);

            if (stop.get() || currentThread.isInterrupted()) {
                throw new TaskTerminatedException(stop.get());
            }

            securityContext.asUser(userToken, () -> {
                CurrentTaskState.pushState(taskThread);
                try {
                    // Get the task handler that will deal with this task.
                    final TaskHandler<Task<R>, R> taskHandler = taskHandlerRegistry.findHandler(task);

                    LOGGER.debug("doExec() - exec >> '{}' {}", task.getClass().getName(), task);
                    taskHandler.exec(task, callback);
                    LOGGER.debug("doExec() - exec << '{}' {}", task.getClass().getName(), task);

                } finally {
                    CurrentTaskState.popState();
                }
            });
        } finally {
            currentTasks.remove(task.getId());
            if (parentTaskThread != null) {
                parentTaskThread.removeChild(taskThread);
            }
            taskThread.setThread(null);
            currentThread.setName(oldThreadName);
        }
    }

    @Override
    public BaseResultList<TaskProgress> terminate(final FindTaskCriteria criteria, final boolean kill) {
        // This can change a little between servers
        final long timeNowMs = System.currentTimeMillis();

        final List<TaskProgress> taskProgressList = new ArrayList<>();

        if (criteria != null && criteria.isConstrained()) {
            final Iterator<TaskThread> iter = currentTasks.values().iterator();

            final List<TaskThread> terminateList = new ArrayList<>();

            // Loop over all of the tasks that this node knows about and see if
            // it should be terminated.
            iter.forEachRemaining(taskThread -> {
                final Task<?> task = taskThread.getTask();

                // Terminate it?
                if (kill || !taskThread.isTerminated()) {
                    if (criteria.isMatch(task)) {
                        terminateList.add(taskThread);
                    }
                }
            });

            // Now terminate the relevant tasks.
            doTerminated(kill, timeNowMs, taskProgressList, terminateList);
        }

        return BaseResultList.createUnboundedList(taskProgressList);
    }

    private void doTerminated(final boolean kill, final long timeNowMs, final List<TaskProgress> taskProgressList,
                              final List<TaskThread> itemsToKill) {
        LAMBDA_LOGGER.trace(() ->
                LogUtil.message("doTerminated() - itemsToKill.size() {}", itemsToKill.size()));

        for (final TaskThread taskThread : itemsToKill) {
            final Task<?> task = taskThread.getTask();
            // First try and terminate the task.
            if (!taskThread.isTerminated()) {
                LOGGER.trace("terminating task {}", task);
                taskThread.terminate();
            }

            // If we are forced to kill then kill the associated thread.
            if (kill) {
                LAMBDA_LOGGER.trace(() ->
                        LogUtil.message("killing task {} on thread {}", task, taskThread.getThreadName()));
                taskThread.kill();
            }
            final TaskProgress taskProgress = buildTaskProgress(timeNowMs, taskThread, taskThread.getTask());
            taskProgressList.add(taskProgress);
        }
    }

    @Override
    public int getCurrentTaskCount() {
        return currentAsyncTaskCount.get();
    }

    @Override
    public BaseResultList<TaskProgress> find(final FindTaskProgressCriteria findTaskProgressCriteria) {
        LOGGER.debug("getTaskProgressMap()");
        // This can change a little between servers.
        final long timeNowMs = System.currentTimeMillis();

        final List<TaskProgress> taskProgressList = new ArrayList<>();

        final Iterator<TaskThread> iter = currentTasks.values().iterator();
        iter.forEachRemaining(taskThread -> {
            final Task<?> task = taskThread.getTask();

            final TaskProgress taskProgress = buildTaskProgress(timeNowMs, taskThread, task);

            // Only add this task progress if it matches the supplied criteria.
            if (findTaskProgressCriteria == null || findTaskProgressCriteria.matches(taskProgress)) {
                taskProgressList.add(taskProgress);
            }
        });

        return BaseResultList.createUnboundedList(taskProgressList);
    }

    private TaskProgress buildTaskProgress(final long timeNowMs, final TaskThread taskThread, final Task<?> task) {
        final TaskProgress taskProgress = new TaskProgress();
        taskProgress.setId(task.getId());
        taskProgress.setTaskName(taskThread.getName());
        taskProgress.setSessionId(task.getUserToken().getSessionId());
        taskProgress.setUserName(task.getUserToken().getUserId());
        taskProgress.setThreadName(taskThread.getThreadName());
        taskProgress.setTaskInfo(taskThread.getInfo());
        taskProgress.setSubmitTimeMs(taskThread.getSubmitTimeMs());
        taskProgress.setTimeNowMs(timeNowMs);
        taskProgress.setNodeName(nodeInfo.getThisNodeName());
        return taskProgress;
    }

    @Override
    public Task<?> getTaskById(final TaskId taskId) {
        final TaskThread taskThread = currentTasks.get(taskId);
        if (taskThread != null) {
            return taskThread.getTask();
        }
        return null;
    }

    @Override
    public boolean isTerminated(final TaskId taskId) {
        final TaskThread taskThread = currentTasks.get(taskId);
        if (taskThread != null) {
            return taskThread.isTerminated();
        }
        return true;
    }

    @Override
    public void terminate(final TaskId taskId) {
        final TaskThread taskThread = currentTasks.get(taskId);
        if (taskThread != null) {
            taskThread.terminate();
        }
    }

    @Override
    public String toString() {
        final List<TaskThread> monitorList = new ArrayList<>(currentTasks.values());
        final String serverTasks = TaskThreadInfoUtil.getInfo(monitorList);

        final StringBuilder sb = new StringBuilder();
        if (serverTasks.length() > 0) {
            sb.append("Server Tasks:\n");
            sb.append(serverTasks);
            sb.append("\n");
        }

        return sb.toString();
    }

    @Override
    public FindTaskProgressCriteria createCriteria() {
        return new FindTaskProgressCriteria();
    }

//    @Override
//    public void appendCriteria(final List<BaseAdvancedQueryItem> items, final FindTaskProgressCriteria criteria) {
//        CriteriaLoggingUtil.appendStringTerm(items, "sessionId", criteria.getSessionId());
//    }

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
