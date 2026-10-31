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

package stroom.ai.impl.tools;

import stroom.ai.impl.tools.sample.ToolJson;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// JSON-like values (maps, lists, strings, numbers, booleans and nulls, as [ToolJson] or Jackson reads them) handled
/// alike wherever the tools read them: truthiness, ordered maps, a missing map read as an empty one, the maps or
/// strings a list holds, numbers compared across types, deep copies and counting.
public final class JsonValues {

    private JsonValues() {
        // Static only.
    }

    /// @param value A value.
    /// @return True if it is something: not null, false, zero, or an empty string, list or map.
    public static boolean truthy(final Object value) {
        return switch (value) {
            case null -> false;
            case final Boolean bool -> bool;
            case final String text -> !text.isEmpty();
            case final Collection<?> items -> !items.isEmpty();
            case final Map<?, ?> map -> !map.isEmpty();
            case final Number number -> number.doubleValue() != 0;
            default -> true;
        };
    }

    /// @param keysAndValues Keys and their values, in turn.
    /// @return A map of them, in that order.
    public static Map<String, Object> dict(final Object... keysAndValues) {
        final Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
            out.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return out;
    }

    /// @param names Names.
    /// @return A set of them that can be asked about null (Set.of can't).
    public static Set<String> nullSafeSet(final String... names) {
        return Collections.unmodifiableSet(new HashSet<>(List.of(names)));
    }

    /// @param value A value.
    /// @return It if it is a map, else null (`x if isinstance(x, dict) else None`).
    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(final Object value) {
        return value instanceof Map<?, ?>
                ? (Map<String, Object>) value
                : null;
    }

    /// @param value A value.
    /// @return It if it is a non-empty map, else an empty one (`x or {}`).
    @SuppressWarnings("unchecked")
    public static Map<String, Object> mapOrEmpty(final Object value) {
        return value instanceof final Map<?, ?> map && !map.isEmpty()
                ? (Map<String, Object>) map
                : new LinkedHashMap<>();
    }

    /// @param map A map, or null.
    /// @param key A key.
    /// @return `(map or {}).get(key) or {}`, as a map: empty when it is missing, falsy or not a map.
    public static Map<String, Object> mapAt(final Map<String, Object> map, final String key) {
        return map == null
                ? new LinkedHashMap<>()
                : mapOrEmpty(map.get(key));
    }

    /// @param value A value.
    /// @return It if it is a list, else an empty list (`x or []`).
    @SuppressWarnings("unchecked")
    public static List<Object> listOrEmpty(final Object value) {
        return value instanceof List<?>
                ? (List<Object>) value
                : new ArrayList<>();
    }

    /// @param value A value.
    /// @return The maps it holds if it is a list (`[x for x in value if isinstance(x, dict)]`), else none.
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> maps(final Object value) {
        final List<Map<String, Object>> out = new ArrayList<>();
        if (value instanceof final List<?> list) {
            list.stream().filter(Map.class::isInstance).forEach(item -> out.add((Map<String, Object>) item));
        }
        return out;
    }

    /// @param value A list of names, or anything else.
    /// @return Its items as text, as [ToolText#str(Object)] writes them (a string as it is); none if it isn't a list.
    public static List<String> strings(final Object value) {
        final List<String> out = new ArrayList<>();
        if (value instanceof final Collection<?> items) {
            items.forEach(item -> out.add(ToolText.str(item)));
        }
        return out;
    }

    /// @param value A record's value.
    /// @return The value as the record's text: "" for a falsy one, a string as it is, anything else (a number read
    /// from JSON) as [ToolText#str(Object)] writes it.
    public static String text(final Object value) {
        return truthy(value)
                ? ToolText.str(value)
                : "";
    }

    /// @param value A value read from JSON.
    /// @return It as text: a string as it is, anything else as `json.dumps` writes it.
    public static String jsonText(final Object value) {
        return value instanceof final String string
                ? string
                : ToolJson.dumps(value);
    }

    /// @param value  A value.
    /// @param number A whole number.
    /// @return True if the value is a number equal to it, compared by value across types (`1024.0` equals `1024`).
    public static boolean equalsNumber(final Object value, final long number) {
        return switch (value) {
            case final Boolean bool -> (bool
                    ? 1
                    : 0) == number;
            case final Double decimal -> decimal == number;
            case final Number whole -> whole.longValue() == number && whole.doubleValue() == number;
            case null, default -> false;
        };
    }

    /// @param value A JSON-like value.
    /// @param <T>   Its type.
    /// @return A deep copy of it (`copy.deepcopy`), maps keeping their order.
    @SuppressWarnings("unchecked")
    public static <T> T deepCopy(final T value) {
        if (value instanceof final Map<?, ?> map) {
            final Map<Object, Object> copy = new LinkedHashMap<>();
            map.forEach((key, item) -> copy.put(key, deepCopy(item)));
            return (T) copy;
        }
        if (value instanceof final List<?> list) {
            final List<Object> copy = new ArrayList<>();
            list.forEach(item -> copy.add(deepCopy(item)));
            return (T) copy;
        }
        return value;
    }

    /// @param counts Counts, in the order their keys were first counted.
    /// @param <K>    The key type.
    /// @return The keys, the most counted first, keys counted alike in the order first counted
    /// (`Counter.most_common()`).
    public static <K> List<K> mostCommon(final Map<K, Integer> counts) {
        final List<Map.Entry<K, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort(Comparator.comparing((Map.Entry<K, Integer> entry) -> entry.getValue()).reversed());
        return entries.stream().map(Map.Entry::getKey).toList();
    }

    /// @param values Values.
    /// @param <K>    The value type.
    /// @return Each value's count, in the order first seen (`Counter(values)`).
    public static <K> Map<K, Integer> counted(final Collection<K> values) {
        final Map<K, Integer> counts = new LinkedHashMap<>();
        values.forEach(value -> counts.merge(value, 1, Integer::sum));
        return counts;
    }
}
