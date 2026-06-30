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

package stroom.planb.impl;

import stroom.docstore.api.DocumentStoreBinder;
import stroom.job.api.ScheduledJobsBinder;
import stroom.lifecycle.api.LifecycleBinder;
import stroom.pipeline.xsltfunctions.PlanBLookup;
import stroom.planb.impl.data.MergeProcessor;
import stroom.planb.impl.data.PlanBRemoteQueryResourceImpl;
import stroom.planb.impl.data.PlanBShardInfoServiceImpl;
import stroom.planb.impl.data.ShardManager;
import stroom.planb.impl.data.TracesRemoteQueryResourceImpl;
import stroom.planb.impl.db.BatchDestination;
import stroom.planb.impl.db.DefaultBatchDestination;
import stroom.planb.impl.fs.SharedFileStoreCleaner;
import stroom.planb.impl.fs.SharedFileStoreDocStore;
import stroom.planb.impl.fs.SharedFileStoreMergeProcessor;
import stroom.planb.impl.pipeline.PlanBElementModule;
import stroom.planb.impl.pipeline.PlanBLookupImpl;
import stroom.planb.impl.pipeline.StateFetcherImpl;
import stroom.planb.impl.pipeline.StateProviderImpl;
import stroom.planb.impl.rest.FileTransferClient;
import stroom.planb.impl.rest.FileTransferClientImpl;
import stroom.planb.impl.rest.FileTransferResourceImpl;
import stroom.planb.impl.rest.FileTransferService;
import stroom.planb.impl.rest.FileTransferServiceImpl;
import stroom.planb.shared.PlanBDoc;
import stroom.query.api.QueryNodeResolver;
import stroom.query.api.datasource.DataSourceProvider;
import stroom.query.common.v2.IndexFieldProvider;
import stroom.query.common.v2.SearchProvider;
import stroom.query.language.functions.StateFetcher;
import stroom.query.language.functions.StateProvider;
import stroom.searchable.api.Searchable;
import stroom.util.RunnableWrapper;
import stroom.util.entityevent.EntityEvent;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.RestResourcesBinder;
import stroom.util.shared.Clearable;
import stroom.util.shared.scheduler.CronExpressions;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import jakarta.inject.Inject;

