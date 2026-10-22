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

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.PyText;
import stroom.ai.impl.tools.sample.PyJson;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/// An Elasticsearch mapping read as the fields it maps (dotted paths), and put together again: the objects it
/// declares, the fields it doesn't index, an index template composed with its component templates as
/// Elasticsearch composes them, and how a set of examples maps each type. Ported from the Stroom MCP server's
/// `utils/templatecheck.py` (read_mapping, read_mapping_fields, object_nodes, not_indexed, unsearchable, compose,
/// type_styles and their helpers).
///
/// Mappings are JSON-like values: maps (in key order), lists, strings, numbers, booleans and nulls.
public final class TemplateMappings {

    // Per-field parameters that describe a style (how this environment maps a type), as opposed to one field's own.
    private static final List<String> STYLE_PARAMS = List.of("type", "ignore_above", "fields", "format", "norms",
            "doc_values", "index", "normalizer", "analyzer", "null_value", "scaling_factor");

    private TemplateMappings() {
        // Static only.
    }

    /// @param mappings A mapping (a template's `mappings`).
    /// @return Its fields by path (an object as `{type: object}`, or its own type), and its dynamic settings.
    public static Mapping readMapping(final Map<String, Object> mappings) {
        final Mapping out = new Mapping(new LinkedHashMap<>(), new LinkedHashMap<>());
        if (mappings.containsKey("dynamic")) {
            out.dynamic().put("", mappings.get("dynamic"));
        }
        walk(out, JsonValues.map(mappings.get("properties")), "");
        return out;
    }

    private static void walk(final Mapping out, final Map<String, Object> properties, final String prefix) {
        if (properties == null) {
            return;
        }
        for (final Map.Entry<String, Object> entry : properties.entrySet()) {
            final String path = prefix.isEmpty()
                    ? entry.getKey()
                    : prefix + "." + entry.getKey();
            final Map<String, Object> spec = JsonValues.mapOrEmpty(entry.getValue());
            if (spec.containsKey("dynamic")) {
                out.dynamic().put(path, spec.get("dynamic"));
            }
            if (spec.containsKey("properties")) {
                final Map<String, Object> object = new LinkedHashMap<>();
                object.put("type", spec.getOrDefault("type", "object"));
                out.fields().put(path, object);
                walk(out, JsonValues.map(spec.get("properties")), path);
            } else {
                out.fields().put(path, spec);
            }
        }
    }

    /// @param body An index template's body.
    /// @return Each field of its mappings by path (objects included, as `{type: object}`).
    public static Map<String, Map<String, Object>> readMappingFields(final Map<String, Object> body) {
        return readMapping(mappingsOf(body)).fields();
    }

    /// @param body A template's body (index or component).
    /// @return `(body.get('template') or {}).get('mappings') or {}`.
    static Map<String, Object> mappingsOf(final Map<String, Object> body) {
        return JsonValues.mapAt(JsonValues.mapAt(body, "template"), "mappings");
    }

    /// @param mappings A mapping.
    /// @return Each object's own parameters (type, dynamic, enabled, subobjects) by path, for each node with
    /// properties.
    public static Map<String, Map<String, Object>> objectNodes(final Map<String, Object> mappings) {
        final Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        objects(out, JsonValues.map(mappings.get("properties")), "");
        return out;
    }

    private static void objects(final Map<String, Map<String, Object>> out,
                                final Map<String, Object> properties,
                                final String prefix) {
        if (properties == null) {
            return;
        }
        for (final Map.Entry<String, Object> entry : properties.entrySet()) {
            if (entry.getValue() instanceof final Map<?, ?> given && given.containsKey("properties")) {
                final Map<String, Object> spec = JsonValues.map(given);
                final String path = prefix.isEmpty()
                        ? entry.getKey()
                        : prefix + "." + entry.getKey();
                final Map<String, Object> own = new LinkedHashMap<>();
                spec.forEach((key, value) -> {
                    if (!"properties".equals(key)) {
                        own.put(key, JsonValues.deepCopy(value));
                    }
                });
                out.put(path, own);
                objects(out, JsonValues.map(spec.get("properties")), path);
            }
        }
    }

    /// The fields a mapping keeps but doesn't index (`"index": false`), and the objects it doesn't parse at all
    /// (`"enabled": false`, everything below kept in _source only). Stroom searches neither (it lists them as not
    /// queryable; Elasticsearch itself may still search a keyword by its doc values): asked about by the user, a
    /// verification search on one finds nothing, as if the documents were missing.
    ///
    /// @param mappings A mapping.
    /// @return The fields and objects.
    public static NotIndexed notIndexed(final Map<String, Object> mappings) {
        final Set<String> fields = new TreeSet<>();
        final Set<String> objects = new TreeSet<>();
        unindexed(fields, objects, JsonValues.map(mappings.get("properties")), "");
        return new NotIndexed(fields, objects);
    }

