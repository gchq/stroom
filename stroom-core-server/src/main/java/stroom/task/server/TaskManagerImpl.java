/*
 * Copyright 2016 Crown Copyright
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

package stroom.task.server;

import event.logging.BaseAdvancedQueryItem;
import org.springframework.stereotype.Component;
import stroom.entity.server.CriteriaLoggingUtil;
import stroom.entity.server.SupportsCriteriaLogging;
import stroom.entity.shared.BaseResultList;
import stroom.node.server.NodeCache;
import stroom.security.SecurityContext;
import stroom.task.shared.FindTaskCriteria;
import stroom.task.shared.FindTaskProgressCriteria;
import stroom.task.shared.TaskProgress;
import stroom.util.logging.LogExecutionTime;
import stroom.util.logging.StroomLogger;
import stroom.util.shared.Monitor;
import stroom.util.shared.SimpleThreadPool;
import stroom.util.shared.Task;
import stroom.util.shared.TaskId;
import stroom.util.shared.ThreadPool;
import stroom.util.shared.UserTokenUtil;
import stroom.util.spring.StroomBeanStore;
import stroom.util.task.ExternalShutdownController;
import stroom.util.task.HasMonitor;
import stroom.util.task.MonitorInfoUtil;
import stroom.util.task.TaskScopeContextHolder;
import stroom.util.task.TaskScopeRunnable;
import stroom.util.thread.CustomThreadFactory;
import stroom.util.thread.ThreadScopeRunnable;
import stroom.util.thread.ThreadUtil;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * NB: we also define this in Spring XML so we can set some of the properties.
 */
@Component("taskManager")
public class TaskManagerImpl implements TaskManager, SupportsCriteriaLogging<FindTaskProgressCriteria> {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(TaskManagerImpl.class);

    private static final ThreadPool DEFAULT_THREAD_POOL = new SimpleThreadPool("Default", 2);

    private final TaskHandlerBeanRegistry taskHandlerBeanRegistry;
    private final NodeCache nodeCache;
    private final StroomBeanStore beanStore;
    private final SecurityContext securityContext;

    private final AtomicInteger currentAsyncTaskCount = new AtomicInteger();
    private final Map<TaskId, TaskThread<?>> currentTasks = new ConcurrentHashMap<>(1024, 0.75F, 1024);
    private final AtomicBoolean stop = new AtomicBoolean();
    // The thread pools that will be used to execute tasks.
    private final ConcurrentHashMap<ThreadPool, ThreadPoolExecutor> threadPoolMap = new ConcurrentHashMap<>();
    private final ReentrantLock poolCreationLock = new ReentrantLock();

    @Inject
    TaskManagerImpl(final TaskHandlerBeanRegistry taskHandlerBeanRegistry, final NodeCache nodeCache, final StroomBeanStore beanStore, final SecurityContext securityContext) {
        this.taskHandlerBeanRegistry = taskHandlerBeanRegistry;
        this.nodeCache = nodeCache;
        this.beanStore = beanStore;
        this.securityContext = securityContext;

        // When we are running unit tests we need to make sure that all Stroom
        // threads complete and are shutdown between tests.
        ExternalShutdownController.addTerminateHandler(TaskManagerImpl.class, () -> shutdown());
    }

