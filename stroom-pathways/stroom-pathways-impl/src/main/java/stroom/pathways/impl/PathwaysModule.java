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

package stroom.pathways.impl;

import stroom.docstore.api.DocumentStoreBinder;
import stroom.job.api.ScheduledJobsBinder;
import stroom.pathways.shared.PathwaysDoc;
import stroom.pathways.shared.TracesStore;
import stroom.planb.impl.data.TracesStoreImpl;
import stroom.util.RunnableWrapper;
import stroom.util.guice.RestResourcesBinder;

import com.google.inject.AbstractModule;
import jakarta.inject.Inject;

public class PathwaysModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(TracesStore.class).to(TracesStoreImpl.class);

        DocumentStoreBinder.create(binder())
                .bind(PathwaysDoc.TYPE, PathwaysStore.class, PathwaysStoreImpl.class);

        RestResourcesBinder.create(binder())
                .bind(PathwaysResourceImpl.class)
                .bind(TracesResourceImpl.class);

        ScheduledJobsBinder.create(binder())
                .bindJobTo(ProcessPathways.class, builder -> builder
                        .name("Process Pathways")
                        .description("Job to process trace data to form pathways and/or validate traces")
                        .frequencySchedule("10m"));
    }

    private static class ProcessPathways extends RunnableWrapper {

        @Inject
        ProcessPathways(final PathwaysProcessor pathwaysProcessor) {
            super(pathwaysProcessor::exec);
        }
    }
}
