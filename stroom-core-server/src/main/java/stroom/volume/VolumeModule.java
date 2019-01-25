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

package stroom.volume;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.OptionalBinder;
import stroom.entity.EntityTypeBinder;
import stroom.entity.FindService;
import stroom.entity.shared.EntityEvent;
import stroom.entity.shared.EntityEvent.Handler;
import stroom.entity.shared.Clearable;
import stroom.entity.shared.Flushable;
import stroom.node.shared.VolumeEntity;
import stroom.statistics.internal.InternalStatisticsReceiver;

public class VolumeModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(VolumeService.class).to(VolumeServiceImpl.class);

        OptionalBinder.newOptionalBinder(binder(), InternalStatisticsReceiver.class);

        final Multibinder<Clearable> clearableBinder = Multibinder.newSetBinder(binder(), Clearable.class);
        clearableBinder.addBinding().to(VolumeServiceImpl.class);

        final Multibinder<Flushable> flushableBinder = Multibinder.newSetBinder(binder(), Flushable.class);
        flushableBinder.addBinding().to(VolumeServiceImpl.class);

        final Multibinder<Handler> entityEventHandlerBinder = Multibinder.newSetBinder(binder(), EntityEvent.Handler.class);
        entityEventHandlerBinder.addBinding().to(VolumeServiceImpl.class);

        EntityTypeBinder.create(binder())
                .bind(VolumeEntity.ENTITY_TYPE, VolumeServiceImpl.class);

        final Multibinder<FindService> findServiceBinder = Multibinder.newSetBinder(binder(), FindService.class);
        findServiceBinder.addBinding().to(VolumeServiceImpl.class);
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