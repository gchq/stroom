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

@JsonInclude(Include.NON_NULL)
public class NodeGroupChange {

    @JsonProperty
    private final int nodeId;
    @JsonProperty
    private final int nodeGroupId;
    @JsonProperty
    private final boolean included;

    @JsonCreator
    public NodeGroupChange(@JsonProperty("nodeId") final int nodeId,
                           @JsonProperty("nodeGroupId") final int nodeGroupId,
                           @JsonProperty("included") final boolean included) {
        this.nodeId = nodeId;
        this.nodeGroupId = nodeGroupId;
        this.included = included;
    }

    public int getNodeId() {
        return nodeId;
    }

    public int getNodeGroupId() {
        return nodeGroupId;
    }

    public boolean isIncluded() {
        return included;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final NodeGroupChange that = (NodeGroupChange) o;
        return nodeId == that.nodeId &&
               nodeGroupId == that.nodeGroupId &&
               included == that.included;
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeId, nodeGroupId, included);
    }

    @Override
    public String toString() {
        return "NodeGroupChange{" +
               "nodeId=" + nodeId +
               ", nodeGroupId=" + nodeGroupId +
               ", included=" + included +
               '}';
    }
}
