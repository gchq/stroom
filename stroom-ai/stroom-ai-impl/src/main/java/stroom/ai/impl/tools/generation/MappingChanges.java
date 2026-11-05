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

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Changes to a translation mapping (or an index field plan), merged into the whole one: a fix sends only what it
/// changes.
///
/// Seen (Gemma 4 31B in VS Code, about 22 tokens a second): each fix resent the whole mapping, some 800 tokens and
/// 40 seconds of writing, five times over. A rule is changed by name: the keys given replace its own, except its
/// `fields`, which are changed entry by entry, as common entries are, by path (and Data name); `"replace": true`
/// replaces the rule whole. Extract entries are changed one by one, by the fields they extract. Any other key is
/// replaced whole; `{"remove": true}` removes a rule, a rule's entry, a common entry or an extract entry. Seen: with a
/// rule replaced whole, changing one entry of a rule of about 140 meant resending them
/// all, so the agent edited the mapping file by hand instead, a dozen calls, and lost an entry it left out.
public final class MappingChanges {

    private MappingChanges() {
        // Static only.
    }

    /// @param base    The mapping, as JSON-like values (left as it is).
    /// @param changes What changes: `events` (rules by name: added after the others, removed with `remove: true`,
    ///                replaced whole with `replace: true`, or else changed: each key given replaces the rule's own,
    ///                and its `fields` entries are each replaced, added or removed by path and Data name),
    ///                `common` (entries, each replacing the entry with its path and Data name, added, or removed),
    ///                `extract` (entries, each replacing the entry that extracts any of its names, added, or removed
    ///                with `remove: true`), any other key replacing that key whole.
    /// @return The mapping with the changes merged in, and what each change did.
    /// @throws ToolRefusal if a change can't apply (no name, no path, removing what isn't there).
    public static Applied applyChanges(final Map<String, Object> base, final Object changes) {
        final Map<String, Object> given = changesOf(changes, "changes is an object holding only what changes, e.g. "
                                                             + "{\"events\": [{\"name\": ..., \"fields\": [only the "
                                                             + "entries that change]}]} or {\"common\": [{\"path\": "
                                                             + "..., \"value\": ...}]}");
        final Map<String, Object> merged = copyMap(base);
        final List<String> done = new ArrayList<>();
        for (final Map.Entry<String, Object> change : given.entrySet()) {
            switch (change.getKey()) {
                case "events" -> changeRules(listIn(merged, "events"), change.getValue(), done);
                case "common" -> changeCommon(listIn(merged, "common"), change.getValue(), done);
                case "extract" -> changeExtracts(listIn(merged, "extract"), change.getValue(), done);
                default -> {
                    merged.put(change.getKey(), change.getValue());
                    done.add("'" + change.getKey() + "' replaced");
                }
            }
        }
        return new Applied(merged, done);
    }

    /// Seen in Ask Stroom AI: a 50-field plan pasted inline to five tools, and drafted again whole to add a field,
    /// its earlier extra_fields lost.
    ///
    /// @param base    An index field plan, as JSON-like values (left as it is).
    /// @param changes What changes: `fields` (each replacing the field of its name whole, or added;
    ///                `{"name": ..., "remove": true}` removing one), any other key replacing that key whole.
    /// @return The plan with the changes merged in, and what each did.
    /// @throws ToolRefusal if a change can't apply.
    public static Applied applyPlanChanges(final Map<String, Object> base, final Object changes) {
        final Map<String, Object> given = changesOf(changes, "changes is an object holding only what changes, e.g. "
                                                             + "{\"fields\": [{\"name\": \"acme.ticket\", \"type\": "
                                                             + "\"keyword\", \"source\": \"...\"}]} or "
                                                             + "{\"drop_when\": [...]}");
        final Map<String, Object> merged = copyMap(base);
        final List<String> done = new ArrayList<>();
        for (final Map.Entry<String, Object> change : given.entrySet()) {
            if (!"fields".equals(change.getKey())) {
                merged.put(change.getKey(), change.getValue());
                done.add("'" + change.getKey() + "' replaced");
                continue;
            }
            final List<Object> fields = listIn(merged, "fields");
            for (final Object field : each(change.getValue())) {
                if (!(field instanceof final Map<?, ?> named) || !JsonValues.truthy(named.get("name"))) {
                    throw new ToolRefusal("Each entry in changes.fields needs its name: it replaces the "
                                          + "field of that name whole, or is added");
                }
                final Object name = named.get("name");
                final int at = indexOf(fields, "name", name);
                if (JsonValues.truthy(named.get("remove"))) {
                    if (at < 0) {
                        throw new ToolRefusal("changes.fields removes '" + str(name) + "', which the plan "
                                              + "does not have (its fields: " + names(fields) + ")");
                    }
                    fields.remove(at);
                    done.add("field '" + str(name) + "' removed");
                } else if (at < 0) {
                    fields.add(field);
                    done.add("field '" + str(name) + "' added");
                } else {
                    fields.set(at, field);
                    done.add("field '" + str(name) + "' replaced");
                }
            }
        }
        return new Applied(merged, done);
    }

