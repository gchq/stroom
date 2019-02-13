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
import com.google.inject.multibindings.Multibinder;
import stroom.docstore.shared.Doc;
import stroom.util.entity.EntityTypeBinder;
import stroom.event.logging.api.ObjectInfoProviderBinder;
import stroom.explorer.api.ExplorerActionHandler;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.textconverter.TextConverterModule;
import stroom.pipeline.xmlschema.XmlSchemaModule;
import stroom.pipeline.xslt.XsltModule;

public class PipelineModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new TextConverterModule());
        install(new XmlSchemaModule());
        install(new XsltModule());

        bind(PipelineStore.class).to(PipelineStoreImpl.class);
        bind(LocationFactory.class).to(LocationFactoryProxy.class);

        final Multibinder<ExplorerActionHandler> explorerActionHandlerBinder = Multibinder.newSetBinder(binder(), ExplorerActionHandler.class);
        explorerActionHandlerBinder.addBinding().to(stroom.pipeline.PipelineStoreImpl.class);

        final Multibinder<ImportExportActionHandler> importExportActionHandlerBinder = Multibinder.newSetBinder(binder(), ImportExportActionHandler.class);
        importExportActionHandlerBinder.addBinding().to(stroom.pipeline.PipelineStoreImpl.class);

        EntityTypeBinder.create(binder())
                .bind(PipelineDoc.DOCUMENT_TYPE, PipelineStoreImpl.class);

        // Provide object info to the logging service.
        ObjectInfoProviderBinder.create(binder())
                .bind(Doc.class, DocObjectInfoProvider.class)
                .bind(PipelineDoc.class, PipelineDocObjectInfoProvider.class);
    }
}