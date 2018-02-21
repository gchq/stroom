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

package stroom.pipeline.state;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import stroom.io.StreamCloser;
import stroom.streamtask.server.StreamProcessorService;
import stroom.util.spring.StroomScope;

@Configuration
public class PipelineStateSpringConfig {
    @Bean
    @Scope(value = StroomScope.TASK)
    public CurrentUserHolder currentUserHolder() {
        return new CurrentUserHolder();
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public FeedHolder feedHolder() {
        return new FeedHolder();
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public MetaData metaData() {
        return new MetaData();
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public MetaDataHolder metaDataHolder(final StreamHolder streamHolder,
                                         final StreamProcessorService streamProcessorService) {
        return new MetaDataHolder(streamHolder, streamProcessorService);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public PipelineContext pipelineContext() {
        return new PipelineContext();
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public PipelineHolder pipelineHolder() {
        return new PipelineHolder();
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public RecordCount recordCount() {
        return new RecordCount();
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public SearchIdHolder searchIdHolder() {
        return new SearchIdHolder();
    }

    @Bean
    @Scope(StroomScope.TASK)
    public StreamHolder streamHolder(final StreamCloser streamCloser) {
        return new StreamHolder(streamCloser);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public StreamProcessorHolder streamProcessorHolder() {
        return new StreamProcessorHolder();
    }
}