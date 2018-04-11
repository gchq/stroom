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

package stroom.node;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import stroom.entity.event.EntityEvent;
import stroom.entity.shared.Clearable;
import stroom.node.shared.DBTableService;
import stroom.node.shared.RecordCountService;
import stroom.task.TaskHandler;

public class MockNodeModule extends AbstractModule {
    @Override
    protected void configure() {
//        bind(DBTableService.class).to(DBTableServiceImpl.class);
        bind(RecordCountService.class).to(MockRecordCountService.class);
//        bind(RemoteStatusService.class).to(RemoteStatusServiceImpl.class);
//
//        final Multibinder<Clearable> clearableBinder = Multibinder.newSetBinder(binder(), Clearable.class);
//        clearableBinder.addBinding().to(NodeCache.class);
//
//        final Multibinder<TaskHandler> taskHandlerBinder = Multibinder.newSetBinder(binder(), TaskHandler.class);
//        taskHandlerBinder.addBinding().to(ClusterNodeInfoHandler.class);
//        taskHandlerBinder.addBinding().to(FetchClientPropertiesHandler.class);
//        taskHandlerBinder.addBinding().to(FetchNodeInfoHandler.class);
//        taskHandlerBinder.addBinding().to(FindSystemTableStatusHandler.class);
//        taskHandlerBinder.addBinding().to(FlushVolumeStatusHandler.class);
//        taskHandlerBinder.addBinding().to(NodeInfoClusterHandler.class);
//
//        final Multibinder<EntityEvent.Handler> entityEventHandlerBinder = Multibinder.newSetBinder(binder(), EntityEvent.Handler.class);
//        entityEventHandlerBinder.addBinding().to(NodeCache.class);
    }
    //    @Bean
//    @Scope(value = StroomScope.TASK)
//    public ClusterNodeInfoHandler clusterNodeInfoHandler(@Named("clusterCallServiceRemote") final ClusterCallService clusterCallService,
//                                                         final NodeCache nodeCache,
//                                                         final NodeService nodeService) {
//        return new ClusterNodeInfoHandler(clusterCallService, nodeCache, nodeService);
//    }
//
//
//    @Bean
//    @Scope(value = StroomScope.TASK)
//    public FetchClientPropertiesHandler fetchClientPropertiesHandler(final ClientPropertiesService clientPropertiesService) {
//        return new FetchClientPropertiesHandler(clientPropertiesService);
//    }
//
//    @Bean
//    @Scope(StroomScope.TASK)
//    public FetchNodeInfoHandler fetchNodeInfoHandler(final ClusterDispatchAsyncHelper dispatchHelper,
//                                                     final ClusterNodeManager clusterNodeManager,
//                                                     final NodeService nodeService) {
//        return new FetchNodeInfoHandler(dispatchHelper, clusterNodeManager, nodeService);
//    }
//
//    @Bean
//    @Scope(StroomScope.TASK)
//    public FindSystemTableStatusHandler findSystemTableStatusHandler(final DBTableService dbTableService) {
//        return new FindSystemTableStatusHandler(dbTableService);
//    }
//
//    @Bean
//    @Scope(StroomScope.TASK)
//    public FlushVolumeStatusHandler flushVolumeStatusHandler(final ClusterDispatchAsyncHelper dispatchHelper) {
//        return new FlushVolumeStatusHandler(dispatchHelper);
//    }
//
//    @Bean
//    @Scope(StroomScope.TASK)
//    public HeapHistogramStatisticsExecutor heapHistogramStatisticsExecutor(final HeapHistogramService heapHistogramService,
//                                                                           final InternalStatisticsReceiver internalStatisticsReceiver,
//                                                                           final NodeCache nodeCache) {
//        return new HeapHistogramStatisticsExecutor(heapHistogramService, internalStatisticsReceiver, nodeCache);
//    }
//
//    @Bean
//    @Scope(value = StroomScope.TASK)
//    public NodeInfoClusterHandler nodeInfoClusterHandler(final NodeCache nodeCache) {
//        return new NodeInfoClusterHandler(nodeCache);
//    }
//
//    @Bean
//    @Scope(value = StroomScope.TASK)
//    public NodeStatusExecutor nodeStatusExecutor(final NodeStatusServiceUtil nodeStatusServiceUtil,
//                                                 final InternalStatisticsReceiver internalStatisticsReceiver) {
//        return new NodeStatusExecutor(nodeStatusServiceUtil, internalStatisticsReceiver);
//    }
//
}