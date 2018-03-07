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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import stroom.entity.StroomEntityManager;
import stroom.entity.event.EntityEvent;
import stroom.explorer.ExplorerActionHandler;
import stroom.importexport.ImportExportActionHandler;
import stroom.importexport.ImportExportHelper;
import stroom.node.NodeCache;
import stroom.node.VolumeService;
import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.factory.Element;
import stroom.pipeline.state.StreamHolder;
import stroom.properties.StroomPropertyService;
import stroom.search.SearchResultCreatorManager;
import stroom.security.SecurityContext;
import stroom.task.ExecutorProvider;
import stroom.task.TaskContext;
import stroom.task.TaskHandler;
import stroom.task.TaskManager;
import stroom.task.cluster.ClusterDispatchAsyncHelper;
import stroom.util.cache.CacheManager;
import stroom.util.spring.StroomScope;

import javax.inject.Provider;

public class IndexModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(IndexShardManager.class).to(IndexShardManagerImpl.class);
        bind(IndexShardWriterCache.class).to(IndexShardWriterCacheImpl.class);
        bind(IndexConfigCache.class).to(IndexConfigCacheImpl.class);
        bind(IndexService.class).to(IndexServiceImpl.class);
        bind(IndexShardService.class).to(IndexShardServiceImpl.class);
        bind(Indexer.class).to(IndexerImpl.class);

        final Multibinder<TaskHandler> taskHandlerBinder = Multibinder.newSetBinder(binder(), TaskHandler.class);
        taskHandlerBinder.addBinding().to(stroom.index.CloseIndexShardActionHandler.class);
        taskHandlerBinder.addBinding().to(stroom.index.DeleteIndexShardActionHandler.class);
        taskHandlerBinder.addBinding().to(stroom.index.FlushIndexShardActionHandler.class);

        final Multibinder<EntityEvent.Handler> entityEventHandlerBinder = Multibinder.newSetBinder(binder(), EntityEvent.Handler.class);
        entityEventHandlerBinder.addBinding().to(IndexConfigCacheEntityEventHandler.class);

        final Multibinder<Element> elementBinder = Multibinder.newSetBinder(binder(), Element.class);
        elementBinder.addBinding().to(stroom.index.IndexingFilter.class);

        final Multibinder<ExplorerActionHandler> explorerActionHandlerBinder = Multibinder.newSetBinder(binder(), ExplorerActionHandler.class);
        explorerActionHandlerBinder.addBinding().to(stroom.index.IndexServiceImpl.class);

        final Multibinder<ImportExportActionHandler> importExportActionHandlerBinder = Multibinder.newSetBinder(binder(), ImportExportActionHandler.class);
        importExportActionHandlerBinder.addBinding().to(stroom.index.IndexServiceImpl.class);
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