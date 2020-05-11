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
import stroom.explorer.api.ExplorerActionHandler;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.index.shared.IndexDoc;
import stroom.job.api.RunnableWrapper;
import stroom.job.api.ScheduledJobsBinder;
import stroom.util.entityevent.EntityEvent;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.RestResourcesBinder;
import stroom.util.shared.Clearable;

import com.google.inject.AbstractModule;

import javax.inject.Inject;

import static stroom.job.api.Schedule.ScheduleType.CRON;
import static stroom.job.api.Schedule.ScheduleType.PERIODIC;

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

        GuiceUtil.buildMultiBinder(binder(), ExplorerActionHandler.class)
                .addBinding(IndexStoreImpl.class);

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

        ScheduledJobsBinder.create(binder())
                .bindJobTo(IndexShardDelete.class, builder -> builder
                        .withName("Index Shard Delete")
                        .withDescription("Job to delete index shards from disk that have been marked as deleted")
                        .withSchedule(CRON, "0 0 *"))
                .bindJobTo(IndexShardRetention.class, builder -> builder
                        .withName("Index Shard Retention")
                        .withDescription("Job to set index shards to have a status of deleted that have past their retention period")
                        .withSchedule(PERIODIC, "10m"))
                .bindJobTo(IndexWriterCacheSweep.class, builder -> builder
                        .withName("Index Writer Cache Sweep")
                        .withDescription("Job to remove old index shard writers from the cache")
                        .withSchedule(PERIODIC, "10m"))
                .bindJobTo(IndexWriterFlush.class, builder -> builder
                        .withName("Index Writer Flush")
                        .withDescription("Job to flush index shard data to disk")
                        .withSchedule(PERIODIC, "10m"))
                .bindJobTo(VolumeStatus.class, builder -> builder
                        .withName("Index Volume Status")
                        .withDescription("Update the usage status of volumes owned by the node")
                        .withSchedule(PERIODIC, "5m"));
    }

    private static class IndexShardDelete extends RunnableWrapper {
        @Inject
        IndexShardDelete(final IndexShardManager indexShardManager) {
            super(indexShardManager::deleteFromDisk);
        }
    }

    private static class IndexShardRetention extends RunnableWrapper {
        @Inject
        IndexShardRetention(final IndexShardManager indexShardManager) {
            super(indexShardManager::checkRetention);
        }
    }

    private static class IndexWriterCacheSweep extends RunnableWrapper {
        @Inject
        IndexWriterCacheSweep(final IndexShardWriterCache indexShardWriterCache) {
            super(indexShardWriterCache::sweep);
        }
    }

    private static class IndexWriterFlush extends RunnableWrapper {
        @Inject
        IndexWriterFlush(final IndexShardWriterCache indexShardWriterCache) {
            super(indexShardWriterCache::flushAll);
        }
    }

    private static class VolumeStatus extends RunnableWrapper {
        @Inject
        VolumeStatus(final IndexVolumeService volumeService) {
            super(volumeService::rescan);
        }
    }
}