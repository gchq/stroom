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

import stroom.bytebuffer.ByteBufferModule;
import stroom.bytebuffer.PooledByteBufferOutputStream;
import stroom.job.api.ScheduledJobsBinder;
import stroom.pipeline.refdata.store.offheapstore.DelegatingRefDataOffHeapStore;
import stroom.pipeline.refdata.store.offheapstore.FastInfosetByteBufferConsumer;
import stroom.pipeline.refdata.store.offheapstore.MapDefinitionUIDStore;
import stroom.pipeline.refdata.store.offheapstore.OffHeapRefDataLoader;
import stroom.pipeline.refdata.store.offheapstore.OffHeapRefDataValueProxyConsumer;
import stroom.pipeline.refdata.store.offheapstore.RefDataLmdbEnv;
import stroom.pipeline.refdata.store.offheapstore.RefDataOffHeapStore;
import stroom.pipeline.refdata.store.offheapstore.StringByteBufferConsumer;
import stroom.pipeline.refdata.store.offheapstore.ValueStore;
import stroom.pipeline.refdata.store.offheapstore.databases.KeyValueStagingDb;
import stroom.pipeline.refdata.store.offheapstore.databases.KeyValueStoreDb;
import stroom.pipeline.refdata.store.offheapstore.databases.MapUidForwardDb;
import stroom.pipeline.refdata.store.offheapstore.databases.MapUidReverseDb;
import stroom.pipeline.refdata.store.offheapstore.databases.ProcessingInfoDb;
import stroom.pipeline.refdata.store.offheapstore.databases.RangeStoreDb;
import stroom.pipeline.refdata.store.offheapstore.databases.RangeValueStagingDb;
import stroom.pipeline.refdata.store.offheapstore.databases.ValueStoreDb;
import stroom.pipeline.refdata.store.offheapstore.databases.ValueStoreMetaDb;
import stroom.pipeline.refdata.store.onheapstore.FastInfosetValueConsumer;
import stroom.pipeline.refdata.store.onheapstore.OnHeapRefDataValueProxyConsumer;
import stroom.pipeline.refdata.store.onheapstore.StringValueConsumer;
import stroom.task.api.TaskTerminatedException;
import stroom.util.RunnableWrapper;
import stroom.util.guice.HasSystemInfoBinder;
import stroom.util.shared.scheduler.CronExpressions;

import com.google.inject.AbstractModule;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RefDataStoreModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new ByteBufferModule());

        // Bind the various RefDataValue ByteBuffer consumer factories into a map keyed on their ID
        ByteBufferConsumerBinder.create(binder())
                .bind(FastInfosetValue.TYPE_ID, FastInfosetByteBufferConsumer.Factory.class)
                .bind(StringValue.TYPE_ID, StringByteBufferConsumer.Factory.class);

        // Bind the various RefDataValue consumer factories into a map keyed on their ID
        ValueConsumerBinder.create(binder())
                .bind(FastInfosetValue.TYPE_ID, FastInfosetValueConsumer.Factory.class)
                .bind(StringValue.TYPE_ID, StringValueConsumer.Factory.class);

        // Bind the @Assisted inject factories
        install(new FactoryModuleBuilder().build(KeyValueStagingDb.Factory.class));
        install(new FactoryModuleBuilder().build(RangeValueStagingDb.Factory.class));
        install(new FactoryModuleBuilder().build(KeyValueStoreDb.Factory.class));
        install(new FactoryModuleBuilder().build(RangeStoreDb.Factory.class));
        install(new FactoryModuleBuilder().build(ValueStoreDb.Factory.class));
        install(new FactoryModuleBuilder().build(MapUidForwardDb.Factory.class));
        install(new FactoryModuleBuilder().build(MapUidReverseDb.Factory.class));
        install(new FactoryModuleBuilder().build(ProcessingInfoDb.Factory.class));
        install(new FactoryModuleBuilder().build(ValueStoreMetaDb.Factory.class));
        install(new FactoryModuleBuilder().build(ValueStore.Factory.class));
        install(new FactoryModuleBuilder().build(MapDefinitionUIDStore.Factory.class));
        install(new FactoryModuleBuilder().build(OffHeapRefDataLoader.Factory.class));
        install(new FactoryModuleBuilder().build(OffHeapRefDataValueProxyConsumer.Factory.class));
        install(new FactoryModuleBuilder().build(OnHeapRefDataValueProxyConsumer.Factory.class));
        install(new FactoryModuleBuilder().build(PooledByteBufferOutputStream.Factory.class));
        install(new FactoryModuleBuilder().build(RefDataValueProxyConsumerFactory.Factory.class));
        install(new FactoryModuleBuilder().build(RefDataLmdbEnv.Factory.class));
        install(new FactoryModuleBuilder().build(RefDataOffHeapStore.Factory.class));

        bind(ValueStoreHashAlgorithm.class).to(XxHashValueStoreHashAlgorithm.class);

        HasSystemInfoBinder.create(binder())
                .bind(DelegatingRefDataOffHeapStore.class);

        ScheduledJobsBinder.create(binder())
                .bindJobTo(RefDataPurge.class, builder -> builder
                        .name(RefDataPurge.JOB_NAME)
                        .description("Purge old and partial reference data loads from the off heap store as " +
                                "configured by 'purgeAge'.")
                        .cronSchedule(CronExpressions.EVERY_DAY_AT_2AM.getExpression()));
    }


    // --------------------------------------------------------------------------------


    public static class RefDataPurge extends RunnableWrapper {

        private static final Logger LOGGER = LoggerFactory.getLogger(RefDataPurge.class);
        public static final String JOB_NAME = "Ref Data Off-heap Store Purge";

        @Inject
        RefDataPurge(final RefDataStoreFactory refDataStoreFactory) {

            super(() -> {
                try {
                    LOGGER.info("Running job '{}'", JOB_NAME);
                    refDataStoreFactory.purgeOldData();
                } catch (final TaskTerminatedException e) {
                    LOGGER.debug("Reference Data Purge terminated", e);
                    LOGGER.warn("Reference Data Purge terminated");
                }
            });
        }
    }
}
