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

package stroom.node.impl;

import stroom.cluster.api.ClusterService;
import stroom.cluster.api.EndpointUrlService;
import stroom.cluster.api.NodeInfo;
import stroom.node.api.ClusterState;
import stroom.node.api.FindNodeCriteria;
import stroom.node.shared.ClusterNodeInfo;
import stroom.node.shared.Node;
import stroom.util.shared.BuildInfo;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Component that remembers the node list and who is the current master node
 */
@Singleton
public class ClusterNodeManager {

    private final NodeInfo nodeInfo;
    private final ClusterService clusterService;
    private final EndpointUrlService endpointUrlService;
    private final NodeDao nodeDao;
    private final BuildInfo buildInfo;

    @Inject
    ClusterNodeManager(final NodeInfo nodeInfo,
                       final ClusterService clusterService,
                       final EndpointUrlService endpointUrlService,
                       final NodeDao nodeDao,
                       final BuildInfo buildInfo) {
        this.nodeInfo = nodeInfo;
        this.clusterService = clusterService;
        this.endpointUrlService = endpointUrlService;
        this.nodeDao = nodeDao;
        this.buildInfo = buildInfo;
    }

    /**
     * Gets the current cluster state. If the current state is null or is older
     * than the expiry time then the cluster state will be updated
     * asynchronously ready for subsequent calls. The current state is still
     * returned so could be null
     */
    public ClusterState getClusterState() {
        return getQuickClusterState();
    }

    /**
     * Gets the current cluster state or builds a new cluster state if no
     * current state exists but without locking. This method is quick as either
     * the current state is returned or a basic state is created that does not
     * attempt to determine which nodes are active or what the current master
     * node is.
     */
    public ClusterState getQuickClusterState() {
        // Not ideal having to hit the service and db twice like this but the service no longer
        // exposes the Node object so limited options.
        final List<String> allNodeNames = nodeDao
                .find(new FindNodeCriteria())
                .stream()
                .map(Node::getName)
                .collect(Collectors.toList());
        final ClusterState clusterState = new ClusterState();
        clusterState.setAllNodes(allNodeNames);
        final Collection<String> enabledNodes = clusterService.getNodeNames();
        clusterState.setEnabledNodes(enabledNodes);
        clusterState.setMasterNodeName(clusterService.getLeaderNodeName().orElse(null));
        clusterState.setEnabledNodes(clusterService.getNodeNames());

        clusterState.setUpdateTime(System.currentTimeMillis());
        return clusterState;
    }

    public ClusterNodeInfo getClusterNodeInfo() {
        final ClusterState clusterState = getQuickClusterState();

        // Take a copy of our working variables;
        final String thisNodeName = nodeInfo.getThisNodeName();
        final String masterNodeName = clusterState.getMasterNodeName();

        final ClusterNodeInfo clusterNodeInfo = new ClusterNodeInfo(clusterState.getUpdateTime(),
                buildInfo,
                thisNodeName,
                endpointUrlService.getBaseEndpointUrl(thisNodeName));

        if (masterNodeName != null) {
            for (final String nodeName : clusterState.getAllNodes()) {
                clusterNodeInfo.addItem(
                        nodeName,
                        clusterState.getEnabledActiveNodes().contains(nodeName),
                        masterNodeName.equals(nodeName));
            }
        }

        return clusterNodeInfo;
    }
}
