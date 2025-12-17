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

import stroom.job.shared.FindJobNodeCriteria;
import stroom.job.shared.JobNode;
import stroom.job.shared.JobNodeUtil;
import stroom.node.api.NodeInfo;
import stroom.task.api.ExecutorProvider;
import stroom.util.concurrent.AsyncReference;
import stroom.util.scheduler.SimpleScheduleExec;
import stroom.util.scheduler.Trigger;
import stroom.util.scheduler.TriggerFactory;
import stroom.util.shared.scheduler.Schedule;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
class JobNodeTrackerCacheImpl implements JobNodeTrackerCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(JobNodeTrackerCacheImpl.class);

    private final AsyncReference<JobNodeTrackersImpl> asyncReference;


    @Inject
    JobNodeTrackerCacheImpl(final NodeInfo nodeInfo,
                            final JobNodeDao jobNodeDao,
                            final ExecutorProvider executorProvider) {
        // Default refresh interval is 10 seconds.
        asyncReference = new AsyncReference<>(
                trackers -> new JobNodeTrackersImpl(trackers, jobNodeDao, nodeInfo.getThisNodeName()),
                Duration.ofSeconds(10),
                executorProvider.get());
    }

    @Override
    public JobNodeTrackers getTrackers() {
        return asyncReference.get();
    }


    // --------------------------------------------------------------------------------


    static class JobNodeTrackersImpl implements JobNodeTrackers {

        private final Map<JobNode, JobNodeTracker> trackersForJobNode = new HashMap<>();
        private final Map<String, JobNodeTracker> trackersForJobName = new HashMap<>();
        private final Map<JobNode, String> scheduleValueMap = new HashMap<>();
        private final Map<JobNode, SimpleScheduleExec> schedulerMap = new ConcurrentHashMap<>();
        private final List<JobNodeTracker> distributedJobNodeTrackers = new ArrayList<>();

        JobNodeTrackersImpl(final JobNodeTrackersImpl previousState,
                            final JobNodeDao jobNodeDao,
                            final String nodeName) {
            try {
                // Get the latest job nodes.
                LOGGER.trace("Refreshing trackers");
                final FindJobNodeCriteria findJobNodeCriteria = new FindJobNodeCriteria();
                findJobNodeCriteria.getNodeName().setString(nodeName);

                final List<JobNode> list = jobNodeDao.find(findJobNodeCriteria).getValues();
                for (final JobNode jobNode : list) {
                    // Get the job name.
                    final String jobName = jobNode.getJob().getName();

                    JobNodeTracker jobNodeTracker = null;
                    if (previousState != null) {
                        jobNodeTracker = previousState.trackersForJobNode.get(jobNode);
                    }
                    if (jobNodeTracker == null) {
                        jobNodeTracker = new JobNodeTracker(jobNode);
                    } else {
                        // Update tracker, so it has new settings and priorities
                        // etc.
                        jobNodeTracker.setJobNode(jobNode);
                    }
                    trackersForJobNode.put(jobNode, jobNodeTracker);
                    trackersForJobName.put(jobName, jobNodeTracker);

                    switch (jobNode.getJobType()) {
                        // Remember trackers for enabled and distributed jobs.
                        case DISTRIBUTED -> {
                            if (jobNode.getJob().isEnabled() && jobNode.isEnabled()) {
                                distributedJobNodeTrackers.add(jobNodeTracker);
                            }
                        }
                        case CRON, FREQUENCY -> {
                            try {
                                // Update schedule and frequency times if this job node
                                // has a job type of cron or frequency.
                                final String expression = jobNode.getSchedule();
                                // Update the schedule cache if the schedule has
                                // changed.
                                if (expression != null) {
                                    scheduleValueMap.put(jobNode, expression);
                                    if (previousState != null
                                            && expression.equals(previousState.scheduleValueMap.get(jobNode))) {
                                        schedulerMap.put(jobNode, previousState.schedulerMap.get(jobNode));
                                    } else {
                                        try {
                                            final Schedule schedule = JobNodeUtil.getSchedule(jobNode);
                                            if (schedule != null) {
                                                schedulerMap.put(jobNode, new SimpleScheduleExec(
                                                        TriggerFactory.create(schedule)));
                                            }
                                        } catch (final RuntimeException e) {
                                            LOGGER.error("Problem updating schedule for '" + jobName + "' job : "
                                                    + e.getMessage(), e);
                                        }
                                    }
                                }
                            } catch (final RuntimeException e) {
                                LOGGER.error("Problem updating schedule for '" + jobName + "' job : " + e.getMessage(),
                                        e);
                            }
                        }
                    }
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }

        @Override
        public JobNodeTracker getTrackerForJobName(final String jobName) {
            return trackersForJobName.get(jobName);
        }

        @Override
        public List<JobNodeTracker> getDistributedJobNodeTrackers() {
            return distributedJobNodeTrackers;
        }

        @Override
        public SimpleScheduleExec getScheduleExec(final JobNode jobNode) {
            return schedulerMap.get(jobNode);
        }

        @Override
        public void triggerImmediateExecution(final JobNode jobNode) {
            if (jobNode != null) {
                switch (jobNode.getJobType()) {
                    case CRON, FREQUENCY -> schedulerMap.compute(
                            jobNode,
                            (jobNode2, curSimpleScheduleExec) -> {
                                if (curSimpleScheduleExec == null) {
                                    final Schedule schedule = JobNodeUtil.getSchedule(jobNode);
                                    final Trigger trigger = TriggerFactory.create(schedule);
                                    return SimpleScheduleExec.createForImmediateExecution(trigger);
                                } else {
                                    return curSimpleScheduleExec.cloneForImmediateExecution();
                                }
                            });
                }
            }
        }
    }
}
