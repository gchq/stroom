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
import stroom.job.shared.JobNode;
import stroom.job.shared.JobNode.JobType;
import stroom.job.shared.JobNodeInfo;
import stroom.job.shared.JobNodeListResponse;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.util.AuditUtil;
import stroom.util.scheduler.Scheduler;
import stroom.util.scheduler.SimpleCron;
import stroom.util.shared.ModelStringUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

@Singleton
class JobNodeService {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobNodeService.class);

    private final JobNodeDao jobNodeDao;
    private final JobNodeTrackerCache jobNodeTrackerCache;
    private final SecurityContext securityContext;

    @Inject
    JobNodeService(final JobNodeDao jobNodeDao,
                   final JobNodeTrackerCache jobNodeTrackerCache,
                   final SecurityContext securityContext) {
        this.jobNodeDao = jobNodeDao;
        this.jobNodeTrackerCache = jobNodeTrackerCache;
        this.securityContext = securityContext;
    }

    JobNode update(final JobNode jobNode) {
        // Stop Job Nodes being saved with invalid crons.
        ensureSchedule(jobNode);

        return securityContext.secureResult(PermissionNames.MANAGE_JOBS_PERMISSION, () -> {
            final Optional<JobNode> before = fetch(jobNode.getId());

            // We always want to update a job node instance even if we have a stale version.
            before.ifPresent(j -> jobNode.setVersion(j.getVersion()));

            AuditUtil.stamp(securityContext.getUserId(), jobNode);
            final JobNode after = jobNodeDao.update(jobNode);
            return after;
        });
    }

    JobNodeListResponse find(final FindJobNodeCriteria findJobNodeCriteria) {
        return securityContext.secureResult(
            PermissionNames.MANAGE_JOBS_PERMISSION,
            () -> jobNodeDao.find(findJobNodeCriteria));
    }

    JobNodeInfo getInfo(final String jobName) {
        return securityContext.secureResult(() -> {
            JobNodeInfo result = null;
            final JobNodeTrackerCache.Trackers trackers = jobNodeTrackerCache.getTrackers();
            if (trackers != null) {
                final JobNodeTracker tracker = trackers.getTrackerForJobName(jobName);
                if (tracker != null) {
                    final JobNode jobNode = tracker.getJobNode();
                    final int currentTaskCount = tracker.getCurrentTaskCount();

                    Long scheduleReferenceTime = null;
                    final Scheduler scheduler = trackers.getScheduler(jobNode);
                    if (scheduler != null) {
                        scheduleReferenceTime = scheduler.getScheduleReferenceTime();
                    }
                    result = new JobNodeInfo(currentTaskCount, scheduleReferenceTime, tracker.getLastExecutedTime());
                }
            }

            return result;
        });
    }

