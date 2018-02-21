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

package stroom.pipeline.server.writer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import stroom.connectors.kafka.StroomKafkaProducerFactoryService;
import stroom.feed.server.FeedService;
import stroom.io.StreamCloser;
import stroom.node.server.NodeCache;
import stroom.pipeline.server.LocationFactory;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.MetaData;
import stroom.pipeline.state.MetaDataHolder;
import stroom.pipeline.state.PipelineHolder;
import stroom.pipeline.state.SearchIdHolder;
import stroom.pipeline.state.StreamHolder;
import stroom.pipeline.state.StreamProcessorHolder;
import stroom.streamstore.server.StreamStore;
import stroom.streamstore.server.StreamTypeService;
import stroom.util.spring.StroomScope;

import java.io.OutputStream;

@Configuration
public class WriterSpringConfig {
    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public FileAppender fileAppender(final ErrorReceiverProxy errorReceiverProxy,
                                     final PathCreator pathCreator) {
        return new FileAppender(errorReceiverProxy, pathCreator);
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public HDFSFileAppender hDFSFileAppender(final ErrorReceiverProxy errorReceiverProxy,
                                             final PathCreator pathCreator) {
        return new HDFSFileAppender(errorReceiverProxy, pathCreator);
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public HTTPAppender hTTPAppender(final ErrorReceiverProxy errorReceiverProxy,
                                     final MetaDataHolder metaDataHolder) {
        return new HTTPAppender(errorReceiverProxy, metaDataHolder);
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public JSONWriter jSONWriter(final ErrorReceiverProxy errorReceiverProxy) {
        return new JSONWriter(errorReceiverProxy);
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public KafkaAppender kafkaAppender(final ErrorReceiverProxy errorReceiverProxy,
                                       final StroomKafkaProducerFactoryService stroomKafkaProducerFactoryService,
                                       final PathCreator pathCreator) {
        return new KafkaAppender(errorReceiverProxy, stroomKafkaProducerFactoryService, pathCreator);
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public OutputRecorder outputRecorder() {
        return new OutputRecorder();
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public PathCreator pathCreator(final FeedHolder feedHolder,
                                   final PipelineHolder pipelineHolder,
                                   final StreamHolder streamHolder,
                                   final SearchIdHolder searchIdHolder,
                                   final NodeCache nodeCache) {
        return new PathCreator(feedHolder, pipelineHolder, streamHolder, searchIdHolder, nodeCache);
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public RollingFileAppender rollingFileAppender(final PathCreator pathCreator) {
        return new RollingFileAppender(pathCreator);
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public RollingKafkaAppender rollingKafkaAppender(final StroomKafkaProducerFactoryService stroomKafkaProducerFactoryService,
                                                     final PathCreator pathCreator,
                                                     final ErrorReceiverProxy errorReceiverProxy) {
        return new RollingKafkaAppender(stroomKafkaProducerFactoryService, pathCreator, errorReceiverProxy);
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public RollingStreamAppender rollingStreamAppender(final StreamStore streamStore,
                                                       final StreamHolder streamHolder,
                                                       final FeedService feedService,
                                                       final StreamTypeService streamTypeService,
                                                       final NodeCache nodeCache) {
        return new RollingStreamAppender(streamStore, streamHolder, feedService, streamTypeService, nodeCache);
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public StreamAppender streamAppender(final ErrorReceiverProxy errorReceiverProxy,
                                         final StreamStore streamStore,
                                         final StreamHolder streamHolder,
                                         final FeedService feedService,
                                         final StreamTypeService streamTypeService,
                                         final StreamProcessorHolder streamProcessorHolder,
                                         final MetaData metaData,
                                         final StreamCloser streamCloser) {
        return new StreamAppender(errorReceiverProxy, streamStore, streamHolder, feedService, streamTypeService, streamProcessorHolder, metaData, streamCloser);
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public TestAppender testAppender(final ErrorReceiverProxy errorReceiverProxy,
                                     final OutputStream outputStream) {
        return new TestAppender(errorReceiverProxy, outputStream);
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public TextWriter textWriter(final ErrorReceiverProxy errorReceiverProxy) {
        return new TextWriter(errorReceiverProxy);
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public XMLWriter xMLWriter(final ErrorReceiverProxy errorReceiverProxy,
                               final LocationFactory locationFactory) {
        return new XMLWriter(errorReceiverProxy, locationFactory);
    }
}