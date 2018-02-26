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

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import stroom.streamstore.ExpressionToFindCriteria;
import stroom.streamstore.StreamStore;
import stroom.util.spring.StroomSpringProfiles;

@Configuration
public class MockStreamTaskSpringConfig {
    @Bean("streamProcessorFilterService")
    public StreamProcessorFilterService streamProcessorFilterService() {
        return new MockStreamProcessorFilterService();
    }

    @Bean("streamProcessorService")
    public StreamProcessorService streamProcessorService() {
        return new MockStreamProcessorService();
    }

    @Bean(name = "cachedStreamProcessorService")
    public StreamProcessorService cachedStreamProcessorService(final StreamProcessorService streamProcessorService) {
        return streamProcessorService;
    }

    @Bean
    public StreamTaskCreator streamTaskCreator(final StreamStore streamStore,
                                                       final StreamProcessorFilterService streamProcessorFilterService,
                                                       final ExpressionToFindCriteria expressionToFindCriteria) {
        return new MockStreamTaskCreator(streamStore, streamProcessorFilterService, expressionToFindCriteria);
    }

    @Bean
    public StreamTaskService streamTaskService() {
        return new MockStreamTaskService();
    }
}