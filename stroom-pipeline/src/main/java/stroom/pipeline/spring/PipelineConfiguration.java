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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

/**
 * Defines the component scanning required for the server module.
 *
 * Defined separately from the main configuration so it can be easily
 * overridden.
 */
@Configuration
@ComponentScan(basePackages = { "stroom.pipeline", "stroom.refdata", "stroom.cache",
        "stroom.resource", "stroom.xml", "stroom.benchmark" }, excludeFilters = {
                // Exclude other configurations that might be found accidentally
                // during a component scan as configurations should be specified
                // explicitly.
                @ComponentScan.Filter(type = FilterType.ANNOTATION, value = Configuration.class), })
public class PipelineConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineConfiguration.class);

    public PipelineConfiguration() {
        LOGGER.info("PipelineConfiguration loading...");
    }
}
