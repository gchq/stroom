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
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

/**
 * This class executes all tasks that are currently queued for execution. This
 * class may execute many tasks concurrently if required, e.g. using separate
 * threads for transforming multiple XML files.
 */
@Singleton
class DistributedTaskFetcher {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DistributedTaskFetcher.class);

    private final TaskStatusTraceLog taskStatusTraceLog = new TaskStatusTraceLog();
    private final AtomicBoolean stopping = new AtomicBoolean();
    private final Set<DistributedTask> runningTasks = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final ExecutorProvider executorProvider;
    private final TaskContextFactory taskContextFactory;
    private final JobNodeTrackerCache jobNodeTrackerCache;
    private final SecurityContext securityContext;
    private final DistributedTaskFactoryRegistry distributedTaskFactoryRegistry;
    private final TargetNodeSetFactory targetNodeSetFactory;

    private volatile Instant lastFetch = Instant.ofEpochMilli(0);
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final AtomicBoolean needsTasks = new AtomicBoolean();
    private final AtomicBoolean running = new AtomicBoolean();

    @Inject
    DistributedTaskFetcher(final ExecutorProvider executorProvider,
                           final TaskContextFactory taskContextFactory,
                           final JobNodeTrackerCache jobNodeTrackerCache,
                           final SecurityContext securityContext,
                           final DistributedTaskFactoryRegistry distributedTaskFactoryRegistry,
                           final TargetNodeSetFactory targetNodeSetFactory) {
        this.executorProvider = executorProvider;
        this.taskContextFactory = taskContextFactory;
        this.jobNodeTrackerCache = jobNodeTrackerCache;
        this.securityContext = securityContext;
        this.distributedTaskFactoryRegistry = distributedTaskFactoryRegistry;
        this.targetNodeSetFactory = targetNodeSetFactory;
    }

    /**
     * Tells tasks to stop and waits for all tasks to stop before cleaning up
     * the executors.
     */
    void shutdown() {
        try {
            stopping.set(true);
            Thread.sleep(1000);

        } catch (final InterruptedException e) {
            LOGGER.debug(e::getMessage, e);

            // Continue to interrupt this thread.
            Thread.currentThread().interrupt();
        }
    }

    /**
     * The Stroom lifecycle service will try and fetch new tasks for execution.
     */
    void execute() {
        if (running.compareAndSet(false, true)) {
            final Executor executor = executorProvider.get();
            executor.execute(this::fetch);
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
                        final int executingTaskCount =
                                taskContextFactory.contextResult("Fetch Tasks", taskContext -> {
                                    try {
                                        return doFetch(taskContext);
                                    } catch (final RuntimeException e) {
                                        LOGGER.error(e.getMessage(), e);
                                    }
                                    return 0;
                                }).get();

                        // If we didn't get any tasks then wait a second.
                        if (executingTaskCount == 0) {
                            ThreadUtil.sleep(1000);
                        }

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
            LOGGER.error("Unable to fetch task!", e);
        }
    }

    private int doFetch(final TaskContext taskContext) {
        int executingTaskCount = 0;
        taskContext.info(() -> "Fetching tasks");
        LOGGER.trace("Trying to fetch tasks");

        // We will force a fetch if it has been more than one minute since
        // our last fetch. This allows the master node to know that the
        // worker nodes are still alive and that it is still going to be
        // required to distribute tasks. If it did not get a call every
        // minute it might try and release cached tasks back to the database
        // event though this doesn't happen in the current implementation.
        final Instant now = Instant.now();
        final boolean forceFetch = now.isAfter(lastFetch.plus(1, ChronoUnit.MINUTES));

        // Get the trackers.
        final JobNodeTrackerCache.Trackers trackers = jobNodeTrackerCache.getTrackers();

        // Get this node.
        final String nodeName = jobNodeTrackerCache.getNodeName();

        // Find out how many tasks we need in total.
        final int count = getRequiredTaskCount(trackers);

        // If there are some tasks we need to get then get them.
        if (count > 0 || forceFetch) {
            if (targetNodeSetFactory.isClusterStateInitialised()) {
                for (final Entry<String, DistributedTaskFactory> entry :
                        distributedTaskFactoryRegistry.getFactoryMap().entrySet()) {

                    final String jobName = entry.getKey();
                    final DistributedTaskFactory distributedTaskFactory = entry.getValue();

                    LOGGER.debug(() -> LogUtil.message("Task request: node=\"{}\"",
                            nodeName));
                    LOGGER.trace(() -> LogUtil.message("\nTask request: node=\"{}\"\n{}",
                            nodeName,
                            distributedTaskFactory));

                    final List<DistributedTask> tasks = distributedTaskFactory.fetch(
                            nodeName,
                            count);
                    handleResult(nodeName, jobName, tasks);
                    executingTaskCount += tasks.size();
                }

                // Remember the last fetch time.
                lastFetch = now;
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
            final JobNodeTrackerCache.Trackers trackers = jobNodeTrackerCache.getTrackers();
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

    private int getRequiredTaskCount(final JobNodeTrackerCache.Trackers trackers) {
        int totalRequiredTasks = 0;
        final Collection<JobNodeTracker> trackerList = trackers.getDistributedJobNodeTrackers();
        for (final JobNodeTracker tracker : trackerList) {
            final int requiredTaskCount = tracker.getJobNode().getTaskLimit() - tracker.getCurrentTaskCount();
            totalRequiredTasks += requiredTaskCount;
        }
        return totalRequiredTasks;
    }
}
