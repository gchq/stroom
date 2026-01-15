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

import stroom.event.logging.api.DocumentEventLog;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.job.shared.Job;
import stroom.job.shared.JobResource;
import stroom.node.shared.NodeSetJobsEnabledRequest;
import stroom.node.shared.NodeSetJobsEnabledResponse;
import stroom.util.shared.ResultPage;

import event.logging.AdvancedQuery;
import event.logging.And;
import event.logging.Query;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

@AutoLogged(OperationType.MANUALLY_LOGGED)
class JobResourceImpl implements JobResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(JobResourceImpl.class);

    private final Provider<JobService> jobServiceProvider;
    private final Provider<DocumentEventLog> documentEventLogProvider;

    @Inject
    JobResourceImpl(final Provider<JobService> jobServiceProvider,
                    final Provider<DocumentEventLog> documentEventLogProvider) {
        this.jobServiceProvider = jobServiceProvider;
        this.documentEventLogProvider = documentEventLogProvider;
    }

    @Override
    public ResultPage<Job> list() {
        ResultPage<Job> response = null;

        final Query query = Query.builder()
                .withAdvanced(AdvancedQuery.builder()
                        .addAnd(And.builder()
                                .build())
                        .build())
                .build();

        try {
            final FindJobCriteria findJobCriteria = new FindJobCriteria();
            findJobCriteria.setSort(FindJobCriteria.FIELD_ADVANCED);
            findJobCriteria.addSort(FindJobCriteria.FIELD_NAME);

            response = jobServiceProvider.get().find(findJobCriteria);
            documentEventLogProvider.get().search(
                    "ListJobs",
                    query,
                    Job.class.getSimpleName(),
                    response.getPageResponse(),
                    null);
        } catch (final RuntimeException e) {
            documentEventLogProvider.get().search(
                    "ListJobs",
                    query,
                    Job.class.getSimpleName(),
                    null,
                    e);
            throw e;
        }
        return response;
    }

    @Override
    public void setEnabled(final Integer id, final Boolean enabled) {
        modifyJob(id, job -> job.setEnabled(enabled));
    }

    private void modifyJob(final int id, final Consumer<Job> mutation) {
        Job job = null;
        Job before = null;
        Job after = null;

        try {
            final JobService jobService = jobServiceProvider.get();
            // Get the before version.
            before = jobService.fetch(id).orElse(null);
            job = jobService.fetch(id).orElse(null);
            if (job == null) {
                throw new RuntimeException("Unknown job: " + id);
            }
            mutation.accept(job);
            after = jobService.update(job);

            documentEventLogProvider.get().update(before, after, null);

        } catch (final RuntimeException e) {
            documentEventLogProvider.get().update(before, after, e);
            throw e;
        }
    }

    @Override
    public NodeSetJobsEnabledResponse setJobsEnabled(final String nodeName, final NodeSetJobsEnabledRequest params) {
        final JobService jobService = jobServiceProvider.get();
        final int recordsUpdated = jobService.setJobsEnabledForNode(
                nodeName,
                params.isEnabled(),
                params.getIncludeJobs(),
                params.getExcludeJobs());

        if (recordsUpdated > 0) {
            final String enabledState = params.isEnabled()
                    ? "Enabled"
                    : "Disabled";
            LOGGER.info(enabledState + " " + recordsUpdated + " tasks for node " + nodeName);
        }

        return new NodeSetJobsEnabledResponse(recordsUpdated);
    }
}
