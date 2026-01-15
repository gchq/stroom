/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.cluster.impl;

import stroom.cluster.api.ClusterNodeManager;
import stroom.cluster.api.ClusterState;
import stroom.node.api.NodeInfo;
import stroom.node.shared.ClusterNodeInfo;

import jakarta.inject.Inject;

import java.util.Collections;
import java.util.Set;

public class MockClusterNodeManager implements ClusterNodeManager {

    private final NodeInfo nodeInfo;

    @Inject
    public MockClusterNodeManager(final NodeInfo nodeInfo) {
        this.nodeInfo = nodeInfo;
    }

    @Override
    public ClusterState getClusterState() {
        final String nodeName = nodeInfo.getThisNodeName();
        final Set<String> nodeNames = Collections.singleton(nodeName);
        final ClusterState clusterState = new ClusterState();
        clusterState.setAllNodes(nodeNames);
        clusterState.setEnabledNodes(nodeNames);
        clusterState.addEnabledActiveNode(nodeName);
        clusterState.setMasterNodeName(nodeName);
        return clusterState;
    }

    @Override
    public ClusterState getQuickClusterState() {
        return getClusterState();
    }

    @Override
    public ClusterNodeInfo getClusterNodeInfo() {
        return null;
    }

    @Override
    public Long ping(final String sourceNode) {
        return System.currentTimeMillis();
    }
}
