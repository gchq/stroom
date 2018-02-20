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
import org.springframework.context.annotation.Import;
import stroom.cache.server.PipelineCacheSpringConfig;
import stroom.explorer.server.ExplorerActionHandlers;
import stroom.importexport.server.ImportExportActionHandlers;
import stroom.pipeline.server.PipelineService;
import stroom.pipeline.server.TextConverterService;
import stroom.pipeline.server.XSLTService;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.TextConverter;
import stroom.pipeline.shared.XSLT;
import stroom.xmlschema.server.XMLSchemaService;
import stroom.xmlschema.shared.XMLSchema;

import javax.inject.Inject;

/**
 * Defines the component scanning required for the server module.
 * <p>
 * Defined separately from the main configuration so it can be easily
 * overridden.
 */
@Configuration
@ComponentScan(basePackages = {
        "stroom.pipeline",
        "stroom.refdata",
        "stroom.resource",
        "stroom.xml",
        "stroom.benchmark",
        "stroom.connectors.kafka"}, excludeFilters = {
        // Exclude other configurations that might be found accidentally
        // during a component scan as configurations should be specified
        // explicitly.
        @ComponentScan.Filter(type = FilterType.ANNOTATION, value = Configuration.class),})
@Import({PipelineCacheSpringConfig.class})
public class PipelineConfiguration {
    @Inject
    public PipelineConfiguration(final ExplorerActionHandlers explorerActionHandlers,
                                 final ImportExportActionHandlers importExportActionHandlers,
                                 final TextConverterService textConverterService,
                                 final XSLTService xsltService,
                                 final PipelineService pipelineService,
                                 final XMLSchemaService xmlSchemaService) {
        explorerActionHandlers.add(4, TextConverter.ENTITY_TYPE, "Text Converter", textConverterService);
        explorerActionHandlers.add(5, XSLT.ENTITY_TYPE, XSLT.ENTITY_TYPE, xsltService);
        explorerActionHandlers.add(6, PipelineEntity.ENTITY_TYPE, PipelineEntity.ENTITY_TYPE, pipelineService);
        explorerActionHandlers.add(13, XMLSchema.ENTITY_TYPE, "XML Schema", xmlSchemaService);
        importExportActionHandlers.add(TextConverter.ENTITY_TYPE, textConverterService);
        importExportActionHandlers.add(XSLT.ENTITY_TYPE, xsltService);
        importExportActionHandlers.add(PipelineEntity.ENTITY_TYPE, pipelineService);
        importExportActionHandlers.add(XMLSchema.ENTITY_TYPE, xmlSchemaService);
    }
}
