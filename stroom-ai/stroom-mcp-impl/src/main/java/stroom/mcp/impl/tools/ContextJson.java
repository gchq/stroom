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

import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.generation.MaskNames;
import stroom.ai.impl.tools.generation.XPathText;
import stroom.ai.impl.tools.sample.ToolJson;
import stroom.mcp.impl.tools.ContextLookups.Context;
import stroom.mcp.impl.tools.ContextLookups.Entry;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/// Context data that is JSON, read into the entries [ContextLookups] matches records against. Four layouts, as the
/// tools read JSON events and reference feeds (`json_layout`):
///
/// * [#ARRAY]: an array of objects, each an entry.
/// * [#LINES]: several top-level objects (JSON lines), each an entry.
/// * [#WRAPPED]: one object whose array of objects (at a key path, `entries`, or else its longest such array) holds
///   the entries, e.g. `{"source": "x", "events": [{...}, ...]}`.
/// * [#OBJECT]: one object holding no array of objects (or `entries` `.`): the one entry, for every record.
///
/// An entry's fields are its scalar values by dotted key path (`Info.Name`), as the tools name JSON fields; a null,
/// and an array of scalars, is no field. Its tables (for `mask_names`) are its arrays of objects, each item its fields
/// by dotted path relative to it, and its objects keyed by mask (every key a bit mask, decimal or `0x` hex, every
/// value a scalar), each item [#KEY] (the mask) and [#VALUE] (the name), as the generated XSLT reads a copied
/// `<string key="0x1">Read</string>`.
///
/// The context loader reads it with a CombinedParser of type JSON, whose reader always wraps the data in one root
/// `map` (as a JSONParser's `addRootObject` does): an array is `/map/array/map`, each JSON line or the one object
/// `/map/map`, so the reference mapping's records are read where that puts them.
final class ContextJson {

    /// An array of objects, each an entry.
    static final String ARRAY = "array";
    /// Several top-level objects, each an entry.
    static final String LINES = "lines";
    /// One object whose array of objects holds the entries.
    static final String WRAPPED = "wrapped";
    /// One object, the one entry.
    static final String OBJECT = "object";
    /// The `entries` that reads one object as the one entry, though it holds arrays of objects.
    static final String ITSELF = ".";
    /// A keyed table item's mask: the key, as the copied element's `key` attribute.
    static final String KEY = "@key";
    /// A keyed table item's name: the value, as the copied element's text.
    static final String VALUE = ".";

    // How deep an entry's objects are read.
    private static final int MAX_DEPTH = 8;

    private ContextJson() {
        // Static only.
    }

    /// @param text    The context data, starting with `{` or `[`.
    /// @param entries The entries' key path in one object (dotted), [#ITSELF], or null to find them.
    /// @return The context's entries, and where they are.
    /// @throws ToolRefusal if it isn't JSON, or holds no entries.
    static Context entries(final String text, final String entries) {
        final List<Object> values = values(text);
        final String given = entries == null || entries.isBlank()
                ? null
                : entries.strip();
        if (values.size() > 1 || values.getFirst() instanceof List) {
            if (given != null) {
                throw new ToolRefusal("entries names the array of entries inside one JSON object: this context data "
                                      + "is " + (values.size() > 1
                        ? "JSON lines"
                        : "a JSON array") + ", each object an entry; leave entries out");
            }
            final List<?> items = values.size() > 1
                    ? values
                    : (List<?>) values.getFirst();
            return context(values.size() > 1
                    ? LINES
                    : ARRAY, "", objects(items, "the context data"));
        }
        if (!(values.getFirst() instanceof final Map<?, ?> object)) {
            throw new ToolRefusal("The context data is one JSON " + kind(values.getFirst()) + ": context lookups "
                                  + "read JSON objects, each an entry");
        }
        if (ITSELF.equals(given)) {
            return context(OBJECT, "", List.of(entry(object)));
        }
        final String path = given != null
                ? given.replace('/', '.').replaceFirst("^\\.+", "")
                : longestArray(object, "");
        if (path == null) {
            return context(OBJECT, "", List.of(entry(object)));
        }
        Object found = object;
        for (final String step : path.split("\\.")) {
            found = found instanceof final Map<?, ?> map
                    ? map.get(step)
                    : null;
        }
        if (!(found instanceof final List<?> list)) {
            throw new ToolRefusal("The context data has no array of entries at '" + path + "'");
        }
        return context(WRAPPED, path, objects(list, "'" + path + "'"));
    }

