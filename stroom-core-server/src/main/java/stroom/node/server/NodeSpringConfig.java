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

package stroom.node.server;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import stroom.cluster.server.ClusterCallService;
import stroom.cluster.server.ClusterNodeManager;
import stroom.entity.server.CachingEntityManager;
import stroom.entity.server.util.StroomDatabaseInfo;
import stroom.entity.server.util.StroomEntityManager;
import stroom.node.shared.ClientPropertiesService;
import stroom.node.shared.DBTableService;
import stroom.node.shared.RecordCountService;
import stroom.properties.StroomPropertyService;
import stroom.statistics.internal.InternalStatisticsReceiver;
import stroom.task.cluster.ClusterDispatchAsyncHelper;
import stroom.util.spring.StroomScope;

import javax.inject.Named;

@Configuration
public class NodeSpringConfig {
    @Bean
    @Scope(value = StroomScope.TASK)
    public ClusterNodeInfoHandler clusterNodeInfoHandler(@Named("clusterCallServiceRemote") final ClusterCallService clusterCallService,
                                                         final NodeCache nodeCache,
                                                         final NodeService nodeService) {
        return new ClusterNodeInfoHandler(clusterCallService, nodeCache, nodeService);
    }

    @Bean("cachedNodeService")
    public CachedNodeService cachedNodeService(final CachingEntityManager entityManager,
                                               final NodeServiceTransactionHelper nodeServiceUtil,
                                               @Value("#{propertyConfigurer.getProperty('stroom.node')}") final String nodeName,
                                               @Value("#{propertyConfigurer.getProperty('stroom.rack')}") final String rackName) {
        return new CachedNodeService(entityManager, nodeServiceUtil, nodeName, rackName);
    }

    @Bean
    public ClientPropertiesService clientPropertiesService() {
        return new ClientPropertiesServiceImpl();
    }

    @Bean("dbTableService")
    public DBTableService dBTableService(final StroomDatabaseInfo stroomDatabaseInfo) {
        return new DBTableServiceImpl(stroomDatabaseInfo);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public FetchClientPropertiesHandler fetchClientPropertiesHandler(final ClientPropertiesService clientPropertiesService) {
        return new FetchClientPropertiesHandler(clientPropertiesService);
    }

    @Bean
    @Scope(StroomScope.TASK)
    public FetchNodeInfoHandler fetchNodeInfoHandler(final ClusterDispatchAsyncHelper dispatchHelper,
                                                     final ClusterNodeManager clusterNodeManager,
                                                     final NodeService nodeService) {
        return new FetchNodeInfoHandler(dispatchHelper, clusterNodeManager, nodeService);
    }

    @Bean
    @Scope(StroomScope.TASK)
    public FindSystemTableStatusHandler findSystemTableStatusHandler(final DBTableService dbTableService) {
        return new FindSystemTableStatusHandler(dbTableService);
    }

    @Bean
    @Scope(StroomScope.TASK)
    public FlushVolumeStatusHandler flushVolumeStatusHandler(final ClusterDispatchAsyncHelper dispatchHelper) {
        return new FlushVolumeStatusHandler(dispatchHelper);
    }

    @Bean
    public GlobalProperties globalProperties() {
        return new GlobalProperties();
    }

    @Bean
    public GlobalPropertyService globalPropertyService(final StroomEntityManager entityManager) {
        return new GlobalPropertyServiceImpl(entityManager);
    }

    @Bean
    public GlobalPropertyUpdater globalPropertyUpdater(final GlobalPropertyService globalPropertyService) {
        return new GlobalPropertyUpdater(globalPropertyService);
    }

    @Bean
    public HeapHistogramService heapHistogramService(final StroomPropertyService stroomPropertyService) {
        return new HeapHistogramService(stroomPropertyService);
    }

    @Bean
    @Scope(StroomScope.TASK)
    public HeapHistogramStatisticsExecutor heapHistogramStatisticsExecutor(final HeapHistogramService heapHistogramService,
                                                                           final InternalStatisticsReceiver internalStatisticsReceiver,
                                                                           final NodeCache nodeCache) {
        return new HeapHistogramStatisticsExecutor(heapHistogramService, internalStatisticsReceiver, nodeCache);
    }

    @Bean
    public NodeCache nodeCache() {
        return new NodeCache();
    }

    @Bean
    public NodeConfig nodeConfig() {
        return new NodeConfigImpl();
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public NodeInfoClusterHandler nodeInfoClusterHandler(final NodeCache nodeCache) {
        return new NodeInfoClusterHandler(nodeCache);
    }

    @Bean("nodeService")
    public NodeService nodeService(final StroomEntityManager entityManager,
                                   final NodeServiceTransactionHelper nodeServiceUtil,
                                   @Value("#{propertyConfigurer.getProperty('stroom.node')}") final String nodeName,
                                   @Value("#{propertyConfigurer.getProperty('stroom.rack')}") final String rackName) {
        return new NodeServiceImpl(entityManager, nodeServiceUtil, nodeName, rackName);
    }

    @Bean
    public NodeServiceTransactionHelper nodeServiceTransactionHelper(final StroomEntityManager entityManager) {
        return new NodeServiceTransactionHelper(entityManager);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public NodeStatusExecutor nodeStatusExecutor(final NodeStatusServiceUtil nodeStatusServiceUtil,
                                                 final InternalStatisticsReceiver internalStatisticsReceiver) {
        return new NodeStatusExecutor(nodeStatusServiceUtil, internalStatisticsReceiver);
    }

    @Bean
    public NodeStatusServiceUtil nodeStatusServiceUtil(final NodeCache nodeCache,
                                                       final RecordCountService recordCountService) {
        return new NodeStatusServiceUtil(nodeCache, recordCountService);
    }

    @Bean
    public RecordCountService recordCountService() {
        return new RecordCountServiceImpl();
    }

    @Bean("remoteStatusService")
    public RemoteStatusService remoteStatusService() {
        return new RemoteStatusServiceImpl();
    }

//    @Bean
//    public RemoteStatusServiceRPC remoteStatusServiceRPC() {
//        return new RemoteStatusServiceRPC();
//    }

}