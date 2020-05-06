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

package stroom.index.mock;

import stroom.docstore.api.DocumentActionHandlerBinder;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.index.impl.IndexElementModule;
import stroom.index.impl.IndexShardService;
import stroom.index.impl.IndexShardWriterCache;
import stroom.index.impl.IndexStore;
import stroom.index.impl.IndexStoreImpl;
import stroom.index.impl.IndexStructureCache;
import stroom.index.impl.IndexStructureCacheImpl;
import stroom.index.impl.IndexVolumeGroupService;
import stroom.index.impl.IndexVolumeService;
import stroom.index.impl.Indexer;
import stroom.index.shared.IndexDoc;
import stroom.util.guice.GuiceUtil;
import stroom.util.shared.Clearable;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;

public class MockIndexModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new IndexElementModule());

        bind(IndexShardWriterCache.class).to(MockIndexShardWriterCache.class);
        bind(IndexStructureCache.class).to(IndexStructureCacheImpl.class);
        bind(IndexStore.class).to(IndexStoreImpl.class);
        bind(IndexVolumeService.class).to(MockIndexVolumeService.class);
        bind(IndexVolumeGroupService.class).to(MockIndexVolumeGroupService.class);
        bind(IndexShardService.class).to(MockIndexShardService.class);
        bind(Indexer.class).to(MockIndexer.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class).addBinding(MockIndexShardService.class);

        final Multibinder<ImportExportActionHandler> importExportActionHandlerBinder = Multibinder.newSetBinder(binder(), ImportExportActionHandler.class);
        importExportActionHandlerBinder.addBinding().to(IndexStoreImpl.class);

        DocumentActionHandlerBinder.create(binder())
                .bind(IndexDoc.DOCUMENT_TYPE, IndexStoreImpl.class);
    }
}