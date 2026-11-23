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

package stroom.ai.impl.tools.catalogue;

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.generation.ModelReader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// One typed placement of an event type (its `placements`, decided by the user 2026-11-21): a field of its records
/// (or a constant value) written to a schema path below the event type's action element, as a translation mapping's
/// fields entry writes it, so the field is typed rather than kept as Data. The entry is the mapping's own form, its
/// path below the action element (`Email/From/EmailAddress` under `Send`), read from a field, a constant, `any_of`,
/// `join`, an `xpath` or a `lookup` (2026-11-22: an element the schema requires is often read by an xpath, e.g. a
/// value out of an array of name/value pairs; its values aren't checked here), with the mapping's conversions
/// (`transform`, `time_format`, `timezone`, `map`, `default`, `repeat`) and its repeated groups (`for_each` with
/// `fields`, each reading the item with `item: true` or a key of it), or (2026-11-23) one pair of a name/value list
/// read by its name, `pair: {list, name}` (its keys filled from the sample by the check, the list's other pairs Data
/// named by their names), or (2026-11-23) one property of an old/new property-change list read by its name for an
/// Update's both snapshots, `change: {list, name}` (`{"path": "User/Name", "change": {...}}` writes `Before/User/Name`
/// from its old value and `After/User/Name` from its new; the list's other properties Data of the snapshots' object;
/// in a group's fields over targets each holding a list, the list a key of the item, the group written in both
/// snapshots); `also_data: true` keeps the field as Data too.
/// The agent places a field from what it knows of the source; nothing matches field names to schema names. A path
/// starting `EventSource/` (2026-11-22: `EventSource/Client/Name` for a field naming the client application) is
/// written by the event type's rule in its EventSource, checked the same way.
///
/// @param entry The entry as given (its path below the action element, or from `EventDetail/<element>/`).
public record Placement(Map<String, Object> entry) {

    /// A placement's path.
    public static final String PATH = "path";
    /// A placement's field.
    public static final String FIELD = "field";
    /// A placement's constant.
    public static final String VALUE = "value";
    /// A placement's XPath, read relative to the record.
    public static final String XPATH = "xpath";
    /// A placement's reference data lookup.
    public static final String LOOKUP = "lookup";
    /// A placement's repeated group's source.
    public static final String FOR_EACH = "for_each";
    /// A repeated group's fields.
    public static final String FIELDS = "fields";
    /// Keeps the placed field as Data too.
    public static final String ALSO_DATA = "also_data";
    /// A placement's pair of a name/value list, read by its name (2026-11-23).
    public static final String PAIR = "pair";
    /// A placement's property of an old/new list, read by its name for both snapshots (2026-11-23).
    public static final String CHANGE = "change";
    /// The keys a placement may have: the mapping's for a field, a constant, an xpath, a lookup or a pair, its
    /// conversions and its groups.
    public static final List<String> KEYS = List.of(PATH, FIELD, VALUE, "any_of", "join", XPATH, LOOKUP, "separator",
            "transform", "time_format", "timezone", "map", "default", "repeat", FOR_EACH, "as", FIELDS, "item",
            "scope", ALSO_DATA, PAIR, CHANGE);

    /// A placement's path starting here is in the event's EventSource, not below its action element (2026-11-22).
    public static final String EVENT_SOURCE = "EventSource/";

    private static final String EVENT_DETAIL = "EventDetail/";
    private static final String BEFORE = "Before/";
    private static final String AFTER = "After/";

    /// Copies the entry, in its order.
    public Placement {
        entry = Collections.unmodifiableMap(new LinkedHashMap<>(entry));
    }

