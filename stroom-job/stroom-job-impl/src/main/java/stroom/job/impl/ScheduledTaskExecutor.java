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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;
import stroom.job.api.ScheduledJob;
import stroom.job.shared.JobNode;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContext;
import stroom.util.logging.LogExecutionTime;
import stroom.util.scheduler.FrequencyScheduler;
import stroom.util.scheduler.Scheduler;
import stroom.util.scheduler.SimpleCron;
import stroom.util.thread.CustomThreadFactory;
import stroom.util.thread.StroomThreadGroup;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

@Singleton
class ScheduledTaskExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScheduledTaskExecutor.class);

    private static final String STROOM_JOB_THREAD_POOL = "Stroom Job#";

    private final ConcurrentHashMap<ScheduledJob, AtomicBoolean> runningMapOfScheduledJobs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ScheduledJob, Scheduler> schedulerMapOfScheduledJobs = new ConcurrentHashMap<>();

    private final Map<ScheduledJob, Provider<Runnable>> scheduledJobsMap;
    private final JobNodeTrackerCache jobNodeTrackerCache;
    private final Executor executor;
    private final Provider<TaskContext> taskContextProvider;
    private final SecurityContext securityContext;

    private final AtomicReference<ScheduledExecutorService> scheduledExecutorService = new AtomicReference<>();
    private final boolean enabled;
    private final long executionInterval;

    @Inject
    ScheduledTaskExecutor(final Map<ScheduledJob, Provider<Runnable>> scheduledJobsMap,
                          final JobNodeTrackerCache jobNodeTrackerCache,
                          final Executor executor,
                          final Provider<TaskContext> taskContextProvider,
                          final JobSystemConfig jobSystemConfig,
                          final SecurityContext securityContext) {
        this.scheduledJobsMap = scheduledJobsMap;
        this.jobNodeTrackerCache = jobNodeTrackerCache;
        this.executor = executor;
        this.taskContextProvider = taskContextProvider;
        this.securityContext = securityContext;
        this.enabled = jobSystemConfig.isEnabled();
        this.executionInterval = jobSystemConfig.getExecutionIntervalMs();
    }

    void startup() {
        if (enabled) {
            LOGGER.info("Starting Stroom Job service");
            // Create the runnable object that will perform execution on all
            // scheduled services.
            final ReentrantLock lock = new ReentrantLock();

            final Runnable runnable = () -> {
                if (lock.tryLock()) {
                    try {
                        securityContext.asProcessingUser(() -> {
                            Thread.currentThread().setName("Stroom Job - ScheduledExecutor");
                            execute();
                        });
                    } catch (final RuntimeException e) {
                        LOGGER.error(e.getMessage(), e);
                    } finally {
                        lock.unlock();
                    }
                } else {
                    LOGGER.warn("Still trying to execute tasks");
                }
            };

            // Create the thread pool that we will use to startup, shutdown and execute lifecycle beans asynchronously.
            final CustomThreadFactory threadFactory = new CustomThreadFactory(STROOM_JOB_THREAD_POOL,
                    StroomThreadGroup.instance(), Thread.MIN_PRIORITY + 1);

            // Create the executor service that will execute scheduled services.
            final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1, threadFactory);
            scheduledExecutorService.scheduleWithFixedDelay(runnable, 0, executionInterval, TimeUnit.MILLISECONDS);
            this.scheduledExecutorService.set(scheduledExecutorService);
        }
    }

    void shutdown() {
        if (enabled) {
            LOGGER.info("Stopping Stroom Job service");
            final ScheduledExecutorService scheduledExecutorService = this.scheduledExecutorService.get();
            if (scheduledExecutorService != null) {
                // Stop the scheduled executor.
                scheduledExecutorService.shutdown();
                try {
                    scheduledExecutorService.awaitTermination(1, TimeUnit.MINUTES);
                } catch (final InterruptedException e) {
                    LOGGER.error("Waiting termination interrupted!", e);

                    // Continue to interrupt this thread.
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private void execute() {
        for (final ScheduledJob scheduledJob : scheduledJobsMap.keySet()) {
            try {
                final String taskName = scheduledJob.getName();
                final ScheduledJobFunction function = create(scheduledJob);
                if (function != null) {
                    final TaskContext taskContext = taskContextProvider.get();
                    Runnable runnable = () -> {
                        try {
                            taskContext.setName(taskName);
                            final LogExecutionTime logExecutionTime = new LogExecutionTime();
                            LOGGER.debug("exec() - >>> {}", taskName);
                            function.run();
                            LOGGER.debug("exec() - <<< {} took {}", taskName, logExecutionTime);
                        } catch (final RuntimeException e) {
                            LOGGER.error("Error calling {}", taskName, e);
                        }
                    };
                    runnable = taskContext.sub(runnable);
                    CompletableFuture
                            .runAsync(runnable, executor)
                            .whenComplete((r, t) -> function.getRunning().set(false));
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
    }

    private ScheduledJobFunction create(final ScheduledJob scheduledJob) {
        ScheduledJobFunction function = null;

        final AtomicBoolean running = getRunningState(scheduledJob);

        // Only run one instance of this method at a time.
        if (running.compareAndSet(false, true)) {
            try {
                boolean enabled = true;
                Scheduler scheduler = null;
                JobNodeTracker jobNodeTracker;

                final JobNodeTrackerCache.Trackers trackers = jobNodeTrackerCache.getTrackers();
                jobNodeTracker = trackers.getTrackerForJobName(scheduledJob.getName());

                if (scheduledJob.isManaged()) {
                    enabled = false;
                    if (jobNodeTracker == null) {
                        LOGGER.error("No job node tracker found for: " + scheduledJob.getName());
                    } else {
                        final JobNode jobNode = jobNodeTracker.getJobNode();
                        if (jobNode == null) {
                            LOGGER.error("Job node tracker has null job node for: " + scheduledJob.getName());
                        } else {
                            enabled = jobNode.isEnabled() && jobNode.getJob().isEnabled();
                            scheduler = trackers.getScheduler(jobNode);
                        }
                    }
                } else {
                    scheduler = getOrCreateScheduler(scheduledJob);
                }

                if (enabled && scheduler != null && scheduler.execute()) {
                    //TODO log trace
//                    LOGGER.trace("Returning runnable for method: {} - {} - {}", methodReference, enabled, scheduler);
                    if (jobNodeTracker != null) {
                        final Provider<Runnable> consumerProvider = scheduledJobsMap.get(scheduledJob);
                        function = new JobNodeTrackedFunction(scheduledJob, consumerProvider.get(), running,
                                jobNodeTracker);
                    } else {
                        final Provider<Runnable> consumerProvider = scheduledJobsMap.get(scheduledJob);
                        function = new ScheduledJobFunction(scheduledJob, consumerProvider.get(), running);
                    }
                } else {
                    LOGGER.trace("Not returning runnable for method: {} - {} - {}", scheduledJob.getName(), enabled, scheduler);
                    running.set(false);
                }
            } catch (final RuntimeException e) {
                LOGGER.error(MarkerFactory.getMarker("FATAL"), e.getMessage());
            }
        } else {
            LOGGER.trace("Skipping as method still running: {}", scheduledJob.getName());
        }

        return function;
    }

    private Scheduler getOrCreateScheduler(final ScheduledJob scheduledJob) {
        Scheduler scheduler = schedulerMapOfScheduledJobs.get(scheduledJob);
        if (scheduler == null) {
            switch (scheduledJob.getSchedule().getScheduleType()) {
                case CRON:
                    final SimpleCron simpleCron = SimpleCron.compile(scheduledJob.getSchedule().getSchedule());
                    scheduler = simpleCron.createScheduler();
                    break;

                case PERIODIC:
                    scheduler = new FrequencyScheduler(scheduledJob.getSchedule().getSchedule());
                    break;
                default:
                    throw new RuntimeException("Unsupported ScheduleType!");
            }
            schedulerMapOfScheduledJobs.put(scheduledJob, scheduler);
        }

        return scheduler;
    }

    private AtomicBoolean getRunningState(final ScheduledJob scheduledJob) {
        return runningMapOfScheduledJobs.computeIfAbsent(scheduledJob, k -> new AtomicBoolean(false));
    }

    private static class JobNodeTrackedFunction extends ScheduledJobFunction {
        private static final Logger LOGGER = LoggerFactory.getLogger(JobNodeTrackedFunction.class);

        private final JobNodeTracker jobNodeTracker;

        JobNodeTrackedFunction(final ScheduledJob scheduledJob,
                               final Runnable consumer,
                               final AtomicBoolean running,
                               final JobNodeTracker jobNodeTracker) {
            super(scheduledJob, consumer, running);
            this.jobNodeTracker = jobNodeTracker;
        }

        @Override
        public void run() {
            try {
                jobNodeTracker.incrementTaskCount();
                try {
                    jobNodeTracker.setLastExecutedTime(System.currentTimeMillis());
                    try {
                        super.run();
                    } finally {
                        jobNodeTracker.setLastExecutedTime(System.currentTimeMillis());
                    }
                } finally {
                    jobNodeTracker.decrementTaskCount();
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }

        @Override
        public String toString() {
            return jobNodeTracker.getJobNode().getJob().getName();
        }
    }
}
