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
import stroom.lifecycle.StroomBeanFunction;
import stroom.lifecycle.StroomBeanStore;
import stroom.jobsystem.JobNodeTrackerCache.Trackers;
import stroom.jobsystem.shared.JobNode;
import stroom.lifecycle.LifecycleTask;
import stroom.lifecycle.StroomBeanMethodExecutable;
import stroom.task.api.TaskManager;
import stroom.util.lifecycle.JobTrackedSchedule;
import stroom.util.lifecycle.MethodReference;
import stroom.util.lifecycle.StroomFrequencySchedule;
import stroom.util.lifecycle.StroomSimpleCronSchedule;
import stroom.util.lifecycle.jobmanagement.ScheduledJob;
import stroom.util.lifecycle.jobmanagement.ScheduledJobs;
import stroom.util.scheduler.FrequencyScheduler;
import stroom.util.scheduler.Scheduler;
import stroom.util.scheduler.SimpleCron;
import stroom.task.shared.Task;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
public class ScheduledTaskExecutorImpl implements ScheduledTaskExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScheduledTaskExecutorImpl.class);
    //TODO: remove and rename below gh-1063
    private final ConcurrentHashMap<MethodReference, AtomicBoolean> runningMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ScheduledJob, AtomicBoolean> runningMapOfScheduledJobs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<MethodReference, Scheduler> schedulerMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ScheduledJob, Scheduler> schedulerMapOfScheduledJobs = new ConcurrentHashMap<>();

    private final StroomBeanStore stroomBeanStore;
    private final JobNodeTrackerCache jobNodeTrackerCache;
    private final TaskManager taskManager;

    //TODO remove this methods stuff gh-1063
    private volatile Set<MethodReference> scheduledMethods;
    private volatile Set<ScheduledJob> scheduledJobs;

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
        if (scheduledJobs == null) {
            synchronized (this) {
                if(scheduledJobs == null) {
                    final Set<ScheduledJob> set = new HashSet<>();
                    for(final ScheduledJobs scheduledJobs : stroomBeanStore.getInstancesOfType(ScheduledJobs.class)) {
                        set.addAll(scheduledJobs.getJobs());
                    }
                    scheduledJobs = set;
                }
            }
        }

        for (final ScheduledJob scheduledJob : scheduledJobs) {
            try {
                final StroomBeanFunction function = create(scheduledJob);
                if (function != null) {
                    taskManager.execAsync(new LifecycleTask(function));
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }

// TODO: clean up gh-1063
        if (scheduledMethods == null) {
            synchronized (this) {
                if (scheduledMethods == null) {
                    final Set<MethodReference> set = new HashSet<>();
                    try {
                        // Find all methods that are annotated with a cron or
                        // frequency schedule.
                        set.addAll(stroomBeanStore.getAnnotatedMethods(StroomSimpleCronSchedule.class));
                        set.addAll(stroomBeanStore.getAnnotatedMethods(StroomFrequencySchedule.class));
                    } catch (final RuntimeException e) {
                        LOGGER.error(e.getMessage(), e);
                    }
                    scheduledMethods = set;
                }
            }
        }

        for (final MethodReference methodReference : scheduledMethods) {
            try {
                final StroomBeanMethodExecutable executable = create(methodReference);
                if (executable != null) {
                    taskManager.execAsync(new LifecycleTask(executable));
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
    }

    private StroomBeanFunction create(final ScheduledJob scheduledJob) {
        StroomBeanFunction function = null;

        final AtomicBoolean running = getRunningState(scheduledJob);

        // Only run one instance of this method at a time.
        if (running.compareAndSet(false, true)) {
            try {
                boolean enabled = true;
                Scheduler scheduler = null;
                JobNodeTracker jobNodeTracker = null;

                final Trackers trackers = jobNodeTrackerCache.getTrackers();
               jobNodeTracker = trackers.getTrackerForJobName(scheduledJob.getName());

               if(scheduledJob.isManaged()) {
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
               } else{
                   scheduler = getOrCreateScheduler(scheduledJob);
               }

                if (enabled && scheduler != null && scheduler.execute()) {
                    //TODO log trace
//                    LOGGER.trace("Returning runnable for method: {} - {} - {}", methodReference, enabled, scheduler);
                    if (jobNodeTracker != null) {
                        function = new JobNodeTrackedFunction(scheduledJob, stroomBeanStore, "Executing", running,
                                jobNodeTracker);
                    } else {
                        function = new StroomBeanFunction(scheduledJob, stroomBeanStore, "Executing", running);
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

// TODO: clean up gh-1063
    private StroomBeanMethodExecutable create(final MethodReference methodReference) {
        StroomBeanMethodExecutable executable = null;

        final AtomicBoolean running = getRunningState(methodReference);

        // Only run one instance of this method at a time.
        if (running.compareAndSet(false, true)) {
            try {
                boolean enabled = true;
                Scheduler scheduler = null;
                JobNodeTracker jobNodeTracker = null;

                final JobTrackedSchedule jobTrackedSchedule = methodReference.getMethod()
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
                    scheduler = getOrCreateScheduler(methodReference);
                }

                if (enabled && scheduler != null && scheduler.execute()) {
                    LOGGER.trace("Returning runnable for method: {} - {} - {}", methodReference, enabled, scheduler);
                    if (jobNodeTracker != null) {
                        executable = new JobNodeTrackedExecutable(methodReference, stroomBeanStore, "Executing", running,
                                jobNodeTracker);
                    } else {
                        executable = new StroomBeanMethodExecutable(methodReference, stroomBeanStore, "Executing", running);
                    }
                } else {
                    LOGGER.trace("Not returning runnable for method: {} - {} - {}", methodReference, enabled, scheduler);
                    running.set(false);
                }
            } catch (final RuntimeException e) {
                LOGGER.error(MarkerFactory.getMarker("FATAL"), e.getMessage());
            }
        } else {
            LOGGER.trace("Skipping as method still running: {}", methodReference);
        }

        return executable;
    }


    private Scheduler getOrCreateScheduler(final ScheduledJob scheduledJob) {
        Scheduler scheduler = schedulerMapOfScheduledJobs.get(scheduledJob);
        if (scheduler == null) {
            scheduler = new InvalidScheduler();

            switch(scheduledJob.getSchedule().getScheduleType()) {
                case CRON:
                    final SimpleCron simpleCron = SimpleCron.compile(scheduledJob.getSchedule().getSchedule());
                    scheduler = simpleCron.createScheduler();
                    break;

                case PERIODIC:
                    scheduler = new FrequencyScheduler(scheduledJob.getSchedule().getSchedule());
                    break;
                default: throw new RuntimeException("Unsupported ScheduleType!");
            }
            schedulerMapOfScheduledJobs.put(scheduledJob, scheduler);
        }

        if (scheduler != null && scheduler instanceof InvalidScheduler) {
            scheduler = null;
        }

        return scheduler;
    }

    // TODO: clean up gh-1063
    private Scheduler getOrCreateScheduler(final MethodReference methodReference) {
        Scheduler scheduler = schedulerMap.get(methodReference);
        if (scheduler == null) {
            scheduler = new InvalidScheduler();

            try {
                final StroomSimpleCronSchedule stroomSimpleCronSchedule = methodReference.getMethod()
                        .getAnnotation(StroomSimpleCronSchedule.class);
                if (stroomSimpleCronSchedule != null) {
                    final SimpleCron simpleCron = SimpleCron.compile(stroomSimpleCronSchedule.cron());
                    scheduler = simpleCron.createScheduler();

                } else {
                    final StroomFrequencySchedule stroomFrequencySchedule = methodReference.getMethod()
                            .getAnnotation(StroomFrequencySchedule.class);
                    scheduler = new FrequencyScheduler(stroomFrequencySchedule.value());
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }

            schedulerMap.put(methodReference, scheduler);
        }

        if (scheduler != null && scheduler instanceof InvalidScheduler) {
            scheduler = null;
        }

        return scheduler;
    }

    private AtomicBoolean getRunningState(final ScheduledJob scheduledJob) {
        AtomicBoolean running = runningMapOfScheduledJobs.get(scheduledJob);
        if (running == null) {
            runningMapOfScheduledJobs.putIfAbsent(scheduledJob, new AtomicBoolean(false));
            running = runningMapOfScheduledJobs.get(scheduledJob);
        }
        return running;
    }

    // TODO: clean up gh-1063
    private AtomicBoolean getRunningState(final MethodReference methodReference) {
        AtomicBoolean running = runningMap.get(methodReference);
        if (running == null) {
            runningMap.putIfAbsent(methodReference, new AtomicBoolean(false));
            running = runningMap.get(methodReference);
        }
        return running;
    }

// TODO: clean up gh-1063
    private static class JobNodeTrackedExecutable extends StroomBeanMethodExecutable {
        private static final Logger LOGGER = LoggerFactory.getLogger(JobNodeTrackedExecutable.class);

        private final JobNodeTracker jobNodeTracker;

        public JobNodeTrackedExecutable(final MethodReference methodReference, final StroomBeanStore stroomBeanStore,
                                        final String message, final AtomicBoolean running, final JobNodeTracker jobNodeTracker) {
            super(methodReference, stroomBeanStore, message, running);
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
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }

        @Override
        public String toString() {
            return jobNodeTracker.getJobNode().getJob().getName();
        }
    }

    private static class JobNodeTrackedFunction extends StroomBeanFunction {
        private static final Logger LOGGER = LoggerFactory.getLogger(JobNodeTrackedExecutable.class);

        private final JobNodeTracker jobNodeTracker;

        public JobNodeTrackedFunction(final ScheduledJob scheduledJob, final StroomBeanStore stroomBeanStore,
                                      final String message, final AtomicBoolean running, final JobNodeTracker jobNodeTracker) {
            super(scheduledJob, stroomBeanStore, message, running);
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
