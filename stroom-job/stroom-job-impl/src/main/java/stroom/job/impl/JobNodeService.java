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

import stroom.job.shared.BatchScheduleRequest;
import stroom.job.shared.FindJobNodeCriteria;
import stroom.job.shared.GetScheduledTimesRequest;
import stroom.job.shared.Job;
import stroom.job.shared.JobNode;
import stroom.job.shared.JobNode.JobType;
import stroom.job.shared.JobNodeInfo;
import stroom.job.shared.JobNodeListResponse;
import stroom.job.shared.ScheduledTimes;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.util.AuditUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.scheduler.CronTrigger;
import stroom.util.scheduler.FrequencyTrigger;
import stroom.util.scheduler.SimpleScheduleExec;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.NullSafe;
import stroom.util.shared.scheduler.Schedule;
import stroom.util.shared.scheduler.ScheduleType;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Singleton
class JobNodeService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(JobNodeService.class);

    private final JobNodeDao jobNodeDao;
    private final JobNodeTrackerCache jobNodeTrackerCache;
    private final SecurityContext securityContext;
    private final ScheduleService scheduleService;
    private final JobService jobService;

    @Inject
    JobNodeService(final JobNodeDao jobNodeDao,
                   final JobNodeTrackerCache jobNodeTrackerCache,
                   final SecurityContext securityContext,
                   final ScheduleService scheduleService,
                   final JobService jobService) {
        this.jobNodeDao = jobNodeDao;
        this.jobNodeTrackerCache = jobNodeTrackerCache;
        this.securityContext = securityContext;
        this.scheduleService = scheduleService;
        this.jobService = jobService;
    }

    JobNode update(final JobNode jobNode) {
        // Stop Job Nodes being saved with invalid crons.
        ensureSchedule(jobNode);

        return securityContext.secureResult(AppPermission.MANAGE_JOBS_PERMISSION, () -> {
            final Optional<JobNode> before = fetch(jobNode.getId());

            // We always want to update a job node instance even if we have a stale version.
            before.ifPresent(j -> jobNode.setVersion(j.getVersion()));

            AuditUtil.stamp(securityContext, jobNode);
            return jobNodeDao.update(jobNode);
        });
    }

    void update(final BatchScheduleRequest batchScheduleRequest) {
        // Stop Job Nodes being saved with invalid crons.
        ensureSchedule(batchScheduleRequest.getJobType(), batchScheduleRequest.getSchedule().getExpression());

        securityContext.secure(AppPermission.MANAGE_JOBS_PERMISSION, () -> {
            jobNodeDao.updateSchedule(batchScheduleRequest);
        });
    }

    JobNodeListResponse find(final FindJobNodeCriteria findJobNodeCriteria) {
        return securityContext.secureResult(
                AppPermission.MANAGE_JOBS_PERMISSION,
                () -> {
                    final JobNodeListResponse jobNodeListResponse = jobNodeDao.find(findJobNodeCriteria);

                    final boolean sortOnAdvanced = NullSafe.stream(findJobNodeCriteria.getSortList())
                            .map(CriteriaFieldSort::getId)
                            .anyMatch(id -> Objects.equals(id, FindJobNodeCriteria.FIELD_ADVANCED));

                    if (sortOnAdvanced) {
                        final List<JobNode> list = jobNodeListResponse.getValues();
                        final List<JobNode> advancedList = new ArrayList<>();
                        final List<JobNode> nonAdvancedList = new ArrayList<>();
                        for (final JobNode jobNode : list) {
                            final Job job = jobNode.getJob();
                            // Add the advanced state
                            jobService.decorate(job);
                            if (job.isAdvanced()) {
                                advancedList.add(jobNode);
                            } else {
                                nonAdvancedList.add(jobNode);
                            }
                        }
                        final List<JobNode> returnList = new ArrayList<>(list.size());
                        returnList.addAll(nonAdvancedList);
                        returnList.addAll(advancedList);
                        return JobNodeListResponse.createUnboundedJobNodeResponse(returnList);
                    } else {
                        return jobNodeListResponse;
                    }
                });
    }

    JobNodeInfo getInfo(final String jobName) {
        return securityContext.secureResult(() -> {
            JobNodeInfo result = null;
            final JobNodeTrackers trackers = jobNodeTrackerCache.getTrackers();
            if (trackers != null) {
                final JobNodeTracker tracker = trackers.getTrackerForJobName(jobName);
                if (tracker != null) {
                    final JobNode jobNode = tracker.getJobNode();
                    final int currentTaskCount = tracker.getCurrentTaskCount();

                    Long scheduleReferenceTime = null;
                    Long nextScheduledTime = null;
                    final SimpleScheduleExec scheduler = trackers.getScheduleExec(jobNode);
                    if (scheduler != null && scheduler.getLastExecutionTime() != null) {
                        scheduleReferenceTime = scheduler.getLastExecutionTime().toEpochMilli();
                        if (jobNode != null && jobNode.isEnabled() && jobNode.getJob().isEnabled()) {
                            final ScheduleType scheduleType = convertJobType(jobNode.getJobType());

                            final ScheduledTimes scheduledTimes = scheduleService.getScheduledTimes(
                                    new GetScheduledTimesRequest(
                                            new Schedule(scheduleType, jobNode.getSchedule()),
                                            scheduleReferenceTime,
                                            null));
                            nextScheduledTime = scheduledTimes.getNextScheduledTimeMs();
                        }
                    }
                    result = new JobNodeInfo(
                            currentTaskCount,
                            scheduleReferenceTime,
                            tracker.getLastExecutedTime(),
                            nextScheduledTime);
                }
            }

            return result;
        });
    }

    private ScheduleType convertJobType(final JobType jobType) {
        return NullSafe.get(
                jobType,
                jobType2 -> switch (jobType2) {
                    case CRON -> ScheduleType.CRON;
                    case FREQUENCY -> ScheduleType.FREQUENCY;
                    default -> null;
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
//                final ClusterCallEntry<SharedMap<JobNode, JobNodeInfo>> response =
//                collector.getResponse(jobNode.getNodeName());
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
//                final ClusterCallEntry<SharedMap<JobNode, JobNodeInfo>> response =
//                collector.getResponse(jobNode.getNodeName());
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
        ensureSchedule(jobNode.getJobType(), jobNode.getSchedule());
    }

    private void ensureSchedule(final JobType jobType, final String scheduleExpression) {
        if (scheduleExpression != null) {
            // Stop Job Nodes being saved with invalid crons.
            switch (jobType) {
                case CRON -> {
                    // This will throw a runtime exception if the expression is invalid.
                    new CronTrigger(scheduleExpression);
                }
                case FREQUENCY -> {
                    // This will throw a runtime exception if the expression is invalid.
                    new FrequencyTrigger(scheduleExpression);
                }
            }
        }
    }

    Optional<JobNode> fetch(final int id) {
        return jobNodeDao.fetch(id);
    }

    void executeJob(final JobNode jobNode) {
        LOGGER.info("Marking job '{}' on node '{}' for immediate execution",
                jobNode.getJobName(), jobNode.getNodeName());

        jobNodeTrackerCache.getTrackers()
                .triggerImmediateExecution(jobNode);
    }
}
