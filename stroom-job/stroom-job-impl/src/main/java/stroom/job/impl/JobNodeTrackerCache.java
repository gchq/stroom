/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.job.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.job.shared.FindJobNodeCriteria;
import stroom.job.shared.JobNode;
import stroom.job.shared.JobNode.JobType;
import stroom.node.api.NodeInfo;
import stroom.util.scheduler.FrequencyScheduler;
import stroom.util.scheduler.Scheduler;
import stroom.util.scheduler.SimpleCron;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

@Singleton
class JobNodeTrackerCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobNodeTrackerCache.class);
    // Default refresh interval is 10 seconds.
    private static final long DEFAULT_REFRESH_INTERVAL = 10000;
    private final long refreshInterval = DEFAULT_REFRESH_INTERVAL;
    private final ReentrantLock refreshLock = new ReentrantLock();

    private final NodeInfo nodeInfo;
    private final JobNodeDao jobNodeDao;

    private volatile String nodeName;
    private volatile Trackers trackers;
    private volatile long lastRefreshMs;

    @Inject
    JobNodeTrackerCache(final NodeInfo nodeInfo,
                        final JobNodeDao jobNodeDao) {
        this.nodeInfo = nodeInfo;
        this.jobNodeDao = jobNodeDao;
    }

    Trackers getTrackers() {
        if (trackers == null) {
            // If trackers are currently null then we will lock so that all
            // threads requiring trackers are blocked until the first one in
            // creates them.
            refreshLock.lock();
            try {
                // If trackers have already been created by a thread that got
                // the lock before then don't bother creating them again.
                if (trackers == null) {
                    // Create the initial trackers.
                    trackers = new Trackers(trackers, jobNodeDao, getNodeName());
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            } finally {
                refreshLock.unlock();
            }
        } else {
            // If we have trackers then let one lucky thread see if they need to
            // be
            // refreshed, others will get the old copy in the meantime.
            if (refreshLock.tryLock()) {
                try {
                    // Check to see if trackers need to be refreshed.
                    final long delta = System.currentTimeMillis() - lastRefreshMs;
                    if (delta > refreshInterval) {
                        try {
                            // Refresh the trackers.
                            trackers = new Trackers(trackers, jobNodeDao, getNodeName());
                        } catch (final RuntimeException e) {
                            LOGGER.error(e.getMessage(), e);
                        }

                        lastRefreshMs = System.currentTimeMillis();
                    }
                } catch (final RuntimeException e) {
                    LOGGER.error(e.getMessage(), e);
                } finally {
                    refreshLock.unlock();
                }
            }
        }

        return trackers;
    }

    String getNodeName() {
        if (nodeName == null) {
            nodeName = nodeInfo.getThisNodeName();
        }
        return nodeName;
    }

    static class Trackers {
        private final Map<JobNode, JobNodeTracker> trackersForJobNode = new HashMap<>();
        private final Map<String, JobNodeTracker> trackersForJobName = new HashMap<>();
        private final Map<JobNode, String> scheduleValueMap = new HashMap<>();
        private final Map<JobNode, Scheduler> schedulerMap = new HashMap<>();

        Trackers(final Trackers previousState, final JobNodeDao jobNodeDao, final String nodeName) {
            try {
                // Get the latest job nodes.
                LOGGER.trace("Refreshing trackers");
                final FindJobNodeCriteria findJobNodeCriteria = new FindJobNodeCriteria();
                findJobNodeCriteria.getNodeName().setString(nodeName);

                final List<JobNode> list = jobNodeDao.find(findJobNodeCriteria);

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
                        // Update tracker so it has new settings and priorities
                        // etc.
                        jobNodeTracker.setJobNode(jobNode);
                    }
                    trackersForJobNode.put(jobNode, jobNodeTracker);
                    trackersForJobName.put(jobName, jobNodeTracker);

                    try {
                        // Update schedule and frequency times if this job node
                        // has a job type of cron or frequency.
                        if (JobType.CRON.equals(jobNode.getJobType())
                                || JobType.FREQUENCY.equals(jobNode.getJobType())) {
                            final String schedule = jobNode.getSchedule();
                            // Update the schedule cache if the schedule has
                            // changed.
                            if (schedule != null) {
                                scheduleValueMap.put(jobNode, schedule);
                                if (previousState != null
                                        && schedule.equals(previousState.scheduleValueMap.get(jobNode))) {
                                    schedulerMap.put(jobNode, previousState.schedulerMap.get(jobNode));
                                } else {
                                    try {
                                        if (JobType.CRON.equals(jobNode.getJobType())) {
                                            schedulerMap.put(jobNode,
                                                    SimpleCron.compile(jobNode.getSchedule()).createScheduler());
                                        } else if (JobType.FREQUENCY.equals(jobNode.getJobType())) {
                                            schedulerMap.put(jobNode, new FrequencyScheduler(jobNode.getSchedule()));
                                        }
                                    } catch (final RuntimeException e) {
                                        LOGGER.error("Problem updating schedule for '" + jobName + "' job : "
                                                + e.getMessage(), e);
                                    }
                                }
                            }
                        }
                    } catch (final RuntimeException e) {
                        LOGGER.error("Problem updating schedule for '" + jobName + "' job : " + e.getMessage(), e);
                    }
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }

        JobNodeTracker getTrackerForJobNode(final JobNode jobNode) {
            return trackersForJobNode.get(jobNode);
        }

        JobNodeTracker getTrackerForJobName(final String jobName) {
            return trackersForJobName.get(jobName);
        }

        Collection<JobNodeTracker> getTrackerList() {
            return trackersForJobNode.values();
        }

        Scheduler getScheduler(final JobNode jobNode) {
            return schedulerMap.get(jobNode);
        }
    }
}
