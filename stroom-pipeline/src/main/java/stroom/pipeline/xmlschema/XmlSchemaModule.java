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

package stroom.pipeline.xmlschema;

import stroom.docstore.api.ContentIndexable;
import stroom.docstore.api.DocumentActionHandlerBinder;
import stroom.explorer.api.ExplorerActionHandler;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.job.api.ScheduledJobsBinder;
import stroom.util.RunnableWrapper;
import stroom.util.entityevent.EntityEvent;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.RestResourcesBinder;
import stroom.xmlschema.shared.XmlSchemaDoc;

import com.google.inject.AbstractModule;
import jakarta.inject.Inject;

public class XmlSchemaModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(XmlSchemaStore.class).to(XmlSchemaStoreImpl.class);

        GuiceUtil.buildMultiBinder(binder(), EntityEvent.Handler.class)
                .addBinding(XmlSchemaCache.class);

        GuiceUtil.buildMultiBinder(binder(), ExplorerActionHandler.class)
                .addBinding(XmlSchemaStoreImpl.class);
        GuiceUtil.buildMultiBinder(binder(), ImportExportActionHandler.class)
                .addBinding(XmlSchemaStoreImpl.class);
        GuiceUtil.buildMultiBinder(binder(), ContentIndexable.class)
                .addBinding(XmlSchemaStoreImpl.class);

        DocumentActionHandlerBinder.create(binder())
                .bind(XmlSchemaDoc.TYPE, XmlSchemaStoreImpl.class);

        RestResourcesBinder.create(binder())
                .bind(XmlSchemaResourceImpl.class);

        ScheduledJobsBinder.create(binder())
                .bindJobTo(ClearOldSchemas.class, builder -> builder
                        .name("Clear Old Schemas")
                        .description("Every 10 minutes try to clear old cached schemas.")
                        .managed(false)
                        .frequencySchedule("10m"));
    }

    private static class ClearOldSchemas extends RunnableWrapper {

        @Inject
        ClearOldSchemas(final XmlSchemaCache xmlSchemaCache) {
            super(xmlSchemaCache::clear);
        }
    }
}
