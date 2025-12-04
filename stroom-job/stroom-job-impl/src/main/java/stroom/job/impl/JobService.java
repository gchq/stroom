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

import stroom.job.api.DistributedTaskFactoryDescription;
import stroom.job.api.ScheduledJob;
import stroom.job.shared.Job;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.util.AuditUtil;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

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
               final Map<ScheduledJob, Provider<Runnable>> scheduledJobsMap,
               final DistributedTaskFactoryRegistry distributedTaskFactoryRegistry) {
        this.jobDao = jobDao;
        this.securityContext = securityContext;

        scheduledJobsMap.keySet().forEach(scheduledJob -> {
            // We only add managed jobs to the descriptions as only managed ones can accept user changes.
            if (scheduledJob.isManaged()) {
                jobDescriptionMap.put(scheduledJob.getName(), scheduledJob.getDescription());
                if (scheduledJob.isAdvanced()) {
                    jobAdvancedSet.add(scheduledJob.getName());
                }
            }
        });

        // Distributed Jobs done a different way
        distributedTaskFactoryRegistry.getFactoryMap().forEach((jobName, factory) -> {
            final DistributedTaskFactoryDescription distributedTaskFactoryBean = factory.getClass().getAnnotation(
                    DistributedTaskFactoryDescription.class);
            jobDescriptionMap.put(distributedTaskFactoryBean.jobName(), distributedTaskFactoryBean.description());
        });
    }

    Job update(final Job job) {
        return securityContext.secureResult(AppPermission.MANAGE_JOBS_PERMISSION, () -> {
            final Optional<Job> before = fetch(job.getId());

            // We always want to update a job instance even if we have a stale version.
            before.ifPresent(j -> job.setVersion(j.getVersion()));

            AuditUtil.stamp(securityContext, job);
            final Job after = jobDao.update(job);
            return decorate(after);
        });
    }

    ResultPage<Job> find(final FindJobCriteria findJobCriteria) {
        final ResultPage<Job> results = securityContext.secureResult(AppPermission.MANAGE_JOBS_PERMISSION,
                () -> jobDao.find(findJobCriteria));
        results.getValues().forEach(this::decorate);

        if (!findJobCriteria.getSortList().isEmpty()) {
            final CriteriaFieldSort sort = findJobCriteria.getSortList().get(0);
            if (sort.getId().equals(FindJobCriteria.FIELD_ADVANCED)) {
                results.getValues().sort(Comparator.comparing(Job::isAdvanced).thenComparing(Job::getName));
            }
        }

        return results;
    }

    Optional<Job> fetch(final int id) {
        return jobDao.fetch(id).map(this::decorate);
    }

    Job decorate(final Job job) {
        job.setDescription(jobDescriptionMap.get(job.getName()));
        job.setAdvanced(jobAdvancedSet.contains(job.getName()));
        return job;
    }

    int setJobsEnabledForNode(final String nodeName,
                              final boolean enabled,
                              final Set<String> includeJobs,
                              final Set<String> excludeJobs) {
        return securityContext.secureResult(
                AppPermission.MANAGE_JOBS_PERMISSION,
                () -> jobDao.setJobsEnabled(nodeName, enabled, includeJobs, excludeJobs));
    }
}
