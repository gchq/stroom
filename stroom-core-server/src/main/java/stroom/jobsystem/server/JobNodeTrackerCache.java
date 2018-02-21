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

package stroom.jobsystem.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.jobsystem.shared.FindJobNodeCriteria;
import stroom.jobsystem.shared.Job;
import stroom.jobsystem.shared.JobNode;
import stroom.jobsystem.shared.JobNode.JobType;
import stroom.node.server.NodeCache;
import stroom.node.shared.Node;
import stroom.util.scheduler.FrequencyScheduler;
import stroom.util.scheduler.Scheduler;
import stroom.util.scheduler.SimpleCron;

import javax.inject.Inject;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class JobNodeTrackerCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobNodeTrackerCache.class);
    // Default refresh interval is 10 seconds.
    private static final long DEFAULT_REFRESH_INTERVAL = 10000;
    private final long refreshInterval = DEFAULT_REFRESH_INTERVAL;
    private final ReentrantLock refreshLock = new ReentrantLock();

    private final NodeCache nodeCache;
    private final JobNodeService jobNodeService;

    private volatile Node node;
    private volatile Trackers trackers;
    private volatile long lastRefreshMs;

    @Inject
    JobNodeTrackerCache(final NodeCache nodeCache,
                        final JobNodeService jobNodeService) {
        this.nodeCache = nodeCache;
        this.jobNodeService = jobNodeService;
    }

    public Trackers getTrackers() {
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
                    trackers = new Trackers(trackers, jobNodeService, getNode());
                }
            } catch (final Throwable t) {
                LOGGER.error(t.getMessage(), t);
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
                            trackers = new Trackers(trackers, jobNodeService, getNode());
                        } catch (final Throwable t) {
                            LOGGER.error(t.getMessage(), t);
                        }

                        lastRefreshMs = System.currentTimeMillis();
                    }
                } catch (final Throwable t) {
                    LOGGER.error(t.getMessage(), t);
                } finally {
                    refreshLock.unlock();
                }
            }
        }

        return trackers;
    }

    public Node getNode() {
        if (node == null) {
            node = nodeCache.getDefaultNode();
        }
        return node;
    }

    public static class Trackers {
        private final Map<JobNode, JobNodeTracker> trackersForJobNode = new HashMap<>();
        private final Map<String, JobNodeTracker> trackersForJobName = new HashMap<>();
        private final Map<JobNode, String> scheduleValueMap = new HashMap<>();
        private final Map<JobNode, Scheduler> schedulerMap = new HashMap<>();

        public Trackers(final Trackers previousState, final JobNodeService jobNodeService, final Node node) {
            try {
                // Get the latest job nodes.
                LOGGER.trace("Refreshing trackers");
                final FindJobNodeCriteria findJobNodeCriteria = new FindJobNodeCriteria();
                findJobNodeCriteria.getNodeIdSet().add(node);
                findJobNodeCriteria.getFetchSet().add(Job.ENTITY_TYPE);
                findJobNodeCriteria.getFetchSet().add(Node.ENTITY_TYPE);

                final List<JobNode> list = jobNodeService.find(findJobNodeCriteria);

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
                                    } catch (final Throwable t) {
                                        LOGGER.error("Problem updating schedule for '" + jobName + "' job : "
                                                + t.getMessage(), t);
                                    }
                                }
                            }
                        }
                    } catch (final Exception ex) {
                        LOGGER.error("Problem updating schedule for '" + jobName + "' job : " + ex.getMessage(), ex);
                    }
                }
            } catch (final Throwable t) {
                LOGGER.error(t.getMessage(), t);
            }
        }

        public JobNodeTracker getTrackerForJobNode(final JobNode jobNode) {
            return trackersForJobNode.get(jobNode);
        }

        public JobNodeTracker getTrackerForJobName(final String jobName) {
            return trackersForJobName.get(jobName);
        }

        public Collection<JobNodeTracker> getTrackerList() {
            return trackersForJobNode.values();
        }

        public Scheduler getScheduler(final JobNode jobNode) {
            return schedulerMap.get(jobNode);
        }
    }
}