    /// Reads one from JSON-like values: an object with a path, and a field, a value, an xpath, a lookup or a group
    /// (`for_each`); keys the mapping writes otherwise (Data's name, its parent) refused, as a placement types a
    /// field of the records and Data is what is left over.
    ///
    /// @param reader The reader, collecting the errors.
    /// @param input  The value given.
    /// @param loc    Where it is.
    /// @return It, or null (with the errors noted) if it doesn't read.
    public static Placement read(final ModelReader reader, final Object input, final List<Object> loc) {
        final ModelReader.Fields fields = reader.fields(input, loc, "Placement");
        if (fields == null) {
            return null;
        }
        final String path = fields.requiredString(PATH);
        if (!fields.ok()) {
            return null;
        }
        final String problem = formProblem(fields.input(), true);
        if (problem != null) {
            fields.valueError("placement '" + path + "': " + problem);
            return null;
        }
        return new Placement(JsonValues.deepCopy(fields.input()));
    }

    // Why an entry (or a group's field) isn't a placement's form; null when it is.
    private static String formProblem(final Map<String, Object> entry, final boolean top) {
        final List<String> other = entry.keySet().stream()
                .filter(key -> !KEYS.contains(key))
                .toList();
        if (!other.isEmpty()) {
            return "a placement is a field (or a value, an xpath, a lookup or a pair) placed at a path, with the "
                   + "mapping's conversions (transform, time_format, timezone, map, default, repeat) or a for_each "
                   + "group: not "
                   + String.join(", ", other) + " (Data is what no placement takes)";
        }
        if (!top && entry.containsKey(ALSO_DATA)) {
            return "also_data goes on the placement, not on a group's field";
        }
        if (entry.containsKey(FOR_EACH)) {
            final List<Map<String, Object>> inner = JsonValues.maps(entry.get(FIELDS));
            if (inner.isEmpty()) {
                return "a for_each group writes its element once per item, each holding its fields: give fields, "
                       + "e.g. [{\"path\": \"EmailAddress\", \"item\": true}]";
            }
            for (final Map<String, Object> field : inner) {
                final String problem = formProblem(field, false);
                if (problem != null) {
                    return problem;
                }
            }
            return null;
        }
        if (entry.containsKey(PAIR)) {
            if (!top) {
                return "pair reads a list of the record, so it goes on a placement, not on a group's field";
            }
            final Map<String, Object> pair = JsonValues.mapOrEmpty(entry.get(PAIR));
            if (!(pair.get("list") instanceof final String list) || list.isBlank()
                || !(pair.get("name") instanceof final String name) || name.isBlank()) {
                return "pair reads one pair of a name/value list by its name: {\"list\": \"<the list field>\", "
                       + "\"name\": \"<the pair's name>\"} (the list's other pairs are Data, each named by its "
                       + "pair's name)";
            }
        }
        if (entry.containsKey(CHANGE)) {
            final Map<String, Object> change = JsonValues.mapOrEmpty(entry.get(CHANGE));
            final boolean named = change.get("name") instanceof final String name && !name.isBlank();
            final boolean data = "Data".equals(ToolText.strip(ToolText.str(entry.get(PATH)).strip(), "/"));
            if (!(change.get("list") instanceof final String list) || list.isBlank() || !named && (top || !data)) {
                return "change reads one property of an old/new list by its name, for both snapshots: {\"list\": "
                       + "\"<the list field>\", \"name\": \"<the property's name>\"} (in a group's fields over "
                       + "targets, the list a key of the item; the list's other properties are Data of the "
                       + "snapshots' object)";
            }
        }
        final long inputs = List.of(FIELD, VALUE, "any_of", "join", XPATH, LOOKUP, PAIR, CHANGE).stream()
                .filter(entry::containsKey)
                .count() + (entry.containsKey("item")
                ? 1
                : 0) - (entry.containsKey("item") && entry.containsKey(FIELD)
                ? 1
                : 0);
        if (inputs != 1) {
            return "give one of field (a field of the records), value (a constant), any_of, join, xpath, lookup, "
                   + "pair (one pair of a name/value list, by its name) or change (one property of an old/new list, "
                   + "by its name)";
        }
        return null;
    }

