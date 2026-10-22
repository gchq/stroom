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

import stroom.ai.impl.tools.sample.PyJson;
import stroom.ai.impl.tools.sample.PyText;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/// The few Python behaviours the draft mapping's code leans on, for values held as Python holds them (maps, lists,
/// strings, numbers, booleans and null): truthiness, `dict` literals, `Counter.most_common` and a field's text.
final class PyValues {

    private PyValues() {
        // Static only.
    }

    /// @param keysAndValues Keys and their values, in turn.
    /// @return A map of them, in that order (a Python `dict` literal).
    static Map<String, Object> dict(final Object... keysAndValues) {
        final Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
            out.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return out;
    }

    /// @param value A value.
    /// @return True if Python would take it as true: not null, false, 0, "" or an empty list or map.
    static boolean truthy(final Object value) {
        return switch (value) {
            case null -> false;
            case final Boolean bool -> bool;
            case final String text -> !text.isEmpty();
            case final Collection<?> collection -> !collection.isEmpty();
            case final Map<?, ?> map -> !map.isEmpty();
            case final Number number -> number.doubleValue() != 0.0;
            default -> true;
        };
    }

    /// @param value A record's value.
    /// @return The value as the record's text: "" for a falsy one, a string as it is, anything else (a number read
    /// from JSON) as Python's `str()` writes it.
    static String text(final Object value) {
        if (!truthy(value)) {
            return "";
        }
        return value instanceof final String string
                ? string
                : PyText.str(value);
    }

    /// @param value A value read from JSON.
    /// @return It as text: a string as it is, anything else as `json.dumps` writes it.
    static String jsonText(final Object value) {
        return value instanceof final String string
                ? string
                : PyJson.dumps(value);
    }

    /// @param name A field name.
    /// @param separator The separator.
    /// @return The part after the last separator (`name.rsplit(separator, 1)[-1]`).
    static String lastPart(final String name, final char separator) {
        return name.substring(name.lastIndexOf(separator) + 1);
    }

    /// @param regex A regex written for Python's `re`.
    /// @return It compiled case-insensitively (`re.I`).
    static Pattern ignoringCase(final String regex) {
        return PyText.re(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    /// @param counts Counts, in the order their keys were first counted.
    /// @param <K>    The key type.
    /// @return The keys, the most counted first, keys counted alike in the order first counted
    /// (`Counter.most_common()`).
    static <K> List<K> mostCommon(final Map<K, Integer> counts) {
        final List<Map.Entry<K, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort(Comparator.comparing((Map.Entry<K, Integer> entry) -> entry.getValue()).reversed());
        return entries.stream().map(Map.Entry::getKey).toList();
    }

    /// @param values Values.
    /// @param <K>    The value type.
    /// @return Each value's count, in the order first seen (`Counter(values)`).
    static <K> Map<K, Integer> counted(final Collection<K> values) {
        final Map<K, Integer> counts = new LinkedHashMap<>();
        values.forEach(value -> counts.merge(value, 1, Integer::sum));
        return counts;
    }

    /// @param value A value.
    /// @return It as a list of maps: the maps a list holds, or empty.
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> maps(final Object value) {
        final List<Map<String, Object>> out = new ArrayList<>();
        if (value instanceof final List<?> list) {
            list.stream().filter(Map.class::isInstance).forEach(item -> out.add((Map<String, Object>) item));
        }
        return out;
    }
}
