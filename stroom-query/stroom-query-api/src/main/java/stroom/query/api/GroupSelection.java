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

package stroom.query.api;

import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@JsonPropertyOrder({"expandedDepth", "openGroups", "closedGroups"})
@JsonInclude(Include.NON_NULL)
public class GroupSelection {
    @JsonProperty
    private final int expandedDepth;
    @JsonProperty
    private final Set<String> openGroups;
    @JsonProperty
    private final Set<String> closedGroups;

    public GroupSelection() {
        this(0, null, null);
    }

    @JsonCreator
    public GroupSelection(@JsonProperty("expandedDepth") final int expandedDepth,
                          @JsonProperty("openGroups") final Set<String> openGroups,
                          @JsonProperty("closedGroups") final Set<String> closedGroups) {
        this.expandedDepth = expandedDepth;
        this.openGroups = openGroups == null ? new HashSet<>() : new HashSet<>(openGroups);
        this.closedGroups = closedGroups == null ? new HashSet<>() : new HashSet<>(closedGroups);
    }

    public int getExpandedDepth() {
        return expandedDepth;
    }

    public Set<String> getOpenGroups() {
        return openGroups;
    }

    public Set<String> getClosedGroups() {
        return closedGroups;
    }

    public void open(final String group) {
        openGroups.add(group);
        closedGroups.remove(group);
    }

    public void close(final String group) {
        closedGroups.add(group);
        openGroups.remove(group);
    }

    public boolean isGroupOpen(final String group, final int depth) {
        return (depth < expandedDepth || openGroups.contains(group)) && !closedGroups.contains(group);
    }

    public boolean hasGroupsSelected() {
        return expandedDepth > 0 || hasOpenGroups() || hasClosedGroups();
    }

    public boolean hasOpenGroups() {
        return !NullSafe.isEmptyCollection(openGroups);
    }

    public boolean hasClosedGroups() {
        return !NullSafe.isEmptyCollection(closedGroups);
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final GroupSelection that = (GroupSelection) o;
        return expandedDepth == that.expandedDepth &&
               Objects.equals(openGroups, that.openGroups) &&
               Objects.equals(closedGroups, that.closedGroups);
    }

    @Override
    public int hashCode() {
        return Objects.hash(expandedDepth, openGroups, closedGroups);
    }

    @Override
    public String toString() {
        return "GroupSelection{" +
               "minDepth=" + expandedDepth +
               ", openGroups=" + openGroups +
               ", closedGroups=" + closedGroups +
               '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {

        private int expandedDepth;
        private Set<String> openGroups;
        private Set<String> closedGroups;

        private Builder() {}

        private Builder(final GroupSelection groupSelection) {
            this.expandedDepth = groupSelection.expandedDepth;
            this.closedGroups = groupSelection.closedGroups;
            this.openGroups = groupSelection.openGroups;
        }

        public Builder expand(final int maxDepth) {
            if (expandedDepth < maxDepth) {
                ++expandedDepth;
            }
            return this;
        }

        public Builder collapse() {
            if (expandedDepth > 0) {
                --expandedDepth;
            }
            return this;
        }

        public Builder expandedDepth(final int expandedDepth) {
            this.expandedDepth = expandedDepth;
            return this;
        }

        public Builder openGroups(final Set<String> openGroups) {
            this.openGroups = openGroups;
            return this;
        }

        public Builder closedGroups(final Set<String> closedGroups) {
            this.closedGroups = closedGroups;
            return this;
        }

        public GroupSelection build() {
            return new GroupSelection(expandedDepth, openGroups, closedGroups);
        }
    }
}
