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

package stroom.explorer.shared;

import java.util.Set;

public class SimpleExplorerItem implements ExplorerData {
    private static final long serialVersionUID = 71700409698917775L;

    private int depth;
    private String iconUrl;
    private String type;
    private String name;
    private NodeState nodeState;

    public SimpleExplorerItem() {
        // Default constructor necessary for GWT serialisation.
    }

    public SimpleExplorerItem(final String iconUrl, final String type, final String name, final NodeState nodeState) {
        this.iconUrl = iconUrl;
        this.type = type;
        this.name = name;
        this.nodeState = nodeState;
    }

    @Override
    public int getDepth() {
        return depth;
    }

    @Override
    public void setDepth(int depth) {
        this.depth = depth;
    }

    @Override
    public String getIconUrl() {
        return iconUrl;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String getDisplayValue() {
        return name;
    }

    @Override
    public NodeState getNodeState() {
         return nodeState;
    }

    @Override
    public void setNodeState(final NodeState nodeState) {
        this.nodeState = nodeState;
    }

    @Override
    public Set<String> getTags() {
        return null;
    }

    @Override
    public int hashCode() {
        return (type.hashCode() * 31) + name.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null || !(obj instanceof SimpleExplorerItem)) {
            return false;
        }

        final SimpleExplorerItem item = (SimpleExplorerItem) obj;
        return type.equals(item.type) && name.equals(item.name);
    }

    @Override
    public String toString() {
        return name;
    }
}
