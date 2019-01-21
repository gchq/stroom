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

package stroom.entity;

import event.logging.BaseAdvancedQueryItem;
import event.logging.BaseAdvancedQueryOperator.And;
import event.logging.Query;
import event.logging.Query.Advanced;
import stroom.docref.SharedObject;
import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.EntityServiceFindAction;
import stroom.entity.shared.ResultList;
import stroom.event.logging.api.DocumentEventLog;
import stroom.security.Security;
import stroom.task.api.AbstractTaskHandler;

import javax.inject.Inject;
import java.util.List;


class EntityServiceFindHandler
        extends AbstractTaskHandler<EntityServiceFindAction<BaseCriteria, SharedObject>, ResultList<SharedObject>> {
    private final EntityServiceBeanRegistry beanRegistry;
    private final DocumentEventLog documentEventLog;
    private final Security security;

    @Inject
    EntityServiceFindHandler(final EntityServiceBeanRegistry beanRegistry,
                             final DocumentEventLog documentEventLog,
                             final Security security) {
        this.beanRegistry = beanRegistry;
        this.documentEventLog = documentEventLog;
        this.security = security;
    }

    @SuppressWarnings("unchecked")
    @Override
    public ResultList<SharedObject> exec(final EntityServiceFindAction<BaseCriteria, SharedObject> action) {
        return security.secureResult(() -> {
            BaseResultList<SharedObject> result;

            final Query query = new Query();
            final Advanced advanced = new Advanced();
            query.setAdvanced(advanced);
            final And and = new And();
            advanced.getAdvancedQueryItems().add(and);

            try {
                final FindService entityService = beanRegistry.getEntityServiceByCriteria(action.getCriteria().getClass());
                addCriteria(entityService, action.getCriteria(), and.getAdvancedQueryItems());

                result = (BaseResultList<SharedObject>) beanRegistry.invoke(entityService, "find", action.getCriteria());
                documentEventLog.search(action.getCriteria(), query, result);
            } catch (final RuntimeException e) {
                documentEventLog.search(action.getCriteria(), query, e);

                throw e;
            }


            return result;
        });
    }

    @SuppressWarnings("unchecked")
    private void addCriteria(final FindService entityService, final BaseCriteria criteria, final List<BaseAdvancedQueryItem> items) {
        security.asProcessingUser(() -> {
            try {
                if (entityService instanceof SupportsCriteriaLogging) {
                    final SupportsCriteriaLogging<BaseCriteria> logging = (SupportsCriteriaLogging<BaseCriteria>) entityService;
                    logging.appendCriteria(items, criteria);
                }
            } catch (final RuntimeException e) {
                // Ignore.
            }
        });
    }
}
