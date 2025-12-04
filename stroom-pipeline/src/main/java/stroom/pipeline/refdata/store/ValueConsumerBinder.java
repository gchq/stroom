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

package stroom.pipeline.refdata.store;

import stroom.pipeline.refdata.store.onheapstore.RefDataValueConsumer;

import com.google.inject.Binder;
import com.google.inject.multibindings.MapBinder;

public class ValueConsumerBinder {

    private final MapBinder<ValueConsumerId, RefDataValueConsumer.Factory> mapBinder;

    private ValueConsumerBinder(final Binder binder) {
        mapBinder = MapBinder.newMapBinder(binder, ValueConsumerId.class, RefDataValueConsumer.Factory.class);
    }

    public static ValueConsumerBinder create(final Binder binder) {
        return new ValueConsumerBinder(binder);
    }

    public <F extends RefDataValueConsumer.Factory> ValueConsumerBinder bind(final byte id, final Class<F> handler) {
        mapBinder.addBinding(new ValueConsumerId(id)).to(handler);
        return this;
    }
}
