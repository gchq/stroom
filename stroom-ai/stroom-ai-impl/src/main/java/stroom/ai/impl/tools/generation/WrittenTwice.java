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

import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.sample.ToolJson;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// The same input a rule writes to two places, one of them (or both) a Data: the event says it twice. Seen (chat
/// 182, rule update_inboxrule): `event.Parameters`' items written as Data of `EventDetail/Update/After/Object` (the
/// agent's change) and as Data of `EventDetail/Update` (the draft's, kept as the agent's removal named another
/// path). The same input is the same entry but for where it is written (its path, data_name and data_parent), read
/// in the same groups (their for_each): the same value. A warning, never a problem, as a copy may be kept on purpose;
/// one written to two typed elements (UserId as EventSource/User/Id and Send/Source/User/Id) is the schema's own
/// shape and not warned of, nor is a top-level field written as it is to an element and as Data, which
/// [EventTrees] warns of in its own words.
final class WrittenTwice {

    private static final String TYPE_ID = "EventDetail/TypeId";

    private WrittenTwice() {
        // Static only.
    }

    /// @param rule A rule.
    /// @return A warning for each pair of the rule's entries writing the same input twice, naming the rule, the
    /// inputs, both places and the change removing one.
    static List<String> warnings(final EventRule rule) {
        final List<Placed> placed = new ArrayList<>();
        for (int top = 0; top < rule.fields().size(); top++) {
            final FieldMapping entry = rule.fields().get(top);
            collect(entry, top, entry, "", List.of(), placed);
        }
        // Each pair of top-level entries once, the inputs they both write listed.
        final Map<List<Integer>, List<String>> pairs = new LinkedHashMap<>();
        final Map<List<Integer>, Placed> removed = new LinkedHashMap<>();
        for (int i = 0; i < placed.size(); i++) {
            for (int j = i + 1; j < placed.size(); j++) {
                final Placed first = placed.get(i);
                final Placed second = placed.get(j);
                if (!first.input().equals(second.input()) || first.place().equals(second.place())
                    || !(first.data() || second.data())
                    || elementAndData(first, second)) {
                    continue;
                }
                final List<Integer> pair = List.of(first.top(), second.top());
                pairs.computeIfAbsent(pair, ignored -> new ArrayList<>()).add(first.describe() + " as "
                                                                              + first.place() + " and as "
                                                                              + second.place());
                removed.putIfAbsent(pair, toRemove(first, second));
            }
        }
        final List<String> out = new ArrayList<>();
        pairs.forEach((pair, inputs) -> out.add(message(rule, inputs, removed.get(pair),
                pair.getFirst().equals(pair.getLast()))));
        return out;
    }

    // Every entry that writes an input, below the groups it is in, with the place it writes it.
    private static void collect(final FieldMapping entry,
                                final int top,
                                final FieldMapping holder,
                                final String parent,
                                final List<ItemSource> groups,
                                final List<Placed> out) {
        final String place = placeOf(entry, parent);
        if (entry.isGroup()) {
            final List<ItemSource> inner = new ArrayList<>(groups);
            inner.add(entry.group().forEach());
            for (final FieldMapping field : entry.group().fields()) {
                collect(field, top, holder, place, List.copyOf(inner), out);
            }
            return;
        }
        if (entry.value() != null || TYPE_ID.equals(entry.strippedPath())) {
            // A constant is no input; a TypeId is what tells kinds apart, not a copy of what it is read from.
            return;
        }
        final FieldMapping input = entry.withPath("").withDataName(null).withDataParent(null);
        out.add(new Placed(top, holder, new Input(groups, input), place, entry.dataName() != null, entry));
    }

