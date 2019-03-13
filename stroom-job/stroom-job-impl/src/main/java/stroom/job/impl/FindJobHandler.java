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
import stroom.job.shared.FindJobAction;
import stroom.job.shared.Job;
import stroom.task.api.AbstractTaskHandler;
import stroom.util.shared.BaseResultList;
import stroom.util.shared.ResultList;

import javax.inject.Inject;

class FindJobHandler extends AbstractTaskHandler<FindJobAction, ResultList<Job>> {
    private final JobService jobService;
    private final DocumentEventLog documentEventLog;

    @Inject
    FindJobHandler(final JobService jobService,
                   final DocumentEventLog documentEventLog) {
        this.jobService = jobService;
        this.documentEventLog = documentEventLog;
    }

    @Override
    public BaseResultList<Job> exec(final FindJobAction action) {
        BaseResultList<Job> results = null;

        final Query query = new Query();
        final Advanced advanced = new Advanced();
        query.setAdvanced(advanced);
        final And and = new And();
        advanced.getAdvancedQueryItems().add(and);

        try {
            results = jobService.find(action.getCriteria());
            documentEventLog.search(action.getCriteria(), query, results, null);
        } catch (final RuntimeException e) {
            documentEventLog.search(action.getCriteria(), query, results, e);
        }

        return results;
    }
}
