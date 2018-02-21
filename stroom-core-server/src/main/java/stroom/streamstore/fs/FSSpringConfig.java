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

package stroom.streamstore.fs;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import stroom.entity.util.StroomDatabaseInfo;
import stroom.entity.util.StroomEntityManager;
import stroom.feed.FeedService;
import stroom.node.NodeCache;
import stroom.node.VolumeService;
import stroom.pipeline.PipelineService;
import stroom.security.SecurityContext;
import stroom.streamstore.ExpressionToFindCriteria;
import stroom.streamstore.StreamAttributeValueFlush;
import stroom.streamstore.StreamMaintenanceService;
import stroom.streamstore.StreamTypeService;
import stroom.streamtask.StreamProcessorService;
import stroom.task.TaskManager;
import stroom.util.spring.StroomScope;
import stroom.util.task.TaskMonitor;

import javax.inject.Named;

@Configuration
public class FSSpringConfig {
    @Bean
    @Scope(value = StroomScope.TASK)
    public FileSystemCleanExecutor fileSystemCleanExecutor(final VolumeService volumeService,
                                                           final TaskMonitor taskMonitor,
                                                           final TaskManager taskManager,
                                                           final NodeCache nodeCache,
                                                           @Value("#{propertyConfigurer.getProperty('stroom.fileSystemCleanBatchSize')}") final String fileSystemCleanBatchSize,
                                                           @Value("#{propertyConfigurer.getProperty('stroom.fileSystemCleanOldAge')}") final String oldFileAge,
                                                           @Value("#{propertyConfigurer.getProperty('stroom.fileSystemCleanDeleteOut')}") final String matchFile) {
        return new FileSystemCleanExecutor(volumeService, taskMonitor, taskManager, nodeCache, fileSystemCleanBatchSize, oldFileAge, matchFile);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public FileSystemCleanSubTaskHandler fileSystemCleanSubTaskHandler(final StreamMaintenanceService streamMaintenanceService,
                                                                       final TaskMonitor taskMonitor) {
        return new FileSystemCleanSubTaskHandler(streamMaintenanceService, taskMonitor);
    }

    @Bean
    public FileSystemStreamMaintenanceService fileSystemStreamMaintenanceService(final StroomEntityManager entityManager,
                                                                                 @Named("cachedStreamTypeService") final StreamTypeService streamTypeService) {
        return new FileSystemStreamMaintenanceService(entityManager, streamTypeService);
    }

    @Bean
    public FileSystemStreamStore fileSystemStreamStore(final StroomEntityManager entityManager,
                                                       final StroomDatabaseInfo stroomDatabaseInfo,
                                                       final NodeCache nodeCache,
                                                       @Named("cachedStreamProcessorService") final StreamProcessorService streamProcessorService,
                                                       @Named("cachedPipelineService") final PipelineService pipelineService,
                                                       @Named("cachedFeedService") final FeedService feedService,
                                                       @Named("cachedStreamTypeService") final StreamTypeService streamTypeService,
                                                       final VolumeService volumeService,
                                                       final StreamAttributeValueFlush streamAttributeValueFlush,
                                                       final ExpressionToFindCriteria expressionToFindCriteria,
                                                       final SecurityContext securityContext) {
        return new FileSystemStreamStoreImpl(entityManager, stroomDatabaseInfo, nodeCache, streamProcessorService, pipelineService, feedService, streamTypeService, volumeService, streamAttributeValueFlush, expressionToFindCriteria, securityContext);
    }

    @Bean
    public FileSystemStreamStoreTransactionHelper fileSystemStreamStoreTransactionHelper(final StroomDatabaseInfo stroomDatabaseInfo,
                                                                                         final StroomEntityManager stroomEntityManager) {
        return new FileSystemStreamStoreTransactionHelper(stroomDatabaseInfo, stroomEntityManager);
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public InputStreamProxy inputStreamProxy() {
        return new InputStreamProxy();
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public OutputStreamProxy outputStreamProxy() {
        return new OutputStreamProxy();
    }
}