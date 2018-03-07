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

package stroom.pipeline.stepping;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import stroom.feed.FeedService;
import stroom.io.StreamCloser;
import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.PipelineService;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.factory.PipelineDataCache;
import stroom.pipeline.factory.PipelineFactory;
import stroom.pipeline.state.CurrentUserHolder;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.PipelineContext;
import stroom.pipeline.state.PipelineHolder;
import stroom.pipeline.state.StreamHolder;
import stroom.security.SecurityContext;
import stroom.streamstore.StreamStore;
import stroom.streamstore.StreamTypeService;
import stroom.task.TaskHandler;
import stroom.util.spring.StroomScope;
import stroom.task.TaskContext;

import javax.inject.Named;

public class PipelineSteppingModule extends AbstractModule {
    @Override
    protected void configure() {
        final Multibinder<TaskHandler> taskHandlerBinder = Multibinder.newSetBinder(binder(), TaskHandler.class);
        taskHandlerBinder.addBinding().to(stroom.pipeline.stepping.GetPipelineForStreamHandler.class);
        taskHandlerBinder.addBinding().to(stroom.pipeline.stepping.SteppingTaskHandler.class);
    }

    //    @Bean
//    @Scope(value = StroomScope.TASK)
//    public GetPipelineForStreamHandler getPipelineForStreamHandler(final StreamStore streamStore,
//                                                                   final PipelineService pipelineService,
//                                                                   final FeedService feedService,
//                                                                   final SecurityContext securityContext) {
//        return new GetPipelineForStreamHandler(streamStore, pipelineService, feedService, securityContext);
//    }
//
//    @Bean
//    @Scope(value = StroomScope.TASK)
//    public SteppingController steppingController(final StreamHolder streamHolder,
//                                                 final LocationFactoryProxy locationFactory,
//                                                 final SteppingResponseCache steppingResponseCache,
//                                                 final ErrorReceiverProxy errorReceiverProxy) {
//        return new SteppingController(streamHolder, locationFactory, steppingResponseCache, errorReceiverProxy);
//    }
//
//    @Bean
//    @Scope(value = StroomScope.TASK)
//    public SteppingResponseCache steppingResponseCache() {
//        return new SteppingResponseCache();
//    }
//
//    @Bean
//    @Scope(value = StroomScope.TASK)
//    public SteppingTaskHandler steppingTaskHandler(final StreamStore streamStore,
//                                                   final StreamCloser streamCloser,
//                                                   final FeedService feedService,
//                                                   @Named("cachedStreamTypeService") final StreamTypeService streamTypeService,
//                                                   final TaskContext taskContext,
//                                                   final FeedHolder feedHolder,
//                                                   final PipelineHolder pipelineHolder,
//                                                   final StreamHolder streamHolder,
//                                                   final LocationFactoryProxy locationFactory,
//                                                   final CurrentUserHolder currentUserHolder,
//                                                   final SteppingController controller,
//                                                   final PipelineService pipelineService,
//                                                   final PipelineFactory pipelineFactory,
//                                                   final ErrorReceiverProxy errorReceiverProxy,
//                                                   final SteppingResponseCache steppingResponseCache,
//                                                   final PipelineDataCache pipelineDataCache,
//                                                   final PipelineContext pipelineContext,
//                                                   final SecurityContext securityContext) {
//        return new SteppingTaskHandler(streamStore,
//                streamCloser,
//                feedService,
//                streamTypeService,
//                taskContext,
//                feedHolder,
//                pipelineHolder,
//                streamHolder,
//                locationFactory,
//                currentUserHolder,
//                controller,
//                pipelineService,
//                pipelineFactory,
//                errorReceiverProxy,
//                steppingResponseCache,
//                pipelineDataCache,
//                pipelineContext,
//                securityContext);
//    }
}