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

package stroom.test;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import stroom.cache.StroomCacheManager;
import stroom.entity.server.util.StroomEntityManager;
import stroom.feed.server.FeedService;
import stroom.importexport.server.ImportExportService;
import stroom.index.server.IndexService;
import stroom.index.server.IndexShardManager;
import stroom.index.server.IndexShardWriterCache;
import stroom.node.server.NodeCache;
import stroom.node.server.NodeConfig;
import stroom.node.server.VolumeService;
import stroom.streamstore.server.StreamAttributeKeyService;
import stroom.streamstore.server.StreamAttributeValueFlush;
import stroom.streamstore.server.StreamStore;
import stroom.streamstore.server.tools.StoreCreationTool;
import stroom.streamtask.server.StreamProcessorFilterService;
import stroom.streamtask.server.StreamProcessorService;
import stroom.streamtask.server.StreamTaskCreator;
import stroom.task.server.TaskManager;
import stroom.util.spring.StroomSpringProfiles;

@Configuration
public class TestSpringConfig {
    @Bean
    public CommonTestScenarioCreator commonTestScenarioCreator(final FeedService feedService,
                                                               final StreamStore streamStore,
                                                               final StreamProcessorService streamProcessorService,
                                                               final StreamProcessorFilterService streamProcessorFilterService,
                                                               final IndexService indexService,
                                                               final VolumeService volumeService,
                                                               final NodeCache nodeCache) {
        return new CommonTestScenarioCreator(feedService, streamStore, streamProcessorService, streamProcessorFilterService, indexService, volumeService, nodeCache);
    }

    @Bean
    @Profile(StroomSpringProfiles.IT)
    public CommonTranslationTest commonTranslationTest(final NodeCache nodeCache,
                                                       final StreamTaskCreator streamTaskCreator,
                                                       final StoreCreationTool storeCreationTool,
                                                       final TaskManager taskManager,
                                                       final StreamStore streamStore) {
        return new CommonTranslationTest(nodeCache, streamTaskCreator, storeCreationTool, taskManager, streamStore);
    }

    @Bean
    public ContentImportService contentImportService(final ImportExportService importExportService) {
        return new ContentImportService(importExportService);
    }

    @Bean
    public DatabaseCommonTestControl databaseCommonTestControl(final VolumeService volumeService,
                                                               final ContentImportService contentImportService,
                                                               final StreamAttributeKeyService streamAttributeKeyService,
                                                               final StreamAttributeValueFlush streamAttributeValueFlush,
                                                               final IndexShardManager indexShardManager,
                                                               final IndexShardWriterCache indexShardWriterCache,
                                                               final DatabaseCommonTestControlTransactionHelper databaseCommonTestControlTransactionHelper,
                                                               final NodeConfig nodeConfig,
                                                               final StreamTaskCreator streamTaskCreator,
                                                               final StroomCacheManager stroomCacheManager) {
        return new DatabaseCommonTestControl(volumeService, contentImportService, streamAttributeKeyService, streamAttributeValueFlush, indexShardManager, indexShardWriterCache, databaseCommonTestControlTransactionHelper, nodeConfig, streamTaskCreator, stroomCacheManager);
    }

    @Bean
    public DatabaseCommonTestControlTransactionHelper databaseCommonTestControlTransactionHelper(final StroomEntityManager entityManager) {
        return new DatabaseCommonTestControlTransactionHelper(entityManager);
    }

    @Bean
    @Profile(StroomSpringProfiles.TEST)
    public MockCommonTestControl mockCommonTestControl() {
        return new MockCommonTestControl();
    }
}