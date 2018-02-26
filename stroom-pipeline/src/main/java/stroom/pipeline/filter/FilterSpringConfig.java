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

package stroom.pipeline.filter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import stroom.cache.SchemaPool;
import stroom.cache.XSLTPool;
import stroom.node.shared.RecordCountService;
import stroom.properties.StroomPropertyService;
import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.XSLTService;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.writer.PathCreator;
import stroom.pipeline.state.PipelineContext;
import stroom.pipeline.state.RecordCount;
import stroom.pipeline.state.StreamHolder;
import stroom.refdata.MapStoreHolder;
import stroom.security.SecurityContext;
import stroom.util.spring.StroomScope;
import stroom.xmlschema.XMLSchemaCache;

@Configuration
public class FilterSpringConfig {
    @Bean
    public EventListInternPool eventListInternPool() {
        return new EventListInternPool();
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public HttpPostFilter httpPostFilter(final ErrorReceiverProxy errorReceiverProxy,
                                         final LocationFactoryProxy locationFactory) {
        return new HttpPostFilter(errorReceiverProxy, locationFactory);
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public IdEnrichmentFilter idEnrichmentFilter(final StreamHolder streamHolder,
                                                 final ErrorReceiverProxy errorReceiverProxy) {
        return new IdEnrichmentFilter(streamHolder, errorReceiverProxy);
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public RecordCountFilter recordCountFilter(final RecordCountService recordCountService,
                                               final RecordCount recordCount) {
        return new RecordCountFilter(recordCountService, recordCount);
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public RecordOutputFilter recordOutputFilter(final ErrorReceiverProxy errorReceiverProxy) {
        return new RecordOutputFilter(errorReceiverProxy);
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public ReferenceDataFilter referenceDataFilter(final MapStoreHolder mapStoreHolder,
                                                   final EventListInternPool internPool,
                                                   final ErrorReceiverProxy errorReceiverProxy) {
        return new ReferenceDataFilter(mapStoreHolder, internPool, errorReceiverProxy);
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public SAXEventRecorder sAXEventRecorder(final StreamHolder streamHolder,
                                             final ErrorReceiverProxy errorReceiverProxy) {
        return new SAXEventRecorder(streamHolder, errorReceiverProxy);
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public SAXRecordDetector sAXRecordDetector() {
        return new SAXRecordDetector();
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public SchemaFilter schemaFilter(final SchemaPool schemaPool,
                                     final XMLSchemaCache xmlSchemaCache,
                                     final ErrorReceiverProxy errorReceiverProxy,
                                     final LocationFactoryProxy locationFactory,
                                     final PipelineContext pipelineContext) {
        return new SchemaFilter(schemaPool, xmlSchemaCache, errorReceiverProxy, locationFactory, pipelineContext);
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public SchemaFilterSplit schemaFilterSplit(final SchemaFilter schemaFilter,
                                               final SecurityContext securityContext) {
        return new SchemaFilterSplit(schemaFilter, securityContext);
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public SplitFilter splitFilter() {
        return new SplitFilter();
    }

    @Bean
    @Scope(StroomScope.TASK)
//    @Profile(StroomSpringProfiles.TEST)
    public TestFilter testFilter(final ErrorReceiverProxy errorReceiverProxy,
                                 final LocationFactoryProxy locationFactory) {
        return new TestFilter(errorReceiverProxy, locationFactory);
    }

    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public XSLTFilter xSLTFilter(final XSLTPool xsltPool,
                                 final ErrorReceiverProxy errorReceiverProxy,
                                 final XSLTService xsltService,
                                 final StroomPropertyService stroomPropertyService,
                                 final LocationFactoryProxy locationFactory,
                                 final PipelineContext pipelineContext,
                                 final PathCreator pathCreator) {
        return new XSLTFilter(xsltPool, errorReceiverProxy, xsltService, stroomPropertyService, locationFactory, pipelineContext, pathCreator);
    }
}