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

package stroom.jobsystem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;
import stroom.jobsystem.JobNodeTrackerCache.Trackers;
import stroom.jobsystem.shared.JobNode;
import stroom.lifecycle.LifecycleTask;
import stroom.task.TaskManager;
import stroom.util.scheduler.FrequencyScheduler;
import stroom.util.scheduler.Scheduler;
import stroom.util.scheduler.SimpleCron;
import stroom.util.shared.Task;
import stroom.util.spring.StroomBeanMethod;
import stroom.util.spring.StroomBeanMethodExecutable;
import stroom.util.spring.StroomBeanStore;
import stroom.util.spring.StroomFrequencySchedule;
import stroom.util.spring.StroomSimpleCronSchedule;

import javax.inject.Inject;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScheduledTaskExecutorImpl implements ScheduledTaskExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScheduledTaskExecutorImpl.class);
    private final ConcurrentHashMap<StroomBeanMethod, AtomicBoolean> runningMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<StroomBeanMethod, Scheduler> schedulerMap = new ConcurrentHashMap<>();

    private final StroomBeanStore stroomBeanStore;
    private final JobNodeTrackerCache jobNodeTrackerCache;
    private final TaskManager taskManager;

    private volatile Set<StroomBeanMethod> scheduledMethods;

    @Inject
    ScheduledTaskExecutorImpl(final StroomBeanStore stroomBeanStore,
                              final JobNodeTrackerCache jobNodeTrackerCache,
                              final TaskManager taskManager) {
        this.stroomBeanStore = stroomBeanStore;
        this.jobNodeTrackerCache = jobNodeTrackerCache;
        this.taskManager = taskManager;
    }

    @Override
    public void execute() {
        if (scheduledMethods == null) {
            synchronized (this) {
                if (scheduledMethods == null) {
                    final Set<StroomBeanMethod> set = new HashSet<>();
                    try {
                        // Find all methods that are annotated with a cron or
                        // frequency schedule.
                        for (final StroomBeanMethod stroomBeanMethod : stroomBeanStore
                                .getAnnotatedStroomBeanMethods(StroomSimpleCronSchedule.class)) {
                            set.add(stroomBeanMethod);
                        }
                        for (final StroomBeanMethod stroomBeanMethod : stroomBeanStore
                                .getAnnotatedStroomBeanMethods(StroomFrequencySchedule.class)) {
                            set.add(stroomBeanMethod);
                        }
                    } catch (final Throwable t) {
                        LOGGER.error(t.getMessage(), t);
                    }
                    scheduledMethods = set;
                }
            }
        }

        try {
            for (final StroomBeanMethod stroomBeanMethod : scheduledMethods) {
                try {
                    final StroomBeanMethodExecutable executable = create(stroomBeanMethod);
                    if (executable != null) {
                        taskManager.execAsync(new LifecycleTask(executable));
                    }
                } catch (final Throwable t) {
                    LOGGER.error(t.getMessage(), t);
                }
            }
        } catch (final Throwable t) {
            LOGGER.error(t.getMessage(), t);
        }
    }

    private StroomBeanMethodExecutable create(final StroomBeanMethod stroomBeanMethod) {
        StroomBeanMethodExecutable executable = null;

        final AtomicBoolean running = getRunningState(stroomBeanMethod);

        // Only run one instance of this method at a time.
        if (running.compareAndSet(false, true)) {
            try {
                boolean enabled = true;
                Scheduler scheduler = null;
                JobNodeTracker jobNodeTracker = null;

                final JobTrackedSchedule jobTrackedSchedule = stroomBeanMethod.getBeanMethod()
                        .getAnnotation(JobTrackedSchedule.class);
                if (jobTrackedSchedule != null) {
                    enabled = false;

                    final String jobName = jobTrackedSchedule.jobName();
                    final Trackers trackers = jobNodeTrackerCache.getTrackers();
                    jobNodeTracker = trackers.getTrackerForJobName(jobName);
                    if (jobNodeTracker == null) {
                        LOGGER.error("No job node tracker found for: " + jobName);
                    } else {
                        final JobNode jobNode = jobNodeTracker.getJobNode();
                        if (jobNode == null) {
                            LOGGER.error("Job node tracker has null job node for: " + jobName);
                        } else {
                            enabled = jobNode.isEnabled() && jobNode.getJob().isEnabled();
                            scheduler = trackers.getScheduler(jobNode);
                        }
                    }

                } else {
                    scheduler = getOrCreateScheduler(stroomBeanMethod);
                }

                if (enabled && scheduler != null && scheduler.execute()) {
                    LOGGER.trace("Returning runnable for method: {} - {} - {}", new Object[]{stroomBeanMethod, enabled, scheduler});
                    if (jobNodeTracker != null) {
                        executable = new JobNodeTrackedExecutable(stroomBeanMethod, stroomBeanStore, "Executing", running,
                                jobNodeTracker);
                    } else {
                        executable = new StroomBeanMethodExecutable(stroomBeanMethod, stroomBeanStore, "Executing", running);
                    }
                } else {
                    LOGGER.trace("Not returning runnable for method: {} - {} - {}", new Object[]{stroomBeanMethod, enabled, scheduler});
                    running.set(false);
                }
            } catch (final Throwable t) {
                LOGGER.error(MarkerFactory.getMarker("FATAL"), t.getMessage());
            }
        } else {
            LOGGER.trace("Skipping as method still running: {}", stroomBeanMethod);
        }

        return executable;
    }

    private Scheduler getOrCreateScheduler(final StroomBeanMethod stroomBeanMethod) {
        Scheduler scheduler = schedulerMap.get(stroomBeanMethod);
        if (scheduler == null) {
            scheduler = new InvalidScheduler();

            try {
                final StroomSimpleCronSchedule stroomSimpleCronSchedule = stroomBeanMethod.getBeanMethod()
                        .getAnnotation(StroomSimpleCronSchedule.class);
                if (stroomSimpleCronSchedule != null) {
                    final SimpleCron simpleCron = SimpleCron.compile(stroomSimpleCronSchedule.cron());
                    scheduler = simpleCron.createScheduler();

                } else {
                    final StroomFrequencySchedule stroomFrequencySchedule = stroomBeanMethod.getBeanMethod()
                            .getAnnotation(StroomFrequencySchedule.class);
                    scheduler = new FrequencyScheduler(stroomFrequencySchedule.value());
                }
            } catch (final Throwable t) {
                LOGGER.error(t.getMessage(), t);
            }

            schedulerMap.put(stroomBeanMethod, scheduler);
        }

        if (scheduler != null && scheduler instanceof InvalidScheduler) {
            scheduler = null;
        }

        return scheduler;
    }

    private AtomicBoolean getRunningState(final StroomBeanMethod stroomBeanMethod) {
        AtomicBoolean running = runningMap.get(stroomBeanMethod);
        if (running == null) {
            runningMap.putIfAbsent(stroomBeanMethod, new AtomicBoolean(false));
            running = runningMap.get(stroomBeanMethod);
        }
        return running;
    }

    private static class JobNodeTrackedExecutable extends StroomBeanMethodExecutable {
        private static final Logger LOGGER = LoggerFactory.getLogger(JobNodeTrackedExecutable.class);

        private final JobNodeTracker jobNodeTracker;

        public JobNodeTrackedExecutable(final StroomBeanMethod stroomBeanMethod, final StroomBeanStore stroomBeanStore,
                                        final String message, final AtomicBoolean running, final JobNodeTracker jobNodeTracker) {
            super(stroomBeanMethod, stroomBeanStore, message, running);
            this.jobNodeTracker = jobNodeTracker;
        }

        @Override
        public void exec(final Task<?> task) {
            try {
                jobNodeTracker.incrementTaskCount();
                try {
                    jobNodeTracker.setLastExecutedTime(System.currentTimeMillis());
                    try {
                        super.exec(task);
                    } finally {
                        jobNodeTracker.setLastExecutedTime(System.currentTimeMillis());
                    }
                } finally {
                    jobNodeTracker.decrementTaskCount();
                }
            } catch (final Throwable t) {
                LOGGER.error(t.getMessage(), t);
            }
        }

        @Override
        public String toString() {
            return jobNodeTracker.getJobNode().getJob().getName();
        }
    }
}
