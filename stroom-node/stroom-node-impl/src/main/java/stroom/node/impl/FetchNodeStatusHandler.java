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
import stroom.cluster.task.api.ClusterCallEntry;
import stroom.cluster.task.api.ClusterDispatchAsyncHelper;
import stroom.cluster.task.api.DefaultClusterResultCollector;
import stroom.cluster.task.api.TargetType;
import stroom.util.EntityServiceExceptionUtil;
import stroom.node.shared.FetchNodeStatusAction;
import stroom.node.shared.FindNodeCriteria;
import stroom.node.shared.Node;
import stroom.node.shared.NodeStatusResult;
import stroom.security.Security;
import stroom.task.api.AbstractTaskHandler;
import stroom.util.shared.BaseResultList;
import stroom.util.shared.ResultList;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;


class FetchNodeStatusHandler extends AbstractTaskHandler<FetchNodeStatusAction, ResultList<NodeStatusResult>> {
    private final ClusterDispatchAsyncHelper dispatchHelper;
    private final ClusterNodeManager clusterNodeManager;
    private final NodeServiceImpl nodeService;
    private final Security security;

    @Inject
    FetchNodeStatusHandler(final ClusterDispatchAsyncHelper dispatchHelper,
                           final ClusterNodeManager clusterNodeManager,
                           final NodeServiceImpl nodeService,
                           final Security security) {
        this.dispatchHelper = dispatchHelper;
        this.clusterNodeManager = clusterNodeManager;
        this.nodeService = nodeService;
        this.security = security;
    }

    @Override
    public ResultList<NodeStatusResult> exec(final FetchNodeStatusAction action) {
        return security.secureResult(() -> {
            // Get pings back from all enabled nodes in the cluster.
            // Wait up to 5 seconds to get responses from each node.
            final DefaultClusterResultCollector<NodeStatusResult> collector = dispatchHelper
                    .execAsync(new NodeStatusClusterTask(action.getUserToken()), 5, TimeUnit.SECONDS, TargetType.ENABLED);

            final ClusterState clusterState = clusterNodeManager.getQuickClusterState();
            final String masterNodeName = clusterState.getMasterNodeName();
            final List<Node> allNodes = nodeService.find(new FindNodeCriteria());

            allNodes.sort((o1, o2) -> {
                if (o1.getName() == null || o2.getName() == null) {
                    return 0;
                }
                return o1.getName().compareToIgnoreCase(o2.getName());
            });

            final ArrayList<NodeStatusResult> responseList = new ArrayList<>();
            for (final Node node : allNodes) {
                final ClusterCallEntry<NodeStatusResult> response = collector.getResponse(node.getName());
                // Get or create result.
                NodeStatusResult result = null;
                if (response != null) {
                    result = response.getResult();
                }
                if (result == null) {
                    result = new NodeStatusResult();
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
                result.setNode(node);
                responseList.add(result);
            }

            return BaseResultList.createUnboundedList(responseList);
        });
    }
}
