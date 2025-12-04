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
        int executingTaskCount = 0;
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
                        handleResult(nodeName, jobName, tasks);
                        executingTaskCount += tasks.size();
                        totalTaskLimit -= tasks.size();
                    }
                }
            }
        }
        return executingTaskCount;
    }

    private void handleResult(
            final String nodeName,
            final String jobName,
            final List<DistributedTask> tasks) {
        try {
            LOGGER.debug(() -> LogUtil.message("Task response: node=\"{}\"", nodeName));
            LOGGER.trace(() -> LogUtil.message("\nTask response: node=\"{}\"\n{}", nodeName, tasks));

            // Get the current time to record execution.
            final long now = System.currentTimeMillis();

            // Execute the returned tasks.
            final JobNodeTrackers trackers = jobNodeTrackerCache.getTrackers();
            taskStatusTraceLog.receiveOnWorkerNode(DistributedTaskFetcher.class, tasks, jobName);

            if (!stopping.get()) {
                // Get the latest local tracker.
                final JobNodeTracker tracker = trackers.getTrackerForJobName(jobName);
                // Try and get more tasks.
                tasks.forEach(task -> {
                    runningTasks.add(task);
                    tracker.incrementTaskCount();
                    tracker.setLastExecutedTime(now);

                    if (!stopping.get()) {
                        final Executor executor = executorProvider.get(task.getThreadPool());
                        CompletableFuture
                                .runAsync(task.getRunnable(), executor)
                                .whenComplete((r, t) -> {
                                    runningTasks.remove(task);
                                    tracker.decrementTaskCount();
                                    signal();
                                });
                    } else {
                        runningTasks.remove(task);
                        tracker.decrementTaskCount();
                    }
                });
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
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