    /// The placement an event type's `affected` and `affected_as` express (the catalogue's form before
    /// placements): the affected field at that path.
    ///
    /// @param field The affected field.
    /// @param path  Its path below the action element (its leaf, as the catalogue writes `affected_as` out).
    /// @return The placement.
    public static Placement affected(final String field, final String path) {
        final Map<String, Object> entry = new LinkedHashMap<>();
        entry.put(PATH, path);
        entry.put(FIELD, field);
        return new Placement(entry);
    }

    /// @return Its path as given, without slashes at either end.
    public String path() {
        return ToolText.strip(ToolText.str(entry.get(PATH)).strip(), "/");
    }

    /// @return True if its path is in the event's EventSource (`EventSource/Client/Name`): written by the event
    /// type's rule there, not below its action element.
    public boolean inEventSource() {
        return path().startsWith(EVENT_SOURCE);
    }

    /// @param detail The event type's action element, e.g. `Send` or `Network/Permit`.
    /// @return Its path below the action element: as given, or without `EventDetail/<element>/`; null when given
    /// from EventDetail but under another element, or in EventSource ([#inEventSource()]).
    public String pathBelow(final String detail) {
        final String path = path();
        if (inEventSource()) {
            return null;
        }
        if (!path.startsWith(EVENT_DETAIL)) {
            return path;
        }
        final String own = EVENT_DETAIL + ToolText.strip(detail.strip(), "/") + "/";
        return path.startsWith(own)
                ? path.substring(own.length())
                : null;
    }

    /// @return Its field, or null for a constant or a group.
    public String field() {
        return entry.get(FIELD) instanceof final String field && !field.isBlank()
                ? field
                : null;
    }

    /// @return Its pair, `{list, name}` (with `name_key` and `value_key` where given), or null for none.
    public Map<String, Object> pair() {
        return entry.get(PAIR) instanceof final Map<?, ?> given
                ? JsonValues.mapOrEmpty(given)
                : null;
    }

    /// @param nameKey  The list's items' key holding each pair's name.
    /// @param valueKey The key holding its value.
    /// @return It with its pair's keys (as the mapping's entry reads them, [stroom.ai.impl.tools.generation.ListPair]);
    /// itself if it has no pair.
    public Placement withPairKeys(final String nameKey, final String valueKey) {
        if (pair() == null) {
            return this;
        }
        final Map<String, Object> pair = new LinkedHashMap<>(pair());
        pair.put("name_key", nameKey);
        pair.put("value_key", valueKey);
        final Map<String, Object> out = new LinkedHashMap<>(entry);
        out.put(PAIR, pair);
        return new Placement(out);
    }

    /// @return Its change, `{list, name}` (with `name_key`, `old_key` and `new_key` where given), or null for none.
    public Map<String, Object> change() {
        return entry.get(CHANGE) instanceof final Map<?, ?> given
                ? JsonValues.mapOrEmpty(given)
                : null;
    }

    /// @return True if it writes both of an Update's snapshots: a change, or a group whose fields read one.
    public boolean snapshots() {
        return change() != null || JsonValues.maps(entry.get(FIELDS)).stream()
                .anyMatch(field -> field.get(CHANGE) instanceof Map);
    }

    /// @return The lists its changes read: its own, or its group's fields' (keys of the item).
    public Set<String> changeLists() {
        final Set<String> out = new LinkedHashSet<>();
        final List<Map<String, Object>> all = new ArrayList<>(JsonValues.maps(entry.get(FIELDS)));
        all.add(entry);
        for (final Map<String, Object> one : all) {
            if (one.get(CHANGE) instanceof final Map<?, ?> change && change.get("list") instanceof final String list) {
                out.add(list);
            }
        }
        return out;
    }

