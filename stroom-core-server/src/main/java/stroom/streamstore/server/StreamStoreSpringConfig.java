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

package stroom.streamstore.server;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import stroom.dictionary.server.DictionaryStore;
import stroom.entity.server.CachingEntityManager;
import stroom.entity.server.util.StroomEntityManager;
import stroom.feed.server.FeedService;
import stroom.internalstatistics.MetaDataStatistic;
import stroom.jobsystem.server.ClusterLockService;
import stroom.logging.StreamEventLog;
import stroom.properties.StroomPropertyService;
import stroom.pipeline.server.PipelineService;
import stroom.policy.server.DataRetentionService;
import stroom.security.SecurityContext;
import stroom.servlet.SessionResourceStore;
import stroom.streamtask.server.BatchIdTransactionHelper;
import stroom.streamtask.server.StreamProcessorService;
import stroom.task.server.TaskManager;
import stroom.util.cache.CacheManager;
import stroom.util.spring.StroomScope;
import stroom.util.task.TaskMonitor;

import javax.inject.Named;
import javax.inject.Provider;
import javax.sql.DataSource;

@Configuration
public class StreamStoreSpringConfig {
    @Bean("cachedStreamTypeService")
    public CachedStreamTypeService cachedStreamTypeService(final CachingEntityManager entityManager,
                                                           final StreamTypeServiceTransactionHelper streamTypeServiceTransactionHelper) {
        return new CachedStreamTypeService(entityManager, streamTypeServiceTransactionHelper);
    }

    @Bean
    public ExpressionToFindCriteria expressionToFindCriteria(@Named("cachedFeedService") final FeedService feedService,
                                                             @Named("cachedPipelineService") final PipelineService pipelineService,
                                                             final DictionaryStore dictionaryStore,
                                                             final StreamAttributeKeyService streamAttributeKeyService) {
        return new ExpressionToFindCriteria(feedService, pipelineService, dictionaryStore, streamAttributeKeyService);
    }

    @Bean
    @Scope(StroomScope.TASK)
    public DownloadDataHandler downloadDataHandler(final SessionResourceStore sessionResourceStore,
                                                   final TaskManager taskManager,
                                                   final StreamEventLog streamEventLog) {
        return new DownloadDataHandler(sessionResourceStore, taskManager, streamEventLog);
    }

    @Bean
    @Scope(StroomScope.TASK)
    public FetchFieldsHandler fetchFieldsHandler() {
        return new FetchFieldsHandler();
    }

    @Bean
    public StreamAttributeKeyService streamAttributeKeyService(final StroomEntityManager entityManager,
                                                               final CacheManager cacheManager) {
        return new StreamAttributeKeyServiceImpl(entityManager, cacheManager);
    }

    @Bean
    public StreamAttributeMapService streamAttributeMapService(@Named("cachedFeedService") final FeedService feedService,
                                                               @Named("cachedPipelineService") final PipelineService pipelineService,
                                                               @Named("cachedStreamTypeService") final StreamTypeService streamTypeService,
                                                               @Named("cachedStreamProcessorService") final StreamProcessorService streamProcessorService,
                                                               final StreamStore streamStore,
                                                               final Provider<DataRetentionService> dataRetentionServiceProvider,
                                                               final DictionaryStore dictionaryStore,
                                                               final StroomEntityManager entityManager,
                                                               final StreamAttributeKeyService streamAttributeKeyService,
                                                               final StreamMaintenanceService streamMaintenanceService,
                                                               final SecurityContext securityContext) {
        return new StreamAttributeMapServiceImpl(feedService, pipelineService, streamTypeService, streamProcessorService, streamStore, dataRetentionServiceProvider, dictionaryStore, entityManager, streamAttributeKeyService, streamMaintenanceService, securityContext);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public StreamAttributeValueDeleteExecutor streamAttributeValueDeleteExecutor(final BatchIdTransactionHelper batchIdTransactionHelper,
                                                                                 final ClusterLockService clusterLockService,
                                                                                 final StroomPropertyService propertyService,
                                                                                 final TaskMonitor taskMonitor) {
        return new StreamAttributeValueDeleteExecutor(batchIdTransactionHelper, clusterLockService, propertyService, taskMonitor);
    }

    @Bean
    public StreamAttributeValueFlush streamAttributeValueFlush(final StreamAttributeKeyService streamAttributeKeyService,
                                                               final StreamAttributeValueService streamAttributeValueService,
                                                               final StreamAttributeValueServiceTransactionHelper streamAttributeValueServiceTransactionHelper,
                                                               final StroomPropertyService stroomPropertyService,
                                                               final ClusterLockService clusterLockService) {
        return new StreamAttributeValueFlushImpl(streamAttributeKeyService, streamAttributeValueService, streamAttributeValueServiceTransactionHelper, stroomPropertyService, clusterLockService);
    }

    @Bean
    public StreamAttributeValueService streamAttributeValueService(final StroomEntityManager entityManager) {
        return new StreamAttributeValueServiceImpl(entityManager);
    }

    @Bean
    public StreamAttributeValueServiceTransactionHelper streamAttributeValueServiceTransactionHelper(final DataSource dataSource) {
        return new StreamAttributeValueServiceTransactionHelper(dataSource);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public StreamDeleteExecutor streamDeleteExecutor(final BatchIdTransactionHelper batchIdTransactionHelper,
                                                     final ClusterLockService clusterLockService,
                                                     final StroomPropertyService propertyService,
                                                     final TaskMonitor taskMonitor) {
        return new StreamDeleteExecutor(batchIdTransactionHelper, clusterLockService, propertyService, taskMonitor);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public StreamDownloadTaskHandler streamDownloadTaskHandler(final TaskMonitor taskMonitor,
                                                               final StreamStore streamStore) {
        return new StreamDownloadTaskHandler(taskMonitor, streamStore);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public StreamRetentionExecutor streamRetentionExecutor(final FeedService feedService,
                                                           final StreamStore streamStore,
                                                           final TaskMonitor taskMonitor,
                                                           final ClusterLockService clusterLockService) {
        return new StreamRetentionExecutor(feedService, streamStore, taskMonitor, clusterLockService);
    }

    @Bean("streamTypeService")
    public StreamTypeService streamTypeService(final StroomEntityManager entityManager,
                                               final StreamTypeServiceTransactionHelper streamTypeServiceTransactionHelper) {
        return new StreamTypeServiceImpl(entityManager, streamTypeServiceTransactionHelper);
    }

    @Bean
    public StreamTypeServiceTransactionHelper streamTypeServiceTransactionHelper(final StroomEntityManager stroomEntityManager) {
        return new StreamTypeServiceTransactionHelper(stroomEntityManager);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public StreamUploadTaskHandler streamUploadTaskHandler(final TaskMonitor taskMonitor,
                                                           final StreamStore streamStore,
                                                           @Named("cachedStreamTypeService") final StreamTypeService streamTypeService,
                                                           @Named("cachedFeedService") final FeedService feedService,
                                                           final MetaDataStatistic metaDataStatistics) {
        return new StreamUploadTaskHandler(taskMonitor, streamStore, streamTypeService, feedService, metaDataStatistics);
    }

    @Bean
    @Scope(StroomScope.TASK)
    public UploadDataHandler uploadDataHandler(final SessionResourceStore sessionResourceStore,
                                               final TaskManager taskManager) {
        return new UploadDataHandler(sessionResourceStore, taskManager);
    }
}