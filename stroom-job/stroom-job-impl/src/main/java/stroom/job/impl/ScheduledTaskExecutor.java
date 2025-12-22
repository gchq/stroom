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

import stroom.cluster.api.ClusterNodeManager;
import stroom.job.api.ScheduledJob;
import stroom.job.shared.JobNode;
import stroom.node.api.NodeInfo;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskTerminatedException;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.scheduler.SimpleScheduleExec;
import stroom.util.scheduler.TriggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.scheduler.Schedule;
import stroom.util.shared.scheduler.ScheduleType;
import stroom.util.thread.CustomThreadFactory;
import stroom.util.thread.StroomThreadGroup;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;

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

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ScheduledTaskExecutor.class);

    private static final String STROOM_JOB_THREAD_POOL = "Stroom Job#";

    private final ConcurrentHashMap<ScheduledJob, AtomicBoolean> runningMapOfScheduledJobs =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ScheduledJob, SimpleScheduleExec> schedulerMapOfScheduledJobs =
            new ConcurrentHashMap<>();

    private final Map<ScheduledJob, Provider<Runnable>> scheduledJobsMap;
    private final JobNodeTrackerCache jobNodeTrackerCache;
    private final Executor executor;
    private final TaskContextFactory taskContextFactory;
    private final SecurityContext securityContext;
    private final ClusterNodeManager clusterNodeManager;
    private final NodeInfo nodeInfo;

    private final AtomicReference<ScheduledExecutorService> scheduledExecutorService = new AtomicReference<>();
    private final boolean enabled;
    private final long executionInterval;

    @Inject
    ScheduledTaskExecutor(final Map<ScheduledJob, Provider<Runnable>> scheduledJobsMap,
                          final JobNodeTrackerCache jobNodeTrackerCache,
                          final Executor executor,
                          final TaskContextFactory taskContextFactory,
                          final JobSystemConfig jobSystemConfig,
                          final SecurityContext securityContext,
                          final ClusterNodeManager clusterNodeManager,
                          final NodeInfo nodeInfo) {
        this.scheduledJobsMap = scheduledJobsMap;
        this.jobNodeTrackerCache = jobNodeTrackerCache;
        this.executor = executor;
        this.taskContextFactory = taskContextFactory;
        this.securityContext = securityContext;
        this.enabled = jobSystemConfig.isEnabled();
        this.executionInterval = jobSystemConfig.getExecutionIntervalMs();
        this.clusterNodeManager = clusterNodeManager;
        this.nodeInfo = nodeInfo;
    }

    void startup() {
        if (enabled) {
            LOGGER.info("Starting Stroom Job service");

            // Create the thread pool that we will use to startup, shutdown and execute lifecycle beans asynchronously.
            final CustomThreadFactory threadFactory = new CustomThreadFactory(STROOM_JOB_THREAD_POOL,
                    StroomThreadGroup.instance(), Thread.MIN_PRIORITY + 1);

            // Create the executor service that will execute scheduled services.
            final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(
                    1, threadFactory);

            // Create the runnable object that will perform execution on all scheduled services.
            final Runnable runnable = buildExecutionRunnable();

            scheduledExecutorService.scheduleWithFixedDelay(
                    runnable, 0, executionInterval, TimeUnit.MILLISECONDS);
            this.scheduledExecutorService.set(scheduledExecutorService);
        }
    }

    private Runnable buildExecutionRunnable() {
        final ReentrantLock lock = new ReentrantLock();
        return () -> {
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
    }

    private boolean isThisNodeEnabled() {
        final String thisNodeName = nodeInfo.getThisNodeName();
        return clusterNodeManager.getClusterState().isEnabled(thisNodeName);
    }

    void shutdown() {
        if (enabled) {
            LOGGER.info("Stopping Stroom Job service");
            final ScheduledExecutorService scheduledExecutorService = this.scheduledExecutorService.get();
            if (scheduledExecutorService != null) {
                // Stop the scheduled executor.
                scheduledExecutorService.shutdown();
                try {
                    final boolean didTerminate = scheduledExecutorService.awaitTermination(
                            1, TimeUnit.MINUTES);
                    if (!didTerminate) {
                        LOGGER.warn("Timed out waiting for executor service to terminate");
                    }
                } catch (final InterruptedException e) {
                    LOGGER.error("Waiting termination interrupted!", e);

                    // Continue to interrupt this thread.
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private void execute() {
        int managedTaskCount = 0;
        int unManagedTaskCount = 0;
        final boolean isThisNodeEnabled = isThisNodeEnabled();
        for (final ScheduledJob scheduledJob : scheduledJobsMap.keySet()) {
            LOGGER.trace(() -> LogUtil.message(
                    "execute() - name: '{}', managed: {}, isThisNodeEnabled: {}, enabled: {}, schedule: {}",
                    scheduledJob.getName(), scheduledJob.isManaged(), isThisNodeEnabled,
                    scheduledJob.isEnabled(), scheduledJob.getSchedule()));

            // Managed jobs don't run on disabled nodes, but un-managed do as they are things
            // like lock keep-alive and meta flush which still need to happen
            if (isThisNodeEnabled || !scheduledJob.isManaged()) {
                final String taskName = scheduledJob.getName();
                try {
                    final ScheduledJobFunction function = create(scheduledJob);
                    if (function != null) {
                        if (scheduledJob.isManaged()) {
                            managedTaskCount++;
                        } else {
                            unManagedTaskCount++;
                        }
                        final Runnable runnable = taskContextFactory.context(taskName, taskContext -> {
                            try {
                                // Run the task
                                LOGGER.logDurationIfDebugEnabled(
                                        function, () -> scheduledJobToStr(scheduledJob));
                            } catch (final RuntimeException e) {
                                LOGGER.error("Error executing task '{}' - {}",
                                        taskName, LogUtil.exceptionMessage(e), e);
                            }
                        });

                        CompletableFuture
                                .runAsync(runnable, executor)
                                .whenComplete((r, t) ->
                                        function.getRunning().set(false));
                    } else {
                        LOGGER.trace(() -> LogUtil.message(
                                "execute() - Not executing {}", scheduledJobToStr(scheduledJob)));
                    }
                } catch (final RuntimeException e) {
                    LOGGER.error("Error executing {} - {}. Enable DEBUG for stack trace.",
                            scheduledJobToStr(scheduledJob), LogUtil.exceptionMessage(e));
                    LOGGER.debug(() -> LogUtil.message(
                                    "Error executing {} - {}",
                                    scheduledJobToStr(scheduledJob),
                                    LogUtil.exceptionMessage(e)),
                            e);
                }
            } else {
                LOGGER.debug(() -> LogUtil.message("Ignoring [{}] as this node '{}' is disabled",
                        scheduledJobToStr(scheduledJob), nodeInfo.getThisNodeName()));
            }
        }
        if (LOGGER.isDebugEnabled()
            && (unManagedTaskCount > 0 || managedTaskCount > 0)) {
            LOGGER.debug("Initiated {} un-managed and {} managed tasks asynchronously",
                    unManagedTaskCount, managedTaskCount);
        }
    }

    private String scheduledJobToStr(final ScheduledJob scheduledJob) {
        if (scheduledJob != null) {
            final String type = scheduledJob.isManaged()
                    ? "Managed   "
                    : "Un-managed";
            final ScheduleType scheduleType = NullSafe.get(scheduledJob.getSchedule(), Schedule::getType);
            final String expression = NullSafe.get(scheduledJob, ScheduledJob::getSchedule, Schedule::getExpression);
            return LogUtil.message("{} task '{}' with {} schedule '{}'",
                    type,
                    scheduledJob.getName(),
                    scheduleType.getDisplayValue().toLowerCase(),
                    expression);
        } else {
            return "";
        }
    }

    private ScheduledJobFunction create(final ScheduledJob scheduledJob) {
        ScheduledJobFunction function = null;

        final AtomicBoolean running = getRunningState(scheduledJob);

        // Only run one instance of this method at a time.
        if (running.compareAndSet(false, true)) {
            try {
                boolean isJobEnabledOnNode = true;
                SimpleScheduleExec scheduler = null;
                final JobNodeTracker jobNodeTracker;

                final JobNodeTrackers trackers = jobNodeTrackerCache.getTrackers();
                jobNodeTracker = trackers.getTrackerForJobName(scheduledJob.getName());

                if (scheduledJob.isManaged()) {
                    isJobEnabledOnNode = false;
                    if (jobNodeTracker == null) {
                        LOGGER.error("No job node tracker found for: " + scheduledJob.getName());
                    } else {
                        final JobNode jobNode = jobNodeTracker.getJobNode();
                        if (jobNode == null) {
                            LOGGER.error("Job node tracker has null job node for: " + scheduledJob.getName());
                        } else {
                            isJobEnabledOnNode = jobNode.getJob().isEnabled()
                                                 && jobNode.isEnabled();
                            scheduler = trackers.getScheduleExec(jobNode);
                        }
                    }
                } else {
                    scheduler = getOrCreateScheduler(scheduledJob);
                }

                if (scheduler != null
                    && (isJobEnabledOnNode || scheduler.isRunIfDisabled())
                    && scheduler.execute()) {
//                    LOGGER.trace("Returning runnable for method: {} - {} - {}", methodReference, enabled, scheduler);
                    final Provider<Runnable> consumerProvider = scheduledJobsMap.get(scheduledJob);
                    if (jobNodeTracker != null) {
                        function = new JobNodeTrackedFunction(scheduledJob, consumerProvider.get(), running,
                                jobNodeTracker);
                    } else {
                        function = new ScheduledJobFunction(scheduledJob, consumerProvider.get(), running);
                    }
                } else {
                    LOGGER.trace("Not returning runnable for method: {} - {} - {}",
                            scheduledJob.getName(),
                            isJobEnabledOnNode,
                            scheduler);
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

    private SimpleScheduleExec getOrCreateScheduler(final ScheduledJob scheduledJob) {
        SimpleScheduleExec scheduler = schedulerMapOfScheduledJobs.get(scheduledJob);
        if (scheduler == null) {
            scheduler = new SimpleScheduleExec(TriggerFactory.create(scheduledJob.getSchedule()));
            schedulerMapOfScheduledJobs.put(scheduledJob, scheduler);
        }

        return scheduler;
    }

    private AtomicBoolean getRunningState(final ScheduledJob scheduledJob) {
        return runningMapOfScheduledJobs.computeIfAbsent(scheduledJob, k -> new AtomicBoolean(false));
    }


    // --------------------------------------------------------------------------------


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
            } catch (final TaskTerminatedException | UncheckedInterruptedException e) {
                LOGGER.debug(e.getMessage(), e);
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
