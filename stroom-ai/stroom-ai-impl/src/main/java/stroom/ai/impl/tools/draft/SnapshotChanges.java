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

package stroom.ai.impl.tools.draft;

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.generation.ListChange;
import stroom.ai.impl.tools.sample.NameValueLists;
import stroom.ai.impl.tools.sample.NameValueLists.ChangeList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/// An Update's old/new property-change lists ([NameValueLists#changes]) written as a Data per property of each
/// snapshot whatever the mapping says of them (decided 2026-11-23, from eval case 76: Haiku and Sonnet wrote the
/// Graph targets as their own for_each groups in the catalogue, `{"path": "After/Object", "for_each": {"array":
/// "targetResources"}, "fields": [{"path": "Id", "field": "id"}]}`, After only or in both snapshots, never with the
/// change form, and no property's old or new value was written; in a build, each target's list as a for_each group of
/// name and value Data, its values JSON text).
///
/// In each rule, as the draft writes an old/new list ([ListFields]) and the change form's Data per property does
/// ([ListChange]):
///
/// * a group below a snapshot (`EventDetail/Update/After/User`) over an array whose items hold an old/new list gains
///   a Data per property of the list on its side, unless one of its fields writes one; a field reading the list whole
///   (`field`, or a for_each group over it), whose values would be the list's JSON run together, is replaced by it;
/// * such a group with no group over the same array at the same object in the other snapshot gets one there, a copy
///   of its fields, so each target is an object in both snapshots, its old values in Before and its new in After (as
///   a group whose fields read a change is written in both);
/// * a plain Data of a record's old/new list below a snapshot becomes a Data per property on its side; below an Update
///   but no snapshot, the Before and After Data per property of the object the rule writes in a snapshot (else
///   `Object`), as the draft's;
/// * an object a rule reads a property of by name in a snapshot (the change form) gains a Data per property there.
///
/// Which value key is old: the user's answer, else the keys a change of the list in the mapping gives, else the key a
/// group in Before reads, else the items' order. Nothing is said for a group whose fields already write the list.
public final class SnapshotChanges {

    private static final String PATH = "path";
    private static final String FIELD = "field";
    private static final String FIELDS = "fields";
    private static final String FOR_EACH = "for_each";
    private static final String CHANGE = "change";
    private static final String DATA = "Data";
    private static final String UPDATE = "EventDetail/Update/";
    private static final String OBJECT = "Object";
    private static final String BEFORE = ListChange.BEFORE;
    private static final String AFTER = ListChange.AFTER;
    // A plain Data entry's keys: what it reads, and nothing done to it.
    private static final Set<String> PLAIN_DATA_KEYS = Set.of(PATH, "data_name", FIELD);

    private final Function<String, List<?>> valuesOf;
    private final Map<String, String> sides;
    private final List<Map<String, Object>> allEntries;
    private final Map<String, Optional<Map.Entry<String, ChangeList>>> heldByArray = new LinkedHashMap<>();
    private final Map<String, Optional<ChangeList>> byField = new LinkedHashMap<>();
    // What was done, by what: the rules each was done in.
    private final Map<String, Set<String>> done = new LinkedHashMap<>();

    private SnapshotChanges(final Map<String, Object> mapping,
                            final Function<String, List<?>> valuesOf,
                            final Map<String, String> sides) {
        this.valuesOf = valuesOf;
        this.sides = Map.copyOf(sides);
        this.allEntries = new ArrayList<>();
        JsonValues.maps(mapping.get("events")).forEach(rule -> everyEntry(JsonValues.maps(rule.get(FIELDS)),
                allEntries));
    }

