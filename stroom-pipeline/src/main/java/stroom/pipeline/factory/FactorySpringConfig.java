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

package stroom.pipeline.factory;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import stroom.entity.DocumentPermissionCache;
import stroom.pipeline.PipelineService;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.security.SecurityContext;
import stroom.task.TaskManager;
import stroom.util.cache.CacheManager;
import stroom.guice.StroomBeanStore;
import stroom.util.spring.StroomScope;
import stroom.task.TaskContext;

@Configuration
public class FactorySpringConfig {
    @Bean
    public ElementRegistryFactory elementRegistryFactory(final StroomBeanStore beanStore) {
        return new ElementRegistryFactoryImpl(beanStore);
    }

    @Bean
    public PipelineDataCache pipelineDataCache(final CacheManager cacheManager,
                                               final PipelineStackLoader pipelineStackLoader,
                                               final SecurityContext securityContext,
                                               final DocumentPermissionCache documentPermissionCache) {
        return new PipelineDataCacheImpl(cacheManager, pipelineStackLoader, securityContext, documentPermissionCache);
    }

    @Bean
    public PipelineDataValidator pipelineDataValidator(final ElementRegistryFactory pipelineElementRegistryFactory) {
        return new PipelineDataValidator(pipelineElementRegistryFactory);
    }

    @Bean
    @Scope(StroomScope.TASK)
    public PipelineFactory pipelineFactory(final ElementRegistryFactory pipelineElementRegistryFactory,
                                           final ElementFactory elementFactory,
                                           final ProcessorFactory processorFactory,
                                           final TaskContext taskContext) {
        return new PipelineFactory(pipelineElementRegistryFactory, elementFactory, processorFactory, taskContext);
    }

    @Bean
    public PipelineStackLoader pipelineStackLoader(final PipelineService pipelineService) {
        return new PipelineStackLoaderImpl(pipelineService);
    }

    @Bean
    @Scope(StroomScope.TASK)
    public ProcessorFactory processorFactory(final TaskManager taskManager, final ErrorReceiverProxy errorReceiverProxy) {
        return new ProcessorFactoryImpl(taskManager, errorReceiverProxy);
    }
}