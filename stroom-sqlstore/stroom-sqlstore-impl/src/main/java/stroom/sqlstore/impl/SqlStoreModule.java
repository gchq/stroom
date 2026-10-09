/*
 * Copyright 2026 Crown Copyright
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

package stroom.sqlstore.impl;

import stroom.docstore.api.ContentIndexable;
import stroom.docstore.api.DocumentActionHandlerBinder;
import stroom.explorer.api.ExplorerActionHandler;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.pipeline.xsltfunctions.SqlStoreLookup;
import stroom.query.api.datasource.DataSourceProvider;
import stroom.query.common.v2.SearchProvider;
import stroom.sqlstore.api.UpdatableTemporalStore;
import stroom.sqlstore.impl.pipeline.SqlStoreElementModule;
import stroom.sqlstore.impl.pipeline.SqlStoreLookupImpl;
import stroom.sqlstore.shared.SqlTemporalStoreDoc;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.RestResourcesBinder;

import com.google.inject.AbstractModule;

public class SqlStoreModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new SqlStoreElementModule());

        bind(SqlTemporalStoreDocStore.class).to(SqlTemporalStoreDocStoreImpl.class);
        bind(UpdatableTemporalStore.class).to(UpdatableSqlTemporalStore.class);
        bind(SqlStoreLookup.class).to(SqlStoreLookupImpl.class);

        GuiceUtil.buildMultiBinder(binder(), ExplorerActionHandler.class)
                .addBinding(SqlTemporalStoreDocStoreImpl.class);
        GuiceUtil.buildMultiBinder(binder(), ImportExportActionHandler.class)
                .addBinding(SqlTemporalStoreDocStoreImpl.class);
        GuiceUtil.buildMultiBinder(binder(), ContentIndexable.class)
                .addBinding(SqlTemporalStoreDocStoreImpl.class);

        DocumentActionHandlerBinder.create(binder())
                .bind(SqlTemporalStoreDoc.TYPE, SqlTemporalStoreDocStoreImpl.class);

        RestResourcesBinder.create(binder())
                .bind(SqlTemporalStoreDocResourceImpl.class)
                .bind(SqlTemporalStoreResourceImpl.class);

        // Bind to Search and DataSource provider registries
        GuiceUtil.buildMultiBinder(binder(), DataSourceProvider.class)
                .addBinding(UpdatableSqlTemporalStore.class);
        GuiceUtil.buildMultiBinder(binder(), SearchProvider.class)
                .addBinding(UpdatableSqlTemporalStore.class);
    }
}
