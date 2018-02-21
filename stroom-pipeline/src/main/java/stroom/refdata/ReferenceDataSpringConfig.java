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

package stroom.refdata;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import stroom.entity.server.DocumentPermissionCache;
import stroom.feed.server.FeedService;
import stroom.io.StreamCloser;
import stroom.pipeline.server.LocationFactoryProxy;
import stroom.pipeline.server.PipelineService;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.factory.PipelineDataCache;
import stroom.pipeline.server.factory.PipelineFactory;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.PipelineHolder;
import stroom.pipeline.state.StreamHolder;
import stroom.security.SecurityContext;
import stroom.streamstore.server.StreamStore;
import stroom.task.server.TaskManager;
import stroom.util.cache.CacheManager;
import stroom.util.spring.StroomScope;
import stroom.util.task.TaskMonitor;

import javax.inject.Named;

@Configuration
public class ReferenceDataSpringConfig {
    @Bean
    public ContextDataLoader contextDataLoader(final TaskManager taskManager) {
        return new ContextDataLoaderImpl(taskManager);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public ContextDataLoadTaskHandler contextDataLoadTaskHandler(final PipelineFactory pipelineFactory,
                                                                 final MapStoreHolder mapStoreHolder,
                                                                 final FeedHolder feedHolder,
                                                                 final ErrorReceiverProxy errorReceiverProxy,
                                                                 @Named("cachedPipelineService") final PipelineService pipelineService,
                                                                 final PipelineDataCache pipelineDataCache) {
        return new ContextDataLoadTaskHandler(pipelineFactory, mapStoreHolder, feedHolder, errorReceiverProxy, pipelineService, pipelineDataCache);
    }

    @Bean
    public EffectiveStreamCache effectiveStreamCache(final CacheManager cacheManager,
                                                     final StreamStore streamStore,
                                                     final EffectiveStreamInternPool internPool,
                                                     final SecurityContext securityContext) {
        return new EffectiveStreamCache(cacheManager, streamStore, internPool, securityContext);
    }

    @Bean
    public EffectiveStreamInternPool effectiveStreamInternPool() {
        return new EffectiveStreamInternPool();
    }

    @Bean
    public MapStoreCache mapStoreCache(final CacheManager cacheManager,
                                       final ReferenceDataLoader referenceDataLoader,
                                       final MapStoreInternPool internPool,
                                       final SecurityContext securityContext) {
        return new MapStoreCache(cacheManager, referenceDataLoader, internPool, securityContext);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public MapStoreHolder mapStoreHolder() {
        return new MapStoreHolder();
    }

    @Bean
    public MapStoreInternPool mapStoreInternPool() {
        return new MapStoreInternPool();
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public ReferenceData referenceData(final EffectiveStreamCache effectiveStreamCache,
                                       final MapStoreCache mapStoreCache,
                                       final FeedHolder feedHolder,
                                       final StreamHolder streamHolder,
                                       final ContextDataLoader contextDataLoader,
                                       final DocumentPermissionCache documentPermissionCache) {
        return new ReferenceData(effectiveStreamCache, mapStoreCache, feedHolder, streamHolder, contextDataLoader, documentPermissionCache);
    }

    @Bean
    public ReferenceDataLoader referenceDataLoader(final TaskManager taskManager) {
        return new ReferenceDataLoaderImpl(taskManager);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public ReferenceDataLoadTaskHandler referenceDataLoadTaskHandler(final StreamStore streamStore,
                                                                     final PipelineFactory pipelineFactory,
                                                                     final MapStoreHolder mapStoreHolder,
                                                                     @Named("cachedFeedService") final FeedService feedService,
                                                                     @Named("cachedPipelineService") final PipelineService pipelineService,
                                                                     final PipelineHolder pipelineHolder,
                                                                     final FeedHolder feedHolder,
                                                                     final StreamHolder streamHolder,
                                                                     final LocationFactoryProxy locationFactory,
                                                                     final StreamCloser streamCloser,
                                                                     final ErrorReceiverProxy errorReceiverProxy,
                                                                     final TaskMonitor taskMonitor,
                                                                     final PipelineDataCache pipelineDataCache) {
        return new ReferenceDataLoadTaskHandler(streamStore,
                pipelineFactory,
                mapStoreHolder,
                feedService,
                pipelineService,
                pipelineHolder,
                feedHolder,
                streamHolder,
                locationFactory,
                streamCloser,
                errorReceiverProxy,
                taskMonitor,
                pipelineDataCache);
    }
}