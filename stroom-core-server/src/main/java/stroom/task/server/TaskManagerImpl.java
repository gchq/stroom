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

package stroom.task.server;

import com.google.common.base.Strings;
import event.logging.BaseAdvancedQueryItem;
import org.springframework.stereotype.Component;
import stroom.entity.server.CriteriaLoggingUtil;
import stroom.entity.server.SupportsCriteriaLogging;
import stroom.entity.shared.BaseResultList;
import stroom.task.shared.FindTaskCriteria;
import stroom.task.shared.FindTaskProgressCriteria;
import stroom.task.shared.TaskProgress;
import stroom.util.concurrent.ScalingThreadPoolExecutor;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Task;
import stroom.util.shared.TaskId;
import stroom.util.shared.ThreadPool;
import stroom.util.concurrent.ExecutorProvider;
import stroom.util.task.ExternalShutdownController;
import stroom.util.task.ServerTask;
import stroom.util.thread.CustomThreadFactory;
import stroom.util.thread.StroomThreadGroup;
import stroom.util.thread.ThreadUtil;

import javax.inject.Inject;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * NB: we also define this in Spring XML so we can set some of the properties.
 */
@Component("taskManager")
class TaskManagerImpl implements TaskManager, ExecutorProvider, SupportsCriteriaLogging<FindTaskProgressCriteria> {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TaskManagerImpl.class);

    private final TaskFactory taskFactory;

    // The thread pools that will be used to execute tasks.
    private final ConcurrentHashMap<ThreadPool, ThreadPoolExecutor> threadPoolMap = new ConcurrentHashMap<>();
    private final ReentrantLock poolCreationLock = new ReentrantLock();
    private final AtomicInteger currentAsyncTaskCount = new AtomicInteger();

    @Inject
    TaskManagerImpl(final TaskFactory taskFactory) {
        this.taskFactory = taskFactory;

        // When we are running unit tests we need to make sure that all Stroom
        // threads complete and are shutdown between tests.
        ExternalShutdownController.addTerminateHandler(TaskManagerImpl.class, this::shutdown);
    }

    @Override
    public Executor getExecutor() {
        return getExecutor(ServerTask.THREAD_POOL);
    }

    @Override
    public Executor getExecutor(final ThreadPool threadPool) {
        return command -> {
            currentAsyncTaskCount.incrementAndGet();
            try {
                final Runnable outer = () -> {
                    try {
                        command.run();
                    } finally {
                        currentAsyncTaskCount.decrementAndGet();
                    }
                };
                getRealExecutor(threadPool).execute(outer);
            } catch (final Throwable t) {
                currentAsyncTaskCount.decrementAndGet();
                throw t;
            }
        };
    }

    private Executor getRealExecutor(final ThreadPool threadPool) {
        Objects.requireNonNull(threadPool, "Null thread pool");
        ThreadPoolExecutor executor = threadPoolMap.get(threadPool);
        if (executor == null) {
            poolCreationLock.lock();
            try {
                // Don't create a thread pool if we are supposed to be stopping
                if (taskFactory.isStopping()) {
                    throw new RejectedExecutionException("Stopping");
                }

                executor = threadPoolMap.computeIfAbsent(threadPool, k -> {
                    // Create a thread factory for the thread pool
                    final ThreadGroup poolThreadGroup = new ThreadGroup(StroomThreadGroup.instance(),
                            threadPool.getName());
                    final CustomThreadFactory taskThreadFactory = new CustomThreadFactory(
                            threadPool.getName() + " #", poolThreadGroup, threadPool.getPriority());

                    // Create the new thread pool for this priority
                    return ScalingThreadPoolExecutor.newScalingThreadPool(
                            threadPool.getCorePoolSize(),
                            threadPool.getMaxPoolSize(),
                            threadPool.getMaxQueueSize(),
                            60L,
                            TimeUnit.SECONDS,
                            taskThreadFactory);
                });
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
        LOGGER.info(() -> "startup()");
        taskFactory.startup();
    }

    /**
     * Tells tasks to terminate and waits for all tasks to terminate before
     * cleaning up the executors.
     */
    @Override
    public synchronized void shutdown() {
        LOGGER.info(() -> "shutdown()");

        // Wait for all tasks to stop executing.
        boolean waiting = true;
        while (waiting) {
            // Stop all of the current tasks.
            taskFactory.shutdown();

            final int currentCount = currentAsyncTaskCount.get();
            waiting = currentCount > 0;
            if (waiting) {
                // Output some debug to list the tasks that are executing
                // and queued.
                LOGGER.info(() -> "shutdown() - Waiting for " + currentCount + " tasks to complete. " + taskFactory.getRunningTasks());

                // Wait 1 second.
                ThreadUtil.sleep(1000);
            }
        }

        // Shut down all executors.
        shutdownExecutors();

        LOGGER.info(() -> "shutdown() - Complete");
    }

    /**
     * Execute a task synchronously.
     *
     * @param task The task to execute.
     * @return The result of the task execution.
     */
    @Override
    public <R> R exec(final Task<R> task) {
        return taskFactory.createSupplier(task).get();
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
            final Executor executor = getExecutor(threadPool);
            final Runnable taskRunnable = taskFactory.createRunnable(task, callback);
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

//    @Override
//    public void execAsync(final Task<?> parentTask, final UserIdentity userIdentity, final String taskName, final Runnable runnable, ThreadPool threadPool) {
//        final Runnable taskRunnable = taskFactory.createRunnable(parentTask, userIdentity, taskName, runnable);
//
//        // Now we have a task scoped runnable we will execute it in a new
//        // thread.
//        final Executor executor = getExecutor(threadPool);
//        try {
//            // We might run out of threads and get a can't fork
//            // exception from the thread pool.
//            currentAsyncTaskCount.incrementAndGet();
//            CompletableFuture
//                    .runAsync(taskRunnable, executor)
//                    .whenComplete((r, t) -> currentAsyncTaskCount.decrementAndGet());
//
//        } catch (final Throwable t) {
//            try {
//                LOGGER.error(() -> "exec() - Unexpected Exception (" + parentTask + taskName + ")", t);
//            } catch (final RuntimeException e) {
//                // Ignore.
//            }
//            throw t;
//        }
//    }

    @Override
    public BaseResultList<TaskProgress> terminate(final FindTaskCriteria criteria, final boolean kill) {
        return taskFactory.terminate(criteria, kill);
    }

    @Override
    public int getCurrentTaskCount() {
        return currentAsyncTaskCount.get();
    }

    @Override
    public BaseResultList<TaskProgress> find(final FindTaskProgressCriteria findTaskProgressCriteria) {
        return taskFactory.find(findTaskProgressCriteria);
    }

    @Override
    public Task<?> getTaskById(final TaskId taskId) {
        return taskFactory.getTaskById(taskId);
    }

    @Override
    public String toString() {
        return taskFactory.toString();
    }

    @Override
    public FindTaskProgressCriteria createCriteria() {
        return new FindTaskProgressCriteria();
    }

    @Override
    public void appendCriteria(final List<BaseAdvancedQueryItem> items, final FindTaskProgressCriteria criteria) {
        if (!Strings.isNullOrEmpty(criteria.getNameFilter())) {
            CriteriaLoggingUtil.appendStringTerm(items, "name", criteria.getNameFilter());
        }
    }

    private static class AsyncTaskCallback<R> extends TaskCallbackAdaptor<R> {
    }
}
