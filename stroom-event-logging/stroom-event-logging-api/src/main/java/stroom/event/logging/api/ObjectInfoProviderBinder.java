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

package stroom.event.logging.api;

import com.google.inject.Binder;
import com.google.inject.multibindings.MapBinder;

public class ObjectInfoProviderBinder {

    private final MapBinder<ObjectType, ObjectInfoProvider> mapBinder;

    private ObjectInfoProviderBinder(final Binder binder) {
        mapBinder = MapBinder.newMapBinder(binder, ObjectType.class, ObjectInfoProvider.class);
    }

    public static ObjectInfoProviderBinder create(final Binder binder) {
        return new ObjectInfoProviderBinder(binder);
    }

    public <H extends ObjectInfoProvider> ObjectInfoProviderBinder bind(final Class<?> task, final Class<H> handler) {
        mapBinder.addBinding(new ObjectType(task)).to(handler);
        return this;
    }
}
