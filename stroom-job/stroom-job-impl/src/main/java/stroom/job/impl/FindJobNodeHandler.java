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
import stroom.job.shared.FindJobNodeAction;
import stroom.job.shared.JobNodeRow;
import stroom.task.api.AbstractTaskHandler;
import stroom.util.shared.BaseResultList;
import stroom.util.shared.ResultList;

import javax.inject.Inject;


class FindJobNodeHandler extends AbstractTaskHandler<FindJobNodeAction, ResultList<JobNodeRow>> {
    private final JobNodeService jobNodeService;
    private final DocumentEventLog documentEventLog;

    @Inject
    FindJobNodeHandler(final JobNodeService jobNodeService,
                       final DocumentEventLog documentEventLog) {
        this.jobNodeService = jobNodeService;
        this.documentEventLog = documentEventLog;
    }

    @Override
    public BaseResultList<JobNodeRow> exec(final FindJobNodeAction action) {
        final Query query = new Query();
        final Advanced advanced = new Advanced();
        query.setAdvanced(advanced);
        final And and = new And();
        advanced.getAdvancedQueryItems().add(and);

        BaseResultList<JobNodeRow> results = null;
        try {
            results = jobNodeService.findStatus(action.getCriteria());
            documentEventLog.search(action.getCriteria(), query, results, null);
        } catch (final RuntimeException e) {
            documentEventLog.search(action.getCriteria(), query, results, e);
        }
        return results;
    }
}
