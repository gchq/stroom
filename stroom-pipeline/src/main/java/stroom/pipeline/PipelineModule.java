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

package stroom.pipeline;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import stroom.entity.CachingEntityManager;
import stroom.entity.FindService;
import stroom.explorer.ExplorerActionHandler;
import stroom.importexport.ImportExportActionHandler;
import stroom.importexport.ImportExportHelper;
import stroom.persist.EntityManagerSupport;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.TextConverter;
import stroom.pipeline.shared.XSLT;
import stroom.refdata.offheapstore.AbstractByteBufferConsumer;
import stroom.refdata.offheapstore.FastInfosetByteBufferConsumer;
import stroom.refdata.offheapstore.FastInfosetValue;
import stroom.refdata.offheapstore.RefDataOffHeapStore;
import stroom.refdata.offheapstore.RefDataStore;
import stroom.refdata.offheapstore.RefDataStoreProvider;
import stroom.refdata.offheapstore.RefDataValue;
import stroom.refdata.offheapstore.StringByteBufferConsumer;
import stroom.refdata.offheapstore.StringValue;
import stroom.refdata.offheapstore.serdes.RefDatValueSubSerde;
import stroom.refdata.offheapstore.serdes.StringValueSerde;
import stroom.refdata.offheapstore.databases.KeyValueStoreDb;
import stroom.refdata.offheapstore.databases.MapUidForwardDb;
import stroom.refdata.offheapstore.databases.MapUidReverseDb;
import stroom.refdata.offheapstore.databases.ProcessingInfoDb;
import stroom.refdata.offheapstore.databases.RangeStoreDb;
import stroom.refdata.offheapstore.databases.ValueStoreDb;
import stroom.security.SecurityContext;
import stroom.task.TaskHandler;

import javax.inject.Named;
import javax.xml.transform.URIResolver;

public class PipelineModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(PipelineService.class).to(PipelineServiceImpl.class);
        bind(XSLTService.class).to(XSLTServiceImpl.class);
        bind(TextConverterService.class).to(TextConverterServiceImpl.class);
        bind(TextConverterService.class).to(TextConverterServiceImpl.class);
        bind(URIResolver.class).to(CustomURIResolver.class);
        bind(LocationFactory.class).to(LocationFactoryProxy.class);
        bind(RefDataStore.class).toProvider(RefDataStoreProvider.class);

        final Multibinder<TaskHandler> taskHandlerBinder = Multibinder.newSetBinder(binder(), TaskHandler.class);
        taskHandlerBinder.addBinding().to(stroom.pipeline.FetchDataHandler.class);
        taskHandlerBinder.addBinding().to(stroom.pipeline.FetchDataWithPipelineHandler.class);
        taskHandlerBinder.addBinding().to(stroom.pipeline.FetchPipelineDataHandler.class);
        taskHandlerBinder.addBinding().to(stroom.pipeline.FetchPipelineXMLHandler.class);
        taskHandlerBinder.addBinding().to(stroom.pipeline.FetchPropertyTypesHandler.class);
        taskHandlerBinder.addBinding().to(stroom.pipeline.PipelineStepActionHandler.class);
        taskHandlerBinder.addBinding().to(stroom.pipeline.SavePipelineXMLHandler.class);

        final Multibinder<ExplorerActionHandler> explorerActionHandlerBinder =
                Multibinder.newSetBinder(binder(), ExplorerActionHandler.class);
        explorerActionHandlerBinder.addBinding().to(stroom.pipeline.PipelineServiceImpl.class);
        explorerActionHandlerBinder.addBinding().to(stroom.pipeline.TextConverterServiceImpl.class);
        explorerActionHandlerBinder.addBinding().to(stroom.pipeline.XSLTServiceImpl.class);

        final Multibinder<ImportExportActionHandler> importExportActionHandlerBinder =
                Multibinder.newSetBinder(binder(), ImportExportActionHandler.class);
        importExportActionHandlerBinder.addBinding().to(stroom.pipeline.PipelineServiceImpl.class);
        importExportActionHandlerBinder.addBinding().to(stroom.pipeline.TextConverterServiceImpl.class);
        importExportActionHandlerBinder.addBinding().to(stroom.pipeline.XSLTServiceImpl.class);

        final MapBinder<String, Object> entityServiceByTypeBinder =
                MapBinder.newMapBinder(binder(), String.class, Object.class);
        entityServiceByTypeBinder
                .addBinding(PipelineEntity.ENTITY_TYPE)
                .to(stroom.pipeline.PipelineServiceImpl.class);
        entityServiceByTypeBinder
                .addBinding(TextConverter.ENTITY_TYPE)
                .to(stroom.pipeline.TextConverterServiceImpl.class);
        entityServiceByTypeBinder
                .addBinding(XSLT.ENTITY_TYPE)
                .to(stroom.pipeline.XSLTServiceImpl.class);

        final Multibinder<FindService> findServiceBinder = Multibinder.newSetBinder(binder(), FindService.class);
        findServiceBinder.addBinding().to(stroom.pipeline.PipelineServiceImpl.class);
        findServiceBinder.addBinding().to(stroom.pipeline.TextConverterServiceImpl.class);
        findServiceBinder.addBinding().to(stroom.pipeline.XSLTServiceImpl.class);

        final Multibinder<RefDataValue> refDataValueBinder = Multibinder.newSetBinder(binder(), RefDataValue.class);
        refDataValueBinder.addBinding().to(stroom.refdata.offheapstore.FastInfosetValue.class);
        refDataValueBinder.addBinding().to(stroom.refdata.offheapstore.StringValue.class);

        // bind the various RefDataValue impls into a map keyed on their ID
        final MapBinder<Integer, RefDatValueSubSerde> refDataValueSerdeBinder = MapBinder.newMapBinder(
                binder(), Integer.class, RefDatValueSubSerde.class);
        refDataValueSerdeBinder
                .addBinding(FastInfosetValue.TYPE_ID)
                .to(stroom.refdata.offheapstore.FastInfoSetValueSerde.class);
        refDataValueSerdeBinder
                .addBinding(StringValue.TYPE_ID)
                .to(StringValueSerde.class);

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

    @Provides
    @Named("cachedPipelineService")
    public PipelineService cachedPipelineService(final CachingEntityManager entityManager,
                                                 final EntityManagerSupport entityManagerSupport,
                                                 final ImportExportHelper importExportHelper,
                                                 final SecurityContext securityContext) {
        return new PipelineServiceImpl(entityManager, entityManagerSupport, importExportHelper, securityContext);
    }
}