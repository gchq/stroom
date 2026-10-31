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
/// 40 seconds of writing, five times over. A rule is replaced whole by name, a common entry by its path (and Data
/// name), and any other key whole; `{"remove": true}` removes a rule or a common entry.
public final class MappingChanges {

    private MappingChanges() {
        // Static only.
    }

    /// @param base    The mapping, as JSON-like values (left as it is).
    /// @param changes What changes: `events` (rules, each replacing the rule of its name whole, added after the
    ///                others, or removed with `remove: true`), `common` (entries, each replacing the entry with its
    ///                path and Data name, added, or removed), any other key replacing that key whole.
    /// @return The mapping with the changes merged in, and what each change did.
    /// @throws ToolRefusal if a change can't apply (no name, no path, removing what isn't there).
    public static Applied applyChanges(final Map<String, Object> base, final Object changes) {
        final Map<String, Object> given = changesOf(changes, "changes is an object holding only what changes, e.g. "
                                                             + "{\"events\": [{the rule, whole}]} or {\"common\": "
                                                             + "[{\"path\": ..., \"value\": ...}]}");
        final Map<String, Object> merged = copyMap(base);
        final List<String> done = new ArrayList<>();
        for (final Map.Entry<String, Object> change : given.entrySet()) {
            switch (change.getKey()) {
                case "events" -> changeRules(listIn(merged, "events"), change.getValue(), done);
                case "common" -> changeCommon(listIn(merged, "common"), change.getValue(), done);
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

    private static void changeRules(final List<Object> rules, final Object value, final List<String> done) {
        for (final Object rule : each(value)) {
            if (!(rule instanceof final Map<?, ?> named) || !JsonValues.truthy(named.get("name"))) {
                throw new ToolRefusal("Each rule in changes.events needs its name: a rule replaces the one "
                                      + "of that name whole, or is added after the others");
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
                rules.add(rule);
                done.add("rule '" + str(name) + "' added after the others");
            } else {
                rules.set(at, rule);
                done.add("rule '" + str(name) + "' replaced");
            }
        }
    }

    private static void changeCommon(final List<Object> entries, final Object value, final List<String> done) {
        for (final Object entry : each(value)) {
            if (!(entry instanceof final Map<?, ?> given) || !JsonValues.truthy(given.get("path"))) {
                throw new ToolRefusal("Each entry in changes.common needs its path (and data_name for a "
                                      + "Data entry): it replaces the entry with that path, or is added");
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
                    throw new ToolRefusal("changes.common removes " + said(found) + ", which the mapping "
                                          + "does not have");
                }
                entries.remove(at);
                done.add(said(found) + " removed");
            } else if (at < 0) {
                entries.add(entry);
                done.add(said(found) + " added");
            } else {
                entries.set(at, entry);
                done.add(said(found) + " replaced");
            }
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

    private static String said(final List<String> key) {
        return "common '" + key.get(0) + "'" + (key.get(1).isEmpty()
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
