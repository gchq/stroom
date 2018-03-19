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

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;
import stroom.entity.CachingEntityManager;
import stroom.entity.FindService;
import stroom.jobsystem.DistributedTaskFactory;
import stroom.persist.EntityManagerSupport;
import stroom.task.TaskHandler;

import javax.inject.Named;

public class StreamTaskModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(StreamTaskCreator.class).to(StreamTaskCreatorImpl.class);
        bind(StreamProcessorFilterService.class).to(StreamProcessorFilterServiceImpl.class);
        bind(StreamProcessorService.class).to(StreamProcessorServiceImpl.class);
        bind(StreamTaskService.class).to(StreamTaskServiceImpl.class);

        final Multibinder<TaskHandler> taskHandlerBinder = Multibinder.newSetBinder(binder(), TaskHandler.class);
        taskHandlerBinder.addBinding().to(stroom.streamtask.CreateProcessorHandler.class);
        taskHandlerBinder.addBinding().to(stroom.streamtask.CreateStreamTasksTaskHandler.class);
        taskHandlerBinder.addBinding().to(stroom.streamtask.FetchProcessorHandler.class);
        taskHandlerBinder.addBinding().to(stroom.streamtask.ReprocessDataHandler.class);
        taskHandlerBinder.addBinding().to(stroom.streamtask.StreamProcessorTaskHandler.class);

        final Multibinder<DistributedTaskFactory> distributedTaskFactoryBinder = Multibinder.newSetBinder(binder(), DistributedTaskFactory.class);
        distributedTaskFactoryBinder.addBinding().to(stroom.streamtask.StreamProcessorTaskFactory.class);

        final Multibinder<FindService> findServiceBinder = Multibinder.newSetBinder(binder(), FindService.class);
        findServiceBinder.addBinding().to(StreamTaskServiceImpl.class);
    }

    @Provides
    @Named("cachedStreamProcessorFilterService")
    public StreamProcessorFilterService cachedStreamProcessorFilterService(final CachingEntityManager entityManager,
                                                                           final EntityManagerSupport entityManagerSupport,
                                                                           final StreamProcessorService streamProcessorService) {
        return new StreamProcessorFilterServiceImpl(entityManager, entityManagerSupport, streamProcessorService);
    }

    @Provides
    @Named("cachedStreamProcessorService")
    public StreamProcessorService cachedStreamProcessorService(final CachingEntityManager entityManager) {
        return new StreamProcessorServiceImpl(entityManager);
    }

    //    @Bean