    private Executor getExecutor() {
        return getExecutor(DEFAULT_THREAD_POOL);
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

                        //Use a LinkedBlockingQueue for any bounded pools (where bounded is any
                        //thread count below Integer.MAX_VALUE)
                        final BlockingQueue<Runnable> executorQueue = threadPool.getMaxPoolSize() == Integer.MAX_VALUE
                                ? new SynchronousQueue<>()
                                : new LinkedBlockingQueue<>();

                        // Create the new thread pool for this priority
                        executor = new ThreadPoolExecutor(
                                threadPool.getCorePoolSize(),
                                threadPool.getMaxPoolSize(),
                                60L,
                                TimeUnit.SECONDS,
                                executorQueue,
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
            while (iter.hasNext()) {
                final ThreadPool threadPool = iter.next();
                final ThreadPoolExecutor executor = threadPoolMap.get(threadPool);
                if (executor != null) {
                    executor.shutdown();
                    threadPoolMap.remove(threadPool);
                }
            }
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

        // Wait for all tasks to stop executing.
        boolean waiting = true;
        while (waiting) {
            // Stop all of the current tasks.
            currentTasks.values().forEach(TaskThread::terminate);

            final int currentCount = currentAsyncTaskCount.get();
            waiting = currentCount > 0;
            if (waiting) {
                final StringBuilder builder = new StringBuilder();
                for (final TaskThread<?> taskThread : currentTasks.values()) {
                    builder.append(taskThread.getTask().getTaskName());
                    builder.append(" ");
                }

                // Output some debug to list the tasks that are executing
                // and queued.
                LOGGER.info("shutdown() - Waiting for %s tasks to complete. %s", currentCount, builder);

                // Wait 1 second.
                ThreadUtil.sleep(1000);
            }
        }

        // Shut down all executors.
        shutdownExecutors();

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
        if (stop.get() || task.isTerminated()) {
            throw new TaskTerminatedException(stop.get());
        }
        if (task.getId() == null) {
            throw new IllegalStateException("All tasks must have a pre-allocated id");
        }

        // We might need to be able to retrieve the associate task handler right
        // away so associate it here before we execute as the task will run
        // asynchronously.
        final TaskThread<R> taskThread = new TaskThread<>(task);

        final SyncTaskCallback<R> callback = new SyncTaskCallback<>();
        currentTasks.put(task.getId(), taskThread);
        try {
            TaskScopeContextHolder.addContext(task);
            try {
                doExec(task, callback);
            } catch (final Throwable t) {
                try {
                    callback.onFailure(t);
                } catch (final Throwable t2) {
                    // Ignore.
                }
            } finally {
                TaskScopeContextHolder.removeContext();
            }
        } finally {
            try {
                currentTasks.remove(task.getId());
            } catch (final Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
        }

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
            final LogExecutionTime logExecutionTime = new LogExecutionTime();

            if (task.getId() == null) {
                throw new IllegalStateException("All tasks must have a pre-allocated id");
            }

            // We might need to be able to retrieve the associate task handler
            // right away so associate it here before we execute as the task
            // will run asynchronously.
            final TaskThread<R> taskThread = new TaskThread<>(task);

            // Create the task scope runnable object outside of the executing
            // thread so we can store a reference to the current task scope
            // context.
            // The reference to the current context is stored during
            // construction of this object.
            final TaskScopeRunnable taskScopeRunnable = new TaskScopeRunnable(task) {
                @Override
                protected void exec() {
                    try {
                        currentTasks.put(task.getId(), taskThread);
                        LOGGER.debug("execAsync()->exec() - %s %s took %s", task.getClass().getSimpleName(), task.getTaskName(), logExecutionTime);

                        taskThread.setThread(Thread.currentThread());

                        if (stop.get() || task.isTerminated()) {
                            throw new TaskTerminatedException(stop.get());
                        }

                        doExec(task, callback);

                    } catch (final Throwable t) {
                        try {
                            callback.onFailure(t);
                        } catch (final Throwable t2) {
                            // Ignore.
                        }

                        if (t instanceof ThreadDeath || t instanceof TaskTerminatedException) {
                            LOGGER.warn("exec() - Task killed! (" + task.getClass().getSimpleName() + ")");
                            LOGGER.debug("exec() (" + task.getClass().getSimpleName() + ")", t);
                        } else {
                            LOGGER.error(t.getMessage() + " (" + task.getClass().getSimpleName() + ")", t);
                        }

                    } finally {
                        taskThread.setThread(null);
                        // Decrease the count of the number of async tasks.
                        currentAsyncTaskCount.decrementAndGet();
                        currentTasks.remove(task.getId());
                    }
                }
            };

            // Now we have a task scoped runnable we will execute it in a new
            // thread.
            final Executor executor = getExecutor(threadPool);
            if (executor != null) {
                currentAsyncTaskCount.incrementAndGet();

                try {
                    // We might run out of threads and get a can't fork
                    // exception from the thread pool.
                    executor.execute(new ThreadScopeRunnable() {
                        @Override
                        protected void exec() {
                            taskScopeRunnable.run();
                        }
                    });

                } catch (final Throwable t) {
                    try {
                        LOGGER.fatal("exec() - Unexpected Exception (" + task.getClass().getSimpleName() + ")", t);
                        throw new RuntimeException(t.getMessage(), t);

                    } finally {
                        taskThread.setThread(null);
                        // Decrease the count of the number of async tasks.
                        currentAsyncTaskCount.decrementAndGet();
                    }
                }
            }

        } catch (final Throwable t) {
            try {
                callback.onFailure(t);
            } catch (final Throwable t2) {
                // Ignore.
            }
        }
    }

    private <R> void doExec(final Task<R> task, final TaskCallback<R> callback) {
        final Thread currentThread = Thread.currentThread();
        final String oldThreadName = currentThread.getName();

        currentThread.setName(oldThreadName + " - " + task.getClass().getSimpleName());
        try {
            String userToken = task.getUserToken();
            if (userToken == null) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Task has null user token: " + task.getClass().getSimpleName());
                }
            }

            securityContext.pushUser(userToken);
            try {

                // Create a task monitor bean to be injected inside the handler.
                final TaskMonitorImpl taskMonitor = beanStore.getBean(TaskMonitorImpl.class);
                if (task instanceof HasMonitor) {
                    final HasMonitor hasMonitor = (HasMonitor) task;
                    taskMonitor.setMonitor(hasMonitor.getMonitor());
                }

                CurrentTaskState.pushState(task, taskMonitor);
                try {
                    // Get the task handler that will deal with this task.
                    final TaskHandler<Task<R>, R> taskHandler = taskHandlerBeanRegistry.findHandler(task);

                    LOGGER.debug("doExec() - exec >> '%s' %s", task.getClass().getName(), task);
                    taskHandler.exec(task, callback);
                    LOGGER.debug("doExec() - exec << '%s' %s", task.getClass().getName(), task);

                } finally {
                    CurrentTaskState.popState();
                }

            } finally {
                securityContext.popUser();
            }

        } finally {
            currentThread.setName(oldThreadName);
        }
    }

    @Override
    public BaseResultList<TaskProgress> terminate(final FindTaskCriteria criteria, final boolean kill) {
        // This can change a little between servers
        final long timeNowMs = System.currentTimeMillis();

        final List<TaskProgress> taskProgressList = new ArrayList<>();

        if (criteria != null && criteria.isConstrained()) {
            final Iterator<TaskThread<?>> iter = currentTasks.values().iterator();

            final List<TaskThread<?>> terminateList = new ArrayList<>();

            // Loop over all of the tasks that this node knows about and see if
            // it should be terminated.
            while (iter.hasNext()) {
                final TaskThread<?> taskThread = iter.next();
                final Task<?> task = taskThread.getTask();

                // Terminate it?
                if (kill || !task.isTerminated()) {
                    if (criteria.isMatch(task)) {
                        terminateList.add(taskThread);
                    }
                }
            }

            // Now terminate the relevant tasks.
            doTerminated(kill, timeNowMs, taskProgressList, terminateList);
        }

        return BaseResultList.createUnboundedList(taskProgressList);
    }

    private void doTerminated(final boolean kill, final long timeNowMs, final List<TaskProgress> taskProgressList,
                              final List<TaskThread<?>> itemsToKill) {
        for (final TaskThread<?> taskThread : itemsToKill) {
            final Task<?> task = taskThread.getTask();
            LOGGER.info("doTerminated() 1 - %s", taskThread.getTask());
            // First try and terminate the task.
            if (!task.isTerminated()) {
                taskThread.terminate();
            }

            // If we are forced to kill then kill the associated thread.
            if (kill) {
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

        final Iterator<TaskThread<?>> iter = currentTasks.values().iterator();
        while (iter.hasNext()) {
            final TaskThread<?> taskThread = iter.next();
            final Task<?> task = taskThread.getTask();

            final TaskProgress taskProgress = buildTaskProgress(timeNowMs, taskThread, task);

            // Only add this task progress if it matches the supplied criteria.
            if (findTaskProgressCriteria == null || findTaskProgressCriteria.matches(taskProgress)) {
                taskProgressList.add(taskProgress);
            }
        }

        return BaseResultList.createUnboundedList(taskProgressList);
    }

    private TaskProgress buildTaskProgress(final long timeNowMs, final TaskThread<?> taskThread, final Task<?> task) {
        final TaskProgress taskProgress = new TaskProgress();
        taskProgress.setId(task.getId());
        taskProgress.setTaskName(taskThread.getName());
        taskProgress.setSessionId(UserTokenUtil.getSessionId(task.getUserToken()));
        taskProgress.setUserName(UserTokenUtil.getUserId(task.getUserToken()));
        taskProgress.setThreadName(taskThread.getThreadName());
        taskProgress.setTaskInfo(taskThread.getInfo());
        taskProgress.setSubmitTimeMs(taskThread.getSubmitTimeMs());
        taskProgress.setTimeNowMs(timeNowMs);
        taskProgress.setNode(nodeCache.getDefaultNode());
        return taskProgress;
    }

    @Override
    public Task<?> getTaskById(final TaskId taskId) {
        final TaskThread<?> taskThread = currentTasks.get(taskId);
        if (taskThread != null) {
            return taskThread.getTask();
        }
        return null;
    }

    @Override
    public String toString() {
        final StringBuilder nonServerTasksSb = new StringBuilder();
        final List<Monitor> monitorList = new ArrayList<>();
        final Iterator<TaskThread<?>> iter = currentTasks.values().iterator();
        while (iter.hasNext()) {
            final TaskThread<?> taskThread = iter.next();
            final Task<?> task = taskThread.getTask();

            if (task instanceof HasMonitor) {
                final HasMonitor hasMonitor = (HasMonitor) task;
                monitorList.add(hasMonitor.getMonitor());
            } else {
                nonServerTasksSb.append(task.getTaskName());
                nonServerTasksSb.append(" ");
                nonServerTasksSb.append(task.getId().toString());
                nonServerTasksSb.append("\n");
            }
        }

        final String nonServerTasks = nonServerTasksSb.toString();
        final String serverTasks = MonitorInfoUtil.getInfo(monitorList);

        final StringBuilder sb = new StringBuilder();
        if (serverTasks != null && serverTasks.length() > 0) {
            sb.append("Server Tasks:\n");
            sb.append(serverTasks);
            sb.append("\n");
        }

        if (nonServerTasks != null && nonServerTasks.length() > 0) {
            sb.append("Non server Tasks:\n");
            sb.append(nonServerTasks);
            sb.append("\n");
        }

        return sb.toString();
    }

    @Override
    public FindTaskProgressCriteria createCriteria() {
        return new FindTaskProgressCriteria();
    }

    @Override
    public void appendCriteria(final List<BaseAdvancedQueryItem> items, final FindTaskProgressCriteria criteria) {
        CriteriaLoggingUtil.appendStringTerm(items, "sessionId", criteria.getSessionId());
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