    /// @param keys Each list's keys (its name's, its old value's and its new value's), by the list.
    /// @return It with its changes' keys (as the mapping's entries read them, [stroom.ai.impl.tools.generation
    /// .ListChange]): a key given and the list's kept; itself if it has no change.
    public Placement withChangeKeys(final Map<String, List<String>> keys) {
        if (!snapshots()) {
            return this;
        }
        final Map<String, Object> out = withKeys(new LinkedHashMap<>(entry), keys);
        if (entry.containsKey(FIELDS)) {
            out.put(FIELDS, JsonValues.maps(entry.get(FIELDS)).stream()
                    .map(field -> withKeys(new LinkedHashMap<>(field), keys))
                    .toList());
        }
        return new Placement(out);
    }

    private static Map<String, Object> withKeys(final Map<String, Object> one, final Map<String, List<String>> keys) {
        if (!(one.get(CHANGE) instanceof final Map<?, ?> given) || !(given.get("list") instanceof final String list)
            || !keys.containsKey(list)) {
            return one;
        }
        final Map<String, Object> change = new LinkedHashMap<>(JsonValues.mapOrEmpty(given));
        final List<String> three = keys.get(list);
        change.putIfAbsent("name_key", three.get(0));
        change.putIfAbsent("old_key", three.get(1));
        change.putIfAbsent("new_key", three.get(2));
        one.put(CHANGE, change);
        return one;
    }

    /// @return True if it is a repeated group (`for_each`).
    public boolean group() {
        return entry.containsKey(FOR_EACH);
    }

    /// @return True if it, or a field of its group, is read by an xpath or a lookup: values the check doesn't read.
    public boolean readElsewhere() {
        return readElsewhere(entry);
    }

    private static boolean readElsewhere(final Map<String, Object> entry) {
        return entry.get(XPATH) != null || entry.get(LOOKUP) != null
               || JsonValues.maps(entry.get(FIELDS)).stream().anyMatch(Placement::readElsewhere);
    }

    /// @return True if the field is kept as Data too.
    public boolean alsoData() {
        return JsonValues.truthy(entry.get(ALSO_DATA));
    }

    /// @return The fields of the records it reads, each once: its field, any_of's, join's and a lookup's key field,
    /// or a group's sources (for_each's) and its fields' that read the record (scope record); not a group item's
    /// keys, nor what an xpath reads.
    public Set<String> inputs() {
        final Set<String> out = new LinkedHashSet<>();
        inputs(entry, true, out);
        return out;
    }

    private static void inputs(final Map<String, Object> entry, final boolean top, final Set<String> out) {
        if (entry.containsKey(FOR_EACH)) {
            final Map<String, Object> source = JsonValues.mapOrEmpty(entry.get(FOR_EACH));
            for (final String key : List.of("split", "array")) {
                if (source.get(key) instanceof final String field && !field.isBlank()) {
                    out.add(field);
                }
            }
            JsonValues.mapOrEmpty(source.get("zip")).values().forEach(field -> out.add(ToolText.str(field)));
            JsonValues.maps(entry.get(FIELDS)).forEach(field -> inputs(field, false, out));
            return;
        }
        if (!top && !"record".equals(entry.get("scope"))) {
            // A group's field reads the item.
            return;
        }
        if (entry.get(FIELD) instanceof final String field && !field.isBlank()) {
            out.add(field);
        }
        out.addAll(JsonValues.strings(entry.get("any_of")));
        out.addAll(JsonValues.strings(entry.get("join")));
        if (JsonValues.mapOrEmpty(entry.get(LOOKUP)).get(FIELD) instanceof final String key && !key.isBlank()) {
            out.add(key);
        }
    }

    /// @param detail The event type's action element.
    /// @return Its path from the event's root: `EventSource/...` as given, else from `EventDetail/<element>/`.
    public String fullPath(final String detail) {
        if (inEventSource()) {
            return path();
        }
        final String below = pathBelow(detail);
        return EVENT_DETAIL + ToolText.strip(detail.strip(), "/") + "/" + (below == null
                ? path()
                : below);
    }

