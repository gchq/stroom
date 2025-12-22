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

package stroom.node.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({"node", "master"})
@JsonInclude(Include.NON_NULL)
public class NodeStatusResult {

    @JsonProperty
    private final Node node;
    @JsonProperty
    private final boolean master;

    @JsonCreator
    public NodeStatusResult(@JsonProperty("node") final Node node,
                            @JsonProperty("master") final boolean master) {
        this.node = node;
        this.master = master;
    }

    public Node getNode() {
        return node;
    }

    public boolean isMaster() {
        return master;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final NodeStatusResult that = (NodeStatusResult) o;
        return Objects.equals(node.getId(), that.node.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(node.getId());
    }
}
