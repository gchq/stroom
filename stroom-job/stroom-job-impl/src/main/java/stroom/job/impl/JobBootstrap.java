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
import stroom.job.api.TaskRunnable;
import stroom.job.shared.Job;
import stroom.job.shared.JobNode;
import stroom.job.shared.JobNode.JobType;
import stroom.node.api.NodeInfo;
import stroom.security.api.SecurityContext;
import stroom.util.AuditUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResultPage;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Singleton
class JobBootstrap {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(JobBootstrap.class);
    private static final String LOCK_NAME = "JobNodeService";

    private final JobDao jobDao;
    private final JobNodeDao jobNodeDao;
    private final ClusterLockService clusterLockService;
    private final SecurityContext securityContext;
    private final NodeInfo nodeInfo;
    private final Map<ScheduledJob, Provider<TaskRunnable>> scheduledJobsMap;
    private final DistributedTaskFactoryBeanRegistry distributedTaskFactoryBeanRegistry;

    @Inject
    JobBootstrap(final JobDao jobDao,
                 final JobNodeDao jobNodeDao,
                 final ClusterLockService clusterLockService,
                 final SecurityContext securityContext,
                 final NodeInfo nodeInfo,
                 final Map<ScheduledJob, Provider<TaskRunnable>> scheduledJobsMap,
                 final DistributedTaskFactoryBeanRegistry distributedTaskFactoryBeanRegistry) {
        this.jobDao = jobDao;
        this.jobNodeDao = jobNodeDao;
        this.clusterLockService = clusterLockService;
        this.securityContext = securityContext;
        this.nodeInfo = nodeInfo;
        this.scheduledJobsMap = scheduledJobsMap;
        this.distributedTaskFactoryBeanRegistry = distributedTaskFactoryBeanRegistry;
    }

    void startup() {
        LOGGER.info(() -> "startup()");

        // Lock the cluster so only 1 node at a time can call the following code.
        LOGGER.trace(() -> "Locking the cluster");
        securityContext.asProcessingUser(() -> clusterLockService.lock(LOCK_NAME, () -> {
            final String nodeName = nodeInfo.getThisNodeName();

            final List<JobNode> existingJobList = findAllJobs(nodeName);
            final Map<String, JobNode> existingJobMap = new HashMap<>();
            for (final JobNode jobNode : existingJobList) {
                existingJobMap.put(jobNode.getJob().getName(), jobNode);
            }

            final Set<String> validJobNames = new HashSet<>();

            // TODO: The form below isn't very clear. Split into job mapping and creation.
            for (ScheduledJob scheduledJob : scheduledJobsMap.keySet()) {
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
                JobNode existingJobNode = existingJobMap.get(scheduledJob.getName());
                if (existingJobNode == null) {
                    LOGGER.info(() -> "Adding JobNode '" + newJobNode.getJob().getName() + "' for node '" + newJobNode.getNodeName() + "'");
                    AuditUtil.stamp(securityContext.getUserId(), newJobNode);
                    jobNodeDao.create(newJobNode);
                    existingJobMap.put(newJobNode.getJob().getName(), newJobNode);

                } else if (!Objects.equals(newJobNode.getJobType(), existingJobNode.getJobType())) {
                    // If the job type has changed then update the job node.
                    existingJobNode.setJobType(newJobNode.getJobType());
                    existingJobNode.setSchedule(newJobNode.getSchedule());
                    AuditUtil.stamp(securityContext.getUserId(), existingJobNode);
                    existingJobNode = jobNodeDao.update(existingJobNode);
                    existingJobMap.put(scheduledJob.getName(), existingJobNode);
                }
            }

            // Distributed Jobs done a different way
            distributedTaskFactoryBeanRegistry.getFactoryMap().forEach((jobName, factory) -> {
                validJobNames.add(jobName);

                // Add the job node to the DB if it isn't there already.
                final JobNode existingJobNode = existingJobMap.get(jobName);
                if (existingJobNode == null) {
                    // Get the actual job.
                    Job job = new Job();
                    job.setName(jobName);
                    job.setEnabled(false);
                    job = getOrCreateJob(job);

                    final JobNode newJobNode = new JobNode();
                    newJobNode.setJob(job);
                    newJobNode.setNodeName(nodeName);
                    newJobNode.setEnabled(false);
                    newJobNode.setJobType(JobType.DISTRIBUTED);

                    LOGGER.info(() -> "Adding JobNode '" + newJobNode.getJob().getName() + "' for node '" + newJobNode.getNodeName() + "'");
                    AuditUtil.stamp(securityContext.getUserId(), newJobNode);
                    jobNodeDao.create(newJobNode);
                    existingJobMap.put(newJobNode.getJob().getName(), newJobNode);
                }
            });

            existingJobList.stream().filter(jobNode -> !validJobNames.contains(jobNode.getJob().getName()))
                    .forEach(jobNode -> {
                        LOGGER.info(() -> "Removing old job node " + jobNode.getJob().getName());
                        jobNodeDao.delete(jobNode.getId());
                    });
//
//                // Force to delete
//                this.entityManager.flush();

            final int deleteCount = jobDao.deleteOrphans();
//                final SqlBuilder sql = new SqlBuilder();
//                sql.append(DELETE_ORPHAN_JOBS_MYSQL);
//
//                final Long deleteCount = this.entityManager.executeNativeUpdate(sql);
            if (deleteCount > 0) {
                LOGGER.info(() -> "Removed " + deleteCount + " orphan jobs");
            }
        }));
    }


//    public JobNode update(final JobNode jobNode) {
//        // We always want to update a job instance even if we have a stale
//        // version.
//        final Optional<JobNode> existing = jobNodeDao.fetch(jobNode.getId());
//        existing.ifPresent(j -> jobNode.setVersion(j.getVersion()));
//
//        // Stop Job Nodes being saved with invalid crons.
//        if (JobType.CRON.equals(jobNode.getJobType())) {
//            if (jobNode.getSchedule() != null) {
//                // This will throw a runtime exception if the expression is
//                // invalid.
//                SimpleCron.compile(jobNode.getSchedule());
//            }
//        }
//        if (JobType.FREQUENCY.equals(jobNode.getJobType())) {
//            if (jobNode.getSchedule() != null) {
//                // This will throw a runtime exception if the expression is
//                // invalid.
//                ModelStringUtil.parseDurationString(jobNode.getSchedule());
//            }
//        }
//
//        if (existing.isPresent()) {
//            return jobNodeDao.update(jobNode);
//        } else {
//            return jobNodeDao.create(jobNode);
//        }
//    }
//
//    //    @Override
//    public JobNode save(final JobNode jobNode) {
//        // We always want to update a job instance even if we have a stale
//        // version.
//        final Optional<JobNode> existing = jobNodeDao.fetch(jobNode.getId());
//        existing.ifPresent(j -> jobNode.setVersion(j.getVersion()));
//
//        // Stop Job Nodes being saved with invalid crons.
//        if (JobType.CRON.equals(jobNode.getJobType())) {
//            if (jobNode.getSchedule() != null) {
//                // This will throw a runtime exception if the expression is
//                // invalid.
//                SimpleCron.compile(jobNode.getSchedule());
//            }
//        }
//        if (JobType.FREQUENCY.equals(jobNode.getJobType())) {
//            if (jobNode.getSchedule() != null) {
//                // This will throw a runtime exception if the expression is
//                // invalid.
//                ModelStringUtil.parseDurationString(jobNode.getSchedule());
//            }
//        }
//
//        if (existing.isPresent()) {
//            return jobNodeDao.update(jobNode);
//        } else {
//            return jobNodeDao.create(jobNode);
//        }
//    }

