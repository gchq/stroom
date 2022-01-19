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
import stroom.job.shared.JobNode;
import stroom.job.shared.JobNode.JobType;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.ThreadPoolImpl;
import stroom.task.shared.ThreadPool;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.Metrics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class executes all tasks that are currently queued for execution. This
 * class may execute many tasks concurrently if required, e.g. using separate
 * threads for transforming multiple XML files.
 */
@Singleton
class DistributedTaskFetcher {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DistributedTaskFetcher.class);
    private static final ThreadPool THREAD_POOL = new ThreadPoolImpl("Data Processor#", 1);

    private final AtomicBoolean stopping = new AtomicBoolean();
    private volatile Thread fetchThread;
    private volatile Thread addRemoveExecutorsThread;
    private final ExecutorProvider executorProvider;
    private final TaskContextFactory taskContextFactory;
    private final JobNodeTrackerCache jobNodeTrackerCache;
    private final SecurityContext securityContext;
    private final DistributedTaskFactoryRegistry distributedTaskFactoryRegistry;
    private final TargetNodeSetFactory targetNodeSetFactory;
    private final AtomicBoolean running = new AtomicBoolean();


    private final ArrayBlockingQueue<DistributedTask> taskQueue = new ArrayBlockingQueue<>(10);
    private final List<TaskExecutor> taskExecutors = new ArrayList<>();
    private final Executor executor;

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

        executor = executorProvider.get(THREAD_POOL);
    }

    /**
     * Tells tasks to stop and waits for all tasks to stop before cleaning up
     * the executors.
     */
    void shutdown() {
        stopping.set(true);
        checkStop();
    }

    /**
     * The Stroom lifecycle service will try and fetch new tasks for execution.
     */
    void execute() {
        if (running.compareAndSet(false, true)) {
            final Executor executor = executorProvider.get();
            executor.execute(this::addRemoveExecutors);
            executor.execute(this::fetchNewTasks);
        }
    }

    /**
     * Dynamically adjusts the number of threads actively executing tasks from the task queue.
     */
    private void addRemoveExecutors() {
        try {
            addRemoveExecutorsThread = Thread.currentThread();
            checkStop();

            securityContext.asProcessingUser(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    // Get the trackers.
                    final JobNodeTrackerCache.Trackers trackers = jobNodeTrackerCache.getTrackers();

                    // Calculate how many tasks we want vs how many we are currently running.
                    final Collection<JobNodeTracker> trackerList = trackers.getTrackerList();
                    int taskLimit = 0;
                    for (final JobNodeTracker tracker : trackerList) {
                        final JobNode jobNode = tracker.getJobNode();
                        if (JobType.DISTRIBUTED.equals(jobNode.getJobType())) {
                            // The job and job node must be enabled in order for us to
                            // request tasks. If they are then we still want to request
                            // tasks even if the number of tasks required is 0 (or less...).
                            // This is to ensure that the job cache on the distributor keeps
                            // tasks cached even though we aren't actually requesting any at
                            // this time.
                            if (jobNode.getJob().isEnabled() && jobNode.isEnabled()) {
                                taskLimit += jobNode.getTaskLimit();
                            }
                        }
                    }

                    // Add or remove executors.
                    if (taskLimit > taskExecutors.size()) {
                        // Start new executors.
                        for (int i = taskExecutors.size(); i < taskLimit; i++) {
                            final TaskExecutor taskExecutor = new TaskExecutor(jobNodeTrackerCache, taskQueue);
                            executor.execute(taskExecutor::start);
                            taskExecutors.add(taskExecutor);
                        }
                    } else {
                        // Stop surplus executors.
                        for (int i = taskExecutors.size() - 1; i >= taskLimit; i--) {
                            final TaskExecutor taskExecutor = taskExecutors.remove(i);
                            taskExecutor.stop();
                        }
                    }

                    try {
                        // Wait 10 seconds before we try to adjust again.
                        Thread.sleep(10000);
                    } catch (final InterruptedException e) {
                        LOGGER.debug(e::getMessage, e);
                        Thread.currentThread().interrupt();
                    }
                }

                // Stop remaining executors.
                for (final TaskExecutor taskExecutor : taskExecutors) {
                    taskExecutor.stop();
                }
            });
        } catch (final RuntimeException e) {
            LOGGER.error(() -> "Unable to adjust number of task executors!", e);
        }
    }

    /**
     * Tries to fetch tasks asynchronously if we aren't already fetching tasks.
     * If we are it will make sure we immediately try and fetch tasks again
     * after the previous fetch.
     */
    private void fetchNewTasks() {
        try {
            fetchThread = Thread.currentThread();
            checkStop();

            securityContext.asProcessingUser(() -> {
                while (!Thread.currentThread().isInterrupted()) {

                    // Allow waiting if we are actually asking for data but might not get any.
                    final List<DistributedTask> newTasks = new ArrayList<>();
                    if (targetNodeSetFactory.isClusterStateInitialised()) {
                        // Get this node.
                        final String nodeName = jobNodeTrackerCache.getNodeName();

                        for (final Entry<String, DistributedTaskFactory> entry :
                                distributedTaskFactoryRegistry.getFactoryMap().entrySet()) {

                            final DistributedTaskFactory distributedTaskFactory = entry.getValue();
                            LOGGER.debug(() -> "Task request: node=\"" + nodeName + "\"");
                            LOGGER.trace(() -> "\nTask request: node=\"" + nodeName + "\"\n"
                                    + distributedTaskFactory);

                            final List<DistributedTask> tasks = Metrics.measure("fetching tasks", () ->
                                    taskContextFactory.contextResult("Fetch Tasks", taskContext -> {
                                        List<DistributedTask> response = null;
                                        try {
                                            taskContext.info(() -> "fetching tasks");
                                            response = distributedTaskFactory.fetch(
                                                    nodeName,
                                                    10);


                                        } catch (final RuntimeException e) {
                                            LOGGER.error(e::getMessage, e);
                                        }
                                        return response;
                                    }).get());

                            LOGGER.debug(() -> "Task response: node=\"" + nodeName + "\"");
                            LOGGER.trace(() -> "\nTask response: node=\"" + nodeName + "\"\n"
                                    + tasks);

                            newTasks.addAll(tasks);
                        }
                    }

                    if (newTasks.size() == 0) {
                        sleep();
                    } else {
                        try {
                            for (final DistributedTask task : newTasks) {
                                taskQueue.put(task);
                            }

                        } catch (final InterruptedException e) {
                            LOGGER.debug(e::getMessage, e);
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            });
        } catch (final RuntimeException e) {
            LOGGER.error(() -> "Unable to fetch task!", e);
        }
    }

    private synchronized void checkStop() {
        if (stopping.get()) {
            if (fetchThread != null) {
                fetchThread.interrupt();
            }
            if (addRemoveExecutorsThread != null) {
                addRemoveExecutorsThread.interrupt();
            }
        }
    }

    private void sleep() {
        // If we didn't get any tasks then wait.
        LOGGER.info(() -> "SLEEPING!!!!!");
        // Wait a second before trying to get more tasks.
        Metrics.measure("Sleeping", () -> {
            try {
                Thread.sleep(1000);
            } catch (final InterruptedException e) {
                LOGGER.debug(e::getMessage, e);
                Thread.currentThread().interrupt();
            }
        });
    }

    private static class TaskExecutor {

        private final JobNodeTrackerCache jobNodeTrackerCache;
        private final ArrayBlockingQueue<DistributedTask> taskQueue;
        private final AtomicBoolean run = new AtomicBoolean(true);

        public TaskExecutor(final JobNodeTrackerCache jobNodeTrackerCache,
                            final ArrayBlockingQueue<DistributedTask> taskQueue) {
            this.jobNodeTrackerCache = jobNodeTrackerCache;
            this.taskQueue = taskQueue;
        }

        public void start() {
            try {
                while (run.get()) {
                    final DistributedTask task = taskQueue.take();

                    final JobNodeTrackerCache.Trackers trackers = jobNodeTrackerCache.getTrackers();
                    final JobNodeTracker tracker = trackers.getTrackerForJobName(task.getJobName());
                    tracker.incrementTaskCount();
                    tracker.setLastExecutedTime(System.currentTimeMillis());
                    try {
                        task.getRunnable().run();
                    } catch (RuntimeException e) {
                        LOGGER.error(e::getMessage, e);
                    } finally {
                        tracker.decrementTaskCount();
                    }
                }
            } catch (final InterruptedException e) {
                LOGGER.debug(e::getMessage, e);
                Thread.currentThread().interrupt();
            }
        }

        public void stop() {
            run.set(false);
        }
    }
}
