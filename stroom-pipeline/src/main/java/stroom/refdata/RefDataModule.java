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

package stroom.refdata;

import com.google.inject.AbstractModule;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.multibindings.MapBinder;
import stroom.properties.StroomPropertyService;
import stroom.refdata.offheapstore.AbstractByteBufferConsumer;
import stroom.refdata.offheapstore.FastInfosetByteBufferConsumer;
import stroom.refdata.offheapstore.FastInfosetValue;
import stroom.refdata.offheapstore.RefDataOffHeapStore;
import stroom.refdata.offheapstore.RefDataStore;
import stroom.refdata.offheapstore.RefDataStoreProvider;
import stroom.refdata.offheapstore.StringByteBufferConsumer;
import stroom.refdata.offheapstore.StringValue;
import stroom.refdata.offheapstore.databases.KeyValueStoreDb;
import stroom.refdata.offheapstore.databases.MapUidForwardDb;
import stroom.refdata.offheapstore.databases.MapUidReverseDb;
import stroom.refdata.offheapstore.databases.ProcessingInfoDb;
import stroom.refdata.offheapstore.databases.RangeStoreDb;
import stroom.refdata.offheapstore.databases.ValueStoreDb;
import stroom.refdata.offheapstore.serdes.FastInfoSetValueSerde;
import stroom.refdata.offheapstore.serdes.RefDatValueSubSerde;
import stroom.refdata.offheapstore.serdes.StringValueSerde;

public class RefDataModule extends AbstractModule {
    @Override
    protected void configure() {
        //deps from other modules
        requireBinding(StroomPropertyService.class);

        bind(RefDataStore.class).toProvider(RefDataStoreProvider.class);

//        final Multibinder<RefDataValue> refDataValueBinder = Multibinder.newSetBinder(binder(), RefDataValue.class);
//        refDataValueBinder.addBinding().to(stroom.refdata.offheapstore.FastInfosetValue.class);
//        refDataValueBinder.addBinding().to(stroom.refdata.offheapstore.StringValue.class);

        // bind the various RefDataValue impls into a map keyed on their ID
        final MapBinder<Integer, RefDatValueSubSerde> refDataValueSerdeBinder = MapBinder.newMapBinder(
                binder(), Integer.class, RefDatValueSubSerde.class);
        refDataValueSerdeBinder.addBinding(FastInfosetValue.TYPE_ID).to(FastInfoSetValueSerde.class);
        refDataValueSerdeBinder.addBinding(StringValue.TYPE_ID).to(StringValueSerde.class);

        // bind the various RefDataValue ByteBuffer consumer factories into a map keyed on their ID
        final MapBinder<Integer, AbstractByteBufferConsumer.Factory> refDataValueByteBufferConsumerBinder = MapBinder.newMapBinder(
                binder(), Integer.class, AbstractByteBufferConsumer.Factory.class);
        refDataValueByteBufferConsumerBinder
                .addBinding(FastInfosetValue.TYPE_ID)
                .to(FastInfosetByteBufferConsumer.Factory.class);
        refDataValueByteBufferConsumerBinder
                .addBinding(StringValue.TYPE_ID)
                .to(StringByteBufferConsumer.Factory.class);

        // bind all the reference data off heap tables
        install(new FactoryModuleBuilder().build(KeyValueStoreDb.Factory.class));
        install(new FactoryModuleBuilder().build(RangeStoreDb.Factory.class));
        install(new FactoryModuleBuilder().build(ValueStoreDb.Factory.class));
        install(new FactoryModuleBuilder().build(MapUidForwardDb.Factory.class));
        install(new FactoryModuleBuilder().build(MapUidReverseDb.Factory.class));
        install(new FactoryModuleBuilder().build(ProcessingInfoDb.Factory.class));

        install(new FactoryModuleBuilder()
                .implement(RefDataStore.class, RefDataOffHeapStore.class)
                .build(RefDataOffHeapStore.Factory.class));
    }
}
