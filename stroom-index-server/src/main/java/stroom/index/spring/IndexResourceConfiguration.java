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

import io.dropwizard.setup.Environment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import stroom.index.server.StroomIndexQueryResource;
import stroom.index.shared.IndexService;
import stroom.search.server.SearchResultCreatorManager;

@Configuration
public class IndexResourceConfiguration {
    @Bean
    public StroomIndexQueryResource stroomIndexQueryResource(final Environment environment, final SearchResultCreatorManager searchResultCreatorManager, final IndexService indexService) {
        final StroomIndexQueryResource stroomIndexQueryResource = new StroomIndexQueryResource(searchResultCreatorManager, indexService);

        // Add health check
        environment.healthChecks().register(stroomIndexQueryResource.getClass().getSimpleName() + "HealthCheck", stroomIndexQueryResource.getHealthCheck());

        // Add resource.
        environment.jersey().register(stroomIndexQueryResource);

        return stroomIndexQueryResource;
    }
}
