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

package stroom.node.server;

import org.springframework.context.annotation.Scope;
import stroom.cluster.server.ClusterNodeManager;
import stroom.cluster.server.ClusterState;
import stroom.entity.server.util.EntityServiceExceptionUtil;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.ResultList;
import stroom.node.shared.FetchNodeInfoAction;
import stroom.node.shared.Node;
import stroom.node.shared.NodeInfoResult;
import stroom.node.shared.NodeService;
import stroom.task.cluster.ClusterCallEntry;
import stroom.task.cluster.ClusterDispatchAsyncHelper;
import stroom.task.cluster.DefaultClusterResultCollector;
import stroom.task.cluster.TargetNodeSetFactory.TargetType;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.shared.ModelStringUtil;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@TaskHandlerBean(task = FetchNodeInfoAction.class)
@Scope(StroomScope.TASK)
class FetchNodeInfoHandler extends AbstractTaskHandler<FetchNodeInfoAction, ResultList<NodeInfoResult>> {
    private final ClusterDispatchAsyncHelper dispatchHelper;
    private final ClusterNodeManager clusterNodeManager;
    private final NodeService nodeService;

    @Inject
    FetchNodeInfoHandler(final ClusterDispatchAsyncHelper dispatchHelper, final ClusterNodeManager clusterNodeManager, final NodeService nodeService) {
        this.dispatchHelper = dispatchHelper;
        this.clusterNodeManager = clusterNodeManager;
        this.nodeService = nodeService;
    }

    @Override
    public ResultList<NodeInfoResult> exec(final FetchNodeInfoAction action) {
        // Get pings back from all enabled nodes in the cluster.
        // Wait up to 5 seconds to get responses from each node.
        final DefaultClusterResultCollector<NodeInfoResult> collector = dispatchHelper
                .execAsync(new NodeInfoClusterTask(action.getUserToken()), 5, TimeUnit.SECONDS, TargetType.ENABLED);

        final ClusterState clusterState = clusterNodeManager.getQuickClusterState();
        final Node masterNode = clusterState.getMasterNode();
        final List<Node> allNodes = nodeService.find(nodeService.createCriteria());

        Collections.sort(allNodes, (o1, o2) -> {
            if (o1.getName() == null || o2.getName() == null) {
                return 0;
            }
            return o1.getName().compareToIgnoreCase(o2.getName());
        });

        final ArrayList<NodeInfoResult> responseList = new ArrayList<NodeInfoResult>();
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
    }
}
