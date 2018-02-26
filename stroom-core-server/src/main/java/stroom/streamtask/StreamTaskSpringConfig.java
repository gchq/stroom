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

package stroom.streamtask;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import stroom.entity.CachingEntityManager;
import stroom.entity.StroomDatabaseInfo;
import stroom.entity.StroomEntityManager;
import stroom.feed.FeedService;
import stroom.internalstatistics.MetaDataStatistic;
import stroom.jobsystem.ClusterLockService;
import stroom.node.NodeCache;
import stroom.properties.StroomPropertyService;
import stroom.pipeline.PipelineService;
import stroom.proxy.repo.ProxyFileProcessor;
import stroom.security.SecurityContext;
import stroom.statistics.internal.InternalStatisticsReceiver;
import stroom.streamstore.ExpressionToFindCriteria;
import stroom.streamstore.StreamStore;
import stroom.task.ExecutorProvider;
import stroom.task.TaskManager;
import stroom.util.spring.StroomBeanStore;
import stroom.util.spring.StroomScope;
import stroom.util.task.TaskMonitor;

import javax.inject.Named;
import javax.inject.Provider;
import javax.sql.DataSource;

@Configuration
public class StreamTaskSpringConfig {
    @Bean
    public BatchIdTransactionHelper batchIdTransactionHelper(final StroomDatabaseInfo stroomDatabaseInfo,
                                                             final StroomEntityManager stroomEntityManager) {
        return new BatchIdTransactionHelper(stroomDatabaseInfo, stroomEntityManager);
    }

    @Bean("cachedStreamProcessorFilterService")
    public CachedStreamProcessorFilterService cachedStreamProcessorFilterService(final CachingEntityManager entityManager,
                                                                                 final StreamProcessorService streamProcessorService) {
        return new CachedStreamProcessorFilterService(entityManager, streamProcessorService);
    }

    @Bean("cachedStreamProcessorService")
    public CachedStreamProcessorService cachedStreamProcessorService(final CachingEntityManager entityManager) {
        return new CachedStreamProcessorService(entityManager);
    }

