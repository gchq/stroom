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

package stroom.index.spring;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import stroom.util.logging.StroomLogger;

@Configuration
@ComponentScan(basePackages = { "stroom.index.server", "stroom.index.shared" }, excludeFilters = {
        // Exclude other configurations that might be found accidentally during
        // a component scan as configurations should be specified explicitly.
        @ComponentScan.Filter(type = FilterType.ANNOTATION, value = Configuration.class), })
public class IndexConfiguration {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(IndexConfiguration.class);

    public IndexConfiguration() {
        LOGGER.info("IndexConfiguration loading...");
    }
}
