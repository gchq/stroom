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

package stroom.streamtask.server;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import stroom.streamstore.server.ExpressionToFindCriteria;
import stroom.streamstore.server.StreamStore;
import stroom.util.spring.StroomSpringProfiles;

@Configuration
public class MockStreamTaskSpringConfig {
    @Bean("streamProcessorFilterService")
    @Profile(StroomSpringProfiles.TEST)
    public MockStreamProcessorFilterService mockStreamProcessorFilterService() {
        return new MockStreamProcessorFilterService();
    }

    @Bean("streamProcessorService")
    @Profile(StroomSpringProfiles.TEST)
    public MockStreamProcessorService mockStreamProcessorService() {
        return new MockStreamProcessorService();
    }

    @Bean
    @Profile(StroomSpringProfiles.TEST)
    public MockStreamTaskCreator mockStreamTaskCreator(final StreamStore streamStore,
                                                       final StreamProcessorFilterService streamProcessorFilterService,
                                                       final ExpressionToFindCriteria expressionToFindCriteria) {
        return new MockStreamTaskCreator(streamStore, streamProcessorFilterService, expressionToFindCriteria);
    }

    @Bean
    @Profile(StroomSpringProfiles.TEST)
    public MockStreamTaskService mockStreamTaskService() {
        return new MockStreamTaskService();
    }
}