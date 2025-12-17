/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.core.entity.event;

import stroom.lifecycle.api.LifecycleBinder;
import stroom.util.RunnableWrapper;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventBus;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.RestResourcesBinder;

import com.google.inject.AbstractModule;
import jakarta.inject.Inject;

public class EntityEventModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(EntityEventBus.class).to(EntityEventBusImpl.class);
        bind(EntityEventResource.class).to(EntityEventResourceImpl.class);

        // Ensure the multibinder is created.
        GuiceUtil.buildMultiBinder(binder(), EntityEvent.Handler.class);

        RestResourcesBinder.create(binder())
                .bind(EntityEventResourceImpl.class);

        LifecycleBinder.create(binder())
                .bindStartupTaskTo(EntityEventBusInit.class);
    }

    private static class EntityEventBusInit extends RunnableWrapper {

        @Inject
        EntityEventBusInit(final EntityEventBusImpl entityEventBus) {
            super(entityEventBus::init);
        }
    }
}
