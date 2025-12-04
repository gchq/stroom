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

package stroom.state.impl;

import stroom.docstore.api.ContentIndexable;
import stroom.docstore.api.DocumentActionHandlerBinder;
import stroom.explorer.api.ExplorerActionHandler;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.pipeline.xsltfunctions.StateLookup;
import stroom.query.language.functions.StateFetcher;
import stroom.query.language.functions.StateProvider;
import stroom.state.impl.pipeline.StateElementModule;
import stroom.state.impl.pipeline.StateFetcherImpl;
import stroom.state.impl.pipeline.StateLookupImpl;
import stroom.state.impl.pipeline.StateProviderImpl;
import stroom.state.shared.ScyllaDbDoc;
import stroom.util.entityevent.EntityEvent;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.RestResourcesBinder;
import stroom.util.shared.Clearable;

import com.google.inject.AbstractModule;

public class MockStateModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new StateElementModule());

        bind(StateLookup.class).to(StateLookupImpl.class);
        GuiceUtil.buildMultiBinder(binder(), StateProvider.class).addBinding(StateProviderImpl.class);
        bind(StateFetcher.class).to(StateFetcherImpl.class);

//        // Services
//
//        bind(StateStoreService.class).to(ElasticSearchProvider.class);
//        bind(ElasticSuggestionsQueryHandler.class).to(ElasticSuggestionsQueryHandlerImpl.class);
//
//        SuggestionsServiceBinder.create(binder())
//                .bind(StateStoreDoc.DOCUMENT_TYPE, ElasticSuggestionsQueryHandler.class);
//
        // Caches
        bind(ScyllaDbDocCache.class).to(ScyllaDbDocCacheImpl.class);
        bind(StateDocCache.class).to(StateDocCacheImpl.class);
        bind(CqlSessionCache.class).to(CqlSessionCacheImpl.class);
        bind(CqlSessionFactory.class).to(CqlSessionFactoryImpl.class);

        GuiceUtil.buildMultiBinder(binder(), EntityEvent.Handler.class)
                .addBinding(ScyllaDbDocCacheImpl.class)
                .addBinding(StateDocCacheImpl.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class)
                .addBinding(ScyllaDbDocCacheImpl.class)
                .addBinding(StateDocCacheImpl.class)
                .addBinding(CqlSessionCacheImpl.class);

        // Scylla DB
        bind(ScyllaDbDocStore.class).to(ScyllaDbDocStoreImpl.class);

        GuiceUtil.buildMultiBinder(binder(), ExplorerActionHandler.class)
                .addBinding(ScyllaDbDocStoreImpl.class);
        GuiceUtil.buildMultiBinder(binder(), ImportExportActionHandler.class)
                .addBinding(ScyllaDbDocStoreImpl.class);
        GuiceUtil.buildMultiBinder(binder(), ContentIndexable.class)
                .addBinding(ScyllaDbDocStoreImpl.class);

        DocumentActionHandlerBinder.create(binder())
                .bind(ScyllaDbDoc.TYPE, ScyllaDbDocStoreImpl.class);

        RestResourcesBinder.create(binder())
                .bind(ScyllaDbDocResourceImpl.class);

        // State
        bind(StateDocStore.class).to(StateDocStoreImpl.class);

        GuiceUtil.buildMultiBinder(binder(), ExplorerActionHandler.class)
                .addBinding(StateDocStoreImpl.class);
        GuiceUtil.buildMultiBinder(binder(), ImportExportActionHandler.class)
                .addBinding(StateDocStoreImpl.class);
        GuiceUtil.buildMultiBinder(binder(), ContentIndexable.class)
                .addBinding(StateDocStoreImpl.class);

//        bind(StateLookup.class).toInstance((docRef, lookupIdentifier, result) -> {
//
//        });
    }
}
