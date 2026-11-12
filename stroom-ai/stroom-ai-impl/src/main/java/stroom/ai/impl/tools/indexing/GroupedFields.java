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

package stroom.ai.impl.tools.indexing;

import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.generation.FieldGroup;
import stroom.ai.impl.tools.generation.FieldMapping;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// The planned fields read from a translation's repeated group (`for_each`: one element written per item, e.g. each
/// recipient one `EventDetail/Send/Destination/User` holding its own `Id` and `Domain`), planned so an item's values
/// stay together where the index can hold that.
///
/// Elasticsearch (with subobjects, as by default): the fields read from one group's element become one field of
/// objects, one object per element, each with the item's own fields, mapped as `object` (not `nested`), so a query
/// on two of an item's fields can match values from different items. Lucene, which holds no objects (and
/// Elasticsearch with flat names): fields of several values each, the pairing of an item's values lost. Each says
/// so in its description, after [#NOTE_LEAD].
///
/// Only the outermost group is an object: a group inside it is fields of several values of each object.
public final class GroupedFields {

    /// What begins the note a grouped field's description ends with.
    public static final String NOTE_LEAD = "Per item: ";

    private GroupedFields() {
        // Static only.
    }

    /// @param plan   A plan.
    /// @param leaves The fields of the translation that wrote its Events ([FieldGroup#leaves(List)]), or none.
    /// @return The plan with the fields read from a group's element planned as above (two or more read from one
    /// element: one field is no pairing to keep), and a note for each; the plan as it is when there are none.
    public static Grouped apply(final FieldPlan plan, final List<FieldGroup.Leaf> leaves) {
        final Map<String, Group> groups = groups(leaves);
        if (plan.discovery() != null || groups.isEmpty()) {
            return new Grouped(plan, List.of());
        }
        final List<PlannedField> fields = new ArrayList<>(plan.fields());
        final Set<String> done = new HashSet<>();
        final List<String> notes = new ArrayList<>();
        for (final Map.Entry<String, Group> group : groups.entrySet()) {
            for (final Map.Entry<String, List<Picked>> read : picked(fields, group.getKey(), group.getValue(), done)
                    .entrySet()) {
                final List<Picked> picked = read.getValue();
                if (picked.size() < 2) {
                    continue;
                }
                picked.forEach(p -> done.add(p.field().name()));
                final String items = FieldGroup.itemsText(List.of(group.getValue().entry()));
                final String name = objectName(picked, fields, plan);
                if (name != null) {
                    asObject(fields, picked, name, read.getKey(), items);
                    notes.add("`" + name + "`: the translation writes " + group.getKey() + " for " + items + ", so "
                              + named(names(picked)) + " are one object per item (mapped as object, not "
                              + "nested)");
                } else {
                    asValues(fields, picked, items);
                    notes.add(named(names(picked)) + ": the translation writes " + group.getKey() + " for "
                              + items + "; as fields of several values, which of an item's values go together is "
                              + "lost");
                }
            }
        }
        return new Grouped(plan.withFields(fields), notes);
    }

    /// @param description A planned field's description.
    /// @return The note on its group it ends with ([#NOTE_LEAD]), or "" for none.
    public static String note(final String description) {
        final int at = description == null
                ? -1
                : description.indexOf(NOTE_LEAD);
        return at < 0
                ? ""
                : description.substring(at);
    }

    /// @param description A planned field's description.
    /// @return It without the note on its group ([#note(String)]), or "" for none.
    public static String withoutNote(final String description) {
        if (description == null) {
            return "";
        }
        final int at = description.indexOf(NOTE_LEAD);
        return at < 0
                ? description
                : description.substring(0, at).strip();
    }

    /// @param items Where a group's items come from ([FieldGroup#itemsText(List)]).
    /// @return The note on a field of objects, and each of its items.
    static String objectNote(final String items) {
        return NOTE_LEAD + "one object for " + items + ", mapped as object, not nested: a query on two of an item's "
               + "fields can match values from different items.";
    }

    /// @param items  Where a group's items come from ([FieldGroup#itemsText(List)]).
    /// @param others The names of the other fields read from the same items.
    /// @return The note on a field of several values read from a group.
    static String valuesNote(final String items, final List<String> others) {
        return NOTE_LEAD + "one value for " + items + ", beside " + named(others) + " of the same item; the "
               + "pairing of an item's values is lost: a query on two of them can match values from different items.";
    }

    // The outermost groups, by the path of their element, each with the paths of its fields' values from it.
    private static Map<String, Group> groups(final List<FieldGroup.Leaf> leaves) {
        final Map<String, Group> out = new LinkedHashMap<>();
        for (final FieldGroup.Leaf leaf : leaves == null
                ? List.<FieldGroup.Leaf>of()
                : leaves) {
            if (!leaf.grouped()) {
                continue;
            }
            final FieldMapping outer = leaf.groups().getFirst();
            final String element = element(outer);
            final String value = value(leaf.entry());
            if (value.startsWith(element + "/")) {
                out.computeIfAbsent(element, k -> new Group(outer, new ArrayList<>()))
                        .members()
                        .add(new Member(value.substring(element.length() + 1), leaf.groups().size() > 1));
            }
        }
        return out;
    }

