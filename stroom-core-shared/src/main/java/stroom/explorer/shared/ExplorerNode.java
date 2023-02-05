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

import stroom.docref.DocRef;
import stroom.docref.HasDisplayValue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class ExplorerNode implements HasDisplayValue {

    @JsonProperty
    private final String type;
    @JsonProperty
    private final String uuid;
    @JsonProperty
    private final String name;
    @JsonProperty
    private final String tags;

    @JsonProperty
    private final int depth;
    @JsonProperty
    private final String iconClassName;
    @JsonProperty
    private final NodeState nodeState;

    @JsonProperty
    private final List<ExplorerNode> children;
    @JsonProperty
    private final ExplorerNode parent;
    @JsonProperty
    private final boolean isFavourite;

    @JsonCreator
    public ExplorerNode(@JsonProperty("type") final String type,
                        @JsonProperty("uuid") final String uuid,
                        @JsonProperty("name") final String name,
                        @JsonProperty("tags") final String tags,
                        @JsonProperty("depth") final int depth,
                        @JsonProperty("iconClassName") final String iconClassName,
                        @JsonProperty("nodeState") final NodeState nodeState,
                        @JsonProperty("children") final List<ExplorerNode> children,
                        @JsonProperty("parent") final ExplorerNode parent,
                        @JsonProperty("isFavourite") final boolean isFavourite) {
        this.type = type;
        this.uuid = uuid;
        this.name = name;
        this.tags = tags;
        this.depth = depth;
        this.iconClassName = iconClassName;
        this.nodeState = nodeState;
        this.children = children;
        this.parent = parent;
        this.isFavourite = isFavourite;
    }

    public static ExplorerNode create(final DocRef docRef) {
        if (docRef == null) {
            return null;
        }
        return ExplorerNode
                .builder()
                .type(docRef.getType())
                .uuid(docRef.getUuid())
                .name(docRef.getName())
                .build();
    }

    public String getType() {
        return type;
    }

    public String getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public String getTags() {
        return tags;
    }

    public int getDepth() {
        return depth;
    }

    public String getIconClassName() {
        return iconClassName;
    }

    public NodeState getNodeState() {
        return nodeState;
    }

    public List<ExplorerNode> getChildren() {
        return children;
    }

    public ExplorerNode getParent() {
        return parent;
    }

    public boolean getIsFavourite() {
        return isFavourite;
    }

    @JsonIgnore
    public DocRef getDocRef() {
        return new DocRef(type, uuid, name);
    }

    @JsonIgnore
    @Override
    public String getDisplayValue() {
        return name;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ExplorerNode that = (ExplorerNode) o;
        return uuid.equals(that.uuid) && Objects.equals(parent, that.parent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid, parent);
    }

    @Override
    public String toString() {
        return getDisplayValue();
    }

    public enum NodeState {
        OPEN,
        CLOSED,
        LEAF
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {

        private String type;
        private String uuid;
        private String name;
        private String tags;
        private int depth;
        private String iconClassName;
        private NodeState nodeState;
        private List<ExplorerNode> children;
        private ExplorerNode parent;
        private boolean isFavourite;

        private Builder() {
        }

        private Builder(final ExplorerNode explorerNode) {
            this.type = explorerNode.type;
            this.uuid = explorerNode.uuid;
            this.name = explorerNode.name;
            this.tags = explorerNode.tags;
            this.depth = explorerNode.depth;
            this.iconClassName = explorerNode.iconClassName;
            this.nodeState = explorerNode.nodeState;
            this.children = explorerNode.children;
            this.parent = explorerNode.parent;
            this.isFavourite = explorerNode.isFavourite;
        }

        public Builder type(final String type) {
            this.type = type;
            return this;
        }

        public Builder uuid(final String uuid) {
            this.uuid = uuid;
            return this;
        }

        public Builder name(final String name) {
            this.name = name;
            return this;
        }

        public Builder tags(final String tags) {
            this.tags = tags;
            return this;
        }

        public Builder depth(final int depth) {
            this.depth = depth;
            return this;
        }

        public Builder iconClassName(final String iconClassName) {
            this.iconClassName = iconClassName;
            return this;
        }

        public Builder nodeState(final NodeState nodeState) {
            this.nodeState = nodeState;
            return this;
        }

        public Builder children(final List<ExplorerNode> children) {
            this.children = children;
            return this;
        }

        public Builder addChild(final ExplorerNode child) {
            if (children == null) {
                children = new ArrayList<>();
            }
            children.add(child);;
            return this;
        }

        public Builder parent(final ExplorerNode parent) {
            this.parent = parent;
            return this;
        }

        public Builder isFavourite(final boolean isFavourite) {
            this.isFavourite = isFavourite;
            return this;
        }

        public ExplorerNode build() {
            return new ExplorerNode(
                    type,
                    uuid,
                    name,
                    tags,
                    depth,
                    iconClassName,
                    nodeState,
                    children,
                    parent,
                    isFavourite);
        }
    }
}