//    public stroom.util.shared.SharedMap<JobNode, JobNodeInfo> exec(final JobNodeInfoClusterTask task) {
//        return securityContext.secureResult(() -> {
//            final SharedMap<JobNode, JobNodeInfo> result = new SharedMap<>();
//            final JobNodeTrackerCache.Trackers trackers = jobNodeTrackerCache.getTrackers();
//            if (trackers != null) {
//                final Collection<JobNodeTracker> trackerList = trackers.getTrackerList();
//                if (trackerList != null) {
//                    for (final JobNodeTracker tracker : trackerList) {
//                        final JobNode jobNode = tracker.getJobNode();
//                        final int currentTaskCount = tracker.getCurrentTaskCount();
//
//                        Long scheduleReferenceTime = null;
//                        final Scheduler scheduler = trackers.getScheduler(jobNode);
//                        if (scheduler != null) {
//                            scheduleReferenceTime = scheduler.getScheduleReferenceTime();
//                        }
//
//                        final JobNodeInfo info = new JobNodeInfo(currentTaskCount, scheduleReferenceTime,
//                                tracker.getLastExecutedTime());
//                        result.put(jobNode, info);
//                    }
//                }
//            }
//
//            return result;
//        });
//    }
//
//    BaseResultList<JobNodeRow> findStatus(final FindJobNodeCriteria findJobNodeCriteria) {
//        return securityContext.secureResult(() -> {
//            // Add the root node.
//            final List<JobNodeRow> values = new ArrayList<>();
//
//            if (findJobNodeCriteria == null) {
//                return BaseResultList.createUnboundedList(values);
//            }
//
//            DefaultClusterResultCollector<SharedMap<JobNode, JobNodeInfo>> collector;
//            collector = dispatchHelper.execAsync(new JobNodeInfoClusterTask(), TargetType.ACTIVE);
//
//            final List<JobNode> jobNodes = find(findJobNodeCriteria);
//
//            // Sort job nodes by node name.
//            jobNodes.sort((JobNode o1, JobNode o2) -> o1.getNodeName().compareToIgnoreCase(o2.getNodeName()));
//
//            // Create the JobNodeRow value
//            for (final JobNode jobNode : jobNodes) {
//                JobNodeInfo jobNodeInfo = null;
//
//                final ClusterCallEntry<SharedMap<JobNode, JobNodeInfo>> response = collector.getResponse(jobNode.getNodeName());
//
//                if (response == null) {
//                    LOGGER.debug("No response for: {}", jobNode);
//                } else if (response.getError() != null) {
//                    LOGGER.debug("Error response for: {} - {}", jobNode, response.getError().getMessage());
//                    LOGGER.debug(response.getError().getMessage(), response.getError());
//                } else {
//                    final Map<JobNode, JobNodeInfo> map = response.getResult();
//                    if (map == null) {
//                        LOGGER.warn("No data for: {}", jobNode);
//                    } else {
//                        jobNodeInfo = map.get(jobNode);
//                    }
//                }
//
//                final JobNodeRow jobNodeRow = new JobNodeRow(jobNode, jobNodeInfo);
//                values.add(jobNodeRow);
//            }
//
//            return BaseResultList.createUnboundedList(values);
//        });
//    }
//
//    BaseResultList<JobNode> list(final String jobName, final String nodeName) {
//        return securityContext.secureResult(() -> {
//            // Add the root node.
//            final List<JobNode> values = new ArrayList<>();
//
//            if (findJobNodeCriteria == null) {
//                return BaseResultList.createUnboundedList(values);
//            }
//
//            DefaultClusterResultCollector<SharedMap<JobNode, JobNodeInfo>> collector;
//            collector = dispatchHelper.execAsync(new JobNodeInfoClusterTask(), TargetType.ACTIVE);
//
//            final List<JobNode> jobNodes = find(findJobNodeCriteria);
//
//            // Sort job nodes by node name.
//            jobNodes.sort((JobNode o1, JobNode o2) -> o1.getNodeName().compareToIgnoreCase(o2.getNodeName()));
//
//            // Create the JobNodeRow value
//            for (final JobNode jobNode : jobNodes) {
//                JobNodeInfo jobNodeInfo = null;
//
//                final ClusterCallEntry<SharedMap<JobNode, JobNodeInfo>> response = collector.getResponse(jobNode.getNodeName());
//
//                if (response == null) {
//                    LOGGER.debug("No response for: {}", jobNode);
//                } else if (response.getError() != null) {
//                    LOGGER.debug("Error response for: {} - {}", jobNode, response.getError().getMessage());
//                    LOGGER.debug(response.getError().getMessage(), response.getError());
//                } else {
//                    final Map<JobNode, JobNodeInfo> map = response.getResult();
//                    if (map == null) {
//                        LOGGER.warn("No data for: {}", jobNode);
//                    } else {
//                        jobNodeInfo = map.get(jobNode);
//                    }
//                }
//
//                final JobNodeRow jobNodeRow = new JobNodeRow(jobNode, jobNodeInfo);
//                values.add(jobNodeRow);
//            }
//
//            return BaseResultList.createUnboundedList(values);
//        });
//    }

    private void ensureSchedule(final JobNode jobNode) {
        // Stop Job Nodes being saved with invalid crons.
        if (JobType.CRON.equals(jobNode.getJobType())) {
            if (jobNode.getSchedule() != null) {
                // This will throw a runtime exception if the expression is invalid.
                SimpleCron.compile(jobNode.getSchedule());
            }
        }
        if (JobType.FREQUENCY.equals(jobNode.getJobType())) {
            if (jobNode.getSchedule() != null) {
                // This will throw a runtime exception if the expression is invalid.
                ModelStringUtil.parseDurationString(jobNode.getSchedule());
            }
        }
    }

    Optional<JobNode> fetch(final int id) {
        return jobNodeDao.fetch(id);
    }
}