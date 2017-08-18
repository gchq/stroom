/*
 * Copyright 2016 Crown Copyright
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

package stroom.pipeline.spring;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import stroom.dictionary.server.DictionaryService;
import stroom.dictionary.shared.Dictionary;
import stroom.explorer.server.ExplorerActionHandlers;
import stroom.feed.server.FeedService;
import stroom.feed.shared.Feed;
import stroom.pipeline.server.PipelineService;
import stroom.pipeline.server.TextConverterService;
import stroom.pipeline.server.XSLTService;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.TextConverter;
import stroom.pipeline.shared.XSLT;
import stroom.xmlschema.server.XMLSchemaService;
import stroom.xmlschema.shared.XMLSchema;

import javax.inject.Inject;
import javax.inject.Provider;

/**
 * Defines the component scanning required for the server module.
 * <p>
 * Defined separately from the main configuration so it can be easily
 * overridden.
 */
@Configuration
@ComponentScan(basePackages = {"stroom.pipeline", "stroom.refdata", "stroom.cache",
        "stroom.resource", "stroom.xml", "stroom.benchmark"}, excludeFilters = {
        // Exclude other configurations that might be found accidentally
        // during a component scan as configurations should be specified
        // explicitly.
        @ComponentScan.Filter(type = FilterType.ANNOTATION, value = Configuration.class),})
public class PipelineConfiguration {
    @Inject
    public PipelineConfiguration(final ExplorerActionHandlers explorerActionHandlers,
                               final Provider<TextConverterService> textConverterServiceProvider,
                               final Provider<XSLTService> xsltServiceProvider,
                               final Provider<PipelineService> pipelineServiceProvider,
                               final Provider<XMLSchemaService> xmlSchemaServiceProvider) {
        explorerActionHandlers.add(4, TextConverter.ENTITY_TYPE,"Text Converter", textConverterServiceProvider);
        explorerActionHandlers.add(5, XSLT.ENTITY_TYPE, XSLT.ENTITY_TYPE, xsltServiceProvider);
        explorerActionHandlers.add(6, PipelineEntity.ENTITY_TYPE, PipelineEntity.ENTITY_TYPE, pipelineServiceProvider);
        explorerActionHandlers.add(13, XMLSchema.ENTITY_TYPE, "XML Schema", xmlSchemaServiceProvider);
    }
}
