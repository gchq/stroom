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

package stroom.cluster.server;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import stroom.node.server.NodeCache;
import stroom.node.shared.ClusterNodeInfo;
import stroom.node.shared.Node;
import stroom.util.spring.StroomSpringProfiles;

import javax.inject.Inject;
import java.util.Collections;
import java.util.Set;

@Component
@Profile(StroomSpringProfiles.IT)
public class MockClusterNodeManager implements ClusterNodeManager {
    private final NodeCache nodeCache;

    @Inject
    public MockClusterNodeManager(final NodeCache nodeCache) {
        this.nodeCache = nodeCache;
    }

    @Override
    public ClusterState getClusterState() {
        final Node node = nodeCache.getDefaultNode();
        final Set<Node> nodes = Collections.singleton(node);
        final ClusterState clusterState = new ClusterState();
        clusterState.setAllNodes(nodes);
        clusterState.setEnabledNodes(nodes);
        clusterState.setEnabledActiveNodes(nodes);
        clusterState.setMasterNode(node);
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
    public Long ping(final Node sourceNode) {
        return System.currentTimeMillis();
    }
}