    // Where an entry writes its value: its path below its group's place, each Data named.
    private static String placeOf(final FieldMapping entry, final String parent) {
        final StringBuilder out = new StringBuilder(parent);
        if (!parent.isEmpty()) {
            out.append('/');
        }
        final String path = entry.strippedPath();
        if (entry.dataName() == null) {
            return out.append(path).toString();
        }
        out.append(path);
        if (entry.dataParent() != null) {
            for (final String name : entry.dataParent().split("/", -1)) {
                out.append("[@Name='").append(name).append("']/Data");
            }
        }
        return out.append("[@Name='").append(entry.dataName()).append("']").toString();
    }

    // The one to suggest removing: the Data beside a typed element, else the Data less deep (the more general
    // place), else the later.
    private static Placed toRemove(final Placed first, final Placed second) {
        if (first.data() != second.data()) {
            return first.data()
                    ? first
                    : second;
        }
        return depth(first.place()) < depth(second.place())
                ? first
                : second;
    }

    private static int depth(final String place) {
        return ToolText.split(place.replaceAll("\\[[^\\]]*\\]", ""), "/").size();
    }

    // A top-level field written as it is to an element and as Data: EventTrees warns of it in its own words.
    private static boolean elementAndData(final Placed first, final Placed second) {
        final FieldMapping e = first.entry();
        return first.topLevel() && second.topLevel() && first.data() != second.data() && e.field() != null
               && !e.field().isEmpty() && e.transform() == null && !e.hasMap()
               && (e.timeFormat() == null || e.timeFormat().isEmpty()) && e.lookup() == null
               && (e.dictionary() == null || e.dictionary().isEmpty()) && e.defaultValue() == null;
    }

    private static String message(final EventRule rule,
                                  final List<String> inputs,
                                  final Placed remove,
                                  final boolean oneGroup) {
        final String said = "[" + rule.name() + "] the same input is written twice: " + String.join("; ", inputs)
                            + ", so each event holds it twice. Remove one";
        final FieldMapping holder = remove.holder();
        if (oneGroup) {
            // Both in one group: only the group sent again without one of them removes it.
            return said + ": both are in the group " + holder.path() + ", so send it again (changes, by its path"
                   + (holder.dataName() == null
                    ? ""
                    : " and data_name") + ") without " + remove.place();
        }
        final Map<String, Object> removal = new LinkedHashMap<>();
        removal.put("path", holder.path());
        if (holder.dataName() != null) {
            removal.put("data_name", holder.dataName());
        }
        removal.put("remove", true);
        final Map<String, Object> change = new LinkedHashMap<>();
        change.put("name", rule.name());
        change.put("fields", List.of(removal));
        final String changes = ToolJson.dumps(Map.of("events", List.of(change)));
        return said + (remove.topLevel()
                ? ""
                : " (the one removed here is in the group " + holder.path() + ": if the group's other fields are "
                  + "wanted, send the group again without it instead)") + ", e.g. changes=" + changes;
    }


    // --------------------------------------------------------------------------------


    // An input as read: the groups it is read in (their for_each, outermost first) and the entry but for where it
    // is written.
    private record Input(List<ItemSource> groups, FieldMapping entry) {

    }

    // An entry writing an input: the index of the rule's top-level entry holding it and that entry (the entry itself,
    // or the group it is in), the input, the place it writes it, whether that is a Data, and the entry itself.
    private record Placed(int top, FieldMapping holder, Input input, String place, boolean data, FieldMapping entry) {

        boolean topLevel() {
            return holder == entry;
        }

        String describe() {
            final FieldMapping e = entry;
            final String what;
            if (e.field() != null) {
                what = "'" + e.field() + "'";
            } else if (e.anyOf() != null && !e.anyOf().isEmpty()) {
                what = "the first of " + ToolText.list(e.anyOf());
            } else if (e.join() != null) {
                what = ToolText.list(e.join()) + " joined";
            } else if (e.lookup() != null) {
                what = "lookup " + e.lookup().map();
            } else if (e.xpath() != null) {
                what = "xpath " + ToolText.repr(e.xpath());
            } else {
                what = "the item";
            }
            return input.groups().isEmpty()
                    ? what
                    : what + " (of " + input.groups().getLast().describe() + ")";
        }
    }
}
