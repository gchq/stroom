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

package stroom.index.server;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import stroom.entity.server.util.StroomEntityManager;
import stroom.explorer.server.ExplorerActionHandlers;
import stroom.importexport.server.ImportExportActionHandlers;
import stroom.importexport.server.ImportExportHelper;
import stroom.index.shared.Index;
import stroom.node.server.NodeCache;
import stroom.properties.StroomPropertyService;
import stroom.node.server.VolumeService;
import stroom.pipeline.server.LocationFactoryProxy;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.state.StreamHolder;
import stroom.search.server.SearchResultCreatorManager;
import stroom.security.SecurityContext;
import stroom.task.cluster.ClusterDispatchAsyncHelper;
import stroom.task.server.ExecutorProvider;
import stroom.task.server.TaskContext;
import stroom.task.server.TaskManager;
import stroom.util.cache.CacheManager;
import stroom.util.spring.StroomScope;
import stroom.util.spring.StroomSpringProfiles;

import javax.inject.Inject;
import javax.inject.Provider;

@Configuration
public class IndexSpringConfig {
    @Inject
    public IndexSpringConfig(final ExplorerActionHandlers explorerActionHandlers,
                              final ImportExportActionHandlers importExportActionHandlers,
                              final IndexService indexService) {
        explorerActionHandlers.add(10, Index.ENTITY_TYPE, Index.ENTITY_TYPE, indexService);
        importExportActionHandlers.add(Index.ENTITY_TYPE, indexService);
    }

    @Bean
    @Scope(StroomScope.TASK)
    public CloseIndexShardActionHandler closeIndexShardActionHandler(final ClusterDispatchAsyncHelper dispatchHelper) {
        return new CloseIndexShardActionHandler(dispatchHelper);
    }

    @Bean
    @Scope(StroomScope.TASK)
    public DeleteIndexShardActionHandler deleteIndexShardActionHandler(final ClusterDispatchAsyncHelper dispatchHelper) {
        return new DeleteIndexShardActionHandler(dispatchHelper);
    }

    @Bean
    @Scope(StroomScope.TASK)
    public FlushIndexShardActionHandler flushIndexShardActionHandler(final ClusterDispatchAsyncHelper dispatchHelper) {
        return new FlushIndexShardActionHandler(dispatchHelper);
    }

    @Bean
    public IndexConfigCacheEntityEventHandler indexConfigCacheEntityEventHandler(final NodeCache nodeCache,
                                                                                 final IndexConfigCacheImpl indexConfigCache,
                                                                                 final IndexShardService indexShardService,
                                                                                 final IndexShardWriterCache indexShardWriterCache) {
        return new IndexConfigCacheEntityEventHandler(nodeCache, indexConfigCache, indexShardService, indexShardWriterCache);
    }

    @Bean
    public IndexConfigCache indexConfigCache(final CacheManager cacheManager,
                                             final IndexService indexService) {
        return new IndexConfigCacheImpl(cacheManager, indexService);
    }

    @Bean("indexService")
    @Profile(StroomSpringProfiles.PROD)
    public IndexService indexService(final StroomEntityManager entityManager,
                                     final ImportExportHelper importExportHelper,
                                     final SecurityContext securityContext) {
        return new IndexServiceImpl(entityManager, importExportHelper, securityContext);
    }

    @Bean
    public IndexShardManager indexShardManager(final IndexShardService indexShardService, final Provider<IndexShardWriterCache> indexShardWriterCacheProvider, final NodeCache nodeCache, final TaskManager taskManager) {
        return new IndexShardManagerImpl(indexShardService, indexShardWriterCacheProvider, nodeCache, taskManager);
    }

    @Bean
    @Profile(StroomSpringProfiles.PROD)
    public IndexShardService indexShardService(final StroomEntityManager entityManager, final VolumeService volumeService, final SecurityContext securityContext) {
        return new IndexShardServiceImpl(entityManager, volumeService, securityContext);
    }

    @Bean
    @Profile(StroomSpringProfiles.PROD)
    public IndexShardWriterCache indexShardWriterCache(final NodeCache nodeCache,
                                                       final IndexShardService indexShardService,
                                                       final StroomPropertyService stroomPropertyService,
                                                       final IndexConfigCache indexConfigCache,
                                                       final IndexShardManager indexShardManager,
                                                       final ExecutorProvider executorProvider,
                                                       final TaskContext taskContext) {
        return new IndexShardWriterCacheImpl(nodeCache, indexShardService, stroomPropertyService, indexConfigCache, indexShardManager, executorProvider, taskContext);
    }

    @Bean("indexer")
    public Indexer indexer(final IndexShardWriterCache indexShardWriterCache,
                           final IndexShardManager indexShardManager) {
        return new IndexerImpl(indexShardWriterCache, indexShardManager);
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public IndexingFilter indexingFilter(final StreamHolder streamHolder,
                                         final LocationFactoryProxy locationFactory,
                                         final Indexer indexer,
                                         final ErrorReceiverProxy errorReceiverProxy,
                                         final IndexConfigCache indexConfigCache) {
        return new IndexingFilter(streamHolder, locationFactory, indexer, errorReceiverProxy, indexConfigCache);
    }

    @Bean
    public StroomIndexQueryResource stroomIndexQueryResource(final SearchResultCreatorManager searchResultCreatorManager,
                                                             final IndexService indexService,
                                                             final SecurityContext securityContext) {
        return new StroomIndexQueryResource(searchResultCreatorManager, indexService, securityContext);
    }
}