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

import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.DocRef;
import stroom.entity.shared.EntityServiceLoadAction;
import stroom.logging.EntityEventLog;
import stroom.security.SecurityContext;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;

import javax.inject.Inject;

@TaskHandlerBean(task = EntityServiceLoadAction.class)
class EntityServiceLoadHandler
        extends AbstractTaskHandler<EntityServiceLoadAction<BaseEntity>, BaseEntity> {
    private final GenericEntityService entityService;
    private final EntityEventLog entityEventLog;

    @Inject
    EntityServiceLoadHandler(final GenericEntityService entityService, final EntityEventLog entityEventLog) {
        this.entityService = entityService;
        this.entityEventLog = entityEventLog;
    }

    @Override
    public BaseEntity exec(final EntityServiceLoadAction<BaseEntity> action) {
        BaseEntity result = null;

        try {
            final DocRef docRef = action.getDocRef();
            if (docRef != null && docRef.getType() != null && docRef.getType().length() > 0) {
                if (docRef.getUuid() != null && docRef.getUuid().length() > 0) {
                    result = entityService.loadByUuid(docRef.getType(), docRef.getUuid(), action.getFetchSet());
                }

                if (result == null && docRef.getId() != null) {
                    result = entityService.loadById(docRef.getType(), docRef.getId(), action.getFetchSet());
                }
            }

            if (result != null) {
                entityEventLog.view(result);
            }
        } catch (final RuntimeException e) {
            throw e;
        }

        return result;
    }
}
