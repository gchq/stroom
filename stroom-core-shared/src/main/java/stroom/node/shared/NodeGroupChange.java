/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.node.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.Set;

@JsonInclude(Include.NON_NULL)
public class NodeGroupChange {

    @JsonProperty
    private final NodeGroup nodeGroup;
    @JsonProperty
    private final Set<Integer> selectedNodes;

    @JsonCreator
    public NodeGroupChange(@JsonProperty("nodeGroup") final NodeGroup nodeGroup,
                           @JsonProperty("selectedNodes") final Set<Integer> selectedNodes) {
        this.nodeGroup = nodeGroup;
        this.selectedNodes = selectedNodes;
    }

    public NodeGroup getNodeGroup() {
        return nodeGroup;
    }

    public Set<Integer> getSelectedNodes() {
        return selectedNodes;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final NodeGroupChange change = (NodeGroupChange) o;
        return Objects.equals(nodeGroup, change.nodeGroup) &&
               Objects.equals(selectedNodes, change.selectedNodes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeGroup, selectedNodes);
    }

    @Override
    public String toString() {
        return "NodeGroupChange{" +
               "nodeGroup=" + nodeGroup +
               ", selectedNodes=" + selectedNodes +
               '}';
    }
}
