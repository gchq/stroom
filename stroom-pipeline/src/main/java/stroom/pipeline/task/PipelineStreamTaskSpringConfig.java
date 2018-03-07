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

package stroom.pipeline.task;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import stroom.feed.FeedService;
import stroom.io.StreamCloser;
import stroom.node.NodeCache;
import stroom.pipeline.ErrorWriterProxy;
import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.PipelineService;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.RecordErrorReceiver;
import stroom.pipeline.factory.PipelineDataCache;
import stroom.pipeline.factory.PipelineFactory;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.MetaData;
import stroom.pipeline.state.PipelineHolder;
import stroom.pipeline.state.RecordCount;
import stroom.pipeline.state.SearchIdHolder;
import stroom.pipeline.state.StreamHolder;
import stroom.pipeline.state.StreamProcessorHolder;
import stroom.statistics.internal.InternalStatisticsReceiver;
import stroom.streamstore.StreamStore;
import stroom.util.spring.StroomScope;
import stroom.task.TaskContext;

import javax.inject.Named;

@Configuration
public class PipelineStreamTaskSpringConfig {
    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public PipelineStreamProcessor pipelineStreamProcessor(final PipelineFactory pipelineFactory,
                                                           final StreamStore streamStore,
                                                           @Named("cachedFeedService") final FeedService feedService,
                                                           @Named("cachedPipelineService") final PipelineService pipelineService,
                                                           final TaskContext taskContext,
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
                taskContext,
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