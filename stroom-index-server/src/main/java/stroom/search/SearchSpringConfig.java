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

package stroom.search;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import stroom.dictionary.DictionaryStore;
import stroom.index.IndexService;
import stroom.index.IndexShardService;
import stroom.node.NodeCache;
import stroom.properties.StroomPropertyService;
import stroom.search.extraction.ExtractionTaskExecutor;
import stroom.search.extraction.ExtractionTaskHandler;
import stroom.search.extraction.ExtractionTaskProperties;
import stroom.search.shard.IndexShardSearchTaskExecutor;
import stroom.search.shard.IndexShardSearchTaskHandler;
import stroom.search.shard.IndexShardSearchTaskProperties;
import stroom.search.shard.IndexShardSearcherCache;
import stroom.security.SecurityContext;
import stroom.streamstore.StreamStore;
import stroom.task.cluster.ClusterDispatchAsync;
import stroom.task.cluster.ClusterDispatchAsyncHelper;
import stroom.task.cluster.ClusterResultCollectorCache;
import stroom.task.cluster.TargetNodeSetFactory;
import stroom.task.ExecutorProvider;
import stroom.task.TaskContext;
import stroom.task.TaskManager;
import stroom.util.cache.CacheManager;
import stroom.util.spring.StroomScope;
import stroom.util.task.TaskMonitor;

import javax.inject.Provider;

@Configuration
public class SearchSpringConfig {
    @Bean
    @Scope(value = StroomScope.TASK)
    public AsyncSearchTaskHandler asyncSearchTaskHandler(final TaskMonitor taskMonitor,
                                                         final TargetNodeSetFactory targetNodeSetFactory,
                                                         final ClusterDispatchAsync dispatcher,
                                                         final ClusterDispatchAsyncHelper dispatchHelper,
                                                         final ClusterResultCollectorCache clusterResultCollectorCache,
                                                         final IndexService indexService,
                                                         final IndexShardService indexShardService,
                                                         final TaskManager taskManager,
                                                         final SecurityContext securityContext) {
        return new AsyncSearchTaskHandler(taskMonitor,
                targetNodeSetFactory,
                dispatcher,
                dispatchHelper,
                clusterResultCollectorCache,
                indexService,
                indexShardService,
                taskManager,
                securityContext);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public ClusterSearchTaskHandler clusterSearchTaskHandler(final IndexService indexService,
                                                             final DictionaryStore dictionaryStore,
                                                             final TaskContext taskContext,
                                                             final CoprocessorFactory coprocessorFactory,
                                                             final IndexShardSearchTaskExecutor indexShardSearchTaskExecutor,
                                                             final IndexShardSearchTaskProperties indexShardSearchTaskProperties,
                                                             final IndexShardSearcherCache indexShardSearcherCache,
                                                             final ExtractionTaskExecutor extractionTaskExecutor,
                                                             final ExtractionTaskProperties extractionTaskProperties,
                                                             final StreamStore streamStore,
                                                             final SecurityContext securityContext,
                                                             @Value("#{propertyConfigurer.getProperty('stroom.search.maxBooleanClauseCount')}") final String maxBooleanClauseCount,
                                                             @Value("#{propertyConfigurer.getProperty('stroom.search.maxStoredDataQueueSize')}") final String maxStoredDataQueueSize,
                                                             final Provider<IndexShardSearchTaskHandler> indexShardSearchTaskHandlerProvider,
                                                             final Provider<ExtractionTaskHandler> extractionTaskHandlerProvider,
                                                             final ExecutorProvider executorProvider) {
        return new ClusterSearchTaskHandler(indexService,
                dictionaryStore,
                taskContext,
                coprocessorFactory,
                indexShardSearchTaskExecutor,
                indexShardSearchTaskProperties,
                indexShardSearcherCache,
                extractionTaskExecutor,
                extractionTaskProperties,
                streamStore,
                securityContext,
                maxBooleanClauseCount,
                maxStoredDataQueueSize,
                indexShardSearchTaskHandlerProvider,
                extractionTaskHandlerProvider,
                executorProvider);
    }

    @Bean
    public CoprocessorFactory coprocessorFactory() {
        return new CoprocessorFactory();
    }

    @Bean
    @Scope(value = StroomScope.PROTOTYPE)
    public EventSearchTaskHandler eventSearchTaskHandler(final ClusterResultCollectorCache clusterResultCollectorCache,
                                                         final TaskManager taskManager,
                                                         final NodeCache nodeCache,
                                                         final StroomPropertyService stroomPropertyService) {
        return new EventSearchTaskHandler(clusterResultCollectorCache,
                taskManager,
                nodeCache,
                stroomPropertyService);
    }

    @Bean
    public LuceneSearchStoreFactory luceneSearchStoreFactory(final IndexService indexService,
                                                             final DictionaryStore dictionaryStore,
                                                             final StroomPropertyService stroomPropertyService,
                                                             final NodeCache nodeCache,
                                                             final TaskManager taskManager,
                                                             final ClusterResultCollectorCache clusterResultCollectorCache,
                                                             @Value("#{propertyConfigurer.getProperty('stroom.search.maxBooleanClauseCount')}") final String maxBooleanClauseCount,
                                                             final SecurityContext securityContext) {
        return new LuceneSearchStoreFactory(indexService, dictionaryStore, stroomPropertyService, nodeCache, taskManager, clusterResultCollectorCache, maxBooleanClauseCount, securityContext);
    }

    @Bean
    public SearchResultCreatorManager searchResultCreatorManager(final CacheManager cacheManager,
                                                                 final LuceneSearchStoreFactory luceneSearchStoreFactory) {
        return new SearchResultCreatorManager(cacheManager, luceneSearchStoreFactory);
    }
}