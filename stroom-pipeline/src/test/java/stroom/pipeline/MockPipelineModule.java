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
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;
import stroom.importexport.ImportExportActionHandler;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.XsltDoc;

import javax.xml.transform.URIResolver;

public class MockPipelineModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(PipelineStore.class).to(PipelineStoreImpl.class);
        bind(XsltStore.class).to(XsltStoreImpl.class);
        bind(TextConverterStore.class).to(TextConverterStoreImpl.class);
        bind(URIResolver.class).to(CustomURIResolver.class);
        bind(LocationFactory.class).to(LocationFactoryProxy.class);
//        bind(PipelineStore.class).annotatedWith(Names.named("cachedPipelineStore")).to(PipelineStoreImpl.class);

        // TODO : @66 FIX PLACES THAT USE PIPELINE CACHING
        bind(PipelineStore.class).annotatedWith(Names.named("cachedPipelineStore")).to(PipelineStoreImpl.class);

//        final Multibinder<Clearable> clearableBinder = Multibinder.newSetBinder(binder(), Clearable.class);
//        clearableBinder.addBinding().to(PipelineStoreImpl.class);
//        clearableBinder.addBinding().to(XsltStoreImpl.class);
//        clearableBinder.addBinding().to(TextConverterStoreImpl.class);

//
//        final Multibinder<TaskHandler> taskHandlerBinder = Multibinder.newSetBinder(binder(), TaskHandler.class);
//        taskHandlerBinder.addBinding().to(FetchDataHandler.class);
//        taskHandlerBinder.addBinding().to(FetchDataWithPipelineHandler.class);
//        taskHandlerBinder.addBinding().to(FetchPipelineDataHandler.class);
//        taskHandlerBinder.addBinding().to(FetchPipelineXMLHandler.class);
//        taskHandlerBinder.addBinding().to(FetchPropertyTypesHandler.class);
//        taskHandlerBinder.addBinding().to(PipelineStepActionHandler.class);
//        taskHandlerBinder.addBinding().to(SavePipelineXMLHandler.class);
//
//        final Multibinder<ExplorerActionHandler> explorerActionHandlerBinder = Multibinder.newSetBinder(binder(), ExplorerActionHandler.class);
//        explorerActionHandlerBinder.addBinding().to(PipelineStoreImpl.class);
//        explorerActionHandlerBinder.addBinding().to(TextConverterServiceImpl.class);
//        explorerActionHandlerBinder.addBinding().to(XsltStoreImpl.class);

        final Multibinder<ImportExportActionHandler> importExportActionHandlerBinder = Multibinder.newSetBinder(binder(), ImportExportActionHandler.class);
        importExportActionHandlerBinder.addBinding().to(PipelineStoreImpl.class);
        importExportActionHandlerBinder.addBinding().to(XsltStoreImpl.class);
        importExportActionHandlerBinder.addBinding().to(TextConverterStoreImpl.class);

        final MapBinder<String, Object> entityServiceByTypeBinder = MapBinder.newMapBinder(binder(), String.class, Object.class);
        entityServiceByTypeBinder.addBinding(PipelineDoc.DOCUMENT_TYPE).to(PipelineStoreImpl.class);
        entityServiceByTypeBinder.addBinding(TextConverterDoc.DOCUMENT_TYPE).to(TextConverterStoreImpl.class);
        entityServiceByTypeBinder.addBinding(XsltDoc.DOCUMENT_TYPE).to(XsltStoreImpl.class);

//        final Multibinder<FindService> findServiceBinder = Multibinder.newSetBinder(binder(), FindService.class);
//        findServiceBinder.addBinding().to(PipelineStoreImpl.class);
//        findServiceBinder.addBinding().to(TextConverterStoreImpl.class);
//        findServiceBinder.addBinding().to(XsltStoreImpl.class);
    }

//    @Provides
//    @Named("cachedPipelineStore")
//    public PipelineStore cachedPipelineStore(final CachingEntityManager entityManager,
//                                                 final EntityManagerSupport entityManagerSupport,
//                                                 final ImportExportHelper importExportHelper,
//                                                 final SecurityContext securityContext) {
//        return new PipelineStoreImpl(entityManager, entityManagerSupport, importExportHelper, securityContext);
//    }
}