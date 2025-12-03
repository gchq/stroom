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

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A group (containing three or more) of mutually exclusive flags.
 */
public class NodeFlagGroup {

    private final Set<NodeFlag> nodeFlags;

    NodeFlagGroup(final Set<NodeFlag> nodeFlags) {
        this.nodeFlags = EnumSet.copyOf(Objects.requireNonNull(nodeFlags));
    }

    NodeFlagGroup(final NodeFlag... nodeFlags) {
        Objects.requireNonNull(nodeFlags);
        this.nodeFlags = EnumSet.copyOf(Arrays.asList(nodeFlags));
    }

    public Set<NodeFlag> getNodeFlags() {
        return nodeFlags;
    }

    public Stream<NodeFlag> stream() {
        return nodeFlags.stream();
    }

    /**
     * Adds nodeFlag to the provided flags set after removing the other members of this {@link NodeFlagGroup}
     *
     * @param nodeFlag Flag to add
     * @param flags    Set to add to
     */
    public void addFlag(final NodeFlag nodeFlag, final Set<NodeFlag> flags) {
        Objects.requireNonNull(flags);
        if (!this.nodeFlags.contains(nodeFlag)) {
            throw new IllegalArgumentException("nodeFlag " + nodeFlag + " not in " + this.nodeFlags);
        }

        for (final NodeFlag flag : this.nodeFlags) {
            if (!Objects.equals(flag, nodeFlag)) {
                flags.remove(flag);
            }
        }
        flags.add(nodeFlag);
        NodeFlag.validateFlag(nodeFlag, flags);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final NodeFlagGroup that = (NodeFlagGroup) o;
        return Objects.equals(nodeFlags, that.nodeFlags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeFlags);
    }

    @Override
    public String toString() {
        return "NodeFlagGroup{" +
                "nodeFlags=" + nodeFlags +
                '}';
    }
}
