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

package stroom.node.impl;

import stroom.node.impl.db.jooq.tables.Node;
import stroom.node.shared.FindNodeGroupRequest;
import stroom.node.shared.NodeGroup;
import stroom.node.shared.NodeGroupChange;
import stroom.node.shared.NodeGroupState;
import stroom.util.shared.ResultPage;

import java.util.Set;

public interface NodeGroupDao {

    ResultPage<NodeGroup> find(FindNodeGroupRequest request);

    NodeGroup create(NodeGroup nodeGroup);

    NodeGroup fetchById(int id);

    NodeGroup fetchByName(String name);

    NodeGroup update(NodeGroup nodeGroup);

    void delete(int id);

    /**
     * Returns all nodes along with their inclusion status for the specified node group.
     * Each {@link NodeGroupState} pairs a {@link stroom.node.shared.Node} with a boolean
     * indicating whether that node is a member of the group. This is achieved via a left
     * outer join, so nodes not in the group are still returned with {@code included = false}.
     * <p>
     * Used by the UI to display a list of all nodes with a tick-box for toggling group membership.
     *
     * @param id the ID of the node group
     * @return a {@link ResultPage} containing a {@link NodeGroupState} entry for every node,
     *         ordered by node name
     */
    ResultPage<NodeGroupState> getNodeGroupState(Integer id);

    /**
     * Returns the set of node names that are members of the specified node group.
     * This is determined by the entries in the {@code node_group_link} table that
     * associate nodes with the given node group.
     *
     * @param nodeGroupId the ID of the node group to query
     * @return an immutable set of node names included in the group, or an empty set
     *         if no nodes are linked to the group
     */
    Set<String> getNodeGroupIncludedNodes(Integer nodeGroupId);

    boolean updateNodeGroupState(NodeGroupChange change);
}
