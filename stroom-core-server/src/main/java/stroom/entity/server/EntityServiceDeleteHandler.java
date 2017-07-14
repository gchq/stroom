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

import org.springframework.context.annotation.Scope;
import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.EntityServiceDeleteAction;
import stroom.dashboard.server.logging.EntityEventLog;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;

@TaskHandlerBean(task = EntityServiceDeleteAction.class)
@Scope(value = StroomScope.TASK)
class EntityServiceDeleteHandler extends AbstractTaskHandler<EntityServiceDeleteAction<BaseEntity>, BaseEntity> {
    private final GenericEntityService entityService;
    private final EntityEventLog entityEventLog;

    @Inject
    EntityServiceDeleteHandler(final GenericEntityService entityService, final EntityEventLog entityEventLog) {
        this.entityService = entityService;
        this.entityEventLog = entityEventLog;
    }

    @Override
    public BaseEntity exec(final EntityServiceDeleteAction<BaseEntity> action) {
        final BaseEntity entity = action.getEntity();
        try {
            entityService.delete(entity);
            if (entity != null) {
                entityEventLog.delete(entity);
            }
        } catch (final RuntimeException e) {
            if (entity != null) {
                entityEventLog.delete(entity, e);
            }
            throw e;
        }

        return entity;
    }
}
