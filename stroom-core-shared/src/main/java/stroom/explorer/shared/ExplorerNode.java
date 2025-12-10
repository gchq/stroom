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

package stroom.explorer.shared;

import stroom.docref.DocRef;
import stroom.docref.HasDisplayValue;
import stroom.util.shared.NullSafe;
import stroom.util.shared.SerialisationTestConstructor;
import stroom.util.shared.Severity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@JsonInclude(Include.NON_NULL)
public class ExplorerNode implements HasDisplayValue {

    public static final String TAGS_DELIMITER = " ";
    public static final String TAG_PATTERN_STR = "^[a-z0-9-]+$";
    public static final String NODE_PATH_STRING_DELIMITER = " / ";

    @JsonProperty
    private final String type;
    @JsonProperty
    private final String uuid;
    @JsonProperty
    private final String name;
    @JsonProperty
    private final Set<String> tags;

    @JsonProperty
    private final int depth;
    @JsonProperty
    private final List<ExplorerNode> children;
    @JsonProperty
    private final String rootNodeUuid;
    @JsonProperty
    private final ExplorerNodeKey uniqueKey;
    @JsonProperty
    private final List<NodeInfo> nodeInfoList;

    // NOTE GWT is not happy if we hold it as an EnumSet, just means we can't use some of the
    // EnumSet specific methods
    @JsonProperty
    private final Set<NodeFlag> nodeFlags;

    @JsonIgnore
    private volatile DocRef docRef;
    @JsonIgnore
    private volatile int hashcode;

    @JsonCreator
    public ExplorerNode(@JsonProperty("type") final String type,
                        @JsonProperty("uuid") final String uuid,
                        @JsonProperty("name") final String name,
                        @JsonProperty("tags") final Set<String> tags,
                        @JsonProperty("depth") final int depth,
                        @JsonProperty("children") final List<ExplorerNode> children,
                        @JsonProperty("rootNodeUuid") final String rootNodeUuid,
                        @JsonProperty("uniqueKey") final ExplorerNodeKey uniqueKey,
                        @JsonProperty("nodeInfoList") final List<NodeInfo> nodeInfoList,
                        @JsonProperty("nodeFlags") final Set<NodeFlag> nodeFlags) {
        this.type = type;
        this.uuid = uuid;
        this.name = name;
        this.tags = tags;
        this.depth = depth;
        this.children = NullSafe.get(children, Collections::unmodifiableList);
        this.rootNodeUuid = rootNodeUuid;
        this.uniqueKey = uniqueKey;
        this.nodeInfoList = NullSafe.get(nodeInfoList, Collections::unmodifiableList);
        this.nodeFlags = NullSafe.get(nodeFlags, EnumSet::copyOf);
    }