public class PlanBModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new PlanBElementModule());

        bind(PlanBLookup.class).to(PlanBLookupImpl.class);
        GuiceUtil.buildMultiBinder(binder(), StateProvider.class).addBinding(StateProviderImpl.class);
        bind(StateFetcher.class).to(StateFetcherImpl.class);

        // Caches
        bind(PlanBDocCache.class).to(PlanBDocCacheImpl.class);

        Multibinder.newSetBinder(binder(), String.class, PlanBDocumentTypes.class)
                .addBinding().toInstance(PlanBDoc.TYPE);

        GuiceUtil.buildMultiBinder(binder(), EntityEvent.Handler.class)
                .addBinding(PlanBDocCacheImpl.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class)
                .addBinding(PlanBDocCacheImpl.class);

        GuiceUtil.buildMultiBinder(binder(), DataSourceProvider.class)
                .addBinding(PlanBShardInfoServiceImpl.class);
        GuiceUtil.buildMapBinder(binder(), Searchable.class)
                .addBinding(PlanBShardInfoServiceImpl.class);

        // State
        bind(BatchDestination.class).to(DefaultBatchDestination.class);
        bind(FileTransferClient.class).to(FileTransferClientImpl.class);
        bind(FileTransferService.class).to(FileTransferServiceImpl.class);
        bind(SharedFileStoreMergeProcessor.class);

        bind(QueryNodeResolver.class).to(QueryNodeResolverImpl.class);

        DocumentStoreBinder.create(binder())
                .bind(PlanBDoc.TYPE, PlanBDocStore.class, PlanBDocStoreImpl.class);

        RestResourcesBinder.create(binder())
                .bind(PlanBDocResourceImpl.class)
                .bind(FileTransferResourceImpl.class)
                .bind(PlanBRemoteQueryResourceImpl.class)
                .bind(TracesRemoteQueryResourceImpl.class);

        GuiceUtil.buildMultiBinder(binder(), DataSourceProvider.class)
                .addBinding(StateSearchProvider.class);
        GuiceUtil.buildMultiBinder(binder(), SearchProvider.class)
                .addBinding(StateSearchProvider.class);
        GuiceUtil.buildMultiBinder(binder(), IndexFieldProvider.class)
                .addBinding(StateSearchProvider.class);

        ScheduledJobsBinder.create(binder())
                .bindJobTo(StateMergeRunnable.class, builder -> builder
                        .name(MergeProcessor.MERGE_TASK_NAME)
                        .description("Plan B state store merge")
                        .cronSchedule(CronExpressions.EVERY_MINUTE.getExpression())
                        .advanced(true));
        ScheduledJobsBinder.create(binder())
                .bindJobTo(StateMaintenanceRunnable.class, builder -> builder
                        .name(MergeProcessor.MAINTAIN_TASK_NAME)
                        .description("Plan B state store maintain")
                        .cronSchedule(CronExpressions.EVERY_10_MINUTES.getExpression())
                        .advanced(true));
        ScheduledJobsBinder.create(binder())
                .bindJobTo(SnapshotCreatorRunnable.class, builder -> builder
                        .name(ShardManager.SNAPSHOT_CREATOR_TASK_NAME)
                        .description("Plan B snapshot creation")
                        .cronSchedule(CronExpressions.EVERY_10_MINUTES.getExpression())
                        .advanced(true));
        ScheduledJobsBinder.create(binder())
                .bindJobTo(ShardManagerCleanupRunnable.class, builder -> builder
                        .name(ShardManager.SNAPSHOT_CLEANUP_TASK_NAME)
                        .description("Plan B snapshot cleanup")
                        .cronSchedule(CronExpressions.EVERY_10_MINUTES.getExpression())
                        .advanced(true));

        ScheduledJobsBinder.create(binder())
                .bindJobTo(SharedFileStoreMergeRunnable.class, builder -> builder
                        .name("Plan B Shared FS Merge")
                        .description("Distributed merge of sharded Plan B batch stores on the shared file store")
                        .cronSchedule(CronExpressions.EVERY_MINUTE.getExpression())
                        .advanced(true));

        GuiceUtil.buildMultiBinder(binder(), SharedFileStoreDocStore.class);

        bind(SharedFileStoreCleaner.class).asEagerSingleton();
        ScheduledJobsBinder.create(binder())
                .bindJobTo(ShardHousekeepingRunnable.class, builder -> builder
                        .name("Plan B Shard Housekeeping")
                        .description("Detects orphaned shard directories on the shared filesystem and "
                                + "moves them to trash, then drains trash entries from previous runs. "
                                + "Covers all PlanB doc types (PlanBDoc, TracesDoc, etc.).")
                        .cronSchedule(CronExpressions.EVERY_HOUR.getExpression())
                        .advanced(true));

        LifecycleBinder.create(binder())
                .bindStartupTaskTo(CleanerStartup.class);
    }

    private static class StateMergeRunnable extends RunnableWrapper {

        @Inject
        StateMergeRunnable(final MergeProcessor mergeProcessor) {
            super(mergeProcessor::merge);
        }
    }

    private static class StateMaintenanceRunnable extends RunnableWrapper {

        @Inject
        StateMaintenanceRunnable(final MergeProcessor mergeProcessor) {
            super(mergeProcessor::maintainShards);
        }
    }

    private static class SnapshotCreatorRunnable extends RunnableWrapper {

        @Inject
        SnapshotCreatorRunnable(final ShardManager shardManager) {
            super(shardManager::createSnapshots);
        }
    }

    private static class ShardManagerCleanupRunnable extends RunnableWrapper {

        @Inject
        ShardManagerCleanupRunnable(final ShardManager shardManager) {
            super(shardManager::cleanup);
        }
    }

    private static class SharedFileStoreMergeRunnable extends RunnableWrapper {

        @Inject
        SharedFileStoreMergeRunnable(final SharedFileStoreMergeProcessor mergeProcessor) {
            super(mergeProcessor::merge);
        }
    }

    private static class ShardHousekeepingRunnable extends RunnableWrapper {

        @Inject
        ShardHousekeepingRunnable(final SharedFileStoreCleaner executor) {
            super(executor::exec);
        }
    }


    // --------------------------------------------------------------------------------


    private static class CleanerStartup extends RunnableWrapper {

        @Inject
        CleanerStartup(final SharedFileStoreCleaner cleaner) {
            super(cleaner::startup);
        }
    }
}
