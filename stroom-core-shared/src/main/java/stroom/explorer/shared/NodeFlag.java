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

import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A set of flags that can be used to mark {@link ExplorerNode} with characteristics.
 * Some flags are mutually exclusive with others.
 * Some are part of a mutually exclusive {@link NodeFlagGroup}.
 *
 * {@link NodeFlag}s are distinct from explorer node tags in that they are not directly visible
 * to users (or not visible at all) and should only be used for internal conditional
 * handling/rendering of nodes.
 * Tags are directly visible to users and are managed by them.
 */
public enum NodeFlag {

    // IMPORTANT - Best not to change the short form codes as these are used for json serialisation.
    // Use a one/two char code for each item.  Must be unique, obvs.

    /**
     * Node is folder that is in its closed state
     */
    CLOSED("Closed", "C"),
    /**
     * Node is a datasource, used for adding searchables to the tree
     */
    DATA_SOURCE("Data Source", "D"),
    /**
     * One or more descendant nodes have {@link stroom.explorer.shared.ExplorerNode.NodeInfo}.
     */
    DESCENDANT_NODE_INFO("Descendant Node Info", "I"),
    /**
     * Node has been marked as a favourite by the user
     */
    FAVOURITE("Favourite", "V"),
    /**
     * Node is a fuzzy filter match
     */
    FILTER_MATCH("Filter Match", "FM"),
    /**
     * Node is not a fuzzy filter match
     */
    FILTER_NON_MATCH("Filter Non-Match", "FN"),
    /**
     * Node is a folder
     */
    FOLDER("Folder", "F"),
    /**
     * Node is a leaf (either a doc or a folder with no children)
     */
    LEAF("Leaf", "L"),
    /**
     * Node is a folder that is in its open state.
     */
    OPEN("Open", "O");

    private static final Map<String, NodeFlag> SHORT_FORM_TO_NODE_FLAG_MAP = Arrays.stream(NodeFlag.values())
            .collect(Collectors.toMap(NodeFlag::getShortForm, Function.identity()));
    private static final EnumMap<NodeFlag, EnumSet<NodeFlag>> INCOMPATIBLE_FLAGS = new EnumMap<>(NodeFlag.class);

    // Private so they don't get confused with the enum values, public can access them
    // via NodeFlagGroups
    private static final NodeFlagPair FILTER_MATCH_PAIR = new NodeFlagPair(FILTER_MATCH, FILTER_NON_MATCH);
    private static final NodeFlagGroup EXPANDER_GROUP = new NodeFlagGroup(OPEN, CLOSED, LEAF);

    static {
        // Define any incompatibilities between flags, either using individual flags or NodeFlagGroup
        // objects
        addIncompatibilities(DATA_SOURCE, FOLDER);
        addIncompatibilities(EXPANDER_GROUP);
        addIncompatibilities(FILTER_MATCH_PAIR);
        addIncompatibilities(EXPANDER_GROUP);

        final long distinctCount = Arrays.stream(values())
                .map(NodeFlag::getShortForm)
                .distinct()
                .count();
        if (distinctCount != values().length) {
            throw new RuntimeException("Found duplicate shortForm values");
        }
    }

    private final String displayName;

    /**
     * This is the form used for json serialisation. A happy medium between using numbers which are a pain
     * when debugging vs the enum names which take up a lot of space.
     */
    private final String shortForm;

    @JsonCreator
    static NodeFlag fromShortForm(final String shortForm) {
        if (shortForm == null) {
            return null;
        } else {
            final NodeFlag nodeFlag = SHORT_FORM_TO_NODE_FLAG_MAP.get(shortForm);
            if (nodeFlag == null) {
                throw new RuntimeException("Unknown shortForm '" + shortForm + "'");
            }
            return nodeFlag;
        }
    }

    NodeFlag(final String displayName, final String shortForm) {
        this.displayName = displayName;
        this.shortForm = shortForm;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Use the shortForm for json serialisation to reduce the size of the json as trees can contain a LOT
     * of nodes.
     */
    @JsonValue
    public String getShortForm() {
        return shortForm;
    }

    private static void addIncompatibilities(final NodeFlag flag1, final NodeFlag flag2) {
        Objects.requireNonNull(flag1);
        Objects.requireNonNull(flag2);

        if (Objects.equals(flag1, flag2)) {
            throw new RuntimeException(flag1 + " can't be incompatible with itself");
        }
        // Map it both ways
        INCOMPATIBLE_FLAGS.computeIfAbsent(flag1, k -> EnumSet.noneOf(NodeFlag.class))
                .add(flag2);
        INCOMPATIBLE_FLAGS.computeIfAbsent(flag2, k -> EnumSet.noneOf(NodeFlag.class))
                .add(flag1);
    }

    private static void addIncompatibilities(final NodeFlagPair nodeFlagPair) {
        Objects.requireNonNull(nodeFlagPair);
        addIncompatibilities(nodeFlagPair.getOnFlag(), nodeFlagPair.getOffFlag());
    }

    private static void addIncompatibilities(final NodeFlagGroup nodeFlagGroup) {
        Objects.requireNonNull(nodeFlagGroup);

        for (final NodeFlag nodeFlag1 : nodeFlagGroup.getNodeFlags()) {
            for (final NodeFlag nodeFlag2 : nodeFlagGroup.getNodeFlags()) {
                if (!Objects.equals(nodeFlag1, nodeFlag2)) {
                    addIncompatibilities(nodeFlag1, nodeFlag2);
                }
            }
        }
    }

    /**
     * Validate a set of flags to make sure it does not contain any that are incompatible
     */
    public static void validateFlags(final Set<NodeFlag> flags) {
        if (NullSafe.hasItems(flags)) {
            for (final NodeFlag flag : flags) {
                final Set<NodeFlag> incompatibleFlags = INCOMPATIBLE_FLAGS.get(flag);
                if (NullSafe.hasItems(incompatibleFlags)) {
                    for (final NodeFlag incompatibleFlag : incompatibleFlags) {
                        if (flags.contains(incompatibleFlag)) {
                            throw new RuntimeException(flag + " is incompatible with "
                                    + incompatibleFlag + " in " + flags);
                        }
                    }
                }
            }
        }
    }

    /**
     * Check whether flag is incompatible with any flags in flags.
     */
    public static void validateFlag(final NodeFlag flag, final Set<NodeFlag> flags) {
        if (flag != null && NullSafe.hasItems(flags)) {
            final Set<NodeFlag> incompatibleFlags = INCOMPATIBLE_FLAGS.get(flag);
            if (NullSafe.hasItems(incompatibleFlags)) {
                for (final NodeFlag incompatibleFlag : incompatibleFlags) {
                    if (flags.contains(incompatibleFlag)) {
                        throw new RuntimeException(flag + " is incompatible with "
                                + incompatibleFlag + " in " + flags);
                    }
                }
            }
        }
    }


    // --------------------------------------------------------------------------------


    public static class NodeFlagGroups {

        private NodeFlagGroups() {
        }

        // Re-defined here as a public source of groups to avoid initialisation order issues
        // with the NodeFlag enum.
        public static final NodeFlagPair FILTER_MATCH_PAIR = NodeFlag.FILTER_MATCH_PAIR;
        public static final NodeFlagGroup EXPANDER_GROUP = NodeFlag.EXPANDER_GROUP;
    }
}
