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
import stroom.lifecycle.StroomBeanFunction;
import stroom.task.api.TaskManager;
import stroom.task.api.job.ScheduledJob;
import stroom.task.api.job.ScheduledJobs;
import stroom.task.shared.Task;
import stroom.util.scheduler.FrequencyScheduler;
import stroom.util.scheduler.Scheduler;
import stroom.util.scheduler.SimpleCron;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
public class ScheduledTaskExecutorImpl implements ScheduledTaskExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScheduledTaskExecutorImpl.class);
    private final ConcurrentHashMap<ScheduledJob, AtomicBoolean> runningMapOfScheduledJobs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ScheduledJob, Scheduler> schedulerMapOfScheduledJobs = new ConcurrentHashMap<>();

    private final Set<ScheduledJobs> scheduledJobsSet;
    private final JobNodeTrackerCache jobNodeTrackerCache;
    private final TaskManager taskManager;

    private volatile Set<ScheduledJob> scheduledJobs;

    @Inject
    ScheduledTaskExecutorImpl(final Set<ScheduledJobs> scheduledJobsSet,
                              final JobNodeTrackerCache jobNodeTrackerCache,
                              final TaskManager taskManager) {
        this.scheduledJobsSet = scheduledJobsSet;
        this.jobNodeTrackerCache = jobNodeTrackerCache;
        this.taskManager = taskManager;
    }

    @Override
    public void execute() {
        if (scheduledJobs == null) {
            synchronized (this) {
                if(scheduledJobs == null) {
                    final Set<ScheduledJob> set = new HashSet<>();
                    for (final ScheduledJobs scheduledJobs : scheduledJobsSet) {
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
    }

    private StroomBeanFunction create(final ScheduledJob scheduledJob) {
        StroomBeanFunction function = null;

        final AtomicBoolean running = getRunningState(scheduledJob);

        // Only run one instance of this method at a time.
        if (running.compareAndSet(false, true)) {
            try {
                boolean enabled = true;
                Scheduler scheduler = null;
                JobNodeTracker jobNodeTracker;

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
                        function = new JobNodeTrackedFunction(scheduledJob, running,
                                jobNodeTracker);
                    } else {
                        function = new StroomBeanFunction(scheduledJob, running);
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

    private static class JobNodeTrackedFunction extends StroomBeanFunction {
        private static final Logger LOGGER = LoggerFactory.getLogger(JobNodeTrackedFunction.class);

        private final JobNodeTracker jobNodeTracker;

        JobNodeTrackedFunction(final ScheduledJob scheduledJob,
                               final AtomicBoolean running,
                               final JobNodeTracker jobNodeTracker) {
            super(scheduledJob, running);
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
