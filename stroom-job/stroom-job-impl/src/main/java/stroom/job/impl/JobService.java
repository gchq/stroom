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
import java.util.Optional;
import java.util.Set;

@Singleton
class JobService {
    private final JobDao jobDao;
    private final Security security;
    private final DocumentEventLog documentEventLog;

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
        Job result = null;
        try {
            result = security.secureResult(PermissionNames.MANAGE_JOBS_PERMISSION, () -> {
                final Optional<Job> before = jobDao.fetch(job.getId()).map(this::decorate);

                // We always want to update a job instance even if we have a stale version.
                before.ifPresent(j -> job.setVersion(j.getVersion()));

                final Job after = jobDao.update(job);
                documentEventLog.update(before.orElse(null), after, null);
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
}