    private static void unindexed(final Set<String> fields,
                                  final Set<String> objects,
                                  final Map<String, Object> properties,
                                  final String prefix) {
        if (properties == null) {
            return;
        }
        for (final Map.Entry<String, Object> entry : properties.entrySet()) {
            final Map<String, Object> spec = JsonValues.mapOrEmpty(entry.getValue());
            final String path = prefix.isEmpty()
                    ? entry.getKey()
                    : prefix + "." + entry.getKey();
            if ("false".equals(PyText.lower(PyText.str(spec.getOrDefault("enabled", true))))) {
                objects.add(path);
            } else if ("false".equals(PyText.lower(PyText.str(spec.getOrDefault("index", true))))) {
                fields.add(path);
            }
            if (spec.containsKey("properties")) {
                unindexed(fields, objects, JsonValues.map(spec.get("properties")), path);
            }
        }
    }

    /// @param mappings A mapping.
    /// @param paths    Field paths.
    /// @return Those the mapping doesn't index or keeps under an object it doesn't parse, sorted.
    public static List<String> unsearchable(final Map<String, Object> mappings, final Collection<String> paths) {
        final NotIndexed hidden = notIndexed(mappings);
        return paths.stream()
                .filter(p -> hidden.fields().contains(p)
                             || hidden.objects().stream().anyMatch(o -> p.equals(o) || p.startsWith(o + ".")))
                .sorted()
                .toList();
    }

    /// Merges a mapping into another, as Elasticsearch composes them: objects merged, other values replaced.
    ///
    /// @param into  The mapping merged into (changed).
    /// @param other The mapping merged in.
    /// @return into.
    @SuppressWarnings("unchecked")
    static Map<String, Object> merge(final Map<String, Object> into, final Map<String, Object> other) {
        for (final Map.Entry<String, Object> entry : other.entrySet()) {
            if (entry.getValue() instanceof Map<?, ?> && into.get(entry.getKey()) instanceof Map<?, ?>) {
                merge((Map<String, Object>) into.get(entry.getKey()), (Map<String, Object>) entry.getValue());
            } else {
                into.put(entry.getKey(), JsonValues.deepCopy(entry.getValue()));
            }
        }
        return into;
    }

    /// The index template with its component templates' mappings merged in as Elasticsearch composes them (each of
    /// composed_of in order, then the template's own mappings over them), and those of composed_of not given.
    ///
    /// @param body       An index template's body.
    /// @param components The component templates given, by name.
    /// @return The composed body, and the components missing (ecs@mappings, built in, is never missing).
    public static Composed compose(final Map<String, Object> body, final Map<String, Map<String, Object>> components) {
        final Map<String, Object> merged = new LinkedHashMap<>();
        final List<String> missing = new ArrayList<>();
        for (final String name : JsonValues.strings(body.get("composed_of"))) {
            if (components.containsKey(name)) {
                merge(merged, mappingsOf(components.get(name)));
            } else if (!EcsSchema.COMPONENT.equals(name)) {
                // ecs@mappings is built in: what it maps is known (EcsSchema.componentType).
                missing.add(name);
            }
        }
        merge(merged, mappingsOf(body));
        final Map<String, Object> template = new LinkedHashMap<>(JsonValues.mapAt(body, "template"));
        template.put("mappings", merged);
        final Map<String, Object> out = new LinkedHashMap<>(body);
        out.put("template", template);
        return new Composed(out, missing);
    }

    /// Removes a field from nested (or, flat, dotted) properties, and objects it leaves empty.
    ///
    /// @param properties The properties (changed).
    /// @param path       The field's path.
    /// @param flat       True if the properties hold dotted names (subobjects: false).
    /// @return True if it was there.
    @SuppressWarnings("unchecked")
    static boolean drop(final Map<String, Object> properties, final String path, final boolean flat) {
        if (flat) {
            final boolean had = properties.get(path) != null;
            properties.remove(path);
            return had;
        }
        final int dot = path.indexOf('.');
        final String head = dot < 0
                ? path
                : path.substring(0, dot);
        final String rest = dot < 0
                ? ""
                : path.substring(dot + 1);
        if (!properties.containsKey(head)) {
            return false;
        }
        if (rest.isEmpty()) {
            properties.remove(head);
            return true;
        }
        final Map<String, Object> spec = JsonValues.mapOrEmpty(properties.get(head));
        final Object given = spec.get("properties");
        final Map<String, Object> inner = given instanceof Map<?, ?> && !((Map<?, ?>) given).isEmpty()
                ? (Map<String, Object>) given
                : new LinkedHashMap<>();
        final boolean dropped = drop(inner, rest, false);
        if (dropped && inner.isEmpty()) {
            properties.remove(head);
        }
        return dropped;
    }