    /// Writes the old/new lists of the mapping's Update rules as a Data per property of each snapshot, in place.
    ///
    /// @param mapping  A JSON input's mapping as JSON (`events`, each with `fields`), changed in place.
    /// @param valuesOf Each field's values in the sample: each a list as read, or JSON text holding one.
    /// @param sides    The user's answers of which key of each old/new list holds the value before the change, by the
    ///                 list ([NameValueLists#nestedName] for a list inside an array's items).
    /// @return What was changed, in words (one line a change, naming its rules); none if nothing was.
    public static List<String> apply(final Map<String, Object> mapping,
                                     final Function<String, List<?>> valuesOf,
                                     final Map<String, String> sides) {
        if (!"json".equals(mapping.get("input"))) {
            return List.of();
        }
        final SnapshotChanges changes = new SnapshotChanges(mapping, valuesOf, sides);
        for (final Map<String, Object> rule : JsonValues.maps(mapping.get("events"))) {
            final String name = ToolText.str(rule.get("name"));
            final List<Map<String, Object>> fields = JsonValues.maps(rule.get(FIELDS));
            final List<Map<String, Object>> written = changes.rule(name, fields);
            if (written != null) {
                rule.put(FIELDS, written);
            }
        }
        final List<String> out = new ArrayList<>();
        changes.done.forEach((what, rules) -> out.add(what + " (" + String.join(", ", rules) + ")"));
        return out;
    }

    // The rule's fields with its old/new lists written per property in its snapshots; null where nothing changed.
    private List<Map<String, Object>> rule(final String name, final List<Map<String, Object>> fields) {
        final List<Map<String, Object>> out = new ArrayList<>();
        boolean changed = false;
        for (final Map<String, Object> entry : fields) {
            final List<Map<String, Object>> written = entry.containsKey(FOR_EACH)
                    ? group(name, entry, fields)
                    : plain(name, entry, fields);
            if (written == null) {
                out.add(entry);
            } else {
                out.addAll(written);
                changed = true;
            }
        }
        final List<Map<String, Object>> named = perPropertyOfNamed(name, out);
        if (named != null) {
            return named;
        }
        return changed
                ? out
                : null;
    }

    // A group below a snapshot over items holding an old/new list: with a Data per property of the list (fields
    // reading it whole replaced), and its copy in the other snapshot where the rule has none. Null where unchanged.
    private List<Map<String, Object>> group(final String rule,
                                            final Map<String, Object> group,
                                            final List<Map<String, Object>> fields) {
        final String path = ToolText.str(group.get(PATH));
        final Boolean before = ListChange.sideOf(path);
        final String array = arrayOf(group);
        if (before == null || !path.startsWith(UPDATE) || array == null) {
            return null;
        }
        final Optional<Map.Entry<String, ChangeList>> held = heldByArray.computeIfAbsent(array,
                ignored -> NameValueLists.heldChanges(valuesOf.apply(array)));
        if (held.isEmpty()) {
            return null;
        }
        final String key = held.get().getKey();
        final ChangeList changes = oriented(NameValueLists.nestedName(array, key), key, held.get().getValue());
        final List<Map<String, Object>> inner = new ArrayList<>();
        final List<String> replaced = new ArrayList<>();
        for (final Map<String, Object> field : JsonValues.maps(group.get(FIELDS))) {
            if (readsWhole(field, key)) {
                replaced.add(ToolText.str(field.get(PATH)));
            } else {
                inner.add(field);
            }
        }
        final boolean perProperty = inner.stream().anyMatch(field -> perProperty(field, key));
        if (!perProperty) {
            inner.add(perPropertyEntry(DATA, key, changes));
        }
        final String other = otherSide(path);
        final boolean counterpart = fields.stream()
                .anyMatch(entry -> other.equals(entry.get(PATH)) && array.equals(arrayOf(entry)));
        if (perProperty && replaced.isEmpty() && counterpart) {
            return null;
        }
        final Map<String, Object> own = JsonValues.deepCopy(group);
        own.put(FIELDS, inner);
        final String list = NameValueLists.nestedName(array, key);
        if (!replaced.isEmpty()) {
            said("'" + list + "' read whole at " + path + "/" + String.join(", " + path + "/", replaced)
                 + " (its JSON run together), written instead as a Data per property of its "
                 + sideName(before) + " values", rule);
        }
        if (!perProperty) {
            said("'" + list + "' (an old/new list) written as a Data per property of each item's object at " + path,
                    rule);
        }
        if (counterpart) {
            return List.of(own);
        }
        final Map<String, Object> copy = JsonValues.deepCopy(own);
        copy.put(PATH, other);
        said("each item of '" + array + "' written in both snapshots, " + other + " too (its "
             + sideName(!before) + " values there)", rule);
        return before
                ? List.of(own, copy)
                : List.of(copy, own);
    }

