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

package stroom.cluster.task.impl;

import stroom.cluster.api.ClusterNodeManager;
import stroom.cluster.api.ClusterState;
import stroom.cluster.task.api.NodeNotFoundException;
import stroom.cluster.task.api.NullClusterStateException;
import stroom.cluster.task.api.TargetNodeSetFactory;
import stroom.cluster.task.api.TargetType;
import stroom.node.api.NodeInfo;

import javax.inject.Inject;
import java.util.Collections;
import java.util.Set;

public class TargetNodeSetFactoryImpl implements TargetNodeSetFactory {
    private final NodeInfo nodeInfo;
    private final ClusterNodeManager clusterNodeManager;

    @Inject
    public TargetNodeSetFactoryImpl(final NodeInfo nodeInfo, final ClusterNodeManager clusterNodeManager) {
        this.nodeInfo = nodeInfo;
        this.clusterNodeManager = clusterNodeManager;
    }

    @Override
    public String getSourceNode() {
        return nodeInfo.getThisNodeName();
    }

    @Override
    public Set<String> getMasterTargetNodeSet() throws NullClusterStateException, NodeNotFoundException {
        final ClusterState clusterState = getClusterState();
        if (clusterState.getMasterNodeName() != null) {
            return Collections.singleton(clusterState.getMasterNodeName());
        } else {
            throw new NodeNotFoundException("No master node can be found");
        }
    }

    @Override
    public Set<String> getEnabledActiveTargetNodeSet() throws NullClusterStateException, NodeNotFoundException {
        final ClusterState clusterState = getClusterState();
        final Set<String> nodes = clusterState.getEnabledActiveNodes();
        if (nodes != null && nodes.size() > 0) {
            return Set.copyOf(nodes);
        } else {
            throw new NodeNotFoundException("No enabled and active nodes can be found");
        }
    }

//    @Override
    public Set<String> getEnabledTargetNodeSet() throws NullClusterStateException, NodeNotFoundException {
        final ClusterState clusterState = getClusterState();
        final Set<String> nodes = clusterState.getEnabledNodes();
        if (nodes != null && nodes.size() > 0) {
            return Set.copyOf(nodes);
        } else {
            throw new NodeNotFoundException("No enabled nodes can be found");
        }
    }

//    @Override
    public Set<String> getAllNodeSet() throws NullClusterStateException, NodeNotFoundException {
        final ClusterState clusterState = getClusterState();
        final Set<String> nodes = clusterState.getAllNodes();
        if (nodes != null && nodes.size() > 0) {
            return Set.copyOf(nodes);
        } else {
            throw new NodeNotFoundException("No nodes can be found");
        }
    }

    public Set<String> getTargetNodesByType(final TargetType targetType)
            throws NullClusterStateException, NodeNotFoundException {
        switch (targetType) {
            case MASTER:
                return getMasterTargetNodeSet();
            case ACTIVE:
                return getEnabledActiveTargetNodeSet();
            case ENABLED:
                return getEnabledTargetNodeSet();
        }

        return null;
    }

    public ClusterState getClusterState() throws NullClusterStateException {
        final ClusterState clusterState = clusterNodeManager.getClusterState();
        if (clusterState == null) {
            throw new NullClusterStateException();
        }
        return clusterState;
    }
}