    // Extract entries by the fields they extract. Seen in testing: changes.extract replaced the whole list, so an
    // agent sending its few new entries wiped the rest (26 entries cut to 2), and every record fell to the rule for
    // the rest.
    private static void changeExtracts(final List<Object> extracts, final Object value, final List<String> done) {
        for (final Object entry : each(value)) {
            final List<String> names = entry instanceof final Map<?, ?> map
                    ? JsonValues.strings(map.get("names"))
                    : List.of();
            if (names.isEmpty()) {
                throw new ToolRefusal("Each entry in changes.extract needs its names: it replaces the entry that "
                                      + "extracts any of them, or is added; with \"remove\": true it removes that "
                                      + "entry. The extract list as it is: " + extractNames(extracts));
            }
            final List<Integer> found = new ArrayList<>();
            for (int n = 0; n < extracts.size(); n++) {
                if (extracts.get(n) instanceof final Map<?, ?> kept
                    && JsonValues.strings(kept.get("names")).stream().anyMatch(names::contains)) {
                    found.add(n);
                }
            }
            final String said = "extract " + String.join(", ", names);
            if (found.size() > 1) {
                throw new ToolRefusal("changes.extract: " + names + " are extracted by " + found.size() + " entries "
                                      + "(" + String.join("; ", found.stream()
                        .map(n -> String.join(", ", JsonValues.strings(((Map<?, ?>) extracts.get(n)).get("names"))))
                        .toList()) + "): change one entry at a time, naming only its fields");
            }
            if (JsonValues.truthy(((Map<?, ?>) entry).get("remove"))) {
                if (found.isEmpty()) {
                    throw new ToolRefusal("changes.extract removes " + names + ", which no entry extracts (the "
                                          + "extract list: " + extractNames(extracts) + ")");
                }
                extracts.remove((int) found.getFirst());
                done.add(said + " removed");
            } else if (found.isEmpty()) {
                extracts.add(entry);
                done.add(said + " added");
            } else {
                extracts.set(found.getFirst(), entry);
                done.add(said + " replaced");
            }
        }
    }

    private static String extractNames(final List<Object> extracts) {
        return extracts.isEmpty()
                ? "none"
                : String.join("; ", extracts.stream()
                        .map(entry -> entry instanceof final Map<?, ?> map
                                ? String.join(", ", JsonValues.strings(map.get("names")))
                                : "?")
                        .toList());
    }

    private static void changeRules(final List<Object> rules, final Object value, final List<String> done) {
        for (final Object rule : each(value)) {
            if (!(rule instanceof final Map<?, ?> named) || !JsonValues.truthy(named.get("name"))) {
                throw new ToolRefusal("Each rule in changes.events needs its name: it changes the rule of that "
                                      + "name, or is added after the others");
            }
            final Object name = named.get("name");
            final int at = indexOf(rules, "name", name);
            if (JsonValues.truthy(named.get("remove"))) {
                if (at < 0) {
                    throw new ToolRefusal("changes.events removes rule '" + str(name) + "', which the "
                                          + "mapping does not have (its rules: " + names(rules) + ")");
                }
                rules.remove(at);
                done.add("rule '" + str(name) + "' removed");
            } else if (at < 0) {
                rules.add(withoutReplace(named));
                done.add("rule '" + str(name) + "' added after the others");
            } else if (JsonValues.truthy(named.get("replace"))) {
                rules.set(at, withoutReplace(named));
                done.add("rule '" + str(name) + "' replaced whole");
            } else {
                rules.set(at, changedRule((Map<?, ?>) rules.get(at), named, done));
            }
        }
    }

    // A rule with the keys given replacing its own, and its fields changed entry by entry.
    @SuppressWarnings("unchecked")
    private static Map<String, Object> changedRule(final Map<?, ?> rule,
                                                   final Map<?, ?> given,
                                                   final List<String> done) {
        final Map<String, Object> changed = (Map<String, Object>) copy(rule);
        final String said = "rule '" + str(given.get("name")) + "'";
        for (final Map.Entry<?, ?> key : given.entrySet()) {
            final String name = String.valueOf(key.getKey());
            if ("name".equals(name)) {
                continue;
            }
            if ("fields".equals(name)) {
                final List<Object> fields = listIn(changed, "fields");
                for (final Object entry : each(key.getValue())) {
                    changeEntry(fields, entry, "changes.events", said + " entry", "Each entry in a rule's fields "
                                                                                + "needs its path (and data_name for "
                                                                                + "a Data entry): it replaces the "
                                                                                + "rule's entry with that path, or is "
                                                                                + "added, or with remove: true is "
                                                                                + "removed", done);
                }
            } else {
                changed.put(name, key.getValue());
                done.add(said + ": '" + name + "' replaced");
            }
        }
        return changed;
    }

