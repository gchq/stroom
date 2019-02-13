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

package stroom.node.impl;

import stroom.cluster.api.ClusterNodeManager;
import stroom.cluster.api.ClusterState;
import stroom.util.shared.BaseResultList;
import stroom.util.shared.ResultList;
import stroom.entity.util.EntityServiceExceptionUtil;
import stroom.node.api.NodeService;
import stroom.node.shared.FetchNodeInfoAction;
import stroom.node.shared.Node;
import stroom.node.shared.NodeInfoResult;
import stroom.security.Security;
import stroom.task.api.AbstractTaskHandler;
import stroom.cluster.task.api.ClusterCallEntry;
import stroom.cluster.task.api.ClusterDispatchAsyncHelper;
import stroom.cluster.task.api.DefaultClusterResultCollector;
import stroom.cluster.task.api.TargetType;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;


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
            final String masterNodeName = clusterState.getMasterNodeName();
            final List<Node> allNodes = nodeService.find(nodeService.createCriteria());

            allNodes.sort((o1, o2) -> {
                if (o1.getName() == null || o2.getName() == null) {
                    return 0;
                }
                return o1.getName().compareToIgnoreCase(o2.getName());
            });

            final ArrayList<NodeInfoResult> responseList = new ArrayList<>();
            for (final Node node : allNodes) {
                final ClusterCallEntry<NodeInfoResult> response = collector.getResponse(node.getName());
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

                result.setMaster(node.getName().equals(masterNodeName));
                result.setEntity(node);
                responseList.add(result);
            }

            return BaseResultList.createUnboundedList(responseList);
        });
    }
}
