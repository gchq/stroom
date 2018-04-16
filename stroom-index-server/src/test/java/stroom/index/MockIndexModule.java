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
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import stroom.entity.FindService;
import stroom.entity.shared.Clearable;
import stroom.importexport.ImportExportActionHandler;
import stroom.index.shared.Index;
import stroom.pipeline.factory.Element;

public class MockIndexModule extends AbstractModule {
    @Override
    protected void configure() {
//        bind(IndexShardManager.class).to(MockIndexShardManagerImpl.class);
        bind(IndexShardWriterCache.class).to(MockIndexShardWriterCache.class);
        bind(IndexConfigCache.class).to(IndexConfigCacheImpl.class);
        bind(IndexService.class).to(MockIndexService.class);
        bind(IndexVolumeService.class).to(MockIndexVolumeService.class);
        bind(IndexShardService.class).to(MockIndexShardService.class);
        bind(Indexer.class).to(MockIndexer.class);
//
//        final Multibinder<TaskHandler> taskHandlerBinder = Multibinder.newSetBinder(binder(), TaskHandler.class);
//        taskHandlerBinder.addBinding().to(CloseIndexShardActionHandler.class);
//        taskHandlerBinder.addBinding().to(DeleteIndexShardActionHandler.class);
//        taskHandlerBinder.addBinding().to(FlushIndexShardActionHandler.class);
//
//        final Multibinder<EntityEvent.Handler> entityEventHandlerBinder = Multibinder.newSetBinder(binder(), EntityEvent.Handler.class);
//        entityEventHandlerBinder.addBinding().to(IndexConfigCacheEntityEventHandler.class);
//

        final Multibinder<Clearable> clearableBinder = Multibinder.newSetBinder(binder(), Clearable.class);
        clearableBinder.addBinding().to(MockIndexService.class);
        clearableBinder.addBinding().to(MockIndexShardService.class);

        final Multibinder<Element> elementBinder = Multibinder.newSetBinder(binder(), Element.class);
        elementBinder.addBinding().to(IndexingFilter.class);
//
//        final Multibinder<ExplorerActionHandler> explorerActionHandlerBinder = Multibinder.newSetBinder(binder(), ExplorerActionHandler.class);
//        explorerActionHandlerBinder.addBinding().to(IndexServiceImpl.class);

        final Multibinder<ImportExportActionHandler> importExportActionHandlerBinder = Multibinder.newSetBinder(binder(), ImportExportActionHandler.class);
        importExportActionHandlerBinder.addBinding().to(MockIndexService.class);

        final MapBinder<String, Object> entityServiceByTypeBinder = MapBinder.newMapBinder(binder(), String.class, Object.class);
        entityServiceByTypeBinder.addBinding(Index.ENTITY_TYPE).to(MockIndexService.class);

        final Multibinder<FindService> findServiceBinder = Multibinder.newSetBinder(binder(), FindService.class);
        findServiceBinder.addBinding().to(MockIndexService.class);
    }
    //    @Bean
//    @Scope(StroomScope.TASK)
//    public CloseIndexShardActionHandler closeIndexShardActionHandler(final ClusterDispatchAsyncHelper dispatchHelper) {
//        return new CloseIndexShardActionHandler(dispatchHelper);
//    }
//
//    @Bean
//    @Scope(StroomScope.TASK)
//    public DeleteIndexShardActionHandler deleteIndexShardActionHandler(final ClusterDispatchAsyncHelper dispatchHelper) {
//        return new DeleteIndexShardActionHandler(dispatchHelper);
//    }
//
//    @Bean
//    @Scope(StroomScope.TASK)
//    public FlushIndexShardActionHandler flushIndexShardActionHandler(final ClusterDispatchAsyncHelper dispatchHelper) {
//        return new FlushIndexShardActionHandler(dispatchHelper);
//    }
//
//    @Bean
//    public IndexConfigCacheEntityEventHandler indexConfigCacheEntityEventHandler(final NodeCache nodeCache,
//                                                                                 final IndexConfigCacheImpl indexConfigCache,
//                                                                                 final IndexShardService indexShardService,
//                                                                                 final IndexShardWriterCache indexShardWriterCache) {
//        return new IndexConfigCacheEntityEventHandler(nodeCache, indexConfigCache, indexShardService, indexShardWriterCache);
//    }
//
//    @Bean
//    @Scope(StroomScope.PROTOTYPE)
//    public IndexingFilter indexingFilter(final StreamHolder streamHolder,
//                                         final LocationFactoryProxy locationFactory,
//                                         final Indexer indexer,
//                                         final ErrorReceiverProxy errorReceiverProxy,
//                                         final IndexConfigCache indexConfigCache) {
//        return new IndexingFilter(streamHolder, locationFactory, indexer, errorReceiverProxy, indexConfigCache);
//    }
//
//    @Bean
//    public StroomIndexQueryResource stroomIndexQueryResource(final SearchResultCreatorManager searchResultCreatorManager,
//                                                             final IndexService indexService,
//                                                             final SecurityContext securityContext) {
//        return new StroomIndexQueryResource(searchResultCreatorManager, indexService, securityContext);
//    }
}