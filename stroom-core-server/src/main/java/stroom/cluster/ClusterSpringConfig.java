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

package stroom.cluster;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import stroom.node.NodeCache;
import stroom.node.NodeService;
import stroom.node.shared.ClientPropertiesService;
import stroom.properties.StroomPropertyService;
import stroom.task.TaskManager;
import stroom.guice.StroomBeanStore;
import stroom.util.spring.StroomScope;

import javax.inject.Named;

@Configuration
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
                                                             final StroomPropertyService propertyService) {
        return new ClusterCallServiceRemote(nodeCache, beanStore, propertyService);
    }

    @Bean(ClusterNodeManager.BEAN_NAME)
    public ClusterNodeManager clusterNodeManager(final ClientPropertiesService clientPropertiesService,
                                                 final NodeCache nodeCache,
                                                 final TaskManager taskManager) {
        return new ClusterNodeManagerImpl(clientPropertiesService, nodeCache, taskManager);
    }

    @Bean
    @Scope(StroomScope.TASK)
    public UpdateClusterStateTaskHandler updateClusterStateTaskHandler(final NodeService nodeService,
                                                                       final NodeCache nodeCache,
                                                                       final ClusterCallServiceRemote clusterCallServiceRemote,
                                                                       final TaskManager taskManager) {
        return new UpdateClusterStateTaskHandler(nodeService, nodeCache, clusterCallServiceRemote, taskManager);
    }
}