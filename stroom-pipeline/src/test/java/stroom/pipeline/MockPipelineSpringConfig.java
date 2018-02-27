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

package stroom.pipeline;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import stroom.importexport.ImportExportHelper;
import stroom.util.spring.StroomScope;

@Configuration
public class MockPipelineSpringConfig {
    @Bean("pipelineService")
    public PipelineService pipelineService() {
        return new MockPipelineService();
    }

    @Bean("cachedPipelineService")
    public PipelineService cachedPipelineService(final PipelineService pipelineService) {
        return pipelineService;
    }

    @Bean
    public TextConverterService textConverterService(final ImportExportHelper importExportHelper) {
        return new MockTextConverterService(importExportHelper);
    }

    @Bean
    public XSLTService xSLTService(final ImportExportHelper importExportHelper) {
        return new MockXSLTService(importExportHelper);
    }

    @Bean
    public CustomURIResolver customURIResolver(final XSLTService xsltService) {
        return new CustomURIResolver(xsltService);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public LocationFactoryProxy locationFactoryProxy() {
        return new LocationFactoryProxy();
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
}