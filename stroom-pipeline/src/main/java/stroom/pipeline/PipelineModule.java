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

package stroom.pipeline;

import stroom.docref.DocRef;
import stroom.docstore.api.DocumentStore;
import stroom.docstore.api.DocumentStoreBinder;
import stroom.docstore.shared.AbstractDoc;
import stroom.event.logging.api.ObjectInfoProviderBinder;
import stroom.job.api.ScheduledJobsBinder;
import stroom.lifecycle.api.LifecycleBinder;
import stroom.pipeline.destination.RollingDestinations;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.stepping.store.StepDataStoreManager;
import stroom.pipeline.textconverter.TextConverterModule;
import stroom.pipeline.xmlschema.XmlSchemaModule;
import stroom.pipeline.xslt.XsltModule;
import stroom.util.RunnableWrapper;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.RestResourcesBinder;

import com.google.inject.AbstractModule;
import jakarta.inject.Inject;

public class PipelineModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new TextConverterModule());
        install(new XmlSchemaModule());
        install(new XsltModule());

        bind(PipelineService.class).to(PipelineServiceImpl.class);
        bind(LocationFactory.class).to(LocationFactoryProxy.class);

        DocumentStoreBinder.create(binder())
                .bind(PipelineDoc.TYPE, PipelineStore.class, PipelineStoreImpl.class);

        GuiceUtil.buildMultiBinder(binder(), DocumentStore.class)
                .addBinding(PipelineStoreImpl.class);

        RestResourcesBinder.create(binder())
                .bind(PipelineResourceImpl.class);

        // Provide object info to the logging service.
        ObjectInfoProviderBinder.create(binder())
                .bind(AbstractDoc.class, DocObjectInfoProvider.class)
                .bind(DocRef.class, DocRefObjectInfoProvider.class)
                .bind(PipelineDoc.class, PipelineDocObjectInfoProvider.class);

        ScheduledJobsBinder.create(binder())
                .bindJobTo(PipelineDestinationRoll.class, builder -> builder
                        .name("Pipeline Destination Roll")
                        .description("Roll any destinations based on their roll settings")
                        .frequencySchedule("1m"))
                .bindJobTo(SteppingStoreCleanup.class, builder -> builder
                        .name("Stepping Store Cleanup")
                        .description("Delete orphaned pipeline stepping data left in the temp directory by " +
                                     "sessions that did not shut down cleanly, as configured by " +
                                     "'orphanMaxAge'.")
                        .managed(false)
                        .frequencySchedule("1h"));

        LifecycleBinder.create(binder())
                .bindShutdownTaskTo(RollingDestinationsForceRoll.class)
                .bindShutdownTaskTo(SteppingStoreShutdown.class);
    }

    private static class PipelineDestinationRoll extends RunnableWrapper {

        @Inject
        PipelineDestinationRoll(final RollingDestinations rollingDestinations) {
            super(rollingDestinations::roll);
        }
    }

    private static class RollingDestinationsForceRoll extends RunnableWrapper {

        @Inject
        RollingDestinationsForceRoll(final RollingDestinations rollingDestinations) {
            super(rollingDestinations::forceRoll);
        }
    }

    private static class SteppingStoreCleanup extends RunnableWrapper {

        @Inject
        SteppingStoreCleanup(final StepDataStoreManager stepDataStoreManager) {
            super(stepDataStoreManager::cleanupOrphans);
        }
    }

    private static class SteppingStoreShutdown extends RunnableWrapper {

        @Inject
        SteppingStoreShutdown(final StepDataStoreManager stepDataStoreManager) {
            super(stepDataStoreManager::deleteAllSessions);
        }
    }
}
