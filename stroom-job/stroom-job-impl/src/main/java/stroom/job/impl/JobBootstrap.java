/*
 * Copyright 2024 Crown Copyright
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

import stroom.cluster.lock.api.ClusterLockService;
import stroom.job.api.ScheduledJob;
import stroom.job.shared.FindJobNodeCriteria;
import stroom.job.shared.Job;
import stroom.job.shared.JobNode;
import stroom.job.shared.JobNode.JobType;
import stroom.node.api.NodeInfo;
import stroom.security.api.SecurityContext;
import stroom.util.AuditUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.scheduler.Schedule;
import stroom.util.shared.scheduler.ScheduleType;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Singleton
public class JobBootstrap {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(JobBootstrap.class);
    private static final String LOCK_NAME = "JobNodeService";

    private final JobDao jobDao;
    private final JobNodeDao jobNodeDao;
    private final Provider<JobSystemConfig> jobSystemConfigProvider;
    private final ClusterLockService clusterLockService;
    private final SecurityContext securityContext;
    private final NodeInfo nodeInfo;
    private final Map<ScheduledJob, Provider<Runnable>> scheduledJobsMap;
    private final DistributedTaskFactoryRegistry distributedTaskFactoryRegistry;

    @Inject
    JobBootstrap(final JobDao jobDao,
                 final JobNodeDao jobNodeDao,
                 final Provider<JobSystemConfig> jobSystemConfigProvider,
                 final ClusterLockService clusterLockService,
                 final SecurityContext securityContext,
                 final NodeInfo nodeInfo,
                 final Map<ScheduledJob, Provider<Runnable>> scheduledJobsMap,
                 final DistributedTaskFactoryRegistry distributedTaskFactoryRegistry) {
        this.jobDao = jobDao;
        this.jobNodeDao = jobNodeDao;
        this.jobSystemConfigProvider = jobSystemConfigProvider;
        this.clusterLockService = clusterLockService;
        this.securityContext = securityContext;
        this.nodeInfo = nodeInfo;
        this.scheduledJobsMap = scheduledJobsMap;
        this.distributedTaskFactoryRegistry = distributedTaskFactoryRegistry;
    }

    public void startup() {
        LOGGER.info(() -> "startup()");

        // Lock the cluster so only 1 node at a time can call the following code.
        LOGGER.trace(() -> "Locking the cluster");
        securityContext.asProcessingUser(() -> clusterLockService.lock(LOCK_NAME, () -> {
            final String nodeName = nodeInfo.getThisNodeName();

            final List<JobNode> existingJobNodes = jobNodeDao.find(new FindJobNodeCriteria()).getValues();
            final Map<String, JobNode> localJobNodeMap = existingJobNodes
                    .stream()
                    .filter(jobNode -> nodeName.equals(jobNode.getNodeName()))
                    .collect(Collectors.toMap(jobNode -> jobNode.getJob().getName(), Function.identity()));

            final Set<String> validJobNames = new HashSet<>();
            final JobSystemConfig jobSystemConfig = jobSystemConfigProvider.get();

            // TODO: The form below isn't very clear. Split into job mapping and creation.
            for (final ScheduledJob scheduledJob : scheduledJobsMap.keySet()) {
                // We only add managed jobs to the DB as only managed ones can accept user changes.
                if (scheduledJob.isManaged()) {
                    if (validJobNames.contains(scheduledJob.getName())) {
                        LOGGER.error("Duplicate job name detected: " + scheduledJob.getName());
                        throw new RuntimeException("Duplicate job name detected: " + scheduledJob.getName());
                    }
                    validJobNames.add(scheduledJob.getName());

                    final Job job = new Job();
                    job.setName(scheduledJob.getName());
                    setEnabledState(scheduledJob, job::setEnabled, jobSystemConfig);
                    final Job persistedJob = getOrCreateJob(job, "scheduled");

                    final JobNode newJobNode = new JobNode();
                    newJobNode.setJob(persistedJob);
                    newJobNode.setNodeName(nodeName);
                    setEnabledState(scheduledJob, newJobNode::setEnabled, jobSystemConfig);

                    final Schedule schedule = scheduledJob.getSchedule();
                    final JobType newJobType = switch (schedule.getType()) {
                        case ScheduleType.CRON -> JobType.CRON;
                        case ScheduleType.FREQUENCY -> JobType.FREQUENCY;
                        default -> throw new RuntimeException("Unexpected ScheduleType "
                                                              + scheduledJob.getSchedule().getType());
                    };
                    newJobNode.setJobType(newJobType);
                    newJobNode.setSchedule(schedule.getExpression());

                    // Add the job node to the DB if it isn't there already.
                    final JobNode jobNode = localJobNodeMap.get(scheduledJob.getName());
                    if (jobNode == null) {
                        LOGGER.info(() -> "Adding   scheduled JobNode '" + newJobNode.getJob().getName() +
                                          "' for node '" + newJobNode.getNodeName() + "' (state: " +
                                          newJobNode.getCombinedStateAsString() + ")");

                        AuditUtil.stamp(securityContext, newJobNode);
                        jobNodeDao.create(newJobNode);
                        localJobNodeMap.put(newJobNode.getJob().getName(), newJobNode);

                    } else if (!Objects.equals(newJobNode.getJobType(), jobNode.getJobType())) {
                        // If the job type has changed then update the job node.
                        jobNode.setJobType(newJobNode.getJobType());
                        jobNode.setSchedule(newJobNode.getSchedule());
                        AuditUtil.stamp(securityContext, jobNode);
                        final JobNode persistedJobNode = jobNodeDao.update(jobNode);
                        localJobNodeMap.put(scheduledJob.getName(), persistedJobNode);
                        LOGGER.info(() -> "Updating scheduled JobNode '" + persistedJobNode.getJob().getName() +
                                          "' for node '" + persistedJobNode.getNodeName() + "' (state: " +
                                          persistedJobNode.getCombinedStateAsString() + ")");
                    }
                }
            }

            // Distributed Jobs done a different way
            distributedTaskFactoryRegistry.getFactoryMap().forEach((jobName, factory) -> {
                if (validJobNames.contains(jobName)) {
                    LOGGER.error("Duplicate job name detected: " + jobName);
                    throw new RuntimeException("Duplicate job name detected: " + jobName);
                }
                validJobNames.add(jobName);

                // Add the job node to the DB if it isn't there already.
                final JobNode jobNode = localJobNodeMap.get(jobName);
                if (jobNode == null) {
                    final boolean enabled = jobSystemConfig.isEnableJobsOnBootstrap();
                    // Get or create the actual parent job record
                    final Job job = new Job();
                    job.setName(jobName);
                    job.setEnabled(enabled);
                    final Job persistedJob = getOrCreateJob(job, "distributed");

                    // Now create the jobNode record for this node
                    final JobNode newJobNode = new JobNode();
                    newJobNode.setJob(persistedJob);
                    newJobNode.setNodeName(nodeName);
                    newJobNode.setEnabled(enabled);
                    newJobNode.setJobType(JobType.DISTRIBUTED);

                    LOGGER.info(() -> "Adding   distributed JobNode '" + newJobNode.getJob().getName() +
                                      "' for node '" + newJobNode.getNodeName() + "' (state: " +
                                      newJobNode.getCombinedStateAsString() + ")");

                    AuditUtil.stamp(securityContext, newJobNode);
                    jobNodeDao.create(newJobNode);
                }
            });

            existingJobNodes.stream().filter(jobNode -> !validJobNames.contains(jobNode.getJob().getName()))
                    .forEach(jobNode -> {
                        LOGGER.info(() -> LogUtil.message("Removing old job node '{}' on node '{}'",
                                jobNode.getJob().getName(),
                                jobNode.getNodeName()));
                        jobNodeDao.delete(jobNode.getId());
                    });

            final int deleteCount = jobDao.deleteOrphans();
            if (deleteCount > 0) {
                LOGGER.info(() -> "Removed " + deleteCount + " orphan jobs");
            }
        }));
    }

    private static void setEnabledState(final ScheduledJob scheduledJob,
                                        final Consumer<Boolean> setter,
                                        final JobSystemConfig jobSystemConfig) {
        final boolean enableJobsOnBootstrap = jobSystemConfig.isEnableJobsOnBootstrap();
        final boolean enabled = enableJobsOnBootstrap
                ? scheduledJob.isEnabledOnBootstrap()
                : scheduledJob.isEnabled();
        LOGGER.debug(() -> LogUtil.message(
                "setEnabledState() - job: {}, scheduledJob.isEnabled: {}, scheduledJob.isEnabledOnBootstrap: {}, " +
                "enableJobsOnBootstrap: {}, enabled: {}",
                scheduledJob.getName(),
                scheduledJob.isEnabled(),
                scheduledJob.isEnabledOnBootstrap(),
                enableJobsOnBootstrap,
                enabled));
        Objects.requireNonNull(setter).accept(enabled);
    }

    private Job getOrCreateJob(final Job job, final String type) {
        Job result;

        // See if the job exists in the database.
        final FindJobCriteria criteria = new FindJobCriteria();
        // Should only match one job
        criteria.getName().setString(job.getName());

        // Add the job to the DB if it isn't there already.
        final ResultPage<Job> existingJobs = jobDao.find(criteria);
        if (NullSafe.hasItems(existingJobs)) {
            result = existingJobs.getFirst();

            // Update the job description if we need to.
            final String jobDescription = job.getDescription();
            if (jobDescription != null && !jobDescription.equals(result.getDescription())) {
                result.setDescription(jobDescription);
                LOGGER.info(() -> LogUtil.message("Updating {} Job     '{}'", type, job.getName()));
                AuditUtil.stamp(securityContext, result);
                result = jobDao.update(result);
            }
        } else {
            LOGGER.info(() -> LogUtil.message("Adding   {} Job     '{}'", type, job.getName()));
            AuditUtil.stamp(securityContext, job);
            result = jobDao.create(job);
        }

        return result;
    }
}
