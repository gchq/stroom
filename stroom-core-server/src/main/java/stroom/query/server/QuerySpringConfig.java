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

package stroom.query.server;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import stroom.feed.server.FeedService;
import stroom.node.server.NodeService;
import stroom.pipeline.server.PipelineService;
import stroom.streamstore.server.StreamTypeService;
import stroom.util.spring.StroomScope;

import javax.inject.Named;

@Configuration
public class QuerySpringConfig {
    @Bean
    @Scope(StroomScope.TASK)
    public FetchSuggestionsHandler fetchSuggestionsHandler(@Named("cachedFeedService") final FeedService feedService,
                                                           @Named("cachedPipelineService") final PipelineService pipelineService,
                                                           @Named("cachedStreamTypeService") final StreamTypeService streamTypeService,
                                                           @Named("cachedNodeService") final NodeService nodeService) {
        return new FetchSuggestionsHandler(feedService, pipelineService, streamTypeService, nodeService);
    }
}