    // A plain Data of a record's old/new list: a Data per property on its snapshot's side, or below an Update with
    // no snapshot, of the object in both. Null where it is no such entry.
    private List<Map<String, Object>> plain(final String rule,
                                            final Map<String, Object> entry,
                                            final List<Map<String, Object>> fields) {
        final String path = ToolText.str(entry.get(PATH));
        if (!PLAIN_DATA_KEYS.containsAll(entry.keySet()) || !(entry.get(FIELD) instanceof final String field)
            || !path.startsWith(UPDATE) || !(path.endsWith("/" + DATA))) {
            return null;
        }
        final Optional<ChangeList> found = byField.computeIfAbsent(field,
                ignored -> NameValueLists.changes(valuesOf.apply(field)));
        if (found.isEmpty()) {
            return null;
        }
        final ChangeList changes = oriented(field, field, found.get());
        final Boolean before = ListChange.sideOf(path);
        if (before != null) {
            said("'" + field + "' (an old/new list) at " + path + " written as a Data per property of its "
                 + sideName(before) + " values, not its JSON whole", rule);
            return List.of(perPropertyEntry(path, field, changes));
        }
        final String object = objectOf(fields);
        said("'" + field + "' (an old/new list) at " + path + " written as the Before and After snapshots of "
             + object + ", a Data per property of each side's values", rule);
        return List.of(perPropertyEntry(UPDATE + BEFORE + "/" + object + "/" + DATA, field, changes),
                perPropertyEntry(UPDATE + AFTER + "/" + object + "/" + DATA, field, changes));
    }

    // The fields with a Data per property added at each snapshot's object that reads a record's list by name only;
    // null where none is added.
    private List<Map<String, Object>> perPropertyOfNamed(final String rule, final List<Map<String, Object>> fields) {
        final Map<String, Map<String, Object>> wanted = new LinkedHashMap<>();
        for (final Map<String, Object> entry : fields) {
            if (!(entry.get(CHANGE) instanceof final Map<?, ?> change) || !(change.get("name") instanceof String)
                || !(change.get("list") instanceof final String list)) {
                continue;
            }
            final String path = ToolText.str(entry.get(PATH));
            final int slash = path.lastIndexOf('/');
            if (slash < 0 || ListChange.sideOf(path) == null) {
                continue;
            }
            final String data = path.substring(0, slash) + "/" + DATA;
            if (fields.stream().noneMatch(other -> data.equals(other.get(PATH)) && perProperty(other, list))) {
                final Map<String, Object> keys = new LinkedHashMap<>(JsonValues.mapOrEmpty(change));
                keys.remove("name");
                wanted.putIfAbsent(data + " " + list, Map.of(PATH, data, CHANGE, keys));
            }
        }
        if (wanted.isEmpty()) {
            return null;
        }
        final List<Map<String, Object>> out = new ArrayList<>(fields);
        wanted.values().forEach(entry -> {
            final Map<String, Object> added = new LinkedHashMap<>();
            added.put(PATH, entry.get(PATH));
            added.put(CHANGE, new LinkedHashMap<>(JsonValues.mapOrEmpty(entry.get(CHANGE))));
            out.add(added);
            said("'" + JsonValues.mapOrEmpty(entry.get(CHANGE)).get("list") + "' read by name in "
                 + ToolText.str(entry.get(PATH)).replaceAll("/" + DATA + "$", "") + ": its other properties "
                 + "written as a Data per property there", rule);
        });
        return out;
    }

