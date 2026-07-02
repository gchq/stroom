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

package stroom.node.api;

import stroom.node.shared.NodeGroup;

import java.util.Set;

public class NodeGroupState {

    private final NodeGroup nodeGroup;
    private final Set<String> selectedNodes;

    private final boolean enabled;
    private final boolean invertSelection;

    public NodeGroupState(final NodeGroup nodeGroup,
                          final Set<String> selectedNodes) {
        this.nodeGroup = nodeGroup;
        this.enabled = nodeGroup.isEnabled();
        this.invertSelection = nodeGroup.isInvertSelection();
        this.selectedNodes = selectedNodes;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getNodeGroup() {
        return nodeGroup.getName();
    }

    public boolean isIncludedNode(final String node) {
        // If the node group does not include the requesting node then return zero tasks.
        boolean selected = selectedNodes.contains(node);
        if (invertSelection) {
            selected = !selected;
        }
        return selected;
    }
}
