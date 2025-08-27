/*
 * Copyright 2024 Crown Copyright
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

package stroom.pathways.impl;

import stroom.docstore.api.ContentIndexable;
import stroom.docstore.api.DocumentActionHandlerBinder;
import stroom.explorer.api.ExplorerActionHandler;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.pathways.shared.PathwaysDoc;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.RestResourcesBinder;

import com.google.inject.AbstractModule;

public class PathwaysModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new PathwaysElementModule());


//        bind(LuceneIndexDocCache.class).to(LuceneIndexDocCacheImpl.class);
//        bind(IndexFieldProviders.class).to(IndexFieldProvidersImpl.class);
//        bind(IndexFieldCache.class).to(IndexFieldCacheImpl.class);
        bind(PathwaysStore.class).to(PathwaysStoreImpl.class);
//        bind(IndexFieldService.class).to(IndexFieldServiceImpl.class);

//        GuiceUtil.buildMultiBinder(binder(), Clearable.class)
//                .addBinding(LuceneIndexDocCacheImpl.class)
//                .addBinding(IndexVolumeServiceImpl.class)
//                .addBinding(IndexVolumeGroupServiceImpl.class)
//                .addBinding(IndexFieldCacheImpl.class);
//
//        GuiceUtil.buildMultiBinder(binder(), EntityEvent.Handler.class)
//                .addBinding(IndexConfigCacheEntityEventHandler.class);

        GuiceUtil.buildMultiBinder(binder(), ExplorerActionHandler.class)
                .addBinding(PathwaysStoreImpl.class);
        GuiceUtil.buildMultiBinder(binder(), ImportExportActionHandler.class)
                .addBinding(PathwaysStoreImpl.class);
        GuiceUtil.buildMultiBinder(binder(), ContentIndexable.class)
                .addBinding(PathwaysStoreImpl.class);

        RestResourcesBinder.create(binder())
                .bind(PathwaysResourceImpl.class)
                .bind(TracesResourceImpl.class);

        DocumentActionHandlerBinder.create(binder())
                .bind(PathwaysDoc.TYPE, PathwaysStoreImpl.class);
//
//        ScheduledJobsBinder.create(binder())
//                .bindJobTo(IndexShardDelete.class, builder -> builder
//                        .name("Index Shard Delete")
//                        .description("Job to delete index shards from disk that have been marked as deleted")
//                        .cronSchedule(CronExpressions.EVERY_DAY_AT_MIDNIGHT.getExpression()))
//                .bindJobTo(IndexShardRetention.class, builder -> builder
//                        .name("Index Shard Retention")
//                        .description("Job to set index shards to have a status of deleted that have past their " +
//                                "retention period")
//                        .frequencySchedule("10m"))
//                .bindJobTo(IndexWriterCacheSweep.class, builder -> builder
//                        .name("Index Writer Cache Sweep")
//                        .description("Job to remove old index shard writers from the cache")
//                        .frequencySchedule("10m"))
//                .bindJobTo(IndexWriterFlush.class, builder -> builder
//                        .name("Index Writer Flush")
//                        .description("Job to flush index shard data to disk")
//                        .frequencySchedule("10m"))
//                .bindJobTo(VolumeStatus.class, builder -> builder
//                        .name("Index Volume Status")
//                        .description("Update the usage status of volumes owned by the node")
//                        .frequencySchedule("5m"));

    }

}
