/*
 * Copyright 2016 Crown Copyright
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

package stroom.entity.server;

import event.logging.BaseAdvancedQueryOperator.And;
import event.logging.Query;
import event.logging.Query.Advanced;
import org.springframework.context.annotation.Scope;
import stroom.dashboard.server.logging.EntityEventLog;
import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.EntityServiceFindDeleteAction;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.shared.SharedLong;
import stroom.util.shared.SharedObject;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;

@TaskHandlerBean(task = EntityServiceFindDeleteAction.class)
@Scope(value = StroomScope.TASK)
class EntityServiceFindDeleteHandler
        extends AbstractTaskHandler<EntityServiceFindDeleteAction<BaseCriteria, SharedObject>, SharedLong> {
    private final EntityServiceBeanRegistry beanRegistry;
    private final EntityEventLog entityEventLog;

    @Inject
    EntityServiceFindDeleteHandler(final EntityServiceBeanRegistry beanRegistry, final EntityEventLog entityEventLog) {
        this.beanRegistry = beanRegistry;
        this.entityEventLog = entityEventLog;
    }

    @SuppressWarnings("unchecked")
    @Override
    public SharedLong exec(final EntityServiceFindDeleteAction<BaseCriteria, SharedObject> action) {
        Long result = null;

        final And and = new And();
        final Advanced advanced = new Advanced();
        advanced.getAdvancedQueryItems().add(and);
        final Query query = new Query();
        query.setAdvanced(advanced);

        try {
            final Object entityService = beanRegistry.getEntityService(action.getCriteria().getClass());
            if (entityService != null) {
                final SupportsCriteriaLogging<BaseCriteria> logging = (SupportsCriteriaLogging<BaseCriteria>) entityService;
                logging.appendCriteria(and.getAdvancedQueryItems(), action.getCriteria());
            }
        } catch (final Exception e) {
            // Ignore.
        }

        try {
            result = (Long) beanRegistry.invoke("findDelete", action.getCriteria());
            entityEventLog.delete(action.getCriteria(), query, result);
        } catch (final RuntimeException e) {
            entityEventLog.delete(action.getCriteria(), query, e);

            throw e;
        }

        return new SharedLong(result);
    }
}
