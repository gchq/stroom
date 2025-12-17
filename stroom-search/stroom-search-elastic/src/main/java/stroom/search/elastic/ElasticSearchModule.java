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

package stroom.search.elastic;

import stroom.docstore.api.ContentIndexable;
import stroom.docstore.api.DocumentActionHandlerBinder;
import stroom.explorer.api.ExplorerActionHandler;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.query.api.datasource.DataSourceProvider;
import stroom.query.common.v2.IndexFieldProvider;
import stroom.query.common.v2.SearchProvider;
import stroom.search.elastic.indexing.ElasticIndexingElementModule;
import stroom.search.elastic.search.ElasticIndexQueryResourceImpl;
import stroom.search.elastic.search.ElasticSearchProvider;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.elastic.suggest.ElasticSuggestionsQueryHandler;
import stroom.search.elastic.suggest.ElasticSuggestionsQueryHandlerImpl;
import stroom.suggestions.api.SuggestionsServiceBinder;
import stroom.util.entityevent.EntityEvent;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.RestResourcesBinder;
import stroom.util.shared.Clearable;

import com.google.inject.AbstractModule;

public class ElasticSearchModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new ElasticIndexingElementModule());

        // Services

        bind(ElasticIndexService.class).to(ElasticSearchProvider.class);
        bind(ElasticSuggestionsQueryHandler.class).to(ElasticSuggestionsQueryHandlerImpl.class);

        SuggestionsServiceBinder.create(binder())
                .bind(ElasticIndexDoc.TYPE, ElasticSuggestionsQueryHandler.class);

        // Caches

        bind(ElasticIndexCache.class).to(ElasticIndexCacheImpl.class);
        bind(ElasticClientCache.class).to(ElasticClientCacheImpl.class);

        GuiceUtil.buildMultiBinder(binder(), EntityEvent.Handler.class)
                .addBinding(ElasticIndexCacheImpl.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class)
                .addBinding(ElasticIndexCacheImpl.class)
                .addBinding(ElasticClientCacheImpl.class);

        // Elastic cluster

        bind(ElasticClusterStore.class).to(ElasticClusterStoreImpl.class);

        GuiceUtil.buildMultiBinder(binder(), ExplorerActionHandler.class)
                .addBinding(ElasticClusterStoreImpl.class);
        GuiceUtil.buildMultiBinder(binder(), ImportExportActionHandler.class)
                .addBinding(ElasticClusterStoreImpl.class);
        GuiceUtil.buildMultiBinder(binder(), ContentIndexable.class)
                .addBinding(ElasticClusterStoreImpl.class);

        DocumentActionHandlerBinder.create(binder())
                .bind(ElasticClusterDoc.TYPE, ElasticClusterStoreImpl.class);

        RestResourcesBinder.create(binder())
                .bind(ElasticClusterResourceImpl.class);

        // Elastic index

        bind(ElasticIndexStore.class).to(ElasticIndexStoreImpl.class);

        GuiceUtil.buildMultiBinder(binder(), ExplorerActionHandler.class)
                .addBinding(ElasticIndexStoreImpl.class);
        GuiceUtil.buildMultiBinder(binder(), ImportExportActionHandler.class)
                .addBinding(ElasticIndexStoreImpl.class);
        GuiceUtil.buildMultiBinder(binder(), ContentIndexable.class)
                .addBinding(ElasticIndexStoreImpl.class);

        DocumentActionHandlerBinder.create(binder())
                .bind(ElasticIndexDoc.TYPE, ElasticIndexStoreImpl.class);

        RestResourcesBinder.create(binder())
                .bind(ElasticIndexResourceImpl.class)
                .bind(ElasticIndexQueryResourceImpl.class);

        GuiceUtil.buildMultiBinder(binder(), DataSourceProvider.class)
                .addBinding(ElasticSearchProvider.class);
        GuiceUtil.buildMultiBinder(binder(), SearchProvider.class)
                .addBinding(ElasticSearchProvider.class);
        GuiceUtil.buildMultiBinder(binder(), IndexFieldProvider.class)
                .addBinding(ElasticSearchProvider.class);
    }
}
