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
 */

package stroom.processor.impl;

import com.codahale.metrics.health.HealthCheck.Result;
import event.logging.BaseAdvancedQueryOperator.And;
import event.logging.Query;
import event.logging.Query.Advanced;
import stroom.entity.shared.ExpressionCriteria;
import stroom.event.logging.api.DocumentEventLog;
import stroom.processor.api.ProcessorTaskService;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.ProcessorTaskResource;
import stroom.processor.shared.ProcessorTaskResultPage;
import stroom.processor.shared.ProcessorTaskSummary;
import stroom.processor.shared.ProcessorTaskSummaryResultPage;
import stroom.security.api.SecurityContext;
import stroom.util.HasHealthCheck;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResultList;
import stroom.util.shared.RestResource;

import javax.inject.Inject;

// TODO : @66 add event logging
class ProcessorTaskResourceImpl implements ProcessorTaskResource, RestResource, HasHealthCheck {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProcessorTaskResourceImpl.class);

    private final ProcessorTaskService processorTaskService;
    private final DocumentEventLog documentEventLog;
    private final SecurityContext securityContext;

    @Inject
    ProcessorTaskResourceImpl(final ProcessorTaskService processorTaskService,
                              final DocumentEventLog documentEventLog,
                              final SecurityContext securityContext) {
        this.processorTaskService = processorTaskService;
        this.documentEventLog = documentEventLog;
        this.securityContext = securityContext;
    }

    @Override
    public ProcessorTaskResultPage find(final ExpressionCriteria criteria) {
        return securityContext.secureResult(() -> {
            ResultList<ProcessorTask> result;

            final Query query = new Query();
            final Advanced advanced = new Advanced();
            query.setAdvanced(advanced);
            final And and = new And();
            advanced.getAdvancedQueryItems().add(and);

            try {
                result = processorTaskService.find(criteria);
                documentEventLog.search(criteria.getClass().getSimpleName(), query, ProcessorTask.class.getSimpleName(), result.getPageResponse(), null);
            } catch (final RuntimeException e) {
                documentEventLog.search(criteria.getClass().getSimpleName(), query, ProcessorTask.class.getSimpleName(), null, e);
                throw e;
            }

            return result.toResultPage(new ProcessorTaskResultPage());
        });
    }

    @Override
    public ProcessorTaskSummaryResultPage findSummary(final ExpressionCriteria criteria) {
        return securityContext.secureResult(() -> {
            ResultList<ProcessorTaskSummary> result;

            final Query query = new Query();
            final Advanced advanced = new Advanced();
            query.setAdvanced(advanced);
            final And and = new And();
            advanced.getAdvancedQueryItems().add(and);

            try {
                result = processorTaskService.findSummary(criteria);
                documentEventLog.search(criteria.getClass().getSimpleName(), query, ProcessorTaskSummary.class.getSimpleName(), result.getPageResponse(), null);
            } catch (final RuntimeException e) {
                documentEventLog.search(criteria.getClass().getSimpleName(), query, ProcessorTaskSummary.class.getSimpleName(), null, e);
                throw e;
            }

            return result.toResultPage(new ProcessorTaskSummaryResultPage());
        });
    }

    @Override
    public Result getHealth() {
        return Result.healthy();
    }
}