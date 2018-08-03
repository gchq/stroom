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
import stroom.entity.shared.Clearable;
import stroom.logging.LoggingModule;
import stroom.task.api.TaskHandler;

public class EntityModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new LoggingModule());

        bind(GenericEntityService.class).to(GenericEntityServiceImpl.class);

        final Multibinder<Clearable> clearableBinder = Multibinder.newSetBinder(binder(), Clearable.class);
        clearableBinder.addBinding().to(CachingEntityManager.class);

        final Multibinder<TaskHandler> taskHandlerBinder = Multibinder.newSetBinder(binder(), TaskHandler.class);
        taskHandlerBinder.addBinding().to(stroom.entity.EntityServiceDeleteHandler.class);
        taskHandlerBinder.addBinding().to(stroom.entity.EntityServiceFindDeleteHandler.class);
        taskHandlerBinder.addBinding().to(stroom.entity.EntityServiceFindHandler.class);
        taskHandlerBinder.addBinding().to(stroom.entity.EntityServiceFindReferenceHandler.class);
        taskHandlerBinder.addBinding().to(stroom.entity.EntityServiceFindSummaryHandler.class);
        taskHandlerBinder.addBinding().to(stroom.entity.EntityServiceSaveHandler.class);
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