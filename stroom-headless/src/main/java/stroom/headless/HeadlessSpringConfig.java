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

package stroom.headless;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Scope;
import stroom.dictionary.DictionarySpringConfig;
import stroom.explorer.ExplorerService;
import stroom.explorer.ExplorerSpringConfig;
import stroom.feed.FeedService;
import stroom.pipeline.ErrorWriterProxy;
import stroom.pipeline.PipelineService;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.RecordErrorReceiver;
import stroom.pipeline.factory.PipelineDataCache;
import stroom.pipeline.factory.PipelineFactory;
import stroom.pipeline.spring.PipelineConfiguration;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.MetaData;
import stroom.pipeline.state.PipelineHolder;
import stroom.pipeline.state.StreamHolder;
import stroom.spring.PersistenceConfiguration;
import stroom.spring.ScopeConfiguration;
import stroom.spring.ServerConfiguration;
import stroom.util.spring.StroomScope;

import javax.inject.Named;

@Configuration
@Import({//                DashboardConfiguration.class,
//                EventLoggingConfiguration.class,
//                IndexConfiguration.class,
//                MetaDataStatisticConfiguration.class,
        PersistenceConfiguration.class,
        DictionarySpringConfig.class,
        PipelineConfiguration.class,
        ScopeConfiguration.class,
//                ScriptConfiguration.class,
//                SearchConfiguration.class,
//                SecurityConfiguration.class,
//        ExplorerSpringConfig,
        ServerConfiguration.class,
//                StatisticsConfiguration.class,
//                VisualisationConfiguration.class,
        HeadlessConfiguration.class})
public class HeadlessSpringConfig {
    @Bean
    @Scope(StroomScope.TASK)
    public HeadlessTranslationTaskHandler headlessTranslationTaskHandler(final PipelineFactory pipelineFactory,
                                                                         @Named("cachedFeedService") final FeedService feedService,
                                                                         @Named("cachedPipelineService") final PipelineService pipelineService,
                                                                         final MetaData metaData,
                                                                         final PipelineHolder pipelineHolder,
                                                                         final FeedHolder feedHolder,
                                                                         final ErrorReceiverProxy errorReceiverProxy,
                                                                         final ErrorWriterProxy errorWriterProxy,
                                                                         final RecordErrorReceiver recordErrorReceiver,
                                                                         final PipelineDataCache pipelineDataCache,
                                                                         final StreamHolder streamHolder) {
        return new HeadlessTranslationTaskHandler(pipelineFactory,
                feedService,
                pipelineService,
                metaData,
                pipelineHolder,
                feedHolder,
                errorReceiverProxy,
                errorWriterProxy,
                recordErrorReceiver,
                pipelineDataCache,
                streamHolder);
    }
}