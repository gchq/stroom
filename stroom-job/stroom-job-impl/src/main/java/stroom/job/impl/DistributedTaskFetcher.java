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

package stroom.job.impl;

import stroom.cluster.task.api.TargetNodeSetFactory;
import stroom.job.api.DistributedTask;
import stroom.job.api.DistributedTaskFactory;
import stroom.node.api.NodeInfo;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * This class executes all tasks that are currently queued for execution. This
 * class may execute many tasks concurrently if required, e.g. using separate
 * threads for transforming multiple XML files.
 */
@Singleton
public class DistributedTaskFetcher {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DistributedTaskFetcher.class);
    public static final String JOB_NAME = "Fetch new tasks";

    private final TaskStatusTraceLog taskStatusTraceLog = new TaskStatusTraceLog();
    private final AtomicBoolean stopping = new AtomicBoolean();
    private final Set<DistributedTask> runningTasks = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final ExecutorProvider executorProvider;
    private final TaskContextFactory taskContextFactory;
    private final JobNodeTrackerCache jobNodeTrackerCache;
    private final NodeInfo nodeInfo;
    private final SecurityContext securityContext;
    private final DistributedTaskFactoryRegistry distributedTaskFactoryRegistry;
    private final TargetNodeSetFactory targetNodeSetFactory;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final AtomicBoolean needsTasks = new AtomicBoolean();
    private final AtomicBoolean running = new AtomicBoolean();

    @Inject
    public DistributedTaskFetcher(final ExecutorProvider executorProvider,
                                  final TaskContextFactory taskContextFactory,
                                  final JobNodeTrackerCache jobNodeTrackerCache,
                                  final NodeInfo nodeInfo,
                                  final SecurityContext securityContext,
                                  final DistributedTaskFactoryRegistry distributedTaskFactoryRegistry,
                                  final TargetNodeSetFactory targetNodeSetFactory) {
        this.executorProvider = executorProvider;
        this.taskContextFactory = taskContextFactory;
        this.jobNodeTrackerCache = jobNodeTrackerCache;
        this.nodeInfo = nodeInfo;
        this.securityContext = securityContext;
        this.distributedTaskFactoryRegistry = distributedTaskFactoryRegistry;
        this.targetNodeSetFactory = targetNodeSetFactory;
    }

    /**
     * Tells tasks to stop and waits for all tasks to stop before cleaning up
     * the executors.
     */
    void shutdown() {
        LOGGER.info("Shutting down '{}' job", JOB_NAME);
        try {
            stopping.set(true);
            // Not sure why this is here
            Thread.sleep(1_000);
        } catch (final InterruptedException e) {
            LOGGER.debug(() ->
                    LogUtil.message("shutdown() - Interrupted - {}", LogUtil.exceptionMessage(e), e));
            // Continue to interrupt this thread.
            Thread.currentThread().interrupt();
        }
    }

    /**
     * The Stroom lifecycle service will try and fetch new tasks for execution.
     */
    public void execute() {
        if (running.compareAndSet(false, true)) {
            final Executor executor = executorProvider.get();
            LOGGER.info("Starting '{}' job", JOB_NAME);
            executor.execute(() -> {
                try {
                    // All being well, this method won't complete until shutdown() is called as
                    // it will keep calling doFetch in a loop, however we need to allow for it failing.
                    fetch();
                } finally {
                    LOGGER.info("'{}' job stopped", JOB_NAME);
                    // Mark as not running so the ScheduledTaskExecutor can execute it again in 10s or so
                    running.set(false);
                }
            });
        }
    }

    /**
     * Tries to fetch tasks asynchronously if we aren't already fetching tasks.
     * If we are it will make sure we immediately try and fetch tasks again
     * after the previous fetch.
     */
    private void fetch() {
        try {
            securityContext.asProcessingUser(() -> {
                try {
                    while (!stopping.get()) {
                        needsTasks.set(false);
                        taskContextFactory.context("Fetch Tasks", taskContext -> {
                            try {
                                doFetch(taskContext);
                            } catch (final RuntimeException e) {
                                LOGGER.error(e.getMessage(), e);
                            }
                        }).run();

                        // If we don't need more tasks right now then lock and await.
                        if (!needsTasks.get()) {
                            lock.lockInterruptibly();
                            try {
                                // Check that we still don't need tasks since locking.
                                if (!needsTasks.get()) {
                                    // Wait up to 10 seconds for a task to complete.
                                    if (condition.await(10, TimeUnit.SECONDS)) {
                                        LOGGER.trace("fetch woken up");
                                    } else {
                                        LOGGER.trace("fetch await timeout");
                                    }
                                }
                            } finally {
                                lock.unlock();
                            }
                        }
                    }
                } catch (final InterruptedException e) {
                    LOGGER.debug(e::getMessage, e);
                    Thread.currentThread().interrupt();
                }
            });
        } catch (final RuntimeException e) {
            LOGGER.error("Error while fetching tasks - {}", LogUtil.exceptionMessage(e), e);
        }
    }

    private int doFetch(final TaskContext taskContext) {
        LOGGER.trace("doFetch()");
        info(taskContext, () -> "Starting task fetch");

        // Get the trackers.
        info(taskContext, () -> "Getting trackers");
        final JobNodeTrackers trackers = jobNodeTrackerCache.getTrackers();

        // Get this node.
        final String nodeName = nodeInfo.getThisNodeName();

        // Find out how many tasks we need in total.
        info(taskContext, () -> "Get required task count");
        int totalTaskLimit = getTotalTaskLimit(trackers);

        // If there are some tasks we need to get then get them.
        int executingTaskCount = 0;
        if (totalTaskLimit > 0) {
            if (targetNodeSetFactory.isClusterStateInitialised()) {
                for (final Entry<String, DistributedTaskFactory> entry :
                        distributedTaskFactoryRegistry.getFactoryMap().entrySet()) {
                    if (totalTaskLimit > 0) {
                        final String jobName = entry.getKey();
                        final DistributedTaskFactory distributedTaskFactory = entry.getValue();

                        LOGGER.debug(() -> LogUtil.message("Task request: node=\"{}\"",
                                nodeName));
                        LOGGER.trace(() -> LogUtil.message("\nTask request: node=\"{}\"\n{}",
                                nodeName,
                                distributedTaskFactory));

                        info(taskContext, () -> "Calling distributed task factory");
                        final List<DistributedTask> tasks = distributedTaskFactory.fetch(
                                nodeName,
                                totalTaskLimit);
                        info(taskContext, () -> "Executing " + tasks.size() + " new tasks");
                        final int startedCount = handleResult(nodeName, jobName, distributedTaskFactory, tasks);
                        executingTaskCount += startedCount;
                        totalTaskLimit -= startedCount;
                    }
                }
            }
        }
        return executingTaskCount;
    }

    /// Starts the tasks that this node has been given, and gives back those that it doesn't start, e.g. as it is
    /// stopping, so that they don't have to wait to be found. Not private so that a test can call it.
    ///
    /// @param nodeName               This node.
    /// @param jobName                The job that the tasks are for.
    /// @param distributedTaskFactory What the tasks came from, and are given back to.
    /// @param tasks                  The tasks.
    /// @return The number of tasks started.
    int handleResult(
            final String nodeName,
            final String jobName,
            final DistributedTaskFactory distributedTaskFactory,
            final List<DistributedTask> tasks) {
        final Set<DistributedTask> started = Collections.newSetFromMap(new IdentityHashMap<>());
        try {
            LOGGER.debug(() -> LogUtil.message("Task response: node=\"{}\"", nodeName));
            LOGGER.trace(() -> LogUtil.message("\nTask response: node=\"{}\"\n{}", nodeName, tasks));

            // Get the current time to record execution.
            final long now = System.currentTimeMillis();

            // Execute the returned tasks.
            final JobNodeTrackers trackers = jobNodeTrackerCache.getTrackers();
            taskStatusTraceLog.receiveOnWorkerNode(DistributedTaskFetcher.class, tasks, jobName);

            // Get the latest local tracker.
            final JobNodeTracker tracker = trackers.getTrackerForJobName(jobName);
            for (final DistributedTask task : tasks) {
                if (stopping.get()) {
                    break;
                }
                if (start(task, tracker, now)) {
                    started.add(task);
                }
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        } finally {
            // Whatever went wrong, every task that wasn't started is given back.
            giveBack(nodeName, distributedTaskFactory, tasks
                    .stream()
                    .filter(task -> !started.contains(task))
                    .toList());
        }
        return started.size();
    }

    // Returns true if the task was started.
    private boolean start(final DistributedTask task, final JobNodeTracker tracker, final long now) {
        runningTasks.add(task);
        tracker.incrementTaskCount();
        tracker.setLastExecutedTime(now);
        try {
            final Executor executor = executorProvider.get(task.getThreadPool());
            CompletableFuture
                    .runAsync(task.getRunnable(), executor)
                    .whenComplete((r, t) -> {
                        runningTasks.remove(task);
                        tracker.decrementTaskCount();
                        signal();
                    });
            return true;
        } catch (final RuntimeException e) {
            // E.g. the executor has been shut down.
            LOGGER.error(e::getMessage, e);
            runningTasks.remove(task);
            tracker.decrementTaskCount();
            return false;
        }
    }

    private void giveBack(final String nodeName,
                          final DistributedTaskFactory distributedTaskFactory,
                          final List<DistributedTask> tasks) {
        if (!tasks.isEmpty()) {
            LOGGER.info(() -> LogUtil.message("Giving back {} tasks that will not be run",
                    tasks.size()));
            try {
                if (!Boolean.TRUE.equals(distributedTaskFactory.abandon(nodeName, tasks))) {
                    LOGGER.error(() -> LogUtil.message("Unable to give back {} tasks", tasks.size()));
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
            }
        }
    }

    private void signal() {
        try {
            lock.lockInterruptibly();
            try {
                needsTasks.set(true);
                condition.signal();
            } finally {
                lock.unlock();
            }
        } catch (final InterruptedException e) {
            LOGGER.debug(e::getMessage, e);
            Thread.currentThread().interrupt();
        }
    }

    private int getTotalTaskLimit(final JobNodeTrackers trackers) {
        int totalTaskLimit = 0;
        final Collection<JobNodeTracker> trackerList = trackers.getDistributedJobNodeTrackers();
        for (final JobNodeTracker tracker : trackerList) {
            final int taskLimit = tracker.getJobNode().getTaskLimit() - tracker.getCurrentTaskCount();
            totalTaskLimit += taskLimit;
        }
        return totalTaskLimit;
    }

    private void info(final TaskContext taskContext,
                      final Supplier<String> messageSupplier) {
        LOGGER.debug(messageSupplier);
        taskContext.info(messageSupplier);
    }
}
