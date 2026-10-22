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
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Changes to a translation mapping (or an index field plan), merged into the whole one: a fix sends only what it
/// changes. The Stroom MCP server's `utils/mappingchanges.py`.
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
    /// @throws MappingChangeException if a change can't apply (no name, no path, removing what isn't there).
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
    /// @throws MappingChangeException if a change can't apply.
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
                if (!(field instanceof final Map<?, ?> named) || !truthy(named.get("name"))) {
                    throw new MappingChangeException("Each entry in changes.fields needs its name: it replaces the "
                                                     + "field of that name whole, or is added");
                }
                final Object name = named.get("name");
                final int at = indexOf(fields, "name", name);
                if (truthy(named.get("remove"))) {
                    if (at < 0) {
                        throw new MappingChangeException("changes.fields removes '" + str(name) + "', which the plan "
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
            if (!(rule instanceof final Map<?, ?> named) || !truthy(named.get("name"))) {
                throw new MappingChangeException("Each rule in changes.events needs its name: a rule replaces the one "
                                                 + "of that name whole, or is added after the others");
            }
            final Object name = named.get("name");
            final int at = indexOf(rules, "name", name);
            if (truthy(named.get("remove"))) {
                if (at < 0) {
                    throw new MappingChangeException("changes.events removes rule '" + str(name) + "', which the "
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
            if (!(entry instanceof final Map<?, ?> given) || !truthy(given.get("path"))) {
                throw new MappingChangeException("Each entry in changes.common needs its path (and data_name for a "
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
            if (truthy(given.get("remove"))) {
                if (at < 0) {
                    throw new MappingChangeException("changes.common removes " + said(found) + ", which the mapping "
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

    // A common entry is known by its path and Data name.
    private static List<String> commonKey(final Map<?, ?> entry) {
        final Object dataName = entry.get("data_name");
        return List.of(str(entry.get("path")), truthy(dataName)
                ? str(dataName)
                : "");
    }

    private static String said(final List<String> key) {
        return "common '" + key.get(0) + "'" + (key.get(1).isEmpty()
                ? ""
                : " (Data '" + key.get(1) + "')");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> changesOf(final Object changes, final String message) {
        if (!(changes instanceof final Map<?, ?> map) || map.isEmpty()) {
            throw new MappingChangeException(message);
        }
        return (Map<String, Object>) map;
    }

    // The list held at a key of the merged mapping, made if it isn't there (Python's setdefault).
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

    // [r.get('name') for r in rules], as Python writes the list.
    private static String names(final List<Object> items) {
        final List<Object> names = new ArrayList<>();
        for (final Object item : items) {
            names.add(item instanceof final Map<?, ?> map
                    ? map.get("name")
                    : null);
        }
        return PythonRepr.repr(names);
    }

    // Python's str() of a JSON value.
    private static String str(final Object value) {
        return value instanceof final String text
                ? text
                : PythonRepr.repr(value);
    }

    // Python's truth of a JSON value.
    private static boolean truthy(final Object value) {
        return switch (value) {
            case null -> false;
            case final Boolean bool -> bool;
            case final String text -> !text.isEmpty();
            case final Number number -> number.doubleValue() != 0;
            case final Collection<?> items -> !items.isEmpty();
            case final Map<?, ?> map -> !map.isEmpty();
            default -> true;
        };
    }

    // copy.deepcopy of JSON-like values: maps and lists copied, so the base is left alone.
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
