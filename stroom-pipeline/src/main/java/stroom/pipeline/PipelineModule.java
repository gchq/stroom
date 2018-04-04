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
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import stroom.entity.CachingEntityManager;
import stroom.entity.FindService;
import stroom.explorer.ExplorerActionHandler;
import stroom.importexport.ImportExportActionHandler;
import stroom.importexport.ImportExportHelper;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.XSLT;
import stroom.security.SecurityContext;
import stroom.persist.EntityManagerSupport;
import stroom.task.TaskHandler;

import javax.inject.Named;
import javax.xml.transform.URIResolver;

public class PipelineModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(PipelineService.class).to(PipelineServiceImpl.class);
        bind(XSLTService.class).to(XSLTServiceImpl.class);
        bind(TextConverterStore.class).to(TextConverterStoreImpl.class);
        bind(TextConverterStore.class).to(TextConverterStoreImpl.class);
        bind(URIResolver.class).to(CustomURIResolver.class);
        bind(LocationFactory.class).to(LocationFactoryProxy.class);

        final Multibinder<TaskHandler> taskHandlerBinder = Multibinder.newSetBinder(binder(), TaskHandler.class);
        taskHandlerBinder.addBinding().to(stroom.pipeline.FetchDataHandler.class);
        taskHandlerBinder.addBinding().to(stroom.pipeline.FetchDataWithPipelineHandler.class);
        taskHandlerBinder.addBinding().to(stroom.pipeline.FetchPipelineDataHandler.class);
        taskHandlerBinder.addBinding().to(stroom.pipeline.FetchPipelineXMLHandler.class);
        taskHandlerBinder.addBinding().to(stroom.pipeline.FetchPropertyTypesHandler.class);
        taskHandlerBinder.addBinding().to(stroom.pipeline.PipelineStepActionHandler.class);
        taskHandlerBinder.addBinding().to(stroom.pipeline.SavePipelineXMLHandler.class);

        final Multibinder<ExplorerActionHandler> explorerActionHandlerBinder = Multibinder.newSetBinder(binder(), ExplorerActionHandler.class);
        explorerActionHandlerBinder.addBinding().to(stroom.pipeline.PipelineServiceImpl.class);
        explorerActionHandlerBinder.addBinding().to(stroom.pipeline.TextConverterStoreImpl.class);
        explorerActionHandlerBinder.addBinding().to(stroom.pipeline.XSLTServiceImpl.class);

        final Multibinder<ImportExportActionHandler> importExportActionHandlerBinder = Multibinder.newSetBinder(binder(), ImportExportActionHandler.class);
        importExportActionHandlerBinder.addBinding().to(stroom.pipeline.PipelineServiceImpl.class);
        importExportActionHandlerBinder.addBinding().to(stroom.pipeline.TextConverterStoreImpl.class);
        importExportActionHandlerBinder.addBinding().to(stroom.pipeline.XSLTServiceImpl.class);

        final MapBinder<String, Object> entityServiceByTypeBinder = MapBinder.newMapBinder(binder(), String.class, Object.class);
        entityServiceByTypeBinder.addBinding(PipelineEntity.ENTITY_TYPE).to(stroom.pipeline.PipelineServiceImpl.class);
        entityServiceByTypeBinder.addBinding(TextConverterDoc.ENTITY_TYPE).to(stroom.pipeline.TextConverterStoreImpl.class);
        entityServiceByTypeBinder.addBinding(XSLT.ENTITY_TYPE).to(stroom.pipeline.XSLTServiceImpl.class);

        final Multibinder<FindService> findServiceBinder = Multibinder.newSetBinder(binder(), FindService.class);
        findServiceBinder.addBinding().to(stroom.pipeline.PipelineServiceImpl.class);
//        findServiceBinder.addBinding().to(stroom.pipeline.TextConverterStoreImpl.class);
        findServiceBinder.addBinding().to(stroom.pipeline.XSLTServiceImpl.class);
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