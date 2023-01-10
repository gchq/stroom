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

package stroom.view.impl;

import stroom.datasource.api.v2.DataSourceProvider;
import stroom.docstore.api.DocumentActionHandlerBinder;
import stroom.event.logging.api.ObjectInfoProviderBinder;
import stroom.explorer.api.ExplorerActionHandler;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.query.common.v2.StoreFactory;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.RestResourcesBinder;
import stroom.view.shared.ViewDoc;

import com.google.inject.AbstractModule;

public class ViewModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(ViewStore.class).to(ViewStoreImpl.class);

        GuiceUtil.buildMultiBinder(binder(), ExplorerActionHandler.class)
                .addBinding(ViewStoreImpl.class);

        GuiceUtil.buildMultiBinder(binder(), ImportExportActionHandler.class)
                .addBinding(ViewStoreImpl.class);

        GuiceUtil.buildMultiBinder(binder(), DataSourceProvider.class)
                .addBinding(ViewStoreFactory.class);
        GuiceUtil.buildMultiBinder(binder(), StoreFactory.class)
                .addBinding(ViewStoreFactory.class);

        DocumentActionHandlerBinder.create(binder())
                .bind(ViewDoc.DOCUMENT_TYPE, ViewStoreImpl.class);

        // Provide object info to the logging service.
        ObjectInfoProviderBinder.create(binder())
                .bind(ViewDoc.class, ViewDocObjectInfoProvider.class);

        RestResourcesBinder.create(binder())
                .bind(ViewResourceImpl.class);
    }
}
