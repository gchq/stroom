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
import stroom.svg.shared.SvgImage;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.Severity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
    private final SvgImage icon;
    @JsonProperty
    private final NodeState nodeState;

    @JsonProperty
    private final List<ExplorerNode> children;
    @JsonProperty
    private final String rootNodeUuid;
    @JsonProperty
    private final boolean isFavourite;
    @JsonProperty
    private final boolean isFilterMatch;
    @JsonProperty
    private final boolean isFolder;
    @JsonProperty
    private final ExplorerNodeKey uniqueKey;
    @JsonProperty
    private final List<NodeInfo> nodeInfoList;

    @JsonIgnore
    private volatile DocRef docRef;
    @JsonIgnore
    private volatile int hashcode;

    @JsonCreator
    public ExplorerNode(@JsonProperty("type") final String type,
                        @JsonProperty("uuid") final String uuid,
                        @JsonProperty("name") final String name,
                        @JsonProperty("tags") final String tags,
                        @JsonProperty("depth") final int depth,
                        @JsonProperty("icon") final SvgImage icon,
                        @JsonProperty("nodeState") final NodeState nodeState,
                        @JsonProperty("children") final List<ExplorerNode> children,
                        @JsonProperty("rootNodeUuid") final String rootNodeUuid,
                        @JsonProperty("isFavourite") final boolean isFavourite,
                        @JsonProperty("isFilterMatch") final boolean isFilterMatch,
                        @JsonProperty("isFolder") final boolean isFolder,
                        @JsonProperty("uniqueKey") final ExplorerNodeKey uniqueKey,
                        @JsonProperty("nodeInfoList") final List<NodeInfo> nodeInfoList) {
        this.type = type;
        this.uuid = uuid;
        this.name = name;
        this.tags = tags;
        this.depth = depth;
        this.icon = icon;
        this.nodeState = nodeState;
        this.children = GwtNullSafe.get(children, Collections::unmodifiableList);
        this.rootNodeUuid = rootNodeUuid;
        this.isFavourite = isFavourite;
        this.isFilterMatch = isFilterMatch;
        this.isFolder = isFolder;
        this.uniqueKey = uniqueKey;
        this.nodeInfoList = GwtNullSafe.get(nodeInfoList, Collections::unmodifiableList);
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

    public SvgImage getIcon() {
        return icon;
    }

    public NodeState getNodeState() {
        return nodeState;
    }

    public List<ExplorerNode> getChildren() {
        return children;
    }

    public boolean hasChildren() {
        return children != null && !children.isEmpty();
    }

    public String getRootNodeUuid() {
        return rootNodeUuid;
    }

    public boolean getIsFavourite() {
        return isFavourite;
    }

    public boolean getIsFilterMatch() {
        return isFilterMatch;
    }

    public boolean getIsFolder() {
        return isFolder;
    }

    @JsonIgnore
    public DocRef getDocRef() {
        // Cache the docRef
        if (docRef == null) {
            docRef = new DocRef(type, uuid, name);
        }
        return docRef;
    }

    @JsonIgnore
    @Override
    public String getDisplayValue() {
        return name;
    }

    public ExplorerNodeKey getUniqueKey() {
        return uniqueKey;
    }

    public List<NodeInfo> getNodeInfoList() {
        return nodeInfoList;
    }

    public boolean hasNodeInfo() {
        return GwtNullSafe.hasItems(nodeInfoList);
    }

    @JsonIgnore
    public Optional<Severity> getMaxSeverity() {
        return Optional.ofNullable(nodeInfoList)
                .flatMap(nodeInfoList2 -> nodeInfoList2.stream()
                        .map(NodeInfo::getSeverity)
                        .max(Severity.HIGH_TO_LOW_COMPARATOR));
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
        if (hashcode == 0) {
            hashcode = Objects.hashCode(uniqueKey);
        }
        return hashcode;
    }

    @Override
    public String toString() {
        return getDisplayValue();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }


    // --------------------------------------------------------------------------------


    public static final class Builder {

        private String type;
        private String uuid;
        private String name;
        private String tags;
        private int depth;
        private SvgImage icon;
        private NodeState nodeState;
        private List<ExplorerNode> children;
        private String rootNodeUuid;
        private boolean isFavourite;
        private Boolean isFilterMatch;
        private boolean isFolder;
        private List<NodeInfo> nodeInfoList;

        private Builder() {
        }

        private Builder(final ExplorerNode explorerNode) {
            this.type = explorerNode.type;
            this.uuid = explorerNode.uuid;
            this.name = explorerNode.name;
            this.tags = explorerNode.tags;
            this.depth = explorerNode.depth;
            this.icon = explorerNode.icon;
            this.nodeState = explorerNode.nodeState;
            this.children = GwtNullSafe.get(explorerNode.children, ArrayList::new);
            this.rootNodeUuid = explorerNode.rootNodeUuid;
            this.isFavourite = explorerNode.isFavourite;
            this.isFilterMatch = explorerNode.isFilterMatch;
            this.isFolder = explorerNode.isFolder;
            this.nodeInfoList = GwtNullSafe.get(explorerNode.nodeInfoList, ArrayList::new);
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

        public Builder icon(final SvgImage icon) {
            this.icon = icon;
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
            children.add(child);
            return this;
        }

        public boolean hasChildren() {
            return GwtNullSafe.hasItems(children);
        }

        public Builder rootNodeUuid(final String rootNodeUuid) {
            this.rootNodeUuid = rootNodeUuid;
            return this;
        }

        public Builder rootNodeUuid(final ExplorerNode node) {
            this.rootNodeUuid = GwtNullSafe.get(node, ExplorerNode::getUuid);
            return this;
        }

        public Builder isFavourite(final boolean isFavourite) {
            this.isFavourite = isFavourite;
            return this;
        }

        public Builder isFilterMatch(final boolean isFilterMatch) {
            this.isFilterMatch = isFilterMatch;
            return this;
        }

        public Builder isFolder(final boolean isFolder) {
            this.isFolder = isFolder;
            return this;
        }

        public Builder nodeInfoList(final List<NodeInfo> nodeInfoList) {
            if (GwtNullSafe.hasItems(nodeInfoList)) {
                this.nodeInfoList = new ArrayList<>(nodeInfoList);
            } else {
                this.nodeInfoList = null;
            }
            return this;
        }

        public Builder addNodeInfo(final NodeInfo nodeInfo) {
            if (nodeInfo != null) {
                if (this.nodeInfoList == null) {
                    this.nodeInfoList = new ArrayList<>();
                }
                this.nodeInfoList.add(nodeInfo);
            }
            return this;
        }

        public Builder addNodeInfo(final Collection<NodeInfo> nodeInfoList) {
            if (GwtNullSafe.hasItems(nodeInfoList)) {
                if (this.nodeInfoList == null) {
                    this.nodeInfoList = new ArrayList<>();
                }
                this.nodeInfoList.addAll(nodeInfoList);
            }
            return this;
        }

        public Builder clearNodeInfo() {
            if (this.nodeInfoList != null) {
                this.nodeInfoList.clear();
            }
            return this;
        }

        public ExplorerNode build() {
            return new ExplorerNode(
                    type,
                    uuid,
                    name,
                    tags,
                    depth,
                    icon,
                    nodeState,
                    children,
                    rootNodeUuid,
                    isFavourite,
                    GwtNullSafe.requireNonNullElse(isFilterMatch, true), // Default to true
                    isFolder,
                    new ExplorerNodeKey(type, uuid, rootNodeUuid),
                    nodeInfoList);
        }
    }


    // --------------------------------------------------------------------------------


    public enum NodeState {
        OPEN,
        CLOSED,
        LEAF
    }


    // --------------------------------------------------------------------------------


    @JsonInclude(Include.NON_NULL)
    @JsonPropertyOrder(alphabetic = true)
    @SuppressWarnings("ClassCanBeRecord") // Cos GWT
    public static class NodeInfo implements Comparable<NodeInfo> {

        private static final Comparator<NodeInfo> COMPARATOR = Comparator.comparing(
                NodeInfo::getSeverity,
                Severity.HIGH_TO_LOW_COMPARATOR);

        @JsonProperty
        private final Severity severity;
        @JsonProperty
        private final String description;

        @JsonCreator
        public NodeInfo(@JsonProperty("severity") final Severity severity,
                        @JsonProperty("description") final String description) {
            this.severity = Objects.requireNonNull(severity);
            this.description = Objects.requireNonNull(description);
        }

        public Severity getSeverity() {
            return severity;
        }

        public String getDescription() {
            return description;
        }

        @Override
        public String toString() {
            return severity + ": " + description;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final NodeInfo nodeInfo = (NodeInfo) o;
            return severity == nodeInfo.severity
                    && Objects.equals(description, nodeInfo.description);
        }

        @Override
        public int hashCode() {
            return Objects.hash(severity, description);
        }

        @Override
        public int compareTo(final NodeInfo other) {
            return COMPARATOR.compare(this, other);
        }
    }
}