    // A rule as given, without the replace flag that only says how to apply it.
    @SuppressWarnings("unchecked")
    private static Map<String, Object> withoutReplace(final Map<?, ?> rule) {
        final Map<String, Object> copy = (Map<String, Object>) copy(rule);
        copy.remove("replace");
        return copy;
    }

    private static void changeCommon(final List<Object> entries, final Object value, final List<String> done) {
        for (final Object entry : each(value)) {
            changeEntry(entries, entry, "changes.common", "common", "Each entry in changes.common needs its path "
                                                                    + "(and data_name for a Data entry): it "
                                                                    + "replaces the entry with that path, or is "
                                                                    + "added", done);
        }
    }

    // One entry (of common, or of a rule's fields) replaced, added or removed by its path and Data name.
    private static void changeEntry(final List<Object> entries,
                                    final Object entry,
                                    final String changes,
                                    final String in,
                                    final String needsPath,
                                    final List<String> done) {
        if (!(entry instanceof final Map<?, ?> given) || !JsonValues.truthy(given.get("path"))) {
            throw new ToolRefusal(needsPath);
        }
        final List<String> found = commonKey(given);
        int at = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i) instanceof final Map<?, ?> other && commonKey(other).equals(found)) {
                at = i;
                break;
            }
        }
        if (JsonValues.truthy(given.get("remove"))) {
            if (at < 0) {
                throw new ToolRefusal(changes + " removes " + said(in, found) + ", which the mapping does not have");
            }
            entries.remove(at);
            done.add(said(in, found) + " removed");
        } else if (at < 0) {
            entries.add(entry);
            done.add(said(in, found) + " added");
        } else {
            entries.set(at, entry);
            done.add(said(in, found) + " replaced");
        }
    }

    // A common entry is known by its path and Data name (with the Data it is in, as FieldMapping.dataKey()).
    private static List<String> commonKey(final Map<?, ?> entry) {
        final Object dataName = entry.get("data_name");
        final Object dataParent = entry.get("data_parent");
        return List.of(str(entry.get("path")), !JsonValues.truthy(dataName)
                ? ""
                : JsonValues.truthy(dataParent)
                        ? str(dataParent) + "/" + str(dataName)
                        : str(dataName));
    }

    private static String said(final String in, final List<String> key) {
        return in + " '" + key.get(0) + "'" + (key.get(1).isEmpty()
                ? ""
                : " (Data '" + key.get(1) + "')");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> changesOf(final Object changes, final String message) {
        if (!(changes instanceof final Map<?, ?> map) || map.isEmpty()) {
            throw new ToolRefusal(message);
        }
        return (Map<String, Object>) map;
    }

    // The list held at a key of the merged mapping, made if it isn't there.
    @SuppressWarnings("unchecked")
    private static List<Object> listIn(final Map<String, Object> merged, final String key) {
        if (merged.get(key) instanceof final List<?> list) {
            return (List<Object>) list;
        }
        final List<Object> made = new ArrayList<>();
        merged.put(key, made);
        return made;
    }

    // A change's value: a list of entries, or one entry on its own.
    private static List<?> each(final Object value) {
        return value instanceof final List<?> list
                ? list
                : Collections.singletonList(value);
    }

    private static int indexOf(final List<Object> items, final String key, final Object value) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) instanceof final Map<?, ?> item && Objects.equals(item.get(key), value)) {
                return i;
            }
        }
        return -1;
    }

    // The items' names, as a list's text: `['a', 'b']`.
    private static String names(final List<Object> items) {
        final List<Object> names = new ArrayList<>();
        for (final Object item : items) {
            names.add(item instanceof final Map<?, ?> map
                    ? map.get("name")
                    : null);
        }
        return ToolText.repr(names);
    }

    // A JSON value as text: a string as it is, anything else as its literal.
    private static String str(final Object value) {
        return value instanceof final String text
                ? text
                : ToolText.repr(value);
    }

    // A deep copy of JSON-like values: maps and lists copied, so the base is left alone.
    private static Map<String, Object> copyMap(final Map<String, Object> map) {
        final Map<String, Object> out = new LinkedHashMap<>();
        map.forEach((key, value) -> out.put(key, copy(value)));
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Object copy(final Object value) {
        if (value instanceof final Map<?, ?> map) {
            return copyMap((Map<String, Object>) map);
        }
        if (value instanceof final List<?> list) {
            final List<Object> out = new ArrayList<>(list.size());
            list.forEach(item -> out.add(copy(item)));
            return out;
        }
        return value;
    }


    // --------------------------------------------------------------------------------


    /// The merged mapping and what each change did.
    ///
    /// @param merged The mapping (or plan) with the changes merged in.
    /// @param done   What each change did, e.g. `rule 'logon' replaced`.
    public record Applied(Map<String, Object> merged, List<String> done) {

    }
}
