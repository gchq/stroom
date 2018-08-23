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
 *
 */

package stroom.refdata.store;

import com.google.inject.AbstractModule;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.multibindings.MapBinder;
import stroom.properties.api.PropertyService;
import stroom.refdata.RefDataValueByteBufferConsumer;
import stroom.refdata.store.offheapstore.FastInfosetByteBufferConsumer;
import stroom.refdata.store.offheapstore.OffHeapRefDataValueProxyConsumer;
import stroom.refdata.store.offheapstore.RefDataOffHeapStore;
import stroom.refdata.store.offheapstore.StringByteBufferConsumer;
import stroom.refdata.store.offheapstore.databases.KeyValueStoreDb;
import stroom.refdata.store.offheapstore.databases.MapUidForwardDb;
import stroom.refdata.store.offheapstore.databases.MapUidReverseDb;
import stroom.refdata.store.offheapstore.databases.ProcessingInfoDb;
import stroom.refdata.store.offheapstore.databases.RangeStoreDb;
import stroom.refdata.store.offheapstore.databases.ValueStoreDb;
import stroom.refdata.store.offheapstore.databases.ValueStoreMetaDb;
import stroom.refdata.store.onheapstore.FastInfosetValueConsumer;
import stroom.refdata.store.onheapstore.OnHeapRefDataValueProxyConsumer;
import stroom.refdata.store.onheapstore.RefDataValueConsumer;
import stroom.refdata.store.onheapstore.StringValueConsumer;
import stroom.refdata.util.PooledByteBufferOutputStream;

public class RefDataStoreModule extends AbstractModule {

    @Override
    protected void configure() {
        //deps from other modules
        requireBinding(PropertyService.class);

        // bind the various RefDataValue ByteBuffer consumer factories into a map keyed on their ID
        final MapBinder<Integer, RefDataValueByteBufferConsumer.Factory> refDataValueByteBufferConsumerBinder = MapBinder.newMapBinder(
                binder(), Integer.class, RefDataValueByteBufferConsumer.Factory.class);

        refDataValueByteBufferConsumerBinder
                .addBinding(FastInfosetValue.TYPE_ID)
                .to(FastInfosetByteBufferConsumer.Factory.class);

        refDataValueByteBufferConsumerBinder
                .addBinding(StringValue.TYPE_ID)
                .to(StringByteBufferConsumer.Factory.class);

        // bind the various RefDataValue consumer factories into a map keyed on their ID
        final MapBinder<Integer, RefDataValueConsumer.Factory> refDataValueConsumerBinder = MapBinder.newMapBinder(
                binder(), Integer.class, RefDataValueConsumer.Factory.class);

        refDataValueConsumerBinder
                .addBinding(FastInfosetValue.TYPE_ID)
                .to(FastInfosetValueConsumer.Factory.class);

        refDataValueConsumerBinder
                .addBinding(StringValue.TYPE_ID)
                .to(StringValueConsumer.Factory.class);

        // bind all the reference data off heap tables
        install(new FactoryModuleBuilder().build(KeyValueStoreDb.Factory.class));
        install(new FactoryModuleBuilder().build(RangeStoreDb.Factory.class));
        install(new FactoryModuleBuilder().build(ValueStoreDb.Factory.class));
        install(new FactoryModuleBuilder().build(MapUidForwardDb.Factory.class));
        install(new FactoryModuleBuilder().build(MapUidReverseDb.Factory.class));
        install(new FactoryModuleBuilder().build(ProcessingInfoDb.Factory.class));
        install(new FactoryModuleBuilder().build(ValueStoreMetaDb.Factory.class));

        install(new FactoryModuleBuilder().build(OffHeapRefDataValueProxyConsumer.Factory.class));
        install(new FactoryModuleBuilder().build(OnHeapRefDataValueProxyConsumer.Factory.class));
        install(new FactoryModuleBuilder().build(PooledByteBufferOutputStream.Factory.class));

        install(new FactoryModuleBuilder()
                .implement(RefDataStore.class, RefDataOffHeapStore.class)
                .build(RefDataOffHeapStore.Factory.class));

        install(new FactoryModuleBuilder().build(RefDataValueProxyConsumerFactory.Factory.class));
    }
}
