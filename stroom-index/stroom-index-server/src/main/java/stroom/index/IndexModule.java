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

package stroom.index;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import stroom.entity.EntityTypeBinder;
import stroom.entity.shared.Clearable;
import stroom.entity.shared.EntityEvent;
import stroom.explorer.api.ExplorerActionHandler;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.index.impl.db.IndexDbModule;
import stroom.index.service.IndexShardService;
import stroom.index.service.IndexVolumeService;
import stroom.index.shared.CloseIndexShardAction;
import stroom.index.shared.DeleteIndexShardAction;
import stroom.index.shared.FetchIndexVolumesAction;
import stroom.index.shared.FlushIndexShardAction;
import stroom.index.shared.IndexDoc;
import stroom.task.api.TaskHandlerBinder;

public class IndexModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new IndexElementModule());
        install(new IndexDbModule());

        bind(IndexShardManager.class).to(IndexShardManagerImpl.class);
        bind(IndexShardWriterCache.class).to(IndexShardWriterCacheImpl.class);
        bind(IndexStructureCache.class).to(IndexStructureCacheImpl.class);
        bind(IndexStore.class).to(IndexStoreImpl.class);
        bind(IndexVolumeService.class).to(IndexVolumeServiceImpl.class);
        bind(IndexShardService.class).to(IndexShardServiceImpl.class);
        bind(Indexer.class).to(IndexerImpl.class);

        final Multibinder<Clearable> clearableBinder = Multibinder.newSetBinder(binder(), Clearable.class);
        clearableBinder.addBinding().to(IndexStructureCacheImpl.class);

        TaskHandlerBinder.create(binder())
                .bind(CloseIndexShardAction.class, stroom.index.CloseIndexShardActionHandler.class)
                .bind(DeleteIndexShardAction.class, stroom.index.DeleteIndexShardActionHandler.class)
                .bind(FlushIndexShardAction.class, stroom.index.FlushIndexShardActionHandler.class)
                .bind(FetchIndexVolumesAction.class, stroom.index.FetchIndexVolumesActionHandler.class)
                .bind(CloseIndexShardClusterTask.class, CloseIndexShardClusterHandler.class)
                .bind(FlushIndexShardClusterTask.class, FlushIndexShardClusterHandler.class)
                .bind(DeleteIndexShardClusterTask.class, DeleteIndexShardClusterHandler.class);

        final Multibinder<EntityEvent.Handler> entityEventHandlerBinder = Multibinder.newSetBinder(binder(), EntityEvent.Handler.class);
        entityEventHandlerBinder.addBinding().to(IndexConfigCacheEntityEventHandler.class);

        final Multibinder<ExplorerActionHandler> explorerActionHandlerBinder = Multibinder.newSetBinder(binder(), ExplorerActionHandler.class);
        explorerActionHandlerBinder.addBinding().to(stroom.index.IndexStoreImpl.class);

        final Multibinder<ImportExportActionHandler> importExportActionHandlerBinder = Multibinder.newSetBinder(binder(), ImportExportActionHandler.class);
        importExportActionHandlerBinder.addBinding().to(stroom.index.IndexStoreImpl.class);

        EntityTypeBinder.create(binder())
                .bind(IndexDoc.DOCUMENT_TYPE, IndexStoreImpl.class);

        // TODO Shards are no longer Findable Entities
//
//        final Multibinder<FindService> findServiceBinder = Multibinder.newSetBinder(binder(), FindService.class);
////        findServiceBinder.addBinding().to(stroom.index.IndexStoreImpl.class);
//        findServiceBinder.addBinding().to(stroom.index.IndexShardServiceImpl.class);
    }
}