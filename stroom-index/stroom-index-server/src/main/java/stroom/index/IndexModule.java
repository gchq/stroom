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
import stroom.entity.EntityTypeBinder;
import stroom.entity.shared.Clearable;
import stroom.entity.shared.EntityEvent;
import stroom.explorer.api.ExplorerActionHandler;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.index.impl.db.IndexDbModule;
import stroom.index.rest.StroomIndexQueryResourceImpl;
import stroom.index.service.IndexShardService;
import stroom.index.service.IndexShardServiceImpl;
import stroom.index.service.IndexVolumeGroupService;
import stroom.index.service.IndexVolumeGroupServiceImpl;
import stroom.index.service.IndexVolumeService;
import stroom.index.service.IndexVolumeServiceImpl;
import stroom.index.shared.CloseIndexShardAction;
import stroom.index.shared.DeleteIndexShardAction;
import stroom.index.shared.FetchIndexVolumesAction;
import stroom.index.shared.FlushIndexShardAction;
import stroom.index.shared.IndexDoc;
import stroom.task.api.TaskHandlerBinder;
import stroom.util.GuiceUtil;
import stroom.util.RestResource;

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
        bind(IndexVolumeGroupService.class).to(IndexVolumeGroupServiceImpl.class);
        bind(IndexShardService.class).to(IndexShardServiceImpl.class);
        bind(Indexer.class).to(IndexerImpl.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class)
                .addBinding(IndexStructureCacheImpl.class);

        TaskHandlerBinder.create(binder())
                .bind(CloseIndexShardAction.class, stroom.index.CloseIndexShardActionHandler.class)
                .bind(DeleteIndexShardAction.class, stroom.index.DeleteIndexShardActionHandler.class)
                .bind(FlushIndexShardAction.class, stroom.index.FlushIndexShardActionHandler.class)
                .bind(FetchIndexVolumesAction.class, stroom.index.FetchIndexVolumesActionHandler.class)
                .bind(CloseIndexShardClusterTask.class, CloseIndexShardClusterHandler.class)
                .bind(FlushIndexShardClusterTask.class, FlushIndexShardClusterHandler.class)
                .bind(DeleteIndexShardClusterTask.class, DeleteIndexShardClusterHandler.class);

        GuiceUtil.buildMultiBinder(binder(), EntityEvent.Handler.class)
                .addBinding(IndexConfigCacheEntityEventHandler.class);

        GuiceUtil.buildMultiBinder(binder(), ExplorerActionHandler.class)
                .addBinding(IndexStoreImpl.class);

        GuiceUtil.buildMultiBinder(binder(), ImportExportActionHandler.class)
                .addBinding(IndexStoreImpl.class);

        GuiceUtil.buildMultiBinder(binder(), RestResource.class)
                .addBinding(StroomIndexQueryResourceImpl.class);

        EntityTypeBinder.create(binder())
                .bind(IndexDoc.DOCUMENT_TYPE, IndexStoreImpl.class);

        // TODO Shards are no longer Findable Entities
//        GuiceUtil.buildMultiBinder(binder(), FindService.class)
//                .addBinding(IndexShardServiceImpl.class);
    }
}