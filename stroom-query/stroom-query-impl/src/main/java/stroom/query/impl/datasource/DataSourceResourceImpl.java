/*
 * Copyright 2017 Crown Copyright
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

package stroom.query.impl.datasource;

import stroom.datasource.api.v2.DataSource;
import stroom.datasource.shared.DataSourceResource;
import stroom.docref.DocRef;
import stroom.docstore.shared.Documentation;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.query.impl.QueryService;
import stroom.util.NullSafe;
import stroom.view.api.ViewStore;
import stroom.view.shared.ViewDoc;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

@AutoLogged
class DataSourceResourceImpl implements DataSourceResource {

    private final Provider<QueryService> queryServiceProvider;
    private final Provider<ViewStore> viewStoreProvider;

    @Inject
    DataSourceResourceImpl(final Provider<QueryService> queryServiceProvider,
                           final Provider<ViewStore> viewStoreProvider) {
        this.queryServiceProvider = queryServiceProvider;
        this.viewStoreProvider = viewStoreProvider;
    }

    @Override
    public DataSource fetch(final DocRef dataSourceRef) {
        return queryServiceProvider.get().getDataSource(dataSourceRef).orElse(null);
    }

    @Override
    public DataSource fetchFromQuery(final String query) {
        return queryServiceProvider.get().getDataSource(query).orElse(null);
    }

    @Override
    public Documentation fetchDocumentation(final DocRef docRef) {
        final String markdown = NullSafe.get(viewStoreProvider.get().readDocument(docRef),
                ViewDoc::getDescription);
        return Documentation.of(markdown);
    }
}
