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

import stroom.docstore.api.DocumentActionHandlerBinder;
import stroom.docstore.shared.Doc;
import stroom.event.logging.api.ObjectInfoProviderBinder;
import stroom.explorer.api.ExplorerActionHandler;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.util.RunnableWrapper;
import stroom.job.api.ScheduledJobsBinder;
import stroom.pipeline.destination.RollingDestinations;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.textconverter.TextConverterModule;
import stroom.pipeline.xmlschema.XmlSchemaModule;
import stroom.pipeline.xslt.XsltModule;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.RestResourcesBinder;

import com.google.inject.AbstractModule;

import javax.inject.Inject;

import static stroom.job.api.Schedule.ScheduleType.PERIODIC;

public class PipelineModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new TextConverterModule());
        install(new XmlSchemaModule());
        install(new XsltModule());

        bind(PipelineStore.class).to(PipelineStoreImpl.class);
        bind(LocationFactory.class).to(LocationFactoryProxy.class);

        GuiceUtil.buildMultiBinder(binder(), ExplorerActionHandler.class)
                .addBinding(PipelineStoreImpl.class);

        GuiceUtil.buildMultiBinder(binder(), ImportExportActionHandler.class)
                .addBinding(PipelineStoreImpl.class);

        RestResourcesBinder.create(binder())
                .bind(PipelineResourceImpl.class)
                .bind(NewUiPipelineResource.class);

        DocumentActionHandlerBinder.create(binder())
                .bind(PipelineDoc.DOCUMENT_TYPE, PipelineStoreImpl.class);

        // Provide object info to the logging service.
        ObjectInfoProviderBinder.create(binder())
                .bind(Doc.class, DocObjectInfoProvider.class)
                .bind(PipelineDoc.class, PipelineDocObjectInfoProvider.class);

        ScheduledJobsBinder.create(binder())
                .bindJobTo(PipelineDestinationRoll.class, builder -> builder
                        .withName("Pipeline Destination Roll")
                        .withDescription("Roll any destinations based on their roll settings")
                        .withSchedule(PERIODIC, "1m"));
   }

    private static class PipelineDestinationRoll extends RunnableWrapper {
        @Inject
        PipelineDestinationRoll(final RollingDestinations rollingDestinations) {
            super(rollingDestinations::roll);
        }
    }
}