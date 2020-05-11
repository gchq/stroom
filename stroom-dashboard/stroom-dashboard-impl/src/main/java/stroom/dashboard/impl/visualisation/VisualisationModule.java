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

package stroom.dashboard.impl.visualisation;

import stroom.docstore.api.DocumentActionHandlerBinder;
import stroom.explorer.api.ExplorerActionHandler;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.RestResourcesBinder;
import stroom.visualisation.shared.VisualisationDoc;

import com.google.inject.AbstractModule;

public class VisualisationModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(VisualisationStore.class).to(VisualisationStoreImpl.class);

        GuiceUtil.buildMultiBinder(binder(), ExplorerActionHandler.class)
                .addBinding(VisualisationStoreImpl.class);

        GuiceUtil.buildMultiBinder(binder(), ImportExportActionHandler.class)
                .addBinding(VisualisationStoreImpl.class);

        DocumentActionHandlerBinder.create(binder())
                .bind(VisualisationDoc.DOCUMENT_TYPE, VisualisationStoreImpl.class);

        RestResourcesBinder.create(binder())
                .bind(VisualisationResourceImpl.class);
    }
}