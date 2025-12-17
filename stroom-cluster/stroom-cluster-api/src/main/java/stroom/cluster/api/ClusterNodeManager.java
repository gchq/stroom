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

package stroom.cluster.api;

import stroom.node.shared.ClusterNodeInfo;

/**
 * Component that remembers the node list and who is the current master node
 */
public interface ClusterNodeManager {

    /**
     * Gets the current cluster state. If the current state is null or is older
     * than the expiry time then the cluster state will be updated
     * asynchronously ready for subsequent calls. The current state is still
     * returned so could be null
     *
     * @return A cluster state object.
     */
    ClusterState getClusterState();

    /**
     * Gets the current cluster state or builds a new cluster state if no
     * current state exists but without locking. This method is quick as either
     * the current state is returned or a basic state is created that does not
     * attempt to determine which nodes are active or what the current master
     * node is.
     *
     * @return A cluster state object.
     */
    ClusterState getQuickClusterState();

    ClusterNodeInfo getClusterNodeInfo();

    Long ping(String sourceNode);
}
