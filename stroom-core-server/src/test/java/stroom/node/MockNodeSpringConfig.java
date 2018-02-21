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

package stroom.node;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import stroom.node.server.MockGlobalPropertyService;
import stroom.node.server.MockNodeService;
import stroom.node.server.MockRecordCountService;
import stroom.util.spring.StroomSpringProfiles;

@Configuration
public class MockNodeSpringConfig {
    @Bean
    @Profile(StroomSpringProfiles.TEST)
    public MockGlobalPropertyService mockGlobalPropertyService() {
        return new MockGlobalPropertyService();
    }

    @Bean("nodeService")
    @Profile(StroomSpringProfiles.TEST)
    public MockNodeService mockNodeService() {
        return new MockNodeService();
    }

    @Bean
    @Profile(StroomSpringProfiles.TEST)
    public MockRecordCountService mockRecordCountService() {
        return new MockRecordCountService();
    }
}