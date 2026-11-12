/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.impl.tools.generation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// A repeated group: the element at a field entry's path written once per item (`for_each`), each holding its own
/// fields, read from that item. Asked for by the user: each recipient of a mail gateway's comma list as one
/// `EventDetail/Send/Destination/User` with its own `Id` and `Domain`, which `repeat` (one element per value of one
/// input) couldn't express.
///
/// A group's fields are field entries whose paths are below the group's element (relative to it, e.g. `Id`). Each
/// reads its group's item by default (a key of it with `field`, the item itself with `item: true`), an outer group's
/// item with `item: "<its as>"`, or the record with `scope: record`. A field may be a group itself, to
/// [#MAX_DEPTH] deep. In the XSLT a group is an `xsl:for-each` over its items: loops nested as the mapping nests
/// them, never a recursion following the input's depth.
///
/// @param forEach `for_each`: where the items come from.
/// @param as      `as`: the item's name, which fields of groups inside this one read it by; null for `item`.
/// @param fields  The fields each item's element holds.
public record FieldGroup(ItemSource forEach,
                         String as,
                         List<FieldMapping> fields) {

    /// The deepest groups nest: a group inside a group inside ... sixteen deep at most.
    public static final int MAX_DEPTH = 16;

    /// Fills in an empty list for a null one.
    public FieldGroup {
        fields = List.copyOf(Objects.requireNonNullElse(fields, List.of()));
    }

    /// @return The item's name: `as`, or `item` when none is given.
    public String itemName() {
        return as == null
                ? "item"
                : as;
    }

    /// @param fields Its fields.
    /// @return This group with those fields.
    public FieldGroup withFields(final List<FieldMapping> fields) {
        return new FieldGroup(forEach, as, fields);
    }

    /// Every field entry, those inside groups too (at any depth), each as given (a group's fields with paths relative
    /// to it), outermost first: what reads input fields by name (the checks of which fields a mapping reads).
    ///
    /// @param entries Field entries.
    /// @return Them and every entry inside their groups.
    public static List<FieldMapping> everyEntry(final List<FieldMapping> entries) {
        final List<FieldMapping> out = new ArrayList<>();
        addEvery(entries, out, 0);
        return out;
    }

    private static void addEvery(final List<FieldMapping> entries, final List<FieldMapping> out, final int depth) {
        for (final FieldMapping entry : entries) {
            out.add(entry);
            if (entry.group() != null && depth < MAX_DEPTH) {
                addEvery(entry.group().fields(), out, depth + 1);
            }
        }
    }

    /// The entries that write values, a group's fields each with its full path (the group's path and its own) and
    /// the groups it is in: what the documentation and the index plans list, a row each. A Data group's Data
    /// entries are given as Data written in it (path the group's, data_parent its Name), as data_parent's are.
    ///
    /// @param entries Field entries.
    /// @return Each entry that isn't a group, and each field of a group (at any depth), in order.
    public static List<Leaf> leaves(final List<FieldMapping> entries) {
        final List<Leaf> out = new ArrayList<>();
        addLeaves(entries, null, null, List.of(), out);
        return out;
    }

    // base: the enclosing group's full path (null at the top); dataKey: its data key, for a Data group.
    private static void addLeaves(final List<FieldMapping> entries,
                                  final String base,
                                  final String dataKey,
                                  final List<FieldMapping> groups,
                                  final List<Leaf> out) {
        for (final FieldMapping entry : entries) {
            final FieldMapping placed;
            if (dataKey != null && entry.dataName() != null) {
                // A Data in a Data group: written in the group's Data element.
                placed = entry.withPath(base).withDataParent(entry.dataParent() == null
                        ? dataKey
                        : dataKey + "/" + entry.dataParent());
            } else {
                placed = entry.withPath(base == null
                        ? entry.strippedPath()
                        : base + "/" + entry.strippedPath());
            }
            if (entry.group() == null) {
                out.add(new Leaf(placed, groups));
            } else if (groups.size() < MAX_DEPTH) {
                final List<FieldMapping> inner = new ArrayList<>(groups);
                inner.add(placed);
                addLeaves(entry.group().fields(), placed.strippedPath(), placed.dataKey(), List.copyOf(inner), out);
            }
        }
    }

    /// @param groups The groups a field is in, outermost first.
    /// @return Where its items come from, for a reader, innermost first, e.g. `` each attachment (each item of
    /// `attachments`) of each recipient (each of `recipients` split on ',') ``.
    public static String itemsText(final List<FieldMapping> groups) {
        final List<String> parts = new ArrayList<>();
        for (int i = groups.size() - 1; i >= 0; i--) {
            final FieldGroup group = groups.get(i).group();
            parts.add("each " + group.itemName() + " (" + group.forEach().describe() + ")");
        }
        return String.join(" of ", parts);
    }


    // --------------------------------------------------------------------------------


    /// A field entry that writes a value, as [#leaves(List)] gives it.
    ///
    /// @param entry  The entry, with its full path below Event.
    /// @param groups The groups it is in, outermost first, each with its full path; empty for one in none.
    public record Leaf(FieldMapping entry, List<FieldMapping> groups) {

        /// @return True if it is in a group.
        public boolean grouped() {
            return !groups.isEmpty();
        }
    }
}
