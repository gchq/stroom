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

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * A pair of mutually exclusive flags
 */
public class NodeFlagPair extends NodeFlagGroup {

    private final NodeFlag on;
    private final NodeFlag off;

    NodeFlagPair(final NodeFlag on, final NodeFlag off) {
        super(EnumSet.of(
                Objects.requireNonNull(on),
                Objects.requireNonNull(off)));
        this.on = on;
        this.off = off;
        if (Objects.equals(on, off)) {
            throw new IllegalArgumentException("Identical nodes flags");
        }
    }

    NodeFlag getNodeFlag(final boolean state) {
        return state
                ? on
                : off;
    }

    public NodeFlag getOnFlag() {
        return on;
    }

    public NodeFlag getOffFlag() {
        return off;
    }

    /**
     * Adds nodeFlag to the provided flags set after removing the other member of this {@link NodeFlagPair}
     *
     * @param isOn  The flag to add based on its state
     * @param flags Set to add to
     */
    public void addFlag(final boolean isOn, final Set<NodeFlag> flags) {
        Objects.requireNonNull(flags);

        final NodeFlag flagToAdd = isOn
                ? on
                : off;
        final NodeFlag flagToRemove = isOn
                ? off
                : on;

        flags.remove(flagToRemove);
        flags.add(flagToAdd);
        NodeFlag.validateFlag(flagToAdd, flags);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final NodeFlagPair that = (NodeFlagPair) o;
        return on == that.on && off == that.off;
    }

    @Override
    public int hashCode() {
        return Objects.hash(on, off);
    }

    @Override
    public String toString() {
        return "NodeFlagPair{" +
                "on=" + on +
                ", off=" + off +
                '}';
    }
}