    /// Mapping properties from dotted paths: `{source.ip: {...}}` -> `{source: {properties: {ip: {...}}}}`. An
    /// object the examples define gets their parameters for it; with typed, a new object is declared `"type":
    /// "object"` as the examples declare theirs.
    ///
    /// @param fields  The fields by path.
    /// @param objects The examples' objects' own parameters by path.
    /// @param typed   True to declare a new object's type.
    /// @return The properties.
    @SuppressWarnings("unchecked")
    static Map<String, Object> nest(final Map<String, Map<String, Object>> fields,
                                    final Map<String, Map<String, Object>> objects,
                                    final boolean typed) {
        final Map<String, Object> properties = new LinkedHashMap<>();
        for (final Map.Entry<String, Map<String, Object>> entry : fields.entrySet()) {
            Map<String, Object> node = properties;
            final String[] parts = entry.getKey().split("\\.", -1);
            for (int i = 0; i < parts.length - 1; i++) {
                final String where = String.join(".", List.of(parts).subList(0, i + 1));
                if (!node.containsKey(parts[i])) {
                    final Map<String, Object> params = objects.containsKey(where)
                            ? JsonValues.deepCopy(objects.get(where))
                            : new LinkedHashMap<>(typed
                                    ? Map.of("type", "object")
                                    : Map.of());
                    final Map<String, Object> object = new LinkedHashMap<>(params);
                    object.put("properties", new LinkedHashMap<String, Object>());
                    node.put(parts[i], object);
                }
                final Map<String, Object> object = (Map<String, Object>) node.get(parts[i]);
                node = (Map<String, Object>) object.computeIfAbsent("properties",
                        key -> new LinkedHashMap<String, Object>());
            }
            node.put(parts[parts.length - 1], JsonValues.deepCopy(entry.getValue()));
        }
        return properties;
    }

    /// The way an example maps each type: for each type, its most common set of style parameters (keyword with
    /// ignore_above 1024, text with a .keyword sub-field, date with a format...).
    ///
    /// @param fields The example's fields by path.
    /// @return Each type's style.
    public static Map<String, Map<String, Object>> typeStyles(final Map<String, Map<String, Object>> fields) {
        final Map<Object, Map<String, Integer>> byType = new LinkedHashMap<>();
        final Map<String, Map<String, Object>> specs = new HashMap<>();
        for (final Map<String, Object> spec : fields.values()) {
            if (!spec.containsKey("type") || spec.containsKey("properties") || "object".equals(spec.get("type"))) {
                continue;
            }
            final Map<String, Object> style = new LinkedHashMap<>();
            for (final String param : STYLE_PARAMS) {
                if (spec.containsKey(param)) {
                    style.put(param, spec.get(param));
                }
            }
            final String key = PyJson.dumpsSortedKeys(style);
            byType.computeIfAbsent(spec.get("type"), type -> new LinkedHashMap<>()).merge(key, 1, Integer::sum);
            specs.put(key, style);
        }
        final Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        byType.forEach((type, counted) -> out.put(PyText.str(type), specs.get(mostCommon(counted))));
        return out;
    }

    // Counter.most_common(1): the highest count, the first counted of those tied.
    private static String mostCommon(final Map<String, Integer> counted) {
        String best = null;
        int most = -1;
        for (final Map.Entry<String, Integer> entry : counted.entrySet()) {
            if (entry.getValue() > most) {
                best = entry.getKey();
                most = entry.getValue();
            }
        }
        return best;
    }

    /// @param path A dotted path.
    /// @return The paths of the objects holding it, nearest first.
    static List<String> parents(final String path) {
        final String[] parts = path.split("\\.", -1);
        final List<String> out = new ArrayList<>();
        for (int n = parts.length - 1; n > 0; n--) {
            out.add(String.join(".", List.of(parts).subList(0, n)));
        }
        return out;
    }

    // --------------------------------------------------------------------------------


    /// A mapping read as its fields.
    ///
    /// @param fields  Each field by path: its spec (type, format, ...), an object as `{type: object}` (or its own
    ///                type).
    /// @param dynamic The dynamic setting of each object that has one, by path (`""` for the root).
    public record Mapping(Map<String, Map<String, Object>> fields, Map<String, Object> dynamic) {

        /// @param path A field's path.
        /// @return The dynamic setting it falls under (its nearest object's, else the root's, else `true`), lower
        /// case.
        public String dynamicFor(final String path) {
            final String[] parts = path.split("\\.", -1);
            for (int n = parts.length - 1; n >= 0; n--) {
                final String at = String.join(".", List.of(parts).subList(0, n));
                if (dynamic.containsKey(at) && dynamic.get(at) != null) {
                    return PyText.lower(PyText.str(dynamic.get(at)));
                }
            }
            return "true";
        }
    }

    /// What a mapping doesn't index.
    ///
    /// @param fields  The fields it keeps but doesn't index (`"index": false`).
    /// @param objects The objects it doesn't parse (`"enabled": false`).
    public record NotIndexed(Set<String> fields, Set<String> objects) {

    }

    /// An index template composed with its component templates.
    ///
    /// @param body    The body, its mappings the components' and its own merged.
    /// @param missing The components of composed_of not given.
    public record Composed(Map<String, Object> body, List<String> missing) {

    }
}