    /// @param context A JSON context.
    /// @return Its records' XPath as the context loader's JSON reader writes them (with its root map, or without),
    /// for a wrapped layout; null for the others, whose `json_layout` says where they are.
    static String recordPath(final Context context) {
        if (!WRAPPED.equals(context.jsonLayout())) {
            return null;
        }
        final String[] steps = context.path().split("\\.");
        final List<String> parts = new ArrayList<>();
        for (int i = 0; i < steps.length; i++) {
            parts.add((i == steps.length - 1
                    ? "array"
                    : "map") + "[@key = " + XPathText.literal(steps[i]) + "]");
        }
        final String inner = String.join("/", parts) + "/map";
        return "/map/map/" + inner + " | /map/" + inner;
    }

    /// @param context A JSON context.
    /// @return The reference mapping's `json_layout`: `array`, or `lines` (each top-level object a record).
    static String jsonLayout(final Context context) {
        return ARRAY.equals(context.jsonLayout())
                ? "array"
                : "lines";
    }

    /// @param path A JSON field's dotted path.
    /// @return Its XPath in Stroom's JSON as XML, relative to its object: `*[@key = 'a']/*[@key = 'b']`.
    static String selector(final String path) {
        return Arrays.stream(path.split("\\."))
                .map(step -> "*[@key=" + XPathText.literal(step) + "]")
                .collect(Collectors.joining("/"));
    }

    /// @param path A table item's field as the entries name it (dotted), [#KEY] or [#VALUE].
    /// @return It as a mask_names path, `/`-separated (each step read as an element or a JSON key alike).
    static String itemPath(final String path) {
        return path.startsWith("@") || VALUE.equals(path)
                ? path
                : path.replace('.', '/');
    }

    /// @param context A part's context.
    /// @return A short account of where its entries are.
    static String describe(final Context context) {
        return switch (context.jsonLayout()) {
            case ARRAY -> "JSON array: each object an entry";
            case LINES -> "JSON lines: each object an entry";
            case WRAPPED -> "JSON object: each object of its '" + context.path() + "' array an entry";
            default -> "JSON object: the one entry";
        };
    }

    private static Context context(final String layout, final String path, final List<Entry> entries) {
        if (entries.isEmpty()) {
            throw new ToolRefusal("The context data holds no entries (JSON objects)");
        }
        return new Context(null, null, path, entries, layout);
    }

    // Every top-level value: one, or several (JSON lines).
    private static List<Object> values(final String text) {
        final List<Object> out = new ArrayList<>();
        int at = skipSpace(text, 0);
        try {
            while (at < text.length()) {
                final ToolJson.Decoded decoded = ToolJson.rawDecode(text, at);
                out.add(decoded.value());
                at = skipSpace(text, decoded.end());
            }
        } catch (final ToolJson.JsonDecodeException e) {
            throw new ToolRefusal("The context data isn't JSON (" + e.getMessage() + "): context lookups read XML "
                                  + "or JSON context data");
        }
        if (out.isEmpty()) {
            throw new ToolRefusal("The context data is empty");
        }
        return out;
    }

    private static int skipSpace(final String text, final int from) {
        int at = from;
        while (at < text.length() && Character.isWhitespace(text.charAt(at))) {
            at++;
        }
        return at;
    }

