/*
 * Copyright 2025 Crown Copyright
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

package stroom.pathways.shared.pathway;

import stroom.pathways.shared.otel.trace.NanoTime;
import stroom.util.shared.AbstractBuilder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@JsonInclude(Include.NON_NULL)
public class PathNode {

    @JsonProperty
    private final String uuid;
    @JsonProperty
    private final String name;
    @JsonProperty
    private final List<String> nodePath;
    @JsonProperty
    private final List<PathNode> children;
    @JsonProperty
    private final Map<String, Constraint> constraints;
    @JsonProperty
    private final long timesUsed;
    @JsonProperty
    private final NanoTime lastUsedTime;
    /**
     * When this node stopped being part of the work, or null while it is still part of it.
     *
     * <p>Set by the narrowing where no trace within the document's narrowing window carried the
     * node, and cleared the moment a trace carries it again. The node is kept either way: a path's steps name
     * nodes by position, a replay puts them back by uuid, and a drawing follows one from frame to
     * frame by uuid — so removing one would invalidate every path that ran through it, and a node that
     * came back would come back a stranger.
     */
    @JsonProperty
    private final NanoTime retiredTime;

    @JsonCreator
    public PathNode(@JsonProperty("uuid") final String uuid,
                    @JsonProperty("name") final String name,
                    @JsonProperty("nodePath") final List<String> nodePath,
                    @JsonProperty("children") final List<PathNode> children,
                    @JsonProperty("constraints") final Map<String, Constraint> constraints,
                    @JsonProperty("timesUsed") final Long timesUsed,
                    @JsonProperty("lastUsedTime") final NanoTime lastUsedTime,
                    @JsonProperty("retiredTime") final NanoTime retiredTime) {
        this.uuid = uuid;
        this.name = name;
        this.nodePath = nodePath;
        this.children = children == null
                ? new ArrayList<>()
                : new ArrayList<>(children);
        this.constraints = constraints;
        this.timesUsed = Objects.requireNonNullElse(timesUsed, 0L);
        this.lastUsedTime = lastUsedTime;
        this.retiredTime = retiredTime;
    }

    /**
     * A node no trace has reached yet, for a caller making one outside the learner.
     */
    public PathNode(final String uuid,
                    final String name,
                    final List<String> nodePath,
                    final List<PathNode> children,
                    final Map<String, Constraint> constraints) {
        this(uuid, name, nodePath, children, constraints, 0L, null, null);
    }

    public PathNode(final String name,
                    final List<String> nodePath) {
        this.uuid = UUID.randomUUID().toString();
        this.name = name;
        this.nodePath = nodePath;
        this.children = new ArrayList<>();
        this.constraints = null;
        this.retiredTime = null;
        this.timesUsed = 0L;
        this.lastUsedTime = null;
    }

    public PathNode(final String name) {
        this.uuid = UUID.randomUUID().toString();
        this.name = name;
        this.nodePath = Collections.singletonList(name);
        this.children = new ArrayList<>();
        this.constraints = null;
        this.retiredTime = null;
        this.timesUsed = 0L;
        this.lastUsedTime = null;
    }

    /**
     * How many spans have been folded into this node. Counted per span rather than per trace, so a
     * trace carrying forty spans of this name adds forty — what a diagram weighs a node by is how much
     * went through it, not how many traces mentioned it.
     */
    public long getTimesUsed() {
        return timesUsed;
    }

    /**
     * The last time a trace reached this node. When it last <em>changed</em> is not held here — that
     * is what the stored changes say, and holding it twice would let the two differ.
     */
    public NanoTime getLastUsedTime() {
        return lastUsedTime;
    }

    /**
     * When this node stopped being part of the work, or null while it is still part of it.
     */
    public NanoTime getRetiredTime() {
        return retiredTime;
    }

    /** Whether no trace within the document's narrowing window has carried this node. */
    @JsonIgnore
    public boolean isRetired() {
        return retiredTime != null;
    }

    public String getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public List<String> getNodePath() {
        return nodePath;
    }

    /**
     * The distinct things seen beneath this one, one for each name rather than one for each span. How
     * many of a name a trace carried, and whether it carried any at all, are constraints on the child
     * rather than a different set of children.
     */
    public List<PathNode> getChildren() {
        return children;
    }

    public Map<String, Constraint> getConstraints() {
        return constraints;
    }

    /**
     * Two nodes are the same node when they carry the same uuid.
     *
     * <p>Not by name. A name is unique only among one node's children, so a model that commits in two
     * places holds two nodes called Commit, and a pathway drawn from one of them is a different thing
     * from the other: its own constraints, its own place, its own history. Telling them apart is what
     * the uuid is for — it is how a path names a node, how a replay puts one back, and how a drawing
     * follows one from frame to frame — and anything holding nodes by name makes those two one.
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PathNode node = (PathNode) o;
        return Objects.equals(uuid, node.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(uuid);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        int depth = 0;
        for (final String part : nodePath) {
            for (int i = 0; i < depth * 3; i++) {
                sb.append(' ');
            }
            sb.append(part);
            sb.append("\n");
            depth++;
        }
        return sb.toString();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractBuilder<PathNode, Builder> {

        private String uuid;
        private String name;
        private List<String> nodePath;
        private List<PathNode> children;
        private Map<String, Constraint> constraints;
        private long timesUsed;
        private NanoTime lastUsedTime;
        private NanoTime retiredTime;

        public Builder() {
        }

        public Builder(final PathNode pathNode) {
            this.uuid = pathNode.uuid;
            this.name = pathNode.name;
            this.nodePath = pathNode.nodePath;
            this.children = pathNode.children;
            this.constraints = pathNode.constraints;
            this.timesUsed = pathNode.timesUsed;
            this.lastUsedTime = pathNode.lastUsedTime;
            this.retiredTime = pathNode.retiredTime;
        }

        public Builder uuid(final String uuid) {
            this.uuid = uuid;
            return self();
        }

        public Builder name(final String name) {
            this.name = name;
            return self();
        }

        public Builder nodePath(final List<String> nodePath) {
            this.nodePath = nodePath;
            return self();
        }

        public Builder children(final List<PathNode> children) {
            this.children = children;
            return self();
        }

        public Builder constraints(final Map<String, Constraint> constraints) {
            this.constraints = constraints;
            return self();
        }

        public Builder timesUsed(final long timesUsed) {
            this.timesUsed = timesUsed;
            return self();
        }

        public Builder lastUsedTime(final NanoTime lastUsedTime) {
            this.lastUsedTime = lastUsedTime;
            return self();
        }

        public Builder retiredTime(final NanoTime retiredTime) {
            this.retiredTime = retiredTime;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        public PathNode build() {
            return new PathNode(
                    uuid,
                    name,
                    nodePath,
                    children,
                    constraints,
                    timesUsed,
                    lastUsedTime,
                    retiredTime);
        }
    }
}