    // The list's keys, its old key the user's answer, else as a change of it in the mapping or a group in Before
    // reading it says, else the items' order.
    private ChangeList oriented(final String answeredAs, final String list, final ChangeList found) {
        final String answered = sides.get(answeredAs);
        if (answered != null) {
            return found.oriented(answered);
        }
        for (final Map<String, Object> entry : allEntries) {
            if (entry.get(CHANGE) instanceof final Map<?, ?> change && list.equals(change.get("list"))
                && change.get("old_key") instanceof final String old) {
                return found.oriented(old);
            }
        }
        for (final Map<String, Object> entry : allEntries) {
            if (list.equals(arrayOf(entry)) && Boolean.TRUE.equals(ListChange.sideOf(ToolText.str(entry.get(PATH))))) {
                for (final Map<String, Object> field : JsonValues.maps(entry.get(FIELDS))) {
                    final Object read = field.get(FIELD);
                    if (found.oldKey().equals(read) || found.newKey().equals(read)) {
                        return found.oriented((String) read);
                    }
                }
            }
        }
        return found;
    }

    private void said(final String what, final String rule) {
        done.computeIfAbsent(what, ignored -> new LinkedHashSet<>()).add(rule);
    }

    // Whether a group's field reads the list whole: its field, or a group over it.
    private static boolean readsWhole(final Map<String, Object> field, final String key) {
        return key.equals(arrayOf(field)) || key.equals(field.get(FIELD)) && !field.containsKey(CHANGE)
                                              && !field.containsKey("pair");
    }

    // Whether an entry writes a Data per property of the list.
    private static boolean perProperty(final Map<String, Object> entry, final String list) {
        return entry.get(CHANGE) instanceof final Map<?, ?> change && list.equals(change.get("list"))
               && !(change.get("name") instanceof String);
    }

    private static Map<String, Object> perPropertyEntry(final String path, final String list,
                                                        final ChangeList changes) {
        final Map<String, Object> change = new LinkedHashMap<>();
        change.put("list", list);
        change.put("name_key", changes.nameKey());
        change.put("old_key", changes.oldKey());
        change.put("new_key", changes.newKey());
        final Map<String, Object> entry = new LinkedHashMap<>();
        entry.put(PATH, path);
        entry.put(CHANGE, change);
        return entry;
    }

    // The object the rule writes in a snapshot (EventDetail/Update/After/User/Id gives User), else Object.
    private static String objectOf(final List<Map<String, Object>> fields) {
        for (final Map<String, Object> entry : fields) {
            final List<String> steps = ToolText.split(ToolText.str(entry.get(PATH)), "/");
            if (steps.size() >= 4 && ToolText.str(entry.get(PATH)).startsWith(UPDATE)
                && (BEFORE.equals(steps.get(2)) || AFTER.equals(steps.get(2))) && !DATA.equals(steps.get(3))) {
                return steps.get(3);
            }
        }
        return OBJECT;
    }

    // The path with its nearer snapshot the other one.
    private static String otherSide(final String path) {
        final List<String> steps = new ArrayList<>(ToolText.split(path, "/"));
        for (int i = steps.size() - 1; i >= 0; i--) {
            if (BEFORE.equals(steps.get(i)) || AFTER.equals(steps.get(i))) {
                steps.set(i, BEFORE.equals(steps.get(i))
                        ? AFTER
                        : BEFORE);
                break;
            }
        }
        return String.join("/", steps);
    }

    private static String sideName(final boolean before) {
        return before
                ? "old"
                : "new";
    }

    private static String arrayOf(final Map<String, Object> entry) {
        return JsonValues.mapOrEmpty(entry.get(FOR_EACH)).get("array") instanceof final String array
               && !array.isBlank()
                ? array
                : null;
    }

    private static void everyEntry(final List<Map<String, Object>> entries, final List<Map<String, Object>> out) {
        for (final Map<String, Object> entry : entries) {
            out.add(entry);
            everyEntry(JsonValues.maps(entry.get(FIELDS)), out);
        }
    }
}