    public static ExplorerNode fromExplorerNodeKey(final ExplorerNodeKey explorerNodeKey) {
        return builder(explorerNodeKey)
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

    public Set<String> getTags() {
        return tags;
    }

    /**
     * @return True if this node's set of tags contains ALL the passed tags.
     */
    public boolean hasTags(final Set<String> tags) {
        return NullSafe.set(this.tags)
                .containsAll(tags);
    }

    public int getDepth() {
        return depth;
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

    /**
     * @return True if this node has {@link NodeInfo} items.
     */
    public boolean hasNodeInfo() {
        return NullSafe.hasItems(nodeInfoList);
    }

    /**
     * @return True if this node has {@link NodeInfo} items or one of its
     * descendants does.
     */
    public boolean hasDescendantNodeInfo() {
        return hasNodeFlag(NodeFlag.DESCENDANT_NODE_INFO) || hasNodeInfo();
    }

    public Set<NodeFlag> getNodeFlags() {
        return nodeFlags;
    }

    /**
     * @return True if this node's set of {@link NodeFlag}s contains nodeFlag.
     */
    public boolean hasNodeFlag(final NodeFlag nodeFlag) {
        if (nodeFlag == null) {
            return false;
        } else {
            return NullSafe.set(nodeFlags).contains(nodeFlag);
        }
    }

    /**
     * @return True if this node's set of {@link NodeFlag} contains at least one flag from nodeFlagGroup.
     */
    public boolean hasNodeFlagGroup(final NodeFlagGroup nodeFlagGroup) {
        if (nodeFlagGroup == null) {
            return false;
        } else {
            return nodeFlagGroup.stream()
                    .anyMatch(nodeFlags::contains);
        }
    }

    /**
     * @return True if this node's set of {@link NodeFlag}s contains ALL the flags in nodeFlags.
     */
    public boolean hasNodeFlags(final NodeFlag... nodeFlags) {
        return NullSafe.set(this.nodeFlags)
                .containsAll(Arrays.asList(nodeFlags));
    }

    /**
     * @return True if this node's set of {@link NodeFlag}s contains ALL the flags in nodeFlags.
     */
    public boolean hasNodeFlags(final Set<NodeFlag> nodeFlags) {
        return NullSafe.set(this.nodeFlags)
                .containsAll(nodeFlags);
    }

    /**
     * @return True if this node's set of {@link NodeFlag}s is missing nodeFlag.
     */
    public boolean isMissingNodeFlag(final NodeFlag nodeFlag) {
        return !NullSafe.set(nodeFlags).contains(nodeFlag);
    }

    /**
     * @return True if this node's set of {@link NodeFlag}s is missing ALL the flags in nodeFlags.
     */
    public boolean isMissingNodeFlags(final NodeFlag... nodeFlags) {
        if (NullSafe.hasItems(this.nodeFlags)) {
            return NullSafe.stream(nodeFlags)
                    .noneMatch(this.nodeFlags::contains);
        } else {
            return true;
        }
    }

//    @JsonIgnore
//    public Optional<Severity> getMaxSeverity() {
//        return Optional.ofNullable(nodeInfoList)
//                .flatMap(nodeInfoList2 -> nodeInfoList2.stream()
//                        .map(NodeInfo::getSeverity)
//                        .max(Severity.HIGH_TO_LOW_COMPARATOR));
//    }

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

    public static Builder builder(final ExplorerNodeKey explorerNodeKey) {
        Objects.requireNonNull(explorerNodeKey);
        return new Builder()
                .type(explorerNodeKey.getType())
                .uuid(explorerNodeKey.getUuid())
                .rootNodeUuid(explorerNodeKey.getRootNodeUuid());
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static String buildDocRefPathString(final Collection<DocRef> path) {
        if (NullSafe.isEmptyCollection(path)) {
            return "";
        } else {
            return path.stream()
                    .filter(Objects::nonNull)
                    .map(DocRef::getDisplayValue)
                    .collect(Collectors.joining(NODE_PATH_STRING_DELIMITER));
        }
    }

    public static String buildNodePathString(final Collection<ExplorerNode> path) {
        if (NullSafe.isEmptyCollection(path)) {
            return "";
        } else {
            return path.stream()
                    .filter(Objects::nonNull)
                    .map(ExplorerNode::getDisplayValue)
                    .collect(Collectors.joining(NODE_PATH_STRING_DELIMITER));
        }
    }


    // --------------------------------------------------------------------------------


    public static final class Builder {

        private String type;
        private String uuid;
        private String name;
        private Set<String> tags;
        private int depth;
        private List<ExplorerNode> children;
        private String rootNodeUuid;
        private List<NodeInfo> nodeInfoList;
        private Set<NodeFlag> nodeFlags;

        private Builder() {
        }

        private Builder(final ExplorerNode explorerNode) {
            // Create new collections
            this.type = explorerNode.type;
            this.uuid = explorerNode.uuid;
            this.name = explorerNode.name;
            this.tags = explorerNode.tags;
            this.depth = explorerNode.depth;
            this.children = NullSafe.get(explorerNode.children, ArrayList::new);
            this.rootNodeUuid = explorerNode.rootNodeUuid;
            this.nodeInfoList = NullSafe.get(explorerNode.nodeInfoList, ArrayList::new);
            this.nodeFlags = NullSafe.get(explorerNode.nodeFlags, EnumSet::copyOf);
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

        /**
         * Set the tags on this builder to tags
         */
        public Builder tags(final Set<String> tags) {
            this.tags = NullSafe.hasItems(tags)
                    ? new HashSet<>(tags)
                    : null;
            return this;
        }

        /**
         * Add a single tag to the builder
         */
        public Builder addTag(final String tag) {
            if (!NullSafe.isBlankString(tag)) {
                if (this.tags == null) {
                    this.tags = new HashSet<>();
                }
                this.tags.add(tag);
            }
            return this;
        }

        /**
         * Add multiple tags to the builder
         */
        public Builder addTags(final Set<String> tags) {
            if (NullSafe.hasItems(tags)) {
                if (this.tags == null) {
                    this.tags = new HashSet<>();
                }
                this.tags.addAll(tags);
            }
            return this;
        }

        public Builder addTags(final String... tags) {
            if (NullSafe.hasItems(tags)) {
                addTags(NullSafe.asSet(tags));
            }
            return this;
        }

        public Builder depth(final int depth) {
            this.depth = depth;
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
            return NullSafe.hasItems(children);
        }

        public Builder rootNodeUuid(final String rootNodeUuid) {
            this.rootNodeUuid = rootNodeUuid;
            return this;
        }

        public Builder rootNodeUuid(final ExplorerNode node) {
            this.rootNodeUuid = NullSafe.get(node, ExplorerNode::getUuid);
            return this;
        }

        public Builder nodeInfoList(final List<NodeInfo> nodeInfoList) {
            if (NullSafe.hasItems(nodeInfoList)) {
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
            if (NullSafe.hasItems(nodeInfoList)) {
                if (this.nodeInfoList == null) {
                    this.nodeInfoList = new ArrayList<>();
                }
                this.nodeInfoList.addAll(nodeInfoList);
            }
            return this;
        }

        public Builder nodeFlags(final Set<NodeFlag> nodeFlags) {
            if (NullSafe.hasItems(nodeFlags)) {
                NodeFlag.validateFlags(nodeFlags);
                this.nodeFlags = EnumSet.copyOf(nodeFlags);
            } else {
                this.nodeFlags = null;
            }
            return this;
        }

        /**
         * Adds a flag to this node if nodeFlag is not null.
         */
        public Builder addNodeFlag(final NodeFlag nodeFlag) {
            if (nodeFlag != null) {
                if (this.nodeFlags == null) {
                    this.nodeFlags = EnumSet.noneOf(NodeFlag.class);
                }
                NodeFlag.validateFlag(nodeFlag, this.nodeFlags);
                this.nodeFlags.add(nodeFlag);
            }
            return this;
        }

        public Builder removeNodeFlag(final NodeFlag nodeFlag) {
            if (nodeFlag != null && NullSafe.hasItems(this.nodeFlags)) {
                this.nodeFlags.remove(nodeFlag);
            }
            return this;
        }

        /**
         * Sets the presence of nodeFlag in this node's set of {@link NodeFlag}s based on the
         * value of isPresent.
         */
        public Builder setNodeFlag(final NodeFlag nodeFlag, final boolean isPresent) {
            if (nodeFlag != null) {
                if (this.nodeFlags == null) {
                    this.nodeFlags = EnumSet.noneOf(NodeFlag.class);
                }
                if (isPresent) {
                    NodeFlag.validateFlag(nodeFlag, this.nodeFlags);
                    this.nodeFlags.add(nodeFlag);
                } else {
                    this.nodeFlags.remove(nodeFlag);
                }
            }
            return this;
        }

        /**
         * Sets the presence of nodeFlag in this node's set of {@link NodeFlag}s based on the
         * value of state.
         */
        public Builder setGroupedNodeFlag(final NodeFlagPair nodeFlagPair,
                                          final boolean isOn) {
            Objects.requireNonNull(nodeFlagPair);
            if (nodeFlags == null) {
                nodeFlags = EnumSet.noneOf(NodeFlag.class);
            }
            nodeFlagPair.addFlag(isOn, nodeFlags);
            return this;
        }

        /**
         * Sets the presence of nodeFlag in this node's set of {@link NodeFlag}s and
         * removes the other members of nodeFlagGroup
         */
        public Builder setGroupedNodeFlag(final NodeFlagGroup nodeFlagGroup,
                                          final NodeFlag nodeFlag) {
            Objects.requireNonNull(nodeFlagGroup);
            Objects.requireNonNull(nodeFlag);
            if (nodeFlags == null) {
                nodeFlags = EnumSet.noneOf(NodeFlag.class);
            }
            nodeFlagGroup.addFlag(nodeFlag, nodeFlags);
            return this;
        }

        public Builder addNodeFlags(final NodeFlag... nodeFlags) {
            if (NullSafe.hasItems(nodeFlags)) {
                if (this.nodeFlags == null) {
                    this.nodeFlags = EnumSet.noneOf(NodeFlag.class);
                }
                for (final NodeFlag nodeFlag : nodeFlags) {
                    NodeFlag.validateFlag(nodeFlag, this.nodeFlags);
                    this.nodeFlags.add(nodeFlag);
                }
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
                    children,
                    rootNodeUuid,
                    new ExplorerNodeKey(type, uuid, rootNodeUuid),
                    nodeInfoList,
                    nodeFlags);
        }
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

        @SerialisationTestConstructor
        private NodeInfo() {
            this.severity = Severity.INFO;
            this.description = "test";
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
