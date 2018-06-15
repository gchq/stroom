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

package stroom.jobsystem;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.NamedEntityServiceImpl;
import stroom.entity.QueryAppender;
import stroom.entity.StroomEntityManager;
import stroom.entity.shared.Sort;
import stroom.entity.shared.Sort.Direction;
import stroom.entity.util.FieldMap;
import stroom.guice.StroomBeanStore;
import stroom.jobsystem.shared.FindJobCriteria;
import stroom.jobsystem.shared.Job;
import stroom.security.Security;
import stroom.security.shared.PermissionNames;
import stroom.util.lifecycle.JobTrackedSchedule;
import stroom.util.lifecycle.MethodReference;
import stroom.util.lifecycle.StroomStartup;
import stroom.util.shared.CompareUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Singleton
public class JobServiceImpl extends NamedEntityServiceImpl<Job, FindJobCriteria> implements JobService {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobServiceImpl.class);

    private final StroomBeanStore stroomBeanStore;
    private final DistributedTaskFactoryBeanRegistry distributedTaskFactoryBeanRegistry;

    @Inject
    JobServiceImpl(final StroomEntityManager entityManager,
                   final Security security,
                   final StroomBeanStore stroomBeanStore,
                   final DistributedTaskFactoryBeanRegistry distributedTaskFactoryBeanRegistry) {
        super(entityManager, security);
        this.stroomBeanStore = stroomBeanStore;
        this.distributedTaskFactoryBeanRegistry = distributedTaskFactoryBeanRegistry;
    }

    @Override
    public Job save(final Job entity) {
        // We always want to update a job even if we have a stale version.
        if (entity.isPersistent()) {
            final Job tmp = load(entity);
            entity.setVersion(tmp.getVersion());
        }
        return super.save(entity);
    }

    @Override
    public Class<Job> getEntityClass() {
        return Job.class;
    }

    @Override
    public FindJobCriteria createCriteria() {
        return new FindJobCriteria();
    }

    @StroomStartup
    @Override
    public void startup() {
        LOGGER.info("startup()");

        final JobQueryAppender queryAppender = (JobQueryAppender) getQueryAppender();
        for (final MethodReference methodReference : stroomBeanStore.getAnnotatedMethods(JobTrackedSchedule.class)) {
            final JobTrackedSchedule jobScheduleDescriptor = methodReference.getMethod()
                    .getAnnotation(JobTrackedSchedule.class);
            queryAppender.getJobDescriptionMap().put(jobScheduleDescriptor.jobName(), jobScheduleDescriptor.description());
            if (jobScheduleDescriptor.advanced()) {
                queryAppender.getJobAdvancedSet().add(jobScheduleDescriptor.jobName());
            }
        }
        // Distributed Jobs done a different way
        distributedTaskFactoryBeanRegistry.getFactoryMap().forEach((jobName, factory) -> {
            final DistributedTaskFactoryBean distributedTaskFactoryBean = factory.getClass().getAnnotation(DistributedTaskFactoryBean.class);
            queryAppender.getJobDescriptionMap().put(distributedTaskFactoryBean.jobName(), distributedTaskFactoryBean.description());

        });
    }

    @Override
    protected QueryAppender<Job, FindJobCriteria> createQueryAppender(StroomEntityManager entityManager) {
        return new JobQueryAppender(entityManager);
    }

    protected FieldMap createFieldMap() {
        return super.createFieldMap()
                .add(FindJobCriteria.FIELD_ADVANCED, null, null);
    }

    @Override
    protected String permission() {
        return PermissionNames.MANAGE_JOBS_PERMISSION;
    }

    private static class JobQueryAppender extends QueryAppender<Job, FindJobCriteria> {
        private final Map<String, String> jobDescriptionMap = new HashMap<>();
        private final Set<String> jobAdvancedSet = new HashSet<>();

        JobQueryAppender(final StroomEntityManager entityManager) {
            super(entityManager);
        }

        @Override
        protected void postLoad(final Job entity) {
            entity.setDescription(jobDescriptionMap.get(entity.getName()));
            entity.setAdvanced(jobAdvancedSet.contains(entity.getName()));
            super.postLoad(entity);
        }

        @Override
        protected List<Job> postLoad(final FindJobCriteria findJobCriteria, final List<Job> list) {
            final List<Job> postLoadList = super.postLoad(findJobCriteria, list);

            if (findJobCriteria.getSortList() != null && findJobCriteria.getSortList().size() > 0) {
                final ArrayList<Job> rtnList = new ArrayList<>(postLoadList);
                rtnList.sort((o1, o2) -> {
                    if (findJobCriteria.getSortList() != null) {
                        for (final Sort sort : findJobCriteria.getSortList()) {
                            final String field = sort.getField();

                            int compare = 0;
                            if (FindJobCriteria.FIELD_ID.equals(field)) {
                                compare = CompareUtil.compareLong(o1.getId(), o2.getId());
                            } else if (FindJobCriteria.FIELD_NAME.equals(field)) {
                                compare = CompareUtil.compareString(o1.getName(), o2.getName());
                            } else if (FindJobCriteria.FIELD_ADVANCED.equals(field)) {
                                compare = CompareUtil.compareBoolean(o1.isAdvanced(), o2.isAdvanced());
                            }
                            if (Direction.DESCENDING.equals(sort.getDirection())) {
                                compare = compare * -1;
                            }

                            if (compare != 0) {
                                return compare;
                            }
                        }
                    }

                    return 0;
                });

                return rtnList;
            }

            return postLoadList;
        }

        Map<String, String> getJobDescriptionMap() {
            return jobDescriptionMap;
        }

        Set<String> getJobAdvancedSet() {
            return jobAdvancedSet;
        }
    }
}