    private static List<Entry> objects(final List<?> items, final String where) {
        final List<Entry> out = new ArrayList<>();
        for (final Object item : items) {
            if (item instanceof final Map<?, ?> map) {
                out.add(entry(map));
            }
        }
        if (out.isEmpty() && !items.isEmpty()) {
            throw new ToolRefusal(where + " holds no JSON objects: context lookups read each entry from an object");
        }
        return out;
    }

    // The key path of an object's array holding the most objects, three levels deep at most (the first of those
    // holding as many); null for none.
    private static String longestArray(final Map<?, ?> object, final String prefix) {
        final Map<String, Integer> found = new LinkedHashMap<>();
        arrays(object, prefix, 0, found);
        return found.entrySet().stream()
                .reduce((best, next) -> next.getValue() > best.getValue()
                        ? next
                        : best)
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private static void arrays(final Map<?, ?> object,
                               final String prefix,
                               final int depth,
                               final Map<String, Integer> found) {
        for (final Map.Entry<?, ?> field : object.entrySet()) {
            final String path = join(prefix, String.valueOf(field.getKey()));
            if (field.getValue() instanceof final List<?> list) {
                final int count = (int) list.stream().filter(Map.class::isInstance).count();
                if (count > 0) {
                    found.put(path, count);
                }
            } else if (field.getValue() instanceof final Map<?, ?> inner && depth < 2) {
                arrays(inner, path, depth + 1, found);
            }
        }
    }

    private static Entry entry(final Map<?, ?> object) {
        final Map<String, String> fields = new LinkedHashMap<>();
        final Map<String, List<Map<String, String>>> tables = new LinkedHashMap<>();
        collect(object, "", 0, fields, tables);
        return new Entry(fields, tables);
    }

    private static void collect(final Map<?, ?> object,
                                final String prefix,
                                final int depth,
                                final Map<String, String> fields,
                                final Map<String, List<Map<String, String>>> tables) {
        for (final Map.Entry<?, ?> field : object.entrySet()) {
            final String path = join(prefix, String.valueOf(field.getKey()));
            final Object value = field.getValue();
            if (value instanceof final Map<?, ?> inner) {
                if (keyedByMask(inner)) {
                    final List<Map<String, String>> items = new ArrayList<>();
                    inner.forEach((mask, name) -> {
                        final Map<String, String> item = new LinkedHashMap<>();
                        item.put(KEY, String.valueOf(mask));
                        item.put(VALUE, String.valueOf(name));
                        items.add(item);
                    });
                    tables.put(path, items);
                } else if (depth < MAX_DEPTH) {
                    collect(inner, path, depth + 1, fields, tables);
                }
            } else if (value instanceof final List<?> list) {
                final List<Map<String, String>> items = new ArrayList<>();
                for (final Object item : list) {
                    if (item instanceof final Map<?, ?> map) {
                        final Map<String, String> itemFields = new LinkedHashMap<>();
                        collect(map, "", depth + 1, itemFields, new LinkedHashMap<>());
                        items.add(itemFields);
                    }
                }
                if (!items.isEmpty()) {
                    tables.put(path, items);
                }
            } else if (value != null) {
                fields.put(path, String.valueOf(value));
            }
        }
    }

    // An object keyed by mask: every key a non-zero mask, every value a scalar.
    private static boolean keyedByMask(final Map<?, ?> object) {
        if (object.isEmpty()) {
            return false;
        }
        for (final Map.Entry<?, ?> field : object.entrySet()) {
            final BigInteger mask = MaskNames.parse(String.valueOf(field.getKey()));
            if (mask == null || mask.signum() == 0 || field.getValue() == null
                || field.getValue() instanceof Map || field.getValue() instanceof List) {
                return false;
            }
        }
        return true;
    }

    private static String kind(final Object value) {
        return value instanceof String
                ? "string"
                : value == null
                        ? "null"
                        : value instanceof Boolean
                                ? "boolean"
                                : "number";
    }

    private static String join(final String prefix, final String step) {
        return prefix.isEmpty()
                ? step
                : prefix + "." + step;
    }
}
