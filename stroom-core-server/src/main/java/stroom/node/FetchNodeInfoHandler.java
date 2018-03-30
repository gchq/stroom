/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.node;

import stroom.cluster.ClusterNodeManager;
import stroom.cluster.ClusterState;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.ResultList;
import stroom.entity.util.EntityServiceExceptionUtil;
import stroom.node.shared.FetchNodeInfoAction;
import stroom.node.shared.Node;
import stroom.node.shared.NodeInfoResult;
import stroom.security.Security;
import stroom.task.AbstractTaskHandler;
import stroom.task.TaskHandlerBean;
import stroom.task.cluster.ClusterCallEntry;
import stroom.task.cluster.ClusterDispatchAsyncHelper;
import stroom.task.cluster.DefaultClusterResultCollector;
import stroom.task.cluster.TargetNodeSetFactory.TargetType;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@TaskHandlerBean(task = FetchNodeInfoAction.class)
class FetchNodeInfoHandler extends AbstractTaskHandler<FetchNodeInfoAction, ResultList<NodeInfoResult>> {
    private final ClusterDispatchAsyncHelper dispatchHelper;
    private final ClusterNodeManager clusterNodeManager;
    private final NodeService nodeService;
    private final Security security;

    @Inject
    FetchNodeInfoHandler(final ClusterDispatchAsyncHelper dispatchHelper,
                         final ClusterNodeManager clusterNodeManager,
                         final NodeService nodeService,
                         final Security security) {
        this.dispatchHelper = dispatchHelper;
        this.clusterNodeManager = clusterNodeManager;
        this.nodeService = nodeService;
        this.security = security;
    }

    @Override
    public ResultList<NodeInfoResult> exec(final FetchNodeInfoAction action) {
        return security.secureResult(() -> {
            // Get pings back from all enabled nodes in the cluster.
            // Wait up to 5 seconds to get responses from each node.
            final DefaultClusterResultCollector<NodeInfoResult> collector = dispatchHelper
                    .execAsync(new NodeInfoClusterTask(action.getUserToken()), 5, TimeUnit.SECONDS, TargetType.ENABLED);

            final ClusterState clusterState = clusterNodeManager.getQuickClusterState();
            final Node masterNode = clusterState.getMasterNode();
            final List<Node> allNodes = nodeService.find(nodeService.createCriteria());

            allNodes.sort((o1, o2) -> {
                if (o1.getName() == null || o2.getName() == null) {
                    return 0;
                }
                return o1.getName().compareToIgnoreCase(o2.getName());
            });

            final ArrayList<NodeInfoResult> responseList = new ArrayList<>();
            for (final Node node : allNodes) {
                final ClusterCallEntry<NodeInfoResult> response = collector.getResponse(node);
                // Get or create result.
                NodeInfoResult result = null;
                if (response != null) {
                    result = response.getResult();
                }
                if (result == null) {
                    result = new NodeInfoResult();
                }

                // Set ping from response.
                if (response != null) {
                    if (response.getError() != null) {
                        result.setError(EntityServiceExceptionUtil.unwrapMessage(response.getError(), response.getError()));

                    } else {
                        result.setPing(response.getTimeMs());
                    }
                } else {
                    result.setError("No response");
                }

                result.setMaster(node.equals(masterNode));
                result.setEntity(node);
                responseList.add(result);
            }

            return BaseResultList.createUnboundedList(responseList);
        });
    }
}
