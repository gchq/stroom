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

import stroom.job.api.DistributedTask;
import stroom.job.api.DistributedTaskFactory;
import stroom.job.shared.Job;
import stroom.job.shared.JobNode;
import stroom.job.shared.JobNode.JobType;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class executes all tasks that are currently queued for execution. This
 * class may execute many tasks concurrently if required, e.g. using separate
 * threads for transforming multiple XML files.
 */
@Singleton
class DistributedTaskFetcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(DistributedTaskFetcher.class);
    private static final long ONE_MINUTE = 60 * 1000;

    private final TaskStatusTraceLog taskStatusTraceLog = new TaskStatusTraceLog();
    private final AtomicBoolean stopping = new AtomicBoolean();
    private final Set<DistributedTask> runningTasks = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final ExecutorProvider executorProvider;
    private final TaskContextFactory taskContextFactory;
    private final JobNodeTrackerCache jobNodeTrackerCache;
    private final SecurityContext securityContext;
    private final DistributedTaskFactoryRegistry distributedTaskFactoryRegistry;

    private long lastFetch;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final AtomicBoolean running = new AtomicBoolean();

    @Inject
    DistributedTaskFetcher(final ExecutorProvider executorProvider,
                           final TaskContextFactory taskContextFactory,
                           final JobNodeTrackerCache jobNodeTrackerCache,
                           final SecurityContext securityContext,
                           final DistributedTaskFactoryRegistry distributedTaskFactoryRegistry) {
        this.executorProvider = executorProvider;
        this.taskContextFactory = taskContextFactory;
        this.jobNodeTrackerCache = jobNodeTrackerCache;
        this.securityContext = securityContext;
        this.distributedTaskFactoryRegistry = distributedTaskFactoryRegistry;
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
            LOGGER.debug(e.getMessage(), e);

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
                        lock.lockInterruptibly();
                        try {
                            final int executingTaskCount =
                                    taskContextFactory.contextResult("Fetch Tasks", taskContext -> {
                                        try {
                                            return doFetch(taskContext);
                                        } catch (final RuntimeException e) {
                                            LOGGER.error(e.getMessage(), e);
                                        }
                                        return 0;
                                    }).get();

                            if (executingTaskCount == 0) {
                                // Wait a second before trying to get more tasks.
                                Thread.sleep(1000);
                            } else {
                                // Just wait until a task completes.
                                condition.await();
                            }
                        } finally {
                            lock.unlock();
                        }
                    }
                } catch (final InterruptedException e) {
                    LOGGER.debug(e.getMessage(), e);
                    Thread.currentThread().interrupt();
                }
            });
        } catch (final RuntimeException e) {
            LOGGER.error("Unable to fetch task!", e);
        }
    }

    private int doFetch(final TaskContext taskContext) {
        int executingTaskCount = 0;
        taskContext.info(() -> "fetching tasks");
        LOGGER.trace("Trying to fetch tasks");

        // We will force a fetch if it has been more than one minute since
        // our last fetch. This allows the master node to know that the
        // worker nodes are still alive and that it is still going to be
        // required to distribute tasks. If it did not get a call every
        // minute it might try and release cached tasks back to the database
        // event though this doesn't happen in the current implementation.
        final long now = System.currentTimeMillis();
        final long elapsed = now - lastFetch;
        final boolean forceFetch = elapsed > ONE_MINUTE;

        // Get the trackers.
        final JobNodeTrackerCache.Trackers trackers = jobNodeTrackerCache.getTrackers();

        // Get this node.
        final String nodeName = jobNodeTrackerCache.getNodeName();

        // Create an array of runnable jobs sorted into priority order.
        final DistributedRequiredTask[] requiredTasks = getDistributedRequiredTasks(trackers);

        // Find out how many tasks we need in total.
        int count = 0;
        for (final DistributedRequiredTask requiredTask : requiredTasks) {
            count += requiredTask.getRequiredTaskCount();
        }

        // If there are some tasks we need to get then get them.
        if (count > 0 || forceFetch) {
            for (final Entry<String, DistributedTaskFactory> entry :
                    distributedTaskFactoryRegistry.getFactoryMap().entrySet()) {

                final String jobName = entry.getKey();
                final DistributedTaskFactory distributedTaskFactory = entry.getValue();

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Task request: node=\"" + nodeName + "\"");
                    if (LOGGER.isTraceEnabled()) {
                        final String trace = "\nTask request: node=\"" + nodeName + "\"\n"
                                + distributedTaskFactory;
                        LOGGER.trace(trace);
                    }
                }

                final List<DistributedTask> tasks = distributedTaskFactory.fetch(
                        nodeName,
                        count);
                handleResult(nodeName, jobName, tasks);
                executingTaskCount += tasks.size();
            }

            // Remember the last fetch time.
            lastFetch = now;
        }
        return executingTaskCount;
    }

    private void handleResult(
            final String nodeName,
            final String jobName,
            final List<DistributedTask> tasks) {
        try {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Task response: node=\"" + nodeName + "\"");
                if (LOGGER.isTraceEnabled()) {
                    final String trace = "\nTask response: node=\"" + nodeName + "\"\n"
                            + tasks;
                    LOGGER.trace(trace);
                }
            }

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
            LOGGER.error(e.getMessage(), e);
        }
    }

    private void signal() {
        try {
            lock.lockInterruptibly();
            try {
                condition.signal();
            } finally {
                lock.unlock();
            }
        } catch (final InterruptedException e) {
            LOGGER.debug(e.getMessage(), e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Creates an array of runnable jobs sorted into priority order.
     */
    private DistributedRequiredTask[] getDistributedRequiredTasks(final JobNodeTrackerCache.Trackers trackers) {
        final Collection<JobNodeTracker> trackerList = trackers.getTrackerList();

        // Create a shortlist of runnable jobs.
        DistributedRequiredTask[] requiredTasks = new DistributedRequiredTask[trackerList.size()];
        int length = 0;

        for (final JobNodeTracker tracker : trackerList) {
            final JobNode jobNode = tracker.getJobNode();
            final Job job = jobNode.getJob();

            if (JobType.DISTRIBUTED.equals(jobNode.getJobType())) {
                // Update the number of tasks that are still required by this
                // tracker.
                final int requiredTaskCount = jobNode.getTaskLimit() - tracker.getCurrentTaskCount();

                // The job and job node must be enabled in order for us to
                // request tasks. If they are then we still want to request
                // tasks even if the number of tasks required is 0 (or less...).
                // This is to ensure that the job cache on the distributor keeps
                // tasks cached even though we aren't actually requesting any at
                // this time.
                if (job.isEnabled() && jobNode.isEnabled()) {
                    // Store the task request.
                    requiredTasks[length++] = new DistributedRequiredTask(jobNode.getJob().getName(),
                            requiredTaskCount);
                }
            }
        }

        // Trim the array.
        final DistributedRequiredTask[] tmp = new DistributedRequiredTask[length];
        System.arraycopy(requiredTasks, 0, tmp, 0, length);
        requiredTasks = tmp;

        return requiredTasks;
    }
}
