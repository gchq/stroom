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

import stroom.pipeline.refdata.RefDataValueByteBufferConsumer;

import com.google.inject.Binder;
import com.google.inject.multibindings.MapBinder;

public class ByteBufferConsumerBinder {

    private final MapBinder<ByteBufferConsumerId, RefDataValueByteBufferConsumer.Factory> mapBinder;

    private ByteBufferConsumerBinder(final Binder binder) {
        mapBinder = MapBinder.newMapBinder(binder,
                ByteBufferConsumerId.class,
                RefDataValueByteBufferConsumer.Factory.class);
    }

    public static ByteBufferConsumerBinder create(final Binder binder) {
        return new ByteBufferConsumerBinder(binder);
    }

    public <F extends RefDataValueByteBufferConsumer.Factory> ByteBufferConsumerBinder bind(final byte id,
                                                                                            final Class<F> handler) {
        mapBinder.addBinding(new ByteBufferConsumerId(id)).to(handler);
        return this;
    }
}