    private List<JobNode> findAllJobs(final String nodeName) {
        // See if the job exists in the database.
        final FindJobNodeCriteria criteria = new FindJobNodeCriteria();
        criteria.getNodeName().setString(nodeName);
        return jobNodeDao.find(criteria).getValues();

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

//    @Override
//    public Class<JobNode> getEntityClass() {
//        return JobNode.class;
//    }
//
//    @Override
//    public FindJobNodeCriteria createCriteria() {
//        return new FindJobNodeCriteria();
//    }
//
//    @Override
//    public void appendCriteria(final List<BaseAdvancedQueryItem> items, final FindJobNodeCriteria criteria) {
//        CriteriaLoggingUtil.appendStringTerm(items, "jobName", criteria.getJobName());
//        CriteriaLoggingUtil.appendEntityIdSet(items, "jobIdSet", criteria.getJobIdSet());
//        CriteriaLoggingUtil.appendEntityIdSet(items, "nodeIdSet", criteria.getNodeIdSet());
//        super.appendCriteria(items, criteria);
//    }
//
//    @Override
//    protected QueryAppender<JobNode, FindJobNodeCriteria> createQueryAppender(StroomEntityManager entityManager) {
//        return new JobNodeQueryAppender(entityManager);
//    }
//
//    @Override
//    protected String permission() {
//        return PermissionNames.MANAGE_JOBS_PERMISSION;
//    }
//
//    private static class JobNodeQueryAppender extends QueryAppender<JobNode, FindJobNodeCriteria> {
//        JobNodeQueryAppender(final StroomEntityManager entityManager) {
//            super(entityManager);
//        }
//
//        @Override
//        protected void appendBasicJoin(final HqlBuilder sql, final String alias, final Set<String> fetchSet) {
//            super.appendBasicJoin(sql, alias, fetchSet);
//            if (fetchSet != null) {
//                if (fetchSet.contains(Node.ENTITY_TYPE)) {
//                    sql.append(" INNER JOIN FETCH " + alias + ".node");
//                }
//                if (fetchSet.contains(Job.ENTITY_TYPE)) {
//                    sql.append(" INNER JOIN FETCH " + alias + ".job");
//                }
//            }
//        }
//
//        @Override
//        protected void appendBasicCriteria(final HqlBuilder sql, final String alias, final FindJobNodeCriteria criteria) {
//            super.appendBasicCriteria(sql, alias, criteria);
//            sql.appendEntityIdSetQuery(alias + ".node", criteria.getNodeIdSet());
//            sql.appendEntityIdSetQuery(alias + ".job", criteria.getJobIdSet());
//            sql.appendValueQuery(alias + ".job.name", criteria.getJobName());
//        }
//    }
}
