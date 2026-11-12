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

package stroom.dataconnection.impl;

import stroom.dataconnection.shared.DataConnectionDoc;
import stroom.docstore.api.DocumentStoreBinder;
import stroom.query.api.datasource.DataSourceProvider;
import stroom.query.common.v2.SearchProvider;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.RestResourcesBinder;
import stroom.util.shared.Clearable;

import com.google.inject.AbstractModule;

/// Guice module for Data Connection documents: their store, REST resource, connection pools and
/// search provider.
public class DataConnectionModule extends AbstractModule {

    @Override
    protected void configure() {
        DocumentStoreBinder.create(binder())
                .bind(DataConnectionDoc.TYPE, DataConnectionStore.class, DataConnectionStoreImpl.class);

        bind(AthenaClientFactory.class).to(AthenaClientFactoryImpl.class);

        RestResourcesBinder.create(binder())
                .bind(DataConnectionResourceImpl.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class)
                .addBinding(JdbcConnectionPoolCache.class)
                .addBinding(SqlFieldCache.class);

        GuiceUtil.buildMultiBinder(binder(), DataSourceProvider.class)
                .addBinding(DataConnectionSearchProvider.class);
        GuiceUtil.buildMultiBinder(binder(), SearchProvider.class)
                .addBinding(DataConnectionSearchProvider.class);
    }
}
