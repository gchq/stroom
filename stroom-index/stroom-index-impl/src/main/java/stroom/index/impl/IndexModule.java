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

package stroom.index.impl;

import stroom.docstore.api.ContentIndexable;
import stroom.docstore.api.DocumentActionHandlerBinder;
import stroom.explorer.api.ExplorerActionHandler;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.index.api.IndexVolumeGroupService;
import stroom.index.shared.LuceneIndexDoc;
import stroom.job.api.ScheduledJobsBinder;
import stroom.lifecycle.api.LifecycleBinder;
import stroom.query.api.datasource.DataSourceProvider;
import stroom.query.common.v2.IndexFieldCache;
import stroom.query.common.v2.IndexFieldProviders;
import stroom.searchable.api.Searchable;
import stroom.util.RunnableWrapper;
import stroom.util.entityevent.EntityEvent;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.HasSystemInfoBinder;
import stroom.util.guice.RestResourcesBinder;
import stroom.util.shared.Clearable;
import stroom.util.shared.scheduler.CronExpressions;

import com.google.inject.AbstractModule;
import jakarta.inject.Inject;

public class IndexModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new IndexElementModule());

        // Needed in IndexStoreImpl
        requireBinding(IndexVolumeGroupService.class);

        bind(IndexShardWriterCache.class).to(IndexShardWriterCacheImpl.class);
        bind(LuceneIndexDocCache.class).to(LuceneIndexDocCacheImpl.class);
        bind(IndexFieldProviders.class).to(IndexFieldProvidersImpl.class);
        bind(IndexFieldCache.class).to(IndexFieldCacheImpl.class);
        bind(IndexStore.class).to(IndexStoreImpl.class);
        bind(IndexVolumeService.class).to(IndexVolumeServiceImpl.class);
        bind(IndexVolumeGroupService.class).to(IndexVolumeGroupServiceImpl.class);
        bind(IndexShardService.class).to(IndexShardServiceImpl.class);
        bind(IndexShardCreator.class).to(IndexShardCreatorImpl.class);
        bind(IndexFieldService.class).to(IndexFieldServiceImpl.class);
        bind(Indexer.class).to(IndexerImpl.class);
        bind(ActiveShardsCache.class).to(ActiveShardsCacheImpl.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class)
                .addBinding(LuceneIndexDocCacheImpl.class)
                .addBinding(IndexVolumeServiceImpl.class)
                .addBinding(IndexVolumeGroupServiceImpl.class)
                .addBinding(IndexFieldCacheImpl.class);

        GuiceUtil.buildMultiBinder(binder(), EntityEvent.Handler.class)
                .addBinding(IndexConfigCacheEntityEventHandler.class);

        GuiceUtil.buildMultiBinder(binder(), ExplorerActionHandler.class)
                .addBinding(IndexStoreImpl.class);
        GuiceUtil.buildMultiBinder(binder(), ImportExportActionHandler.class)
                .addBinding(IndexStoreImpl.class);
        GuiceUtil.buildMultiBinder(binder(), ContentIndexable.class)
                .addBinding(IndexStoreImpl.class);

        GuiceUtil.buildMultiBinder(binder(), DataSourceProvider.class)
                .addBinding(IndexShardServiceImpl.class);
//        GuiceUtil.buildMultiBinder(binder(), Searchable.class)
//                .addBinding(IndexShardServiceImpl.class);
        GuiceUtil.buildMapBinder(binder(), Searchable.class)
                .addBinding(IndexShardServiceImpl.class);

        RestResourcesBinder.create(binder())
                .bind(IndexResourceImpl.class)
                .bind(IndexVolumeGroupResourceImpl.class)
                .bind(IndexVolumeResourceImpl.class);

        DocumentActionHandlerBinder.create(binder())
                .bind(LuceneIndexDoc.TYPE, IndexStoreImpl.class);

        GuiceUtil.buildMultiBinder(binder(), EntityEvent.Handler.class)
                .addBinding(IndexVolumeServiceImpl.class);

        ScheduledJobsBinder.create(binder())
                .bindJobTo(IndexShardDelete.class, builder -> builder
                        .name("Index Shard Delete")
                        .description("Job to delete index shards from disk that have been marked as deleted")
                        .cronSchedule(CronExpressions.EVERY_DAY_AT_MIDNIGHT.getExpression()))
                .bindJobTo(IndexShardRetention.class, builder -> builder
                        .name("Index Shard Retention")
                        .description("Job to set index shards to have a status of deleted that have past their " +
                                     "retention period")
                        .frequencySchedule("10m"))
                .bindJobTo(IndexWriterCacheSweep.class, builder -> builder
                        .name("Index Writer Cache Sweep")
                        .description("Job to remove old index shard writers from the cache")
                        .frequencySchedule("10m"))
                .bindJobTo(IndexWriterFlush.class, builder -> builder
                        .name("Index Writer Flush")
                        .description("Job to flush index shard data to disk")
                        .frequencySchedule("10m"))
                .bindJobTo(VolumeStatus.class, builder -> builder
                        .name("Index Volume Status")
                        .description("Update the usage status of volumes owned by the node")
                        .frequencySchedule("5m"));

        LifecycleBinder.create(binder())
                .bindStartupTaskTo(IndexShardWriterCacheStartup.class)
                .bindShutdownTaskTo(IndexShardWriterCacheShutdown.class);

        HasSystemInfoBinder.create(binder())
                .bind(IndexVolumeServiceImpl.class);
        HasSystemInfoBinder.create(binder()).bind(IndexSystemInfo.class);
    }


    // --------------------------------------------------------------------------------


    private static class IndexShardDelete extends RunnableWrapper {

        @Inject
        IndexShardDelete(final IndexShardManager indexShardManager) {
            super(indexShardManager::deleteFromDisk);
        }
    }


    // --------------------------------------------------------------------------------


    private static class IndexShardRetention extends RunnableWrapper {

        @Inject
        IndexShardRetention(final IndexShardManager indexShardManager) {
            super(indexShardManager::checkRetention);
        }
    }


    // --------------------------------------------------------------------------------


    private static class IndexWriterCacheSweep extends RunnableWrapper {

        @Inject
        IndexWriterCacheSweep(final IndexShardWriterCache indexShardWriterCache) {
            super(indexShardWriterCache::sweep);
        }
    }


    // --------------------------------------------------------------------------------


    private static class IndexWriterFlush extends RunnableWrapper {

        @Inject
        IndexWriterFlush(final IndexShardWriterCache indexShardWriterCache) {
            super(indexShardWriterCache::flushAll);
        }
    }


    // --------------------------------------------------------------------------------


    private static class VolumeStatus extends RunnableWrapper {

        @Inject
        VolumeStatus(final IndexVolumeService volumeService) {
            super(volumeService::rescan);
        }
    }


    // --------------------------------------------------------------------------------


    private static class IndexShardWriterCacheStartup extends RunnableWrapper {

        @Inject
        IndexShardWriterCacheStartup(final IndexShardWriterCacheImpl indexShardWriterCache) {
            super(indexShardWriterCache::startup);
        }
    }


    // --------------------------------------------------------------------------------


    private static class IndexShardWriterCacheShutdown extends RunnableWrapper {

        @Inject
        IndexShardWriterCacheShutdown(final IndexShardWriterCacheImpl indexShardWriterCache) {
            super(indexShardWriterCache::shutdown);
        }
    }
}
