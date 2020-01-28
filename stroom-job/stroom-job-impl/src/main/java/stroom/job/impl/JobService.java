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


import stroom.job.api.DistributedTaskFactoryDescription;
import stroom.job.api.ScheduledJob;
import stroom.job.api.TaskConsumer;
import stroom.job.shared.Job;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.util.AuditUtil;
import stroom.util.shared.BaseResultList;
import stroom.util.shared.Sort;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Singleton
class JobService {
    private final JobDao jobDao;
    private final SecurityContext securityContext;

    private final Map<String, String> jobDescriptionMap = new HashMap<>();
    private final Set<String> jobAdvancedSet = new HashSet<>();

    @Inject
    JobService(final JobDao jobDao,
               final SecurityContext securityContext,
               final Map<ScheduledJob, Provider<TaskConsumer>> scheduledJobsMap,
               final DistributedTaskFactoryBeanRegistry distributedTaskFactoryBeanRegistry) {
        this.jobDao = jobDao;
        this.securityContext = securityContext;

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

    Job update(final Job job) {
        return securityContext.secureResult(PermissionNames.MANAGE_JOBS_PERMISSION, () -> {
            final Optional<Job> before = fetch(job.getId());

                // We always want to update a job instance even if we have a stale version.
                before.ifPresent(j -> job.setVersion(j.getVersion()));

            AuditUtil.stamp(securityContext.getUserId(), job);
                final Job after = jobDao.update(job);
                return decorate(after);
            });
    }

    BaseResultList<Job> find(final FindJobCriteria findJobCriteria) {
        final BaseResultList<Job> results = securityContext.secureResult(PermissionNames.MANAGE_JOBS_PERMISSION, () -> jobDao.find(findJobCriteria));
        results.forEach(this::decorate);

        if (findJobCriteria.getSortList().size() > 0) {
            final Sort sort = findJobCriteria.getSortList().get(0);
            if (sort.getField().equals(FindJobCriteria.FIELD_ADVANCED)) {
                results.sort(Comparator.comparing(Job::isAdvanced).thenComparing(Job::getName));
            }
        }

        return results;
    }

    Optional<Job> fetch(final int id) {
        return jobDao.fetch(id).map(this::decorate);
    }

    private Job decorate(final Job job) {
        job.setDescription(jobDescriptionMap.get(job.getName()));
        job.setAdvanced(jobAdvancedSet.contains(job.getName()));
        return job;
    }
}
