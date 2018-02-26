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

package stroom.index;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import stroom.importexport.ImportExportHelper;
import stroom.util.spring.StroomSpringProfiles;

@Configuration
public class MockIndexSpringConfig {
    @Bean("indexService")
    public IndexService indexService(final ImportExportHelper importExportHelper) {
        return new MockIndexService(importExportHelper);
    }

    @Bean("indexShardService")
    public IndexShardService indexShardService() {
        return new MockIndexShardService();
    }

    @Bean("indexShardWriterCache")
    public IndexShardWriterCache indexShardWriterCache() {
        return new MockIndexShardWriterCache();
    }

//    @Bean
//    public MockIndexer mockIndexer() {
//        return new MockIndexer();
//    }


}