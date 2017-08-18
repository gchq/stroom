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

package stroom.entity.server;

import event.logging.BaseAdvancedQueryOperator.And;
import event.logging.Query;
import event.logging.Query.Advanced;
import org.springframework.context.annotation.Scope;
import stroom.dashboard.server.logging.DocumentEventLog;
import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.EntityServiceFindSummaryAction;
import stroom.entity.shared.ResultList;
import stroom.entity.shared.SummaryDataRow;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;

@TaskHandlerBean(task = EntityServiceFindSummaryAction.class)
@Scope(value = StroomScope.TASK)
class EntityServiceFindSummaryHandler
        extends AbstractTaskHandler<EntityServiceFindSummaryAction<BaseCriteria>, ResultList<SummaryDataRow>> {
    private final EntityServiceBeanRegistry beanRegistry;
    private final DocumentEventLog documentEventLog;

    @Inject
    EntityServiceFindSummaryHandler(final EntityServiceBeanRegistry beanRegistry, final DocumentEventLog documentEventLog) {
        this.beanRegistry = beanRegistry;
        this.documentEventLog = documentEventLog;
    }

    @SuppressWarnings("unchecked")
    @Override
    public ResultList<SummaryDataRow> exec(final EntityServiceFindSummaryAction<BaseCriteria> action) {
        BaseResultList<SummaryDataRow> result = null;

        final And and = new And();
        final Advanced advanced = new Advanced();
        advanced.getAdvancedQueryItems().add(and);
        final Query query = new Query();
        query.setAdvanced(advanced);

        try {
            final Object entityService = beanRegistry.getEntityService(action.getCriteria().getClass());
            if (entityService != null && entityService instanceof SupportsCriteriaLogging<?>) {
                final SupportsCriteriaLogging<BaseCriteria> usesCriteria = (SupportsCriteriaLogging<BaseCriteria>) entityService;
                usesCriteria.appendCriteria(and.getAdvancedQueryItems(), action.getCriteria());
            }
        } catch (final Exception e) {
            // Ignore.
        }

        try {
            result = (BaseResultList<SummaryDataRow>) beanRegistry.invoke("findSummary", action.getCriteria());
            documentEventLog.searchSummary(action.getCriteria(), query, result);
        } catch (final RuntimeException e) {
            documentEventLog.searchSummary(action.getCriteria(), query, e);

            throw e;
        }

        return result;
    }
}