//    public BatchIdTransactionHelper batchIdTransactionHelper(final StroomDatabaseInfo stroomDatabaseInfo,
//                                                             final StroomEntityManager stroomEntityManager) {
//        return new BatchIdTransactionHelper(stroomDatabaseInfo, stroomEntityManager);
//    }
//
//    @Bean
//    @Scope(StroomScope.TASK)
//    public CreateProcessorHandler createProcessorHandler(final StreamProcessorFilterService streamProcessorFilterService,
//                                                         final PipelineService pipelineService) {
//        return new CreateProcessorHandler(streamProcessorFilterService, pipelineService);
//    }
//
//    @Bean
//    @Scope(value = StroomScope.TASK)
//    public CreateStreamTasksTaskHandler createStreamTasksTaskHandler(final StreamTaskCreator streamTaskCreator,
//                                                                     final TaskContext taskContext) {
//        return new CreateStreamTasksTaskHandler(streamTaskCreator, taskContext);
//    }
//
//    @Bean
//    @Scope(StroomScope.TASK)
//    public FetchProcessorHandler fetchProcessorHandler(final StreamProcessorFilterService streamProcessorFilterService,
//                                                       final StreamProcessorService streamProcessorService,
//                                                       final SecurityContext securityContext) {
//        return new FetchProcessorHandler(streamProcessorFilterService, streamProcessorService, securityContext);
//    }
//
//    @Bean
//    @Scope(value = StroomScope.PROTOTYPE)
//    public ProxyAggregationExecutor proxyAggregationExecutor(final ProxyFileProcessorImpl proxyFileProcessor,
//                                                             final TaskContext taskContext,
//                                                             final ExecutorProvider executorProvider,
//                                                             @Value(propertyConfigurer.getProperty('stroom.proxyDir')}") final String proxyDir,
//                                                             @Value(propertyConfigurer.getProperty('stroom.proxyThreads')}") final String threadCount,
//                                                             @Value(propertyConfigurer.getProperty('stroom.maxAggregation')}") final String maxAggregation,
//                                                             @Value(propertyConfigurer.getProperty('stroom.maxAggregationScan')}") final String maxFileScan,
//                                                             @Value(propertyConfigurer.getProperty('stroom.maxStreamSize')}") final String maxStreamSize) {
//        return new ProxyAggregationExecutor(proxyFileProcessor, taskContext, executorProvider, proxyDir, threadCount, maxAggregation, maxFileScan, maxStreamSize);
//    }
//
//    @Bean
//    @Scope(StroomScope.PROTOTYPE)
//    public ProxyFileProcessorImpl proxyFileProcessorImpl(final StreamStore streamStore,
//                                                 @Named("cachedFeedService") final FeedService feedService,
//                                                 final MetaDataStatistic metaDataStatistic,
//                                                 @Value(propertyConfigurer.getProperty('stroom.maxAggregation')}") final String maxAggregation,
//                                                 @Value(propertyConfigurer.getProperty('stroom.maxStreamSize')}") final String maxStreamSize) {
//        return new ProxyFileProcessorImpl(streamStore, feedService, metaDataStatistic, maxAggregation, maxStreamSize);
//    }
//
//    @Bean
//    @Scope(StroomScope.PROTOTYPE)
//    public ProxyFileProcessor proxyFileProcessor(final ProxyFileProcessorImpl proxyFileProcessor) {
//        return proxyFileProcessor;
//    }
//
//    @Bean
//    @Scope(StroomScope.TASK)
//    public ReprocessDataHandler reprocessDataHandler(final StreamProcessorFilterService streamProcessorFilterService,
//                                                     final StreamStore streamStore) {
//        return new ReprocessDataHandler(streamProcessorFilterService, streamStore);
//    }
//
//
//    @Bean
//    public StreamProcessorTaskFactory streamProcessorTaskFactory(final StreamTaskCreator streamTaskCreator) {
//        return new StreamProcessorTaskFactory(streamTaskCreator);
//    }
//
//    @Bean
//    @Scope(value = StroomScope.TASK)
//    public StreamProcessorTaskHandler streamProcessorTaskHandler(final StroomBeanStore beanStore,
//                                                                 @Named("cachedStreamProcessorService") final StreamProcessorService streamProcessorService,
//                                                                 @Named("cachedStreamProcessorFilterService") final StreamProcessorFilterService streamProcessorFilterService,
//                                                                 final StreamTaskHelper streamTaskHelper,
//                                                                 final StreamStore streamStore,
//                                                                 final NodeCache nodeCache,
//                                                                 final TaskContext taskContext) {
//        return new StreamProcessorTaskHandler(beanStore, streamProcessorService, streamProcessorFilterService, streamTaskHelper, streamStore, nodeCache, taskContext);
//    }
//
//    @Bean("streamProcessorTaskTester")
//    @Scope(StroomScope.PROTOTYPE)
//    public StreamProcessorTaskTester streamProcessorTaskTester() {
//        return new StreamProcessorTaskTester();
//    }
//
//    @Bean
//    public StreamTaskCreator streamTaskCreator(final StreamProcessorFilterService streamProcessorFilterService,
//                                               final StreamTaskCreatorTransactionHelper streamTaskTransactionHelper,
//                                               final TaskManager taskManager,
//                                               final NodeCache nodeCache,
//                                               final StreamTaskService streamTaskService,
//                                               final StreamTaskHelper streamTaskHelper,
//                                               final StroomPropertyService propertyService,
//                                               final Provider<InternalStatisticsReceiver> internalStatisticsReceiverProvider,
//                                               final StreamStore streamStore,
//                                               final SecurityContext securityContext,
//                                               final ExpressionToFindCriteria expressionToFindCriteria) {
//        return new StreamTaskCreatorImpl(streamProcessorFilterService, streamTaskTransactionHelper, taskManager, nodeCache, streamTaskService, streamTaskHelper, propertyService, internalStatisticsReceiverProvider, streamStore, securityContext, expressionToFindCriteria);
//    }
//
//    @Bean
//    public StreamTaskCreatorTransactionHelper streamTaskCreatorTransactionHelper(final NodeCache nodeCache,
//                                                                                 final ClusterLockService clusterLockService,
//                                                                                 final StreamTaskService streamTaskService,
//                                                                                 final StreamStore streamStore,
//                                                                                 final StroomEntityManager stroomEntityManager,
//                                                                                 @Named("dataSource") final DataSource dataSource) {
//        return new StreamTaskCreatorTransactionHelper(nodeCache, clusterLockService, streamTaskService, streamStore, stroomEntityManager, dataSource);
//    }
//
//    @Bean
//    @Scope(value = StroomScope.TASK)
//    public StreamTaskDeleteExecutor streamTaskDeleteExecutor(final BatchIdTransactionHelper batchIdTransactionHelper,
//                                                             final ClusterLockService clusterLockService,
//                                                             final StroomPropertyService propertyService,
//                                                             final TaskContext taskContext,
//                                                             final StreamTaskCreatorImpl streamTaskCreator,
//                                                             final StreamProcessorFilterService streamProcessorFilterService) {
//        return new StreamTaskDeleteExecutor(batchIdTransactionHelper, clusterLockService, propertyService, taskContext, streamTaskCreator, streamProcessorFilterService);
//    }
//
//    @Bean
//    public StreamTaskHelper streamTaskHelper(final StreamTaskService streamTaskService) {
//        return new StreamTaskHelper(streamTaskService);
//    }
}