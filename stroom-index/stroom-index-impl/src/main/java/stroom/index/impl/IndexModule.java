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

package stroom.index.impl;

import stroom.docstore.api.DocumentActionHandlerBinder;
import stroom.explorer.api.ExplorerActionHandlerBinder;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.index.shared.IndexDoc;
import stroom.util.entityevent.EntityEvent;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.RestResourcesBinder;
import stroom.util.shared.Clearable;

import com.google.inject.AbstractModule;

public class IndexModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new IndexElementModule());

        bind(IndexShardWriterCache.class).to(IndexShardWriterCacheImpl.class);
        bind(IndexStructureCache.class).to(IndexStructureCacheImpl.class);
        bind(IndexStore.class).to(IndexStoreImpl.class);
        bind(IndexVolumeService.class).to(IndexVolumeServiceImpl.class);
        bind(IndexVolumeGroupService.class).to(IndexVolumeGroupServiceImpl.class);
        bind(IndexShardService.class).to(IndexShardServiceImpl.class);
        bind(Indexer.class).to(IndexerImpl.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class)
                .addBinding(IndexStructureCacheImpl.class);

        GuiceUtil.buildMultiBinder(binder(), EntityEvent.Handler.class)
                .addBinding(IndexConfigCacheEntityEventHandler.class);

        ExplorerActionHandlerBinder.create(binder())
                .bind(IndexStoreImpl.class);

        GuiceUtil.buildMultiBinder(binder(), ImportExportActionHandler.class)
                .addBinding(IndexStoreImpl.class);

        RestResourcesBinder.create(binder())
                .bind(NewUIIndexResourceImpl.class)
                .bind(NewUIIndexVolumeGroupResourceImpl.class)
                .bind(NewUIIndexVolumeResourceImpl.class)
                .bind(IndexResourceImpl.class)
                .bind(IndexVolumeGroupResourceImpl.class)
                .bind(IndexVolumeResourceImpl.class);

        DocumentActionHandlerBinder.create(binder())
                .bind(IndexDoc.DOCUMENT_TYPE, IndexStoreImpl.class);
    }
}