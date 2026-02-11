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

package stroom.query.impl;

import stroom.docstore.api.ContentIndexable;
import stroom.docstore.api.DocumentActionHandlerBinder;
import stroom.event.logging.api.ObjectInfoProviderBinder;
import stroom.explorer.api.ExplorerActionHandler;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.query.api.datasource.QueryFieldProvider;
import stroom.query.shared.QueryDoc;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.RestResourcesBinder;

import com.google.inject.AbstractModule;

public class QueryModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(QueryStore.class).to(QueryStoreImpl.class);
        bind(QueryService.class).to(QueryServiceImpl.class);
        bind(QueryFieldProvider.class).to(QueryServiceImpl.class);

        GuiceUtil.buildMultiBinder(binder(), ExplorerActionHandler.class)
                .addBinding(QueryStoreImpl.class);
        GuiceUtil.buildMultiBinder(binder(), ImportExportActionHandler.class)
                .addBinding(QueryStoreImpl.class);
        GuiceUtil.buildMultiBinder(binder(), ContentIndexable.class)
                .addBinding(QueryStoreImpl.class);

        DocumentActionHandlerBinder.create(binder())
                .bind(QueryDoc.TYPE, QueryStoreImpl.class);

        // Provide object info to the logging service.
        ObjectInfoProviderBinder.create(binder())
                .bind(QueryDoc.class, QueryDocObjectInfoProvider.class);

        RestResourcesBinder.create(binder())
                .bind(AskStroomAiResourceImpl.class)
                .bind(QueryResourceImpl.class)
                .bind(ExpressionResourceImpl.class)
                .bind(ResultStoreResourceImpl.class);
    }
}
