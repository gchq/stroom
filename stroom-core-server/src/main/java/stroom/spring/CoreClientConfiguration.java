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

package stroom.spring;

import io.dropwizard.setup.Environment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import stroom.feed.MetaMap;
import stroom.node.server.StroomPropertyService;
import stroom.proxy.repo.MetaMapFactory;
import stroom.servicediscovery.ServiceDiscoverer;
import stroom.servicediscovery.ServiceDiscovererImpl;
import stroom.servicediscovery.ServiceDiscoveryManager;
import stroom.servicediscovery.ServiceDiscoveryRegistrar;
import stroom.statistics.server.sql.StatisticsQueryService;
import stroom.util.config.StroomProperties;
import stroom.util.spring.StroomScope;
import stroom.util.thread.ThreadLocalBuffer;

/**
 * Exclude other configurations that might be found accidentally during a
 * component scan as configurations should be specified explicitly.
 */
@Configuration
public class CoreClientConfiguration {
    @Bean
    @Scope(StroomScope.REQUEST)
    public MetaMap metaMap() {
        return new MetaMapFactory().create();
    }

    @Bean
    @Scope(StroomScope.REQUEST)
    public ThreadLocalBuffer requestThreadLocalBuffer() {
        final ThreadLocalBuffer threadLocalBuffer = new ThreadLocalBuffer();
        threadLocalBuffer.setBufferSize(StroomProperties.getProperty("stroom.buffersize"));
        return threadLocalBuffer;
    }

    @Bean
    public ServiceDiscoveryRegistrar serviceDiscoveryRegistrar(final Environment environment,
                                                               final ServiceDiscoveryManager serviceDiscoveryManager,
                                                               final StroomPropertyService stroomPropertyService) {
        final ServiceDiscoveryRegistrar serviceDiscoveryRegistrar = new ServiceDiscoveryRegistrar(serviceDiscoveryManager, stroomPropertyService);

        // Add health check
        environment.healthChecks().register(serviceDiscoveryRegistrar.getClass().getSimpleName() + "HealthCheck", serviceDiscoveryRegistrar.getHealthCheck());

        return serviceDiscoveryRegistrar;
    }

    @Bean
    public ServiceDiscovererImpl serviceDiscoverer(final Environment environment, final ServiceDiscoveryManager serviceDiscoveryManager) {
        final ServiceDiscovererImpl serviceDiscoverer = new ServiceDiscovererImpl(serviceDiscoveryManager);

        // Add health check
        environment.healthChecks().register(serviceDiscoverer.getClass().getSimpleName() + "HealthCheck", serviceDiscoverer.getHealthCheck());


        return serviceDiscoverer;
    }
}
