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

package stroom.util.shared;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class Expander {

    @JsonProperty
    private int depth;
    @JsonProperty
    private boolean expanded;
    @JsonProperty
    private boolean leaf;

    public Expander() {
    }

    @JsonCreator
    public Expander(@JsonProperty("depth") final int depth,
                    @JsonProperty("expanded") final boolean expanded,
                    @JsonProperty("leaf") final boolean leaf) {
        this.depth = depth;
        this.expanded = expanded;
        this.leaf = leaf;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(final int depth) {
        this.depth = depth;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(final boolean expanded) {
        this.expanded = expanded;
    }

    public boolean isLeaf() {
        return leaf;
    }

    public void setLeaf(final boolean leaf) {
        this.leaf = leaf;
    }
}