    /// @param detail The event type's action element.
    /// @return It as the translation mapping's fields entry: its path from `EventDetail/<element>/` (or in
    /// EventSource, as given), without `also_data`; a change's, its After snapshot's ([#mappingEntries(String)]).
    public Map<String, Object> mappingEntry(final String detail) {
        return mappingEntries(detail).getLast();
    }

    /// @param detail The event type's action element.
    /// @return It as the translation mapping's fields entries: one ([#mappingEntry(String)]), or for a change
    /// ([#snapshots()]) two, its Before snapshot's (`EventDetail/Update/Before/User/Name`) and its After's; a group's
    /// fields reading a list by name gain its Data per property, each list's other properties Data of the item's
    /// object.
    public List<Map<String, Object>> mappingEntries(final String detail) {
        final Map<String, Object> out = new LinkedHashMap<>(JsonValues.deepCopy(entry));
        out.remove(ALSO_DATA);
        if (!snapshots()) {
            out.put(PATH, fullPath(detail));
            return List.of(out);
        }
        if (group()) {
            out.put(FIELDS, withDataPerProperty(JsonValues.maps(out.get(FIELDS))));
        }
        final String head = EVENT_DETAIL + ToolText.strip(detail.strip(), "/") + "/";
        final String below = snapshotPath(detail);
        final List<Map<String, Object>> both = new ArrayList<>();
        for (final String side : List.of(BEFORE, AFTER)) {
            final Map<String, Object> one = JsonValues.deepCopy(out);
            one.put(PATH, head + side + below);
            both.add(one);
        }
        return both;
    }

    // A group's fields with a Data per property for each list they read by name and don't already write as Data.
    private static List<Map<String, Object>> withDataPerProperty(final List<Map<String, Object>> fields) {
        final List<Map<String, Object>> out = new ArrayList<>(fields);
        final Set<String> perProperty = new LinkedHashSet<>();
        final Set<String> named = new LinkedHashSet<>();
        for (final Map<String, Object> field : fields) {
            if (field.get(CHANGE) instanceof final Map<?, ?> change
                && change.get("list") instanceof final String list) {
                if (change.get("name") instanceof String) {
                    named.add(list);
                } else {
                    perProperty.add(list);
                }
            }
        }
        for (final Map<String, Object> field : fields) {
            if (field.get(CHANGE) instanceof final Map<?, ?> change && change.get("list") instanceof final String list
                && named.contains(list) && perProperty.add(list)) {
                final Map<String, Object> keys = new LinkedHashMap<>(JsonValues.mapOrEmpty(change));
                keys.remove("name");
                final Map<String, Object> data = new LinkedHashMap<>();
                data.put(PATH, "Data");
                data.put(CHANGE, keys);
                out.add(data);
            }
        }
        return out;
    }

    /// @param detail The event type's action element.
    /// @return A change's path below the snapshot ([#snapshots()]): below the action element, without a `Before/` or
    /// `After/` given (`User/Name`).
    String snapshotPath(final String detail) {
        final String below = Objects.requireNonNullElse(pathBelow(detail), path());
        if (below.startsWith(BEFORE)) {
            return below.substring(BEFORE.length());
        }
        return below.startsWith(AFTER)
                ? below.substring(AFTER.length())
                : below;
    }

    /// @param detail The event type's action element.
    /// @return The paths it writes below the action element: its own, or a group's path with each of its fields'
    /// paths below it; none for one in EventSource ([#writesFromRoot(String)] has those).
    public List<String> writes(final String detail) {
        final String below = pathBelow(detail);
        if (below == null) {
            return List.of();
        }
        final List<String> out = new ArrayList<>();
        if (snapshots()) {
            // Both snapshots, as the translation writes them.
            final String own = snapshotPath(detail);
            writes(entry, BEFORE + own, out);
            writes(entry, AFTER + own, out);
            return out;
        }
        writes(entry, below, out);
        return out;
    }

