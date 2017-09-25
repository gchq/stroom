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

package stroom.datafeed.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import stroom.feed.MetaMap;
import stroom.proxy.repo.MetaMapFactory;
import stroom.streamstore.server.MockStreamStore;
import stroom.util.spring.StroomScope;
import stroom.util.thread.ThreadLocalBuffer;

/**
 * @Configuration specific to TesetDataFeedServiceImpl.
 * <p>
 * The combination of mock and prod classes means this test needs
 * its own context.
 */
@Configuration
public class TestDataFeedServiceImplConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestDataFeedServiceImplConfiguration.class);

    public TestDataFeedServiceImplConfiguration() {
        LOGGER.info("TestDataFeedServiceImplConfiguration loading...");
    }

    @Bean
    @Scope(StroomScope.REQUEST)
    public ThreadLocalBuffer requestThreadLocalBuffer() {
        return new ThreadLocalBuffer();
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public MockMetaMapFactory metaMapFactory() {
        return new MockMetaMapFactory();
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public MetaMap metaMap(MetaMapFactory metaMapFactory) {
        return metaMapFactory.create();
    }

    @Bean
    public MockStreamStore mockStreamStore() {
        return new MockStreamStore();
    }
}
