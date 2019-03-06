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


import event.logging.BaseAdvancedQueryOperator.And;
import event.logging.Query;
import event.logging.Query.Advanced;
import stroom.event.logging.api.DocumentEventLog;
import stroom.job.api.DistributedTaskFactoryDescription;
import stroom.job.api.ScheduledJob;
import stroom.job.api.TaskConsumer;
import stroom.job.shared.FindJobCriteria;
import stroom.job.shared.Job;
import stroom.security.Security;
import stroom.security.shared.PermissionNames;
import stroom.util.shared.BaseResultList;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Singleton
class JobService {
//    private static final Logger LOGGER = LoggerFactory.getLogger(JobService.class);

    private final JobDao jobDao;
    private final Security security;
    private final DocumentEventLog documentEventLog;
//    private final Map<ScheduledJob, Provider<TaskConsumer>> scheduledJobsMap;
//    private final DistributedTaskFactoryBeanRegistry distributedTaskFactoryBeanRegistry;

    private final Map<String, String> jobDescriptionMap = new HashMap<>();
    private final Set<String> jobAdvancedSet = new HashSet<>();

    @Inject
    JobService(final JobDao jobDao,
               final Security security,
               final DocumentEventLog documentEventLog,
               final Map<ScheduledJob, Provider<TaskConsumer>> scheduledJobsMap,
               final DistributedTaskFactoryBeanRegistry distributedTaskFactoryBeanRegistry) {
        this.jobDao = jobDao;
        this.security = security;
        this.documentEventLog = documentEventLog;
//        this.scheduledJobsMap = scheduledJobsMap;
//        this.distributedTaskFactoryBeanRegistry = distributedTaskFactoryBeanRegistry;

        scheduledJobsMap.keySet().forEach(scheduledJob -> {
            jobDescriptionMap.put(scheduledJob.getName(), scheduledJob.getDescription());
            if (scheduledJob.isAdvanced()) {
                jobAdvancedSet.add(scheduledJob.getName());
            }
        });

        // Distributed Jobs done a different way
        distributedTaskFactoryBeanRegistry.getFactoryMap().forEach((jobName, factory) -> {
            final DistributedTaskFactoryDescription distributedTaskFactoryBean = factory.getClass().getAnnotation(DistributedTaskFactoryDescription.class);
            jobDescriptionMap.put(distributedTaskFactoryBean.jobName(), distributedTaskFactoryBean.description());
        });
    }

//    void startup() {
//        LOGGER.info("startup()");
//
//        scheduledJobsMap.keySet().forEach(scheduledJob -> {
//            jobDescriptionMap.put(scheduledJob.getName(), scheduledJob.getDescription());
//            if (scheduledJob.isAdvanced()) {
//                jobAdvancedSet.add(scheduledJob.getName());
//            }
//        });
//
//        // Distributed Jobs done a different way
//        distributedTaskFactoryBeanRegistry.getFactoryMap().forEach((jobName, factory) -> {
//            final DistributedTaskFactoryBean distributedTaskFactoryBean = factory.getClass().getAnnotation(DistributedTaskFactoryBean.class);
//            jobDescriptionMap.put(distributedTaskFactoryBean.jobName(), distributedTaskFactoryBean.description());
//        });
//    }
//
//    Job fetch(final int jobId) {
//        Job result = null;
//        try {
//            result = security.secureResult(PermissionNames.MANAGE_JOBS_PERMISSION, () ->
//                    jobDao.fetch(jobId)
//                            .map(this::decorate)
//                            .orElse(null));
//        } catch (final RuntimeException e) {
//            documentEventLog.view(result,e);
//        }
//
//        return result;
//    }

    Job update(final Job job) {
        Job result = null;
        try {
            result = security.secureResult(PermissionNames.MANAGE_JOBS_PERMISSION, () -> {
                final Job before = jobDao.fetch(job.getId())
                        .map(this::decorate)
                        .orElse(null);
                final Job after = jobDao.update(job);
                documentEventLog.update(before, after, null);
                return decorate(after);
            });
        } catch (final RuntimeException e) {
            documentEventLog.update(job, null, e);
        }

        return result;
    }

