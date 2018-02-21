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

package stroom.pipeline.server;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import stroom.entity.server.CachingEntityManager;
import stroom.entity.server.util.StroomEntityManager;
import stroom.feed.server.FeedService;
import stroom.importexport.server.ImportExportHelper;
import stroom.logging.StreamEventLog;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.factory.ElementRegistryFactory;
import stroom.pipeline.server.factory.PipelineDataCache;
import stroom.pipeline.server.factory.PipelineDataValidator;
import stroom.pipeline.server.factory.PipelineFactory;
import stroom.pipeline.server.factory.PipelineStackLoader;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.PipelineHolder;
import stroom.pipeline.state.StreamHolder;
import stroom.security.SecurityContext;
import stroom.streamstore.server.StreamStore;
import stroom.task.server.TaskManager;
import stroom.util.spring.StroomScope;

@Configuration
public class PipelineSpringConfig {
    @Bean("cachedPipelineService")
    public CachedPipelineService cachedPipelineService(final CachingEntityManager entityManager,
                                                       final ImportExportHelper importExportHelper,
                                                       final SecurityContext securityContext) {
        return new CachedPipelineService(entityManager, importExportHelper, securityContext);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public DefaultErrorWriter defaultErrorWriter() {
        return new DefaultErrorWriter();
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public ErrorWriterProxy errorWriterProxy() {
        return new ErrorWriterProxy();
    }

    @Bean
    @Scope(StroomScope.TASK)
    public FetchDataHandler fetchDataHandler(final StreamStore streamStore,
                                             final FeedService feedService,
                                             final FeedHolder feedHolder,
                                             final PipelineHolder pipelineHolder,
                                             final StreamHolder streamHolder,
                                             final PipelineService pipelineService,
                                             final PipelineFactory pipelineFactory,
                                             final ErrorReceiverProxy errorReceiverProxy,
                                             final PipelineDataCache pipelineDataCache,
                                             final StreamEventLog streamEventLog,
                                             final SecurityContext securityContext) {
        return new FetchDataHandler(streamStore,
                feedService,
                feedHolder,
                pipelineHolder,
                streamHolder,
                pipelineService,
                pipelineFactory,
                errorReceiverProxy,
                pipelineDataCache,
                streamEventLog,
                securityContext);
    }

    @Bean
    @Scope(StroomScope.TASK)
    public FetchDataWithPipelineHandler fetchDataWithPipelineHandler(final StreamStore streamStore,
                                                                     final FeedService feedService,
                                                                     final FeedHolder feedHolder,
                                                                     final PipelineHolder pipelineHolder,
                                                                     final StreamHolder streamHolder,
                                                                     final PipelineService pipelineService,
                                                                     final PipelineFactory pipelineFactory,
                                                                     final ErrorReceiverProxy errorReceiverProxy,
                                                                     final PipelineDataCache pipelineDataCache,
                                                                     final StreamEventLog streamEventLog,
                                                                     final SecurityContext securityContext) {
        return new FetchDataWithPipelineHandler(streamStore,
                feedService,
                feedHolder,
                pipelineHolder,
                streamHolder,
                pipelineService,
                pipelineFactory,
                errorReceiverProxy,
                pipelineDataCache,
                streamEventLog,
                securityContext);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public FetchPipelineDataHandler fetchPipelineDataHandler(final PipelineService pipelineService,
                                                             final PipelineStackLoader pipelineStackLoader,
                                                             final PipelineDataValidator pipelineDataValidator,
                                                             final SecurityContext securityContext) {
        return new FetchPipelineDataHandler(pipelineService, pipelineStackLoader, pipelineDataValidator, securityContext);
    }

    @Bean
    public FetchPipelineXMLHandler fetchPipelineXMLHandler(final PipelineService pipelineService) {
        return new FetchPipelineXMLHandler(pipelineService);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public FetchPropertyTypesHandler fetchPropertyTypesHandler(final ElementRegistryFactory pipelineElementRegistryFactory) {
        return new FetchPropertyTypesHandler(pipelineElementRegistryFactory);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public LocationFactoryProxy locationFactoryProxy() {
        return new LocationFactoryProxy();
    }

    @Bean("pipelineService")
    public PipelineService pipelineService(final StroomEntityManager entityManager,
                                           final ImportExportHelper importExportHelper,
                                           final SecurityContext securityContext) {
        return new PipelineServiceImpl(entityManager, importExportHelper, securityContext);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public PipelineStepActionHandler pipelineStepActionHandler(final TaskManager taskManager) {
        return new PipelineStepActionHandler(taskManager);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public SavePipelineXMLHandler savePipelineXMLHandler(final PipelineService pipelineService) {
        return new SavePipelineXMLHandler(pipelineService);
    }

    @Bean
    public TextConverterService textConverterService(final StroomEntityManager entityManager,
                                                     final ImportExportHelper importExportHelper,
                                                     final SecurityContext securityContext) {
        return new TextConverterServiceImpl(entityManager, importExportHelper, securityContext);
    }

    @Bean
    public XSLTService xSLTService(final StroomEntityManager entityManager,
                                   final ImportExportHelper importExportHelper,
                                   final SecurityContext securityContext) {
        return new XSLTServiceImpl(entityManager, importExportHelper, securityContext);
    }
}