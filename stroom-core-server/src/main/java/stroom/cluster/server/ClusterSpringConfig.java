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

package stroom.cluster.server;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import stroom.cache.CacheSpringConfig;
import stroom.node.server.NodeCache;
import stroom.node.shared.ClientPropertiesService;
import stroom.task.server.TaskManager;
import stroom.util.spring.StroomBeanStore;

import javax.inject.Named;


@Configuration
@Import({CacheSpringConfig.class})
public class ClusterSpringConfig {

    @Bean("clusterCallServiceLocal")
    public ClusterCallServiceLocal clusterCallServiceLocal(final StroomBeanStore beanStore, final NodeCache nodeCache) {
        return new ClusterCallServiceLocal(beanStore, nodeCache);
    }

    @Bean
    public ClusterCallServiceRPC clusterCallServiceRPC(@Named("clusterCallServiceLocal") final ClusterCallService clusterCallService) {
        return new ClusterCallServiceRPC(clusterCallService);
    }

    @Bean("clusterCallServiceRemote")
    public ClusterCallServiceRemote clusterCallServiceRemote(final NodeCache nodeCache,
                                                             final StroomBeanStore beanStore,
                                                             @Value("#{propertyConfigurer.getProperty('stroom.clusterCallUseLocal')}") final boolean clusterCallUseLocal,
                                                             @Value("#{propertyConfigurer.getProperty('stroom.clusterCallReadTimeout')}") final String clusterCallReadTimeout,
                                                             @Value("#{propertyConfigurer.getProperty('stroom.clusterCallIgnoreSSLHostnameVerifier')}") final boolean ignoreSSLHostnameVerifier) {
        return new ClusterCallServiceRemote(nodeCache, beanStore, clusterCallUseLocal, clusterCallReadTimeout, ignoreSSLHostnameVerifier);
    }

    @Bean(ClusterNodeManager.BEAN_NAME)
    public ClusterNodeManager clusterNodeManager(final ClientPropertiesService clientPropertiesService,
                                                 final NodeCache nodeCache,
                                                 final TaskManager taskManager) {
        return new ClusterNodeManagerImpl(clientPropertiesService, nodeCache, taskManager);
    }
}