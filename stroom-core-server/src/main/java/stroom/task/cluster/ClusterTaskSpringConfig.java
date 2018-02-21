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

package stroom.task.cluster;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;
import stroom.cluster.ClusterCallService;
import stroom.cluster.ClusterNodeManager;
import stroom.node.NodeCache;
import stroom.properties.StroomPropertyService;
import stroom.task.TaskManager;
import stroom.util.cache.CacheManager;
import stroom.util.spring.StroomScope;

import javax.inject.Named;

@Configuration
public class ClusterTaskSpringConfig {
    @Bean
    @Scope(value = StroomScope.TASK)
    public ClusterDispatchAsyncHelper clusterDispatchAsyncHelper(final StroomPropertyService stroomPropertyService,
                                                                 final ClusterResultCollectorCache collectorCache,
                                                                 final ClusterDispatchAsync dispatcher,
                                                                 final TargetNodeSetFactory targetNodeSetFactory) {
        return new ClusterDispatchAsyncHelper(stroomPropertyService, collectorCache, dispatcher, targetNodeSetFactory);
    }

    @Bean(ClusterDispatchAsyncImpl.BEAN_NAME)
    @Lazy
    public ClusterDispatchAsync clusterDispatchAsync(final TaskManager taskManager,
                                                     final ClusterResultCollectorCache collectorCache,
                                                     @Named("clusterCallServiceRemote") final ClusterCallService clusterCallService) {
        return new ClusterDispatchAsyncImpl(taskManager, collectorCache, clusterCallService);
    }

    @Bean
    public ClusterResultCollectorCache clusterResultCollectorCache(final CacheManager cacheManager) {
        return new ClusterResultCollectorCache(cacheManager);
    }

    @Bean(ClusterWorkerImpl.BEAN_NAME)
    @Lazy
    public ClusterWorker clusterWorker(final TaskManager taskManager,
                                       final NodeCache nodeCache,
                                       @Named("clusterCallServiceRemote") final ClusterCallService clusterCallService) {
        return new ClusterWorkerImpl(taskManager, nodeCache, clusterCallService);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public TargetNodeSetFactory targetNodeSetFactory(final NodeCache nodeCache,
                                                     final ClusterNodeManager clusterNodeManager) {
        return new TargetNodeSetFactory(nodeCache, clusterNodeManager);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public TerminateTaskClusterHandler terminateTaskClusterHandler(final TaskManager taskManager) {
        return new TerminateTaskClusterHandler(taskManager);
    }
}