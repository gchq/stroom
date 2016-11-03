/*
 * Copyright 2016 Crown Copyright
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

package stroom.jobsystem.server;

import stroom.entity.server.NamedEntityServiceImpl;
import stroom.entity.server.QueryAppender;
import stroom.entity.server.util.StroomEntityManager;
import stroom.jobsystem.shared.FindJobCriteria;
import stroom.jobsystem.shared.Job;
import stroom.jobsystem.shared.JobService;
import stroom.security.Secured;
import stroom.util.logging.StroomLogger;
import stroom.util.spring.StroomBeanMethod;
import stroom.util.spring.StroomBeanStore;
import stroom.util.spring.StroomStartup;
import org.apache.commons.lang.builder.CompareToBuilder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Transactional
@Secured(Job.MANAGE_JOBS_PERMISSION)
@Component
public class JobServiceImpl extends NamedEntityServiceImpl<Job, FindJobCriteria> implements JobService {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(JobServiceImpl.class);

    private final StroomBeanStore stroomBeanStore;

    @Inject
    JobServiceImpl(final StroomEntityManager entityManager, final StroomBeanStore stroomBeanStore) {
        super(entityManager);
        this.stroomBeanStore = stroomBeanStore;
    }

    @Override
//    @Secured(permission = DocumentPermissionNames.UPDATE)
    public Job save(final Job entity) throws RuntimeException {
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
        for (final StroomBeanMethod stroomBeanMethod : stroomBeanStore.getStroomBeanMethod(JobTrackedSchedule.class)) {
            final JobTrackedSchedule jobScheduleDescriptor = stroomBeanMethod.getBeanMethod()
                    .getAnnotation(JobTrackedSchedule.class);
            queryAppender.getJobDescriptionMap().put(jobScheduleDescriptor.jobName(), jobScheduleDescriptor.description());
            if (jobScheduleDescriptor.advanced()) {
                queryAppender.getJobAdvancedSet().add(jobScheduleDescriptor.jobName());
            }
        }
        // Distributed Jobs done a different way
        for (final String beanFactory : stroomBeanStore.getStroomBean(DistributedTaskFactoryBean.class)) {
            final DistributedTaskFactoryBean distributedTaskFactoryBean = stroomBeanStore.findAnnotationOnBean(beanFactory,
                    DistributedTaskFactoryBean.class);

            queryAppender.getJobDescriptionMap().put(distributedTaskFactoryBean.jobName(), distributedTaskFactoryBean.description());

        }
    }

    @Override
    protected QueryAppender<Job, FindJobCriteria> createQueryAppender(StroomEntityManager entityManager) {
        return new JobQueryAppender(entityManager);
    }

    private static class JobQueryAppender extends QueryAppender<Job, FindJobCriteria> {
        private final Map<String, String> jobDescriptionMap = new HashMap<>();
        private final Set<String> jobAdvancedSet = new HashSet<>();

        public JobQueryAppender(final StroomEntityManager entityManager) {
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
            if (FindJobCriteria.ORDER_BY_ADVANCED_AND_NAME.equals(findJobCriteria.getOrderBy())) {
                list.stream().forEach(this::postLoad);

                final ArrayList<Job> rtnList = new ArrayList<>(list);
                Collections.sort(rtnList, (o1, o2) -> {
                    final CompareToBuilder compareToBuilder = new CompareToBuilder();
                    compareToBuilder.append(o1.isAdvanced(), o2.isAdvanced());
                    compareToBuilder.append(o1.getName(), o2.getName());
                    return compareToBuilder.toComparison();
                });
                return rtnList;
            } else {
                return super.postLoad(findJobCriteria, list);
            }
        }

        public Map<String, String> getJobDescriptionMap() {
            return jobDescriptionMap;
        }

        public Set<String> getJobAdvancedSet() {
            return jobAdvancedSet;
        }
    }
}
