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

package stroom.entity;

import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.EntityServiceFindReferenceAction;
import stroom.entity.shared.ResultList;
import stroom.docref.DocRef;
import stroom.security.Security;
import stroom.task.api.AbstractTaskHandler;
import stroom.task.api.TaskHandlerBean;

import javax.inject.Inject;

@TaskHandlerBean(task = EntityServiceFindReferenceAction.class)
class EntityServiceFindReferenceHandler
        extends AbstractTaskHandler<EntityServiceFindReferenceAction<BaseEntity>, ResultList<DocRef>> {
    private final EntityServiceBeanRegistry beanRegistry;
    private final Security security;

    @Inject
    EntityServiceFindReferenceHandler(final EntityServiceBeanRegistry beanRegistry,
                                      final Security security) {
        this.beanRegistry = beanRegistry;
        this.security = security;
    }

    @SuppressWarnings("unchecked")
    @Override
    public ResultList<DocRef> exec(final EntityServiceFindReferenceAction<BaseEntity> action) {
        return security.secureResult(() -> {
            final Object entityService = beanRegistry.getEntityServiceByType(action.getEntity().getType());
            return (ResultList<DocRef>) beanRegistry.invoke(entityService, "findReference", action.getEntity());
        });
    }
}
