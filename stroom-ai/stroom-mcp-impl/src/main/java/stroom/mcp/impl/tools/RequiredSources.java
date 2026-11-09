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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.generation.DumpMode;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.sample.Placeholders;
import stroom.ai.impl.tools.validation.EventSchema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// The elements the schema requires that a stand-in for none, left out (nil_values), would leave empty: those whose
/// every source in the mapping is a field holding it. Seen in testing: '-' in the host field, EventSource/Device's
/// only source, was put to the user with "Leave out" recommended, which would have left those events without the
/// Device the schema requires.
final class RequiredSources {

    /// The element a record's sender can stand in for (see [#withDeviceFallback]).
    static final String DEVICE = "EventSource/Device";
    // The stream meta attributes a Device leaf falls back to, tried in order: the sender's name, then its address.
    private static final List<String> HOST_FALLBACK = List.of("RemoteHost", "RemoteAddress");
    private static final List<String> ADDRESS_FALLBACK = List.of("RemoteAddress");
    // The keys of an entry that give it a value whatever its fields hold.
    private static final List<String> FILLED_OTHERWISE = List.of("value", "xpath", "meta", "default", "lookup",
            "time_fallback", "meta_fallback");

    private RequiredSources() {
        // Static only.
    }

    /// @param mapping The mapping.
    /// @param schema  The event-logging schema, or null (when nothing is found).
    /// @param found   The stand-ins found.
    /// @return For each stand-in that left out would leave a required element empty in some rule's events, those
    /// elements' paths (e.g. `EventSource/Device`), by value.
    static Map<String, List<String>> emptied(final TranslationMapping mapping,
                                             final EventSchema schema,
                                             final List<Placeholders.Found> found) {
        final Map<String, List<String>> out = new LinkedHashMap<>();
        if (schema == null || mapping == null) {
            return out;
        }
        final List<List<Map<String, Object>>> scopes = scopes(mapping);
        for (final Placeholders.Found each : found) {
            final Set<String> holding = Set.copyOf(each.fields());
            final Set<String> paths = new LinkedHashSet<>();
            for (final List<Map<String, Object>> scope : scopes) {
                for (final Map<String, Object> entry : scope) {
                    if (!onlyFrom(entry, holding)) {
                        continue;
                    }
                    final String required = requiredPrefix(schema, path(entry));
                    if (required != null && scope.stream()
                            .filter(other -> under(path(other), required))
                            .allMatch(other -> onlyFrom(other, holding))) {
                        paths.add(required);
                    }
                }
            }
            if (!paths.isEmpty()) {
                out.put(each.value(), List.copyOf(paths));
            }
        }
        return out;
    }

    /// @param mapping The mapping.
    /// @param fields  The fields holding the stand-ins the user chose to leave out with the sender standing in.
    /// @return The mapping with each Device entry read only from those fields given the sender's name or address
    /// (meta_fallback) for a record whose field holds none.
    static TranslationMapping withDeviceFallback(final TranslationMapping mapping, final Set<String> fields) {
        final Map<String, Object> json = new LinkedHashMap<>(mapping.toJson(DumpMode.COMPACT));
        json.put("common", fallenBack(JsonValues.maps(json.get("common")), fields));
        final List<Map<String, Object>> rules = new ArrayList<>();
        for (final Map<String, Object> rule : JsonValues.maps(json.get("events"))) {
            final Map<String, Object> copy = new LinkedHashMap<>(rule);
            if (copy.get("fields") != null) {
                copy.put("fields", fallenBack(JsonValues.maps(rule.get("fields")), fields));
            }
            rules.add(copy);
        }
        json.put("events", rules);
        return TranslationMapping.fromJson(json);
    }

    private static List<Map<String, Object>> fallenBack(final List<Map<String, Object>> entries,
                                                        final Set<String> fields) {
        final List<Map<String, Object>> out = new ArrayList<>();
        for (final Map<String, Object> entry : entries) {
            final String path = path(entry);
            if (under(path, DEVICE) && !path.equals(DEVICE) && onlyFrom(entry, fields)) {
                final Map<String, Object> copy = new LinkedHashMap<>(entry);
                copy.put("meta_fallback", path.endsWith("/IPAddress")
                        ? ADDRESS_FALLBACK
                        : HOST_FALLBACK);
                out.add(copy);
            } else {
                out.add(entry);
            }
        }
        return out;
    }

    // The entries each rule's events are written from: the common ones with the rule's own (drop rules write none).
    private static List<List<Map<String, Object>>> scopes(final TranslationMapping mapping) {
        final Map<String, Object> json = mapping.toJson(DumpMode.COMPACT);
        final List<Map<String, Object>> common = JsonValues.maps(json.get("common"));
        final List<List<Map<String, Object>>> scopes = new ArrayList<>();
        for (final Map<String, Object> rule : JsonValues.maps(json.get("events"))) {
            if (JsonValues.truthy(rule.get("drop"))) {
                continue;
            }
            final List<Map<String, Object>> scope = new ArrayList<>(common);
            scope.addAll(JsonValues.maps(rule.get("fields")));
            scopes.add(scope);
        }
        if (scopes.isEmpty()) {
            scopes.add(common);
        }
        return scopes;
    }

    // Whether the entry's value comes only from these fields: none of them holding a value leaves it empty.
    private static boolean onlyFrom(final Map<String, Object> entry, final Set<String> fields) {
        if (FILLED_OTHERWISE.stream().anyMatch(key -> entry.get(key) != null)) {
            return false;
        }
        final List<String> inputs = new ArrayList<>();
        if (entry.get("field") instanceof final String field) {
            inputs.add(field);
        }
        inputs.addAll(JsonValues.strings(entry.get("any_of")));
        inputs.addAll(JsonValues.strings(entry.get("join")));
        return !inputs.isEmpty() && fields.containsAll(inputs);
    }

    // The deepest element on the path that every element down to it is required: the one an empty value leaves
    // missing. Null when the path's first element is optional, or the schema doesn't know the path.
    private static String requiredPrefix(final EventSchema schema, final String path) {
        final List<EventSchema.Child> chain;
        try {
            chain = schema.resolve(path);
        } catch (final IllegalArgumentException e) {
            return null;
        }
        final List<String> names = new ArrayList<>();
        for (final EventSchema.Child child : chain) {
            if (!child.required()) {
                break;
            }
            names.add(child.name());
        }
        return names.isEmpty()
                ? null
                : String.join("/", names);
    }

    // An entry's path below Event, without slashes at either end.
    private static String path(final Map<String, Object> entry) {
        final String path = ToolText.strip(String.valueOf(entry.get("path")), "/");
        return path.startsWith("Event/")
                ? path.substring("Event/".length())
                : path;
    }

    private static boolean under(final String path, final String element) {
        return path.equals(element) || path.startsWith(element + "/");
    }
}
