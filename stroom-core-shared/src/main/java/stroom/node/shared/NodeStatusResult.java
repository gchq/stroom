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

package stroom.node.shared;

import java.util.Objects;

public class NodeStatusResult {
    private Node node;
    private boolean master;

    public NodeStatusResult() {
    }

    public NodeStatusResult(final Node node, final boolean master) {
        this.node = node;
        this.master = master;
    }

    public Node getNode() {
        return node;
    }

    public void setNode(final Node node) {
        this.node = node;
    }

    public boolean isMaster() {
        return master;
    }

    public void setMaster(final boolean master) {
        this.master = master;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final NodeStatusResult that = (NodeStatusResult) o;
        return Objects.equals(node.getId(), that.node.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(node.getId());
    }
}
