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

package stroom.pipeline.server.task;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import stroom.feed.server.FeedService;
import stroom.io.StreamCloser;
import stroom.node.server.NodeCache;
import stroom.pipeline.server.ErrorWriterProxy;
import stroom.pipeline.server.LocationFactoryProxy;
import stroom.pipeline.server.PipelineService;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.errorhandler.RecordErrorReceiver;
import stroom.pipeline.server.factory.PipelineDataCache;
import stroom.pipeline.server.factory.PipelineFactory;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.MetaData;
import stroom.pipeline.state.PipelineHolder;
import stroom.pipeline.state.RecordCount;
import stroom.pipeline.state.SearchIdHolder;
import stroom.pipeline.state.StreamHolder;
import stroom.pipeline.state.StreamProcessorHolder;
import stroom.statistics.internal.InternalStatisticsReceiver;
import stroom.streamstore.server.StreamStore;
import stroom.util.spring.StroomScope;
import stroom.util.task.TaskMonitor;

import javax.inject.Named;

@Configuration
public class PipelineStreamTaskSpringConfig {
    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public PipelineStreamProcessor pipelineStreamProcessor(final PipelineFactory pipelineFactory,
                                                           final StreamStore streamStore,
                                                           @Named("cachedFeedService") final FeedService feedService,
                                                           @Named("cachedPipelineService") final PipelineService pipelineService,
                                                           final TaskMonitor taskMonitor,
                                                           final PipelineHolder pipelineHolder,
                                                           final FeedHolder feedHolder,
                                                           final StreamHolder streamHolder,
                                                           final SearchIdHolder searchIdHolder,
                                                           final LocationFactoryProxy locationFactory,
                                                           final StreamProcessorHolder streamProcessorHolder,
                                                           final ErrorReceiverProxy errorReceiverProxy,
                                                           final ErrorWriterProxy errorWriterProxy,
                                                           final MetaData metaData,
                                                           final RecordCount recordCount,
                                                           final StreamCloser streamCloser,
                                                           final RecordErrorReceiver recordErrorReceiver,
                                                           final NodeCache nodeCache,
                                                           final PipelineDataCache pipelineDataCache,
                                                           final InternalStatisticsReceiver internalStatisticsReceiver) {
        return new PipelineStreamProcessor(pipelineFactory,
                streamStore,
                feedService,
                pipelineService,
                taskMonitor,
                pipelineHolder,
                feedHolder,
                streamHolder,
                searchIdHolder,
                locationFactory,
                streamProcessorHolder,
                errorReceiverProxy,
                errorWriterProxy,
                metaData,
                recordCount,
                streamCloser,
                recordErrorReceiver,
                nodeCache,
                pipelineDataCache,
                internalStatisticsReceiver);
    }
}