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

package stroom.xml;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import stroom.pipeline.PipelineService;
import stroom.pipeline.TextConverterService;
import stroom.pipeline.XSLTService;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.factory.PipelineFactory;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.RecordCount;
import stroom.streamstore.StreamStore;
import stroom.util.spring.StroomScope;

@Configuration
public class XmlSpringConfig {
    @Bean
    @Scope(StroomScope.TASK)
    public F2XTestUtil f2XTestUtil(final PipelineFactory pipelineFactory,
                                   final FeedHolder feedHolder,
                                   final TextConverterService textConverterService,
                                   final XSLTService xsltService,
                                   final ErrorReceiverProxy errorReceiverProxy,
                                   final RecordCount recordCount,
                                   final StreamStore streamStore) {
        return new F2XTestUtil(pipelineFactory, feedHolder, textConverterService, xsltService, errorReceiverProxy, recordCount, streamStore);
    }

    @Bean
    @Scope(StroomScope.TASK)
    public XMLValidator xMLValidator(final PipelineFactory pipelineFactory,
                                     final PipelineService pipelineService,
                                     final ErrorReceiverProxy errorReceiver) {
        return new XMLValidator(pipelineFactory, pipelineService, errorReceiver);
    }
}