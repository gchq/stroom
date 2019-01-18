/*
 * Copyright 2018 Crown Copyright
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

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.Clearable;
import stroom.entity.shared.EntityServiceDeleteAction;
import stroom.entity.shared.EntityServiceFindAction;
import stroom.entity.shared.EntityServiceFindDeleteAction;
import stroom.entity.shared.EntityServiceFindReferenceAction;
import stroom.entity.shared.EntityServiceFindSummaryAction;
import stroom.entity.shared.EntityServiceLoadAction;
import stroom.entity.shared.EntityServiceSaveAction;
import stroom.event.logging.api.ObjectInfoProviderBinder;
import stroom.task.api.TaskHandlerBinder;

public class EntityModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(GenericEntityService.class).to(GenericEntityServiceImpl.class);

        final Multibinder<Clearable> clearableBinder = Multibinder.newSetBinder(binder(), Clearable.class);
        clearableBinder.addBinding().to(CachingEntityManager.class);

        TaskHandlerBinder.create(binder())
                .bind(EntityServiceDeleteAction.class, stroom.entity.EntityServiceDeleteHandler.class)
                .bind(EntityServiceFindDeleteAction.class, stroom.entity.EntityServiceFindDeleteHandler.class)
                .bind(EntityServiceFindAction.class, stroom.entity.EntityServiceFindHandler.class)
                .bind(EntityServiceFindReferenceAction.class, stroom.entity.EntityServiceFindReferenceHandler.class)
                .bind(EntityServiceFindSummaryAction.class, stroom.entity.EntityServiceFindSummaryHandler.class)
                .bind(EntityServiceSaveAction.class, stroom.entity.EntityServiceSaveHandler.class)
                .bind(EntityServiceLoadAction.class, stroom.entity.EntityServiceLoadHandler.class);

        // Provide object info to the logging service.
        ObjectInfoProviderBinder.create(binder())
                .bind(BaseEntity.class, BaseEntityObjectInfoProvider.class)
                .bind(BaseCriteria.class, BaseCriteriaObjectInfoProvider.class);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return 0;
    }
}