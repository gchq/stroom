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
    private final String rootNodeUuid;
    @JsonProperty
    private final boolean isFavourite;
    @JsonProperty
    private final ExplorerNodeKey uniqueKey;

    @JsonCreator
    public ExplorerNode(@JsonProperty("type") final String type,
                        @JsonProperty("uuid") final String uuid,
                        @JsonProperty("name") final String name,
                        @JsonProperty("tags") final String tags,
                        @JsonProperty("depth") final int depth,
                        @JsonProperty("iconClassName") final String iconClassName,
                        @JsonProperty("nodeState") final NodeState nodeState,
                        @JsonProperty("children") final List<ExplorerNode> children,
                        @JsonProperty("rootNodeUuid") final String rootNodeUuid,
                        @JsonProperty("isFavourite") final boolean isFavourite,
                        @JsonProperty("uniqueKey") final ExplorerNodeKey uniqueKey) {
        this.type = type;
        this.uuid = uuid;
        this.name = name;
        this.tags = tags;
        this.depth = depth;
        this.iconClassName = iconClassName;
        this.nodeState = nodeState;
        this.children = children;
        this.rootNodeUuid = rootNodeUuid;
        this.isFavourite = isFavourite;
        this.uniqueKey = uniqueKey;
    }

    public static ExplorerNode create(final DocRef docRef) {
        if (docRef == null) {
            return null;
        }
        // If not the `System` node, use the `System` node as a parent when constructing from a DocRef.
        // If multiple root nodes are supported in the future, the relevant parent node will need to be obtained by
        // querying the database.
        final String systemNodeUuid = ExplorerConstants.SYSTEM_NODE.getUuid();
        final String rootNodeUuid = systemNodeUuid.equals(docRef.getUuid()) ? null : systemNodeUuid;
        return ExplorerNode
                .builder()
                .docRef(docRef)
                .rootNodeUuid(rootNodeUuid)
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

    public String getRootNodeUuid() {
        return rootNodeUuid;
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

    public ExplorerNodeKey getUniqueKey() {
        return uniqueKey;
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
        return Objects.equals(uniqueKey, that.uniqueKey);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(uniqueKey);
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
        private String rootNodeUuid;
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
            this.rootNodeUuid = explorerNode.rootNodeUuid;
            this.isFavourite = explorerNode.isFavourite;
        }

        public Builder docRef(final DocRef docRef) {
            if (docRef != null) {
                this.type = docRef.getType();
                this.uuid = docRef.getUuid();
                this.name = docRef.getName();
            }

            return this;
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

        public Builder rootNodeUuid(final String rootNodeUuid) {
            this.rootNodeUuid = rootNodeUuid;
            return this;
        }

        public Builder rootNodeUuid(final ExplorerNode rootNodeUuid) {
            this.rootNodeUuid = rootNodeUuid != null ? rootNodeUuid.getUuid() : null;
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
                    rootNodeUuid,
                    isFavourite,
                    new ExplorerNodeKey(type, uuid, rootNodeUuid));
        }
    }
}
