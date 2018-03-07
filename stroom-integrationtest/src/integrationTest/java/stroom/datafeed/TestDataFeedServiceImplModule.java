/*
 * Copyright 2016 Crown Copyright
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

package stroom.datafeed;

import com.google.inject.AbstractModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.streamstore.MockStreamStore;

/**
 * @Configuration specific to TesetDataFeedServiceImpl.
 * <p>
 * The combination of mock and prod classes means this test needs
 * its own context.
 */
//@ComponentScan("ignore")
//@Configuration
//
//
//@Import({
//        stroom.cache.CacheSpringConfig.class,
//        DataFeedSpringConfig.class,
//        MockDataFeedSpringConfig.class,
//        stroom.dictionary.DictionarySpringConfig.class,
//        stroom.docstore.DocstoreSpringConfig.class,
//        stroom.docstore.db.DBSpringConfig.class,
//        stroom.entity.EntitySpringConfig.class,
//        stroom.explorer.ExplorerSpringConfig.class,
//        stroom.feed.MockFeedSpringConfig.class,
//        stroom.importexport.ImportExportSpringConfig.class,
//        stroom.index.MockIndexSpringConfig.class,
//        stroom.internalstatistics.MockMetaDataStatisticSpringConfig.class,
//        stroom.logging.LoggingSpringConfig.class,
//        stroom.node.MockNodeServiceSpringConfig.class,
//        stroom.node.NodeSpringConfig.class,
//        stroom.node.NodeTestSpringConfig.class,
//        stroom.pipeline.MockPipelineSpringConfig.class,
//        stroom.properties.PropertySpringConfig.class,
//        stroom.ruleset.RulesetSpringConfig.class,
//        stroom.security.MockSecuritySpringConfig.class,
//        stroom.security.MockSecurityContextSpringConfig.class,
//        stroom.spring.PersistenceConfiguration.class,
//        stroom.streamstore.MockStreamStoreSpringConfig.class,
//        stroom.streamtask.MockStreamTaskSpringConfig.class,
//        stroom.task.TaskSpringConfig.class,
//        MockTestControlSpringConfig.class,
//        stroom.util.cache.CacheManagerSpringConfig.class,
//        stroom.util.spring.UtilSpringConfig.class,
//        stroom.volume.MockVolumeSpringConfig.class,
//})
public class TestDataFeedServiceImplModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestDataFeedServiceImplModule.class);

    @Override
    protected void configure() {
        LOGGER.info("TestDataFeedServiceImplModule loading...");
    }


//    @Bean
//    public MockStreamStore mockStreamStore() {
//        return new MockStreamStore();
//    }
}
