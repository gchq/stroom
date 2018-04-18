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
import stroom.entity.shared.EntityServiceSaveAction;
import stroom.logging.DocumentEventLog;
import stroom.security.Security;
import stroom.task.AbstractTaskHandler;
import stroom.task.TaskHandlerBean;

import javax.inject.Inject;

@TaskHandlerBean(task = EntityServiceSaveAction.class)
class EntityServiceSaveHandler extends AbstractTaskHandler<EntityServiceSaveAction<BaseEntity>, BaseEntity> {
    private final EntityServiceBeanRegistry beanRegistry;
    private final DocumentEventLog entityEventLog;
    private final Security security;

    @Inject
    EntityServiceSaveHandler(final EntityServiceBeanRegistry beanRegistry,
                             final DocumentEventLog entityEventLog,
                             final Security security) {
        this.beanRegistry = beanRegistry;
        this.entityEventLog = entityEventLog;
        this.security = security;
    }

    @SuppressWarnings("unchecked")
    @Override
    public BaseEntity exec(final EntityServiceSaveAction<BaseEntity> action) {
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
            final boolean persistent = entity.isPersistent();

            BaseEntity result = null;

            try {
                if (persistent) {
                    // Get the before version.
                    final BaseEntity before = entityService.load(entity);

//                // Validate the entity name.
//                NameValidationUtil.validate(entityService, before, entity);

                    result = entityService.save(entity);
                    entityEventLog.update(before, result, null);

                } else {
//                // Validate the entity name.
//                NameValidationUtil.validate(entityService, entity);

                    result = entityService.save(entity);
                    entityEventLog.create(result, null);
                }
            } catch (final RuntimeException e) {
                if (persistent) {
                    // Get the before version.
                    entityEventLog.update(null, entity, e);
                } else {
                    entityEventLog.create(entity, e);
                }
                throw e;
            }

            return result;
        });
    }
}
