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
import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.EntityServiceFindAction;
import stroom.entity.shared.ResultList;
import stroom.logging.EntityEventLog;
import stroom.pool.SecurityHelper;
import stroom.security.SecurityContext;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.shared.SharedObject;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;

@TaskHandlerBean(task = EntityServiceFindAction.class)
@Scope(value = StroomScope.TASK)
class EntityServiceFindHandler
        extends AbstractTaskHandler<EntityServiceFindAction<BaseCriteria, SharedObject>, ResultList<SharedObject>> {
    private final EntityServiceBeanRegistry beanRegistry;
    private final EntityEventLog entityEventLog;
    private final SecurityContext securityContext;

    @Inject
    EntityServiceFindHandler(final EntityServiceBeanRegistry beanRegistry,
                             final EntityEventLog entityEventLog,
                             final SecurityContext securityContext) {
        this.beanRegistry = beanRegistry;
        this.entityEventLog = entityEventLog;
        this.securityContext = securityContext;
    }

    @SuppressWarnings("unchecked")
    @Override
    public ResultList<SharedObject> exec(final EntityServiceFindAction<BaseCriteria, SharedObject> action) {
        BaseResultList<SharedObject> result;

        final Query query = new Query();
        try (final SecurityHelper securityHelper = SecurityHelper.elevate(securityContext)) {
            final And and = new And();
            final Advanced advanced = new Advanced();
            advanced.getAdvancedQueryItems().add(and);
            query.setAdvanced(advanced);

            final Object entityService = beanRegistry.getEntityService(action.getCriteria().getClass());
            if (entityService != null) {
                final SupportsCriteriaLogging<BaseCriteria> logging = (SupportsCriteriaLogging<BaseCriteria>) entityService;
                logging.appendCriteria(and.getAdvancedQueryItems(), action.getCriteria());
            }
        } catch (final Exception e) {
            // Ignore.
        }

        try {
            result = (BaseResultList<SharedObject>) beanRegistry.invoke("find", action.getCriteria());
            entityEventLog.search(action.getCriteria(), query, result);
        } catch (final RuntimeException e) {
            entityEventLog.search(action.getCriteria(), query, e);

            throw e;
        }

        return result;
    }
}
