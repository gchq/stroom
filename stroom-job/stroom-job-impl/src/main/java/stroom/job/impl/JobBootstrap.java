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

import stroom.cluster.lock.api.ClusterLockService;
import stroom.job.api.ScheduledJob;
import stroom.job.shared.Job;
import stroom.job.shared.JobNode;
import stroom.job.shared.JobNode.JobType;
import stroom.node.api.NodeInfo;
import stroom.security.api.SecurityContext;
import stroom.util.AuditUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResultPage;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
class JobBootstrap {

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

    void startup() {
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

            // TODO: The form below isn't very clear. Split into job mapping and creation.
            for (ScheduledJob scheduledJob : scheduledJobsMap.keySet()) {
                // We only add managed jobs to the DB as only managed ones can accept user changes.
                if (scheduledJob.isManaged()) {
                    if (validJobNames.contains(scheduledJob.getName())) {
                        LOGGER.error("Duplicate job name detected: " + scheduledJob.getName());
                        throw new RuntimeException("Duplicate job name detected: " + scheduledJob.getName());
                    }
                    validJobNames.add(scheduledJob.getName());

                    Job job = new Job();
                    job.setName(scheduledJob.getName());
                    job.setEnabled(scheduledJob.isEnabled());
                    job = getOrCreateJob(job);

                    final JobNode newJobNode = new JobNode();
                    newJobNode.setJob(job);
                    newJobNode.setNodeName(nodeName);
                    newJobNode.setEnabled(scheduledJob.isEnabled());

                    switch (scheduledJob.getSchedule().getScheduleType()) {
                        case CRON:
                            newJobNode.setJobType(JobType.CRON);
                            break;
                        case PERIODIC:
                            newJobNode.setJobType(JobType.FREQUENCY);
                            break;
                        default:
                            throw new RuntimeException("Unknown ScheduleType!");
                    }
                    newJobNode.setSchedule(scheduledJob.getSchedule().getSchedule());

                    // Add the job node to the DB if it isn't there already.
                    JobNode jobNode = localJobNodeMap.get(scheduledJob.getName());
                    if (jobNode == null) {
                        LOGGER.info(() -> "Adding JobNode '" + newJobNode.getJob().getName() +
                                "' for node '" + newJobNode.getNodeName() + "' (state: " +
                                (newJobNode.isEnabled()
                                        ? "ENABLED"
                                        : "DISABLED") + ")");

                        AuditUtil.stamp(securityContext.getUserId(), newJobNode);
                        jobNodeDao.create(newJobNode);
                        localJobNodeMap.put(newJobNode.getJob().getName(), newJobNode);

                    } else if (!Objects.equals(newJobNode.getJobType(), jobNode.getJobType())) {
                        // If the job type has changed then update the job node.
                        jobNode.setJobType(newJobNode.getJobType());
                        jobNode.setSchedule(newJobNode.getSchedule());
                        AuditUtil.stamp(securityContext.getUserId(), jobNode);
                        jobNode = jobNodeDao.update(jobNode);
                        localJobNodeMap.put(scheduledJob.getName(), jobNode);
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
                    final JobSystemConfig jobSystemConfig = jobSystemConfigProvider.get();
                    // Get or create the actual parent job record
                    Job job = new Job();
                    job.setName(jobName);
                    job.setEnabled(jobSystemConfig.isEnableJobsOnBootstrap());
                    job = getOrCreateJob(job);

                    // Now create the jobNode record for this node
                    final JobNode newJobNode = new JobNode();
                    newJobNode.setJob(job);
                    newJobNode.setNodeName(nodeName);
                    newJobNode.setEnabled(jobSystemConfig.isEnableJobsOnBootstrap());
                    newJobNode.setJobType(JobType.DISTRIBUTED);

                    LOGGER.info(() -> "Adding JobNode '" + newJobNode.getJob().getName() +
                            "' for node '" + newJobNode.getNodeName() + "' (state: " +
                            (newJobNode.isEnabled()
                                    ? "ENABLED"
                                    : "DISABLED") + ")");

                    AuditUtil.stamp(securityContext.getUserId(), newJobNode);
                    jobNodeDao.create(newJobNode);
                }
            });

            existingJobNodes.stream().filter(jobNode -> !validJobNames.contains(jobNode.getJob().getName()))
                    .forEach(jobNode -> {
                        LOGGER.info(() -> "Removing old job node " + jobNode.getJob().getName());
                        jobNodeDao.delete(jobNode.getId());
                    });

            final int deleteCount = jobDao.deleteOrphans();
            if (deleteCount > 0) {
                LOGGER.info(() -> "Removed " + deleteCount + " orphan jobs");
            }
        }));
    }

    private Job getOrCreateJob(final Job job) {
        Job result;

        // See if the job exists in the database.
        final FindJobCriteria criteria = new FindJobCriteria();
        criteria.getName().setString(job.getName());

        // Add the job to the DB if it isn't there already.
        final ResultPage<Job> existingJob = jobDao.find(criteria);
        if (existingJob != null && existingJob.size() > 0) {
            result = existingJob.getFirst();

            // Update the job description if we need to.
            if (job.getDescription() != null && !job.getDescription().equals(result.getDescription())) {
                result.setDescription(job.getDescription());
                LOGGER.info(() -> "Updating Job     '" + job.getName() + "'");
                AuditUtil.stamp(securityContext.getUserId(), result);
                result = jobDao.update(result);
            }
        } else {
            LOGGER.info(() -> "Adding Job     '" + job.getName() + "'");
            AuditUtil.stamp(securityContext.getUserId(), job);
            result = jobDao.create(job);
        }

        return result;
    }
}