    BaseResultList<Job> find(final FindJobCriteria findJobCriteria) {
        final Query query = new Query();
        final Advanced advanced = new Advanced();
        query.setAdvanced(advanced);
        final And and = new And();
        advanced.getAdvancedQueryItems().add(and);

        BaseResultList<Job> results = null;
        try {
            results = security.secureResult(PermissionNames.MANAGE_JOBS_PERMISSION, () -> jobDao.find(findJobCriteria));
            results.forEach(this::decorate);
            documentEventLog.search(findJobCriteria, query, results, null);
        } catch (final RuntimeException e) {
            documentEventLog.search(findJobCriteria, query, null, e);
        }

        return results;
    }


    private Job decorate(final Job job) {
        job.setDescription(jobDescriptionMap.get(job.getName()));
        job.setAdvanced(jobAdvancedSet.contains(job.getName()));
        return job;
    }

//    @Override
//    public Job save(final Job entity) {
//
//
//        // We always want to update a job even if we have a stale version.
//        if (entity.isPersistent()) {
//            final Job tmp = load(entity);
//            entity.setVersion(tmp.getVersion());
//        }
//        return super.save(entity);
//    }
//
//    @Override
//    public Class<Job> getEntityClass() {
//        return Job.class;
//    }
//
//    @Override
//    public FindJobCriteria createCriteria() {
//        return new FindJobCriteria();
//    }
//
//    @Override
//    protected QueryAppender<Job, FindJobCriteria> createQueryAppender(StroomEntityManager entityManager) {
//        return new JobQueryAppender(entityManager);
//    }
//
//    protected FieldMap createFieldMap() {
//        return super.createFieldMap()
//                .add(FindJobCriteria.FIELD_ADVANCED, null, null);
//    }
//
//    @Override
//    protected String permission() {
//        return PermissionNames.MANAGE_JOBS_PERMISSION;
//    }
//
//    private static class JobQueryAppender extends QueryAppender<Job, FindJobCriteria> {
//        private final Map<String, String> jobDescriptionMap = new HashMap<>();
//        private final Set<String> jobAdvancedSet = new HashSet<>();
//
//        JobQueryAppender(final StroomEntityManager entityManager) {
//            super(entityManager);
//        }
//
//        @Override
//        protected void postLoad(final Job entity) {
//            entity.setDescription(jobDescriptionMap.get(entity.getName()));
//            entity.setAdvanced(jobAdvancedSet.contains(entity.getName()));
//            super.postLoad(entity);
//        }
//
//        @Override
//        protected List<Job> postLoad(final FindJobCriteria findJobCriteria, final List<Job> list) {
//            final List<Job> postLoadList = super.postLoad(findJobCriteria, list);
//
//            if (findJobCriteria.getSortList() != null && findJobCriteria.getSortList().size() > 0) {
//                final ArrayList<Job> rtnList = new ArrayList<>(postLoadList);
//                rtnList.sort((o1, o2) -> {
//                    if (findJobCriteria.getSortList() != null) {
//                        for (final Sort sort : findJobCriteria.getSortList()) {
//                            final String field = sort.getField();
//
//                            int compare = 0;
//                            if (FindJobCriteria.FIELD_ID.equals(field)) {
//                                compare = CompareUtil.compareLong(o1.getId(), o2.getId());
//                            } else if (FindJobCriteria.FIELD_NAME.equals(field)) {
//                                compare = CompareUtil.compareString(o1.getName(), o2.getName());
//                            } else if (FindJobCriteria.FIELD_ADVANCED.equals(field)) {
//                                compare = CompareUtil.compareBoolean(o1.isAdvanced(), o2.isAdvanced());
//                            }
//                            if (Direction.DESCENDING.equals(sort.getDirection())) {
//                                compare = compare * -1;
//                            }
//
//                            if (compare != 0) {
//                                return compare;
//                            }
//                        }
//                    }
//
//                    return 0;
//                });
//
//                return rtnList;
//            }
//
//            return postLoadList;
//        }
//
//        Map<String, String> getJobDescriptionMap() {
//            return jobDescriptionMap;
//        }
//
//        Set<String> getJobAdvancedSet() {
//            return jobAdvancedSet;
//        }
//    }
}
