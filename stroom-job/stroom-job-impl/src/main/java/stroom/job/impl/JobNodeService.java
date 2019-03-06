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
import stroom.job.shared.FindJobNodeCriteria;
import stroom.job.shared.JobNode;
import stroom.job.shared.JobNode.JobType;
import stroom.security.Security;
import stroom.security.shared.PermissionNames;
import stroom.util.scheduler.SimpleCron;
import stroom.util.shared.BaseResultList;
import stroom.util.shared.ModelStringUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

@Singleton
class JobNodeService {
    private final JobNodeDao jobNodeDao;
    private final Security security;
    private final DocumentEventLog documentEventLog;

    @Inject
    JobNodeService(final JobNodeDao jobNodeDao,
                   final Security security,
                   final DocumentEventLog documentEventLog) {
        this.jobNodeDao = jobNodeDao;
        this.security = security;
        this.documentEventLog = documentEventLog;
    }

    JobNode update(final JobNode jobNode) {
        // Stop Job Nodes being saved with invalid crons.
        ensureSchedule(jobNode);

        JobNode result = null;
        try {
            result = security.secureResult(PermissionNames.MANAGE_JOBS_PERMISSION, () -> {
                final Optional<JobNode> before = jobNodeDao.fetch(jobNode.getId());

                // We always want to update a job node instance even if we have a stale version.
                before.ifPresent(j -> jobNode.setVersion(j.getVersion()));

                final JobNode after = jobNodeDao.update(jobNode);
                documentEventLog.update(before.orElse(null), after, null);
                return after;
            });
        } catch (final RuntimeException e) {
            documentEventLog.update(jobNode, null, e);
        }

        return result;
    }

    BaseResultList<JobNode> find(final FindJobNodeCriteria findJobNodeCriteria) {
        final Query query = new Query();
        final Advanced advanced = new Advanced();
        query.setAdvanced(advanced);
        final And and = new And();
        advanced.getAdvancedQueryItems().add(and);

        BaseResultList<JobNode> results = null;
        try {
            results = security.secureResult(PermissionNames.MANAGE_JOBS_PERMISSION, () -> jobNodeDao.find(findJobNodeCriteria));
            documentEventLog.search(findJobNodeCriteria, query, results, null);
        } catch (final RuntimeException e) {
            documentEventLog.search(findJobNodeCriteria, query, null, e);
        }

        return results;
    }

    private void ensureSchedule(final JobNode jobNode) {
        // Stop Job Nodes being saved with invalid crons.
        if (JobType.CRON.equals(jobNode.getJobType())) {
            if (jobNode.getSchedule() != null) {
                // This will throw a runtime exception if the expression is invalid.
                SimpleCron.compile(jobNode.getSchedule());
            }
        }
        if (JobType.FREQUENCY.equals(jobNode.getJobType())) {
            if (jobNode.getSchedule() != null) {
                // This will throw a runtime exception if the expression is invalid.
                ModelStringUtil.parseDurationString(jobNode.getSchedule());
            }
        }
    }
}