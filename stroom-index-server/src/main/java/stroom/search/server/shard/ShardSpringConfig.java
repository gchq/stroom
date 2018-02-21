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

package stroom.search.server.shard;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import stroom.index.server.IndexShardService;
import stroom.index.server.IndexShardWriterCache;
import stroom.properties.StroomPropertyService;
import stroom.task.server.ExecutorProvider;
import stroom.task.server.TaskContext;
import stroom.util.cache.CacheManager;
import stroom.util.spring.StroomScope;

import javax.inject.Singleton;

@Configuration
public class ShardSpringConfig {
    @Bean
    @Singleton
    public IndexShardSearchTaskExecutor indexShardSearchTaskExecutor() {
        return new IndexShardSearchTaskExecutor();
    }

    @Bean
    @Scope(StroomScope.TASK)
    public IndexShardSearchTaskHandler indexShardSearchTaskHandler(final IndexShardSearcherCache indexShardSearcherCache,
                                                                   final StroomPropertyService propertyService,
                                                                   final ExecutorProvider executorProvider,
                                                                   final TaskContext taskContext) {
        return new IndexShardSearchTaskHandler(indexShardSearcherCache, propertyService, executorProvider, taskContext);
    }

    @Bean
    public IndexShardSearchTaskProperties indexShardSearchTaskProperties(final StroomPropertyService propertyService) {
        return new IndexShardSearchTaskProperties(propertyService);
    }

    @Bean
    public IndexShardSearcherCache indexShardSearcherCache(final CacheManager cacheManager,
                                                           final IndexShardService indexShardService,
                                                           final IndexShardWriterCache indexShardWriterCache,
                                                           final ExecutorProvider executorProvider,
                                                           final TaskContext taskContext) {
        return new IndexShardSearcherCacheImpl(cacheManager, indexShardService, indexShardWriterCache, executorProvider, taskContext);
    }
}