    /// @param detail The event type's action element.
    /// @return The paths it writes from the event's root (`EventDetail/<element>/...` or `EventSource/...`).
    public List<String> writesFromRoot(final String detail) {
        final List<String> out = new ArrayList<>();
        writes(entry, fullPath(detail), out);
        return out;
    }

    private static void writes(final Map<String, Object> entry, final String path, final List<String> out) {
        if (!entry.containsKey(FOR_EACH)) {
            out.add(path);
            return;
        }
        final List<Map<String, Object>> inner = JsonValues.maps(entry.get(FIELDS));
        if (inner.isEmpty()) {
            out.add(path);
        }
        for (final Map<String, Object> field : inner) {
            if (field.get(CHANGE) instanceof final Map<?, ?> change && !(change.get("name") instanceof String)) {
                // A Data per property: Data, not a typed element.
                continue;
            }
            final String own = ToolText.strip(ToolText.str(field.get(PATH)).strip(), "/");
            writes(field, own.isEmpty()
                    ? path
                    : path + "/" + own, out);
        }
    }

    // What an entry reads, in words.
    private static String input(final Map<String, Object> entry) {
        if (entry.get(FIELD) != null) {
            return String.valueOf(entry.get(FIELD));
        }
        if (entry.get(VALUE) != null) {
            return "'" + entry.get(VALUE) + "'";
        }
        if (entry.get(XPATH) != null) {
            return "xpath " + entry.get(XPATH);
        }
        if (entry.get(LOOKUP) != null) {
            return "lookup in " + JsonValues.mapOrEmpty(entry.get(LOOKUP)).get("map");
        }
        if (Boolean.TRUE.equals(entry.get("item"))) {
            return "the item";
        }
        if (entry.get(PAIR) != null) {
            final Map<String, Object> pair = JsonValues.mapOrEmpty(entry.get(PAIR));
            return pair.get("list") + "'s pair " + pair.get("name");
        }
        if (entry.get(CHANGE) != null) {
            final Map<String, Object> change = JsonValues.mapOrEmpty(entry.get(CHANGE));
            return change.get("list") + "'s property " + Objects.requireNonNullElse(change.get("name"), "each")
                   + " (old in Before, new in After)";
        }
        return entry.get("any_of") != null
                ? "any of " + JsonValues.strings(entry.get("any_of"))
                : "join of " + JsonValues.strings(entry.get("join"));
    }

    /// @return It in words for a reader: `event.Subject -> Email/Subject`, a group's source and fields.
    public String said() {
        return said(entry, path());
    }

    private static String said(final Map<String, Object> entry, final String path) {
        if (entry.containsKey(FOR_EACH)) {
            final Map<String, Object> source = JsonValues.mapOrEmpty(entry.get(FOR_EACH));
            final String from = source.get("split") != null
                    ? source.get("split") + " split on '" + source.get("on") + "'"
                    : source.get("array") != null
                            ? String.valueOf(source.get("array"))
                            : String.join(", ", JsonValues.mapOrEmpty(source.get("zip")).values().stream()
                                    .map(String::valueOf).toList());
            final List<String> inner = new ArrayList<>();
            for (final Map<String, Object> field : JsonValues.maps(entry.get(FIELDS))) {
                final String own = ToolText.strip(ToolText.str(field.get(PATH)).strip(), "/");
                inner.add(said(field, own));
            }
            return path + " for each of " + from + " (" + String.join("; ", inner) + ")";
        }
        final String input = input(entry);
        final List<String> how = new ArrayList<>();
        for (final String key : List.of("transform", "time_format", "map")) {
            if (entry.get(key) != null) {
                how.add(key);
            }
        }
        return input + " -> " + path + (how.isEmpty()
                ? ""
                : " (" + String.join(", ", how) + ")") + (JsonValues.truthy(entry.get(ALSO_DATA))
                ? ", also as Data"
                : "");
    }
}