    // The fields of values reading a group's members, by the element they read them from (the field's source less
    // the member's path from it: a wildcard source keeps its wildcard), each field once.
    private static Map<String, List<Picked>> picked(final List<PlannedField> fields,
                                                    final String element,
                                                    final Group group,
                                                    final Set<String> done) {
        final Map<String, List<Picked>> out = new LinkedHashMap<>();
        for (final PlannedField field : fields) {
            final String source = field.source();
            if (field.isObjects() || done.contains(field.name()) || source == null || source.startsWith("@")
                || source.startsWith("shared:") || FieldPlans.branches(source).size() > 1) {
                continue;
            }
            for (final Member member : group.members()) {
                final String suffix = "/" + member.relative();
                if (source.endsWith(suffix) && FieldPlans.sourceMatches(source, element + suffix)) {
                    out.computeIfAbsent(source.substring(0, source.length() - suffix.length()),
                            k -> new ArrayList<>()).add(new Picked(field, member));
                    break;
                }
            }
        }
        return out;
    }

    // The field of objects' name: the dotted name its fields' names share, each with a name of its own below it;
    // null where there is none, the plan's names are flat (subobjects: false) or it isn't Elasticsearch, or another
    // field has that name or one below it (its document key would be written twice).
    private static String objectName(final List<Picked> picked, final List<PlannedField> fields, final FieldPlan plan) {
        if (!plan.isElasticsearch() || !plan.subobjects()) {
            return null;
        }
        List<String> common = null;
        int shortest = Integer.MAX_VALUE;
        for (final Picked p : picked) {
            final List<String> parts = Arrays.asList(p.field().name().split("\\.", -1));
            shortest = Math.min(shortest, parts.size());
            if (common == null) {
                common = new ArrayList<>(parts);
            } else {
                int same = 0;
                while (same < common.size() && same < parts.size() && common.get(same).equals(parts.get(same))) {
                    same++;
                }
                common = new ArrayList<>(common.subList(0, same));
            }
        }
        if (common == null || common.isEmpty() || common.size() >= shortest || common.contains("")) {
            return null;
        }
        final String name = String.join(".", common);
        final Set<String> members = new HashSet<>(names(picked));
        final boolean taken = fields.stream()
                .filter(f -> !members.contains(f.name()))
                .anyMatch(f -> f.name().equals(name) || f.name().startsWith(name + ".")
                               || name.startsWith(f.name() + "."));
        return taken
                ? null
                : name;
    }

    // The picked fields as one field of objects, where the first of them was.
    private static void asObject(final List<PlannedField> fields,
                                 final List<Picked> picked,
                                 final String name,
                                 final String element,
                                 final String items) {
        final String note = objectNote(items);
        final List<PlannedField> objectItems = picked.stream()
                .map(p -> new PlannedField(p.field().name().substring(name.length() + 1), p.field().type(),
                        p.member().relative(), described(p.field().description(), note), p.field().transform(),
                        p.member().nested(), List.of(), null))
                .toList();
        final int at = fields.indexOf(picked.getFirst().field());
        picked.forEach(p -> fields.remove(p.field()));
        fields.add(Math.min(at, fields.size()), new PlannedField(name, PlannedField.OBJECT, element, note, null, true,
                objectItems, null));
    }

    // The picked fields as fields of several values, each saying the pairing is lost.
    private static void asValues(final List<PlannedField> fields, final List<Picked> picked, final String items) {
        final List<String> names = names(picked);
        for (final Picked p : picked) {
            final PlannedField field = p.field();
            final List<String> others = names.stream().filter(n -> !n.equals(field.name())).toList();
            fields.set(fields.indexOf(field), new PlannedField(field.name(), field.type(), field.source(),
                    described(field.description(), valuesNote(items, others)), field.transform(), true,
                    field.items(), field.distinctBy()));
        }
    }

    private static String described(final String description, final String note) {
        final String own = withoutNote(description);
        return own.isEmpty()
                ? note
                : own + " " + note;
    }

    // Names as a reader is given them: `a`, `b`.
    private static String named(final List<String> names) {
        return String.join(", ", names.stream().map(n -> "`" + n + "`").toList());
    }

    private static List<String> names(final List<Picked> picked) {
        return picked.stream().map(p -> p.field().name()).toList();
    }

    // An outermost group's element as an event path: a Data group's by its Name (Data[@Name='recipient']).
    private static String element(final FieldMapping group) {
        return group.dataName() == null
                ? group.strippedPath()
                : dataPath(group);
    }

    // A field's value as an event path, as a plan's source names it: a Data entry's @Value by its Names.
    private static String value(final FieldMapping entry) {
        return entry.dataName() == null
                ? entry.strippedPath()
                : dataPath(entry) + "/@Value";
    }

    // A Data entry's element: its path, then each Name of its data key, the Data elements below the first.
    private static String dataPath(final FieldMapping entry) {
        final List<String> names = ToolText.split(entry.dataKey(), "/");
        final StringBuilder sb = new StringBuilder(entry.strippedPath());
        for (int i = 0; i < names.size(); i++) {
            sb.append(i == 0
                    ? ""
                    : "/Data").append("[@Name='").append(names.get(i)).append("']");
        }
        return sb.toString();
    }


    // --------------------------------------------------------------------------------


    /// A plan with its grouped fields planned, and what was done.
    ///
    /// @param plan  The plan.
    /// @param notes For each group's fields, what they were planned as; empty for none.
    public record Grouped(FieldPlan plan, List<String> notes) {

    }

    // An outermost group and its fields' paths from its element.
    private record Group(FieldMapping entry, List<Member> members) {

    }

    // A field's value path from its outermost group's element; nested: in a group inside it, so several per object.
    private record Member(String relative, boolean nested) {

    }

    // A planned field and the group member it reads.
    private record Picked(PlannedField field, Member member) {

    }
}