    @Bean
    @Scope(StroomScope.TASK)
    public CreateProcessorHandler createProcessorHandler(final StreamProcessorFilterService streamProcessorFilterService,
                                                         final PipelineService pipelineService) {
        return new CreateProcessorHandler(streamProcessorFilterService, pipelineService);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public CreateStreamTasksTaskHandler createStreamTasksTaskHandler(final StreamTaskCreator streamTaskCreator,
                                                                     final TaskMonitor taskMonitor) {
        return new CreateStreamTasksTaskHandler(streamTaskCreator, taskMonitor);
    }

    @Bean
    @Scope(StroomScope.TASK)
    public FetchProcessorHandler fetchProcessorHandler(final StreamProcessorFilterService streamProcessorFilterService,
                                                       final StreamProcessorService streamProcessorService,
                                                       final SecurityContext securityContext) {
        return new FetchProcessorHandler(streamProcessorFilterService, streamProcessorService, securityContext);
    }

    @Bean
    @Scope(value = StroomScope.PROTOTYPE)
    public ProxyAggregationExecutor proxyAggregationExecutor(final ProxyFileProcessorImpl proxyFileProcessor,
                                                             final TaskMonitor taskMonitor,
                                                             final ExecutorProvider executorProvider,
                                                             @Value("#{propertyConfigurer.getProperty('stroom.proxyDir')}") final String proxyDir,
                                                             @Value("#{propertyConfigurer.getProperty('stroom.proxyThreads')}") final String threadCount,
                                                             @Value("#{propertyConfigurer.getProperty('stroom.maxAggregation')}") final String maxAggregation,
                                                             @Value("#{propertyConfigurer.getProperty('stroom.maxAggregationScan')}") final String maxFileScan,
                                                             @Value("#{propertyConfigurer.getProperty('stroom.maxStreamSize')}") final String maxStreamSize) {
        return new ProxyAggregationExecutor(proxyFileProcessor, taskMonitor, executorProvider, proxyDir, threadCount, maxAggregation, maxFileScan, maxStreamSize);
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public ProxyFileProcessor proxyFileProcessor(final StreamStore streamStore,
                                                 @Named("cachedFeedService") final FeedService feedService,
                                                 final MetaDataStatistic metaDataStatistic,
                                                 @Value("#{propertyConfigurer.getProperty('stroom.maxAggregation')}") final String maxAggregation,
                                                 @Value("#{propertyConfigurer.getProperty('stroom.maxStreamSize')}") final String maxStreamSize) {
        return new ProxyFileProcessorImpl(streamStore, feedService, metaDataStatistic, maxAggregation, maxStreamSize);
    }

    @Bean
    @Scope(StroomScope.TASK)
    public ReprocessDataHandler reprocessDataHandler(final StreamProcessorFilterService streamProcessorFilterService,
                                                     final StreamStore streamStore) {
        return new ReprocessDataHandler(streamProcessorFilterService, streamStore);
    }

    @Bean("streamProcessorFilterService")
    public StreamProcessorFilterService streamProcessorFilterService(final StroomEntityManager entityManager,
                                                                     final StreamProcessorService streamProcessorService) {
        return new StreamProcessorFilterServiceImpl(entityManager, streamProcessorService);
    }

    @Bean("streamProcessorService")
    public StreamProcessorService streamProcessorService(final StroomEntityManager entityManager) {
        return new StreamProcessorServiceImpl(entityManager);
    }

    @Bean
    public StreamProcessorTaskFactory streamProcessorTaskFactory(final StreamTaskCreator streamTaskCreator) {
        return new StreamProcessorTaskFactory(streamTaskCreator);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public StreamProcessorTaskHandler streamProcessorTaskHandler(final StroomBeanStore beanStore,
                                                                 @Named("cachedStreamProcessorService") final StreamProcessorService streamProcessorService,
                                                                 @Named("cachedStreamProcessorFilterService") final StreamProcessorFilterService streamProcessorFilterService,
                                                                 final StreamTaskHelper streamTaskHelper,
                                                                 final StreamStore streamStore,
                                                                 final NodeCache nodeCache,
                                                                 final TaskMonitor taskMonitor) {
        return new StreamProcessorTaskHandler(beanStore, streamProcessorService, streamProcessorFilterService, streamTaskHelper, streamStore, nodeCache, taskMonitor);
    }

    @Bean("streamProcessorTaskTester")
    @Scope(StroomScope.PROTOTYPE)
    public StreamProcessorTaskTester streamProcessorTaskTester() {
        return new StreamProcessorTaskTester();
    }

    @Bean
    public StreamTaskCreator streamTaskCreator(final StreamProcessorFilterService streamProcessorFilterService,
                                               final StreamTaskCreatorTransactionHelper streamTaskTransactionHelper,
                                               final TaskManager taskManager,
                                               final NodeCache nodeCache,
                                               final StreamTaskService streamTaskService,
                                               final StreamTaskHelper streamTaskHelper,
                                               final StroomPropertyService propertyService,
                                               final Provider<InternalStatisticsReceiver> internalStatisticsReceiverProvider,
                                               final StreamStore streamStore,
                                               final SecurityContext securityContext,
                                               final ExpressionToFindCriteria expressionToFindCriteria) {
        return new StreamTaskCreatorImpl(streamProcessorFilterService, streamTaskTransactionHelper, taskManager, nodeCache, streamTaskService, streamTaskHelper, propertyService, internalStatisticsReceiverProvider, streamStore, securityContext, expressionToFindCriteria);
    }

    @Bean
    public StreamTaskCreatorTransactionHelper streamTaskCreatorTransactionHelper(final NodeCache nodeCache,
                                                                                 final ClusterLockService clusterLockService,
                                                                                 final StreamTaskService streamTaskService,
                                                                                 final StreamStore streamStore,
                                                                                 final StroomEntityManager stroomEntityManager,
                                                                                 @Named("dataSource") final DataSource dataSource) {
        return new StreamTaskCreatorTransactionHelper(nodeCache, clusterLockService, streamTaskService, streamStore, stroomEntityManager, dataSource);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public StreamTaskDeleteExecutor streamTaskDeleteExecutor(final BatchIdTransactionHelper batchIdTransactionHelper,
                                                             final ClusterLockService clusterLockService,
                                                             final StroomPropertyService propertyService,
                                                             final TaskMonitor taskMonitor,
                                                             final StreamTaskCreatorImpl streamTaskCreator,
                                                             final StreamProcessorFilterService streamProcessorFilterService) {
        return new StreamTaskDeleteExecutor(batchIdTransactionHelper, clusterLockService, propertyService, taskMonitor, streamTaskCreator, streamProcessorFilterService);
    }

    @Bean
    public StreamTaskHelper streamTaskHelper(final StreamTaskService streamTaskService) {
        return new StreamTaskHelper(streamTaskService);
    }

    @Bean
    public StreamTaskService streamTaskService(final StroomEntityManager entityManager,
                                               final StreamStore streamStore) {
        return new StreamTaskServiceImpl(entityManager, streamStore);
    }
}