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

package stroom.streamstore.tools;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import stroom.feed.FeedService;
import stroom.index.IndexService;
import stroom.pipeline.PipelineService;
import stroom.pipeline.TextConverterService;
import stroom.pipeline.XSLTService;
import stroom.streamstore.StreamStore;
import stroom.streamtask.StreamProcessorFilterService;
import stroom.streamtask.StreamProcessorService;
import stroom.test.CommonTestControl;
import stroom.test.CommonTestScenarioCreator;
import stroom.util.spring.StroomSpringProfiles;

@Configuration
public class ToolsSpringConfig {
    @Bean
    @Profile(StroomSpringProfiles.IT)
    public StoreCreationTool storeCreationTool(final StreamStore streamStore,
                                               final FeedService feedService,
                                               final TextConverterService textConverterService,
                                               final XSLTService xsltService,
                                               final PipelineService pipelineService,
                                               final CommonTestScenarioCreator commonTestScenarioCreator,
                                               final CommonTestControl commonTestControl,
                                               final StreamProcessorService streamProcessorService,
                                               final StreamProcessorFilterService streamProcessorFilterService,
                                               final IndexService indexService) {
        return new StoreCreationTool(streamStore,
                feedService,
                textConverterService,
                xsltService,
                pipelineService,
                commonTestScenarioCreator,
                commonTestControl,
                streamProcessorService,
                streamProcessorFilterService,
                indexService);
    }
}