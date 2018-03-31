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

package stroom.entity;

import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.EntityServiceException;
import stroom.entity.shared.EntityServiceLoadAction;
import stroom.logging.DocumentEventLog;
import stroom.security.Security;
import stroom.task.AbstractTaskHandler;
import stroom.task.TaskHandlerBean;

import javax.inject.Inject;

@TaskHandlerBean(task = EntityServiceLoadAction.class)
class EntityServiceLoadHandler extends AbstractTaskHandler<EntityServiceLoadAction<BaseEntity>, BaseEntity> {
    private final EntityServiceBeanRegistry beanRegistry;
    private final DocumentEventLog entityEventLog;
    private final Security security;

    @Inject
    EntityServiceLoadHandler(final EntityServiceBeanRegistry beanRegistry,
                             final DocumentEventLog entityEventLog,
                             final Security security) {
        this.beanRegistry = beanRegistry;
        this.entityEventLog = entityEventLog;
        this.security = security;
    }

    @SuppressWarnings("unchecked")
    @Override
    public BaseEntity exec(final EntityServiceLoadAction<BaseEntity> action) {
        return security.secureResult(() -> {
            final Object bean = beanRegistry.getEntityServiceByType(action.getEntity().getType());
            if (bean == null) {
                throw new EntityServiceException("No entity service can be found");
            }
            if (!(bean instanceof EntityService<?>)) {
                throw new EntityServiceException("Bean is not an entity service");
            }

            final EntityService<BaseEntity> entityService = (EntityService<BaseEntity>) bean;
            final BaseEntity entity = action.getEntity();

            BaseEntity result;
            try {
                result = entityService.load(entity);
                entityEventLog.view(result, null);
            } catch (final RuntimeException e) {
                entityEventLog.view(entity, e);
                throw e;
            }

            return result;
        });
    }
}
