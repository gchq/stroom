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

package stroom.index.mock;

import stroom.docstore.api.DocumentActionHandlerBinder;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.index.api.IndexVolumeGroupService;
import stroom.index.impl.IndexElementModule;
import stroom.index.impl.IndexFieldCacheImpl;
import stroom.index.impl.IndexFieldProvidersImpl;
import stroom.index.impl.IndexFieldService;
import stroom.index.impl.IndexShardCreator;
import stroom.index.impl.IndexShardDao;
import stroom.index.impl.IndexShardWriterCache;
import stroom.index.impl.IndexStore;
import stroom.index.impl.IndexStoreImpl;
import stroom.index.impl.IndexVolumeService;
import stroom.index.impl.Indexer;
import stroom.index.impl.LuceneIndexDocCache;
import stroom.index.impl.LuceneIndexDocCacheImpl;
import stroom.index.shared.LuceneIndexDoc;
import stroom.query.common.v2.IndexFieldCache;
import stroom.query.common.v2.IndexFieldProvider;
import stroom.query.common.v2.IndexFieldProviders;
import stroom.util.guice.GuiceUtil;

import com.google.inject.AbstractModule;

public class MockIndexModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new IndexElementModule());

        bind(IndexShardWriterCache.class).to(MockIndexShardWriterCache.class);
        bind(LuceneIndexDocCache.class).to(LuceneIndexDocCacheImpl.class);
        bind(IndexFieldProviders.class).to(IndexFieldProvidersImpl.class);
        bind(IndexFieldCache.class).to(IndexFieldCacheImpl.class);
        bind(IndexStore.class).to(IndexStoreImpl.class);
        bind(IndexVolumeService.class).to(MockIndexVolumeService.class);
        bind(IndexVolumeGroupService.class).to(MockIndexVolumeGroupService.class);
        bind(IndexShardDao.class).to(MockIndexShardDao.class);
        bind(IndexShardCreator.class).to(MockIndexShardCreator.class);
        bind(IndexFieldService.class).to(MockIndexFieldService.class);
        bind(Indexer.class).to(MockIndexer.class);

        GuiceUtil.buildMultiBinder(binder(), ImportExportActionHandler.class)
                .addBinding(IndexStoreImpl.class);

        DocumentActionHandlerBinder.create(binder())
                .bind(LuceneIndexDoc.TYPE, IndexStoreImpl.class);

        GuiceUtil.buildMultiBinder(binder(), IndexFieldProvider.class)
                .addBinding(MockIndexFieldService.class);
    }
}
