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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/// JSON-like values (maps, lists, strings, numbers, booleans and nulls, as [stroom.ai.impl.tools.sample.PyJson]
/// reads them) handled as the Stroom MCP server's Python handles its dicts and lists: truth, `str()`, numbers
/// compared across types, `dict.get(...) or {}`, deep copies, and the string methods whose Unicode rules differ from
/// Java's.
final class PyValues {

    private PyValues() {
        // Static only.
    }

    /// @param names Names.
    /// @return A set of them that can be asked about null (Set.of can't), as a Python set can.
    static Set<String> nullSafeSet(final String... names) {
        return Collections.unmodifiableSet(new HashSet<>(List.of(names)));
    }

    /// @param value A value.
    /// @return Python's truth of it: not null, false, zero, or an empty string, list or map.
    static boolean truthy(final Object value) {
        return switch (value) {
            case null -> false;
            case final Boolean bool -> bool;
            case final String text -> !text.isEmpty();
            case final Collection<?> items -> !items.isEmpty();
            case final Map<?, ?> map -> !map.isEmpty();
            case final Double number -> number != 0;
            case final Number number -> number.longValue() != 0 || number.doubleValue() != 0;
            default -> true;
        };
    }

    /// @param value A value.
    /// @return It as Python's `str()` writes it (`True`, `None`, `1.0`).
    static String str(final Object value) {
        return switch (value) {
            case null -> "None";
            case final Boolean bool -> bool
                    ? "True"
                    : "False";
            case final String text -> text;
            default -> String.valueOf(value);
        };
    }

    /// @param value  A value.
    /// @param number A whole number.
    /// @return True if the value is a number equal to it, as Python's `==` compares them (`1024.0 == 1024`).
    static boolean equalsNumber(final Object value, final long number) {
        return switch (value) {
            case final Boolean bool -> (bool
                    ? 1
                    : 0) == number;
            case final Double decimal -> decimal == number;
            case final Number whole -> whole.longValue() == number && whole.doubleValue() == number;
            case null, default -> false;
        };
    }

    /// @param value A value.
    /// @return It if it is a map, else null (`x if isinstance(x, dict) else None`).
    @SuppressWarnings("unchecked")
    static Map<String, Object> map(final Object value) {
        return value instanceof Map<?, ?>
                ? (Map<String, Object>) value
                : null;
    }

    /// @param value A value.
    /// @return It if it is a non-empty map, else an empty one (`x or {}`).
    @SuppressWarnings("unchecked")
    static Map<String, Object> mapOrEmpty(final Object value) {
        return value instanceof final Map<?, ?> map && !map.isEmpty()
                ? (Map<String, Object>) map
                : new LinkedHashMap<>();
    }

    /// @param map A map, or null.
    /// @param key A key.
    /// @return `(map or {}).get(key) or {}`, as a map: empty when it is missing, falsy or not a map.
    static Map<String, Object> mapAt(final Map<String, Object> map, final String key) {
        return map == null
                ? new LinkedHashMap<>()
                : mapOrEmpty(map.get(key));
    }

    /// @param value A value.
    /// @return It if it is a list, else an empty list (`x or []`).
    @SuppressWarnings("unchecked")
    static List<Object> listOrEmpty(final Object value) {
        return value instanceof List<?>
                ? (List<Object>) value
                : new ArrayList<>();
    }

    /// @param value A list of names, or anything else.
    /// @return Its items as strings (none if it isn't a list).
    static List<String> strings(final Object value) {
        final List<String> out = new ArrayList<>();
        for (final Object item : listOrEmpty(value)) {
            out.add(item instanceof final String text
                    ? text
                    : str(item));
        }
        return out;
    }

    /// @param value A JSON-like value.
    /// @return A deep copy of it (`copy.deepcopy`), maps keeping their order.
    @SuppressWarnings("unchecked")
    static <T> T deepCopy(final T value) {
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

    /// @param text A text.
    /// @return It lower-cased, as Python's `str.lower` does for the names these tools see.
    static String lower(final String text) {
        return text.toLowerCase(Locale.ROOT);
    }

    /// @param text A text.
    /// @return It without leading and trailing whitespace, as Python's `str.strip()` has it.
    static String strip(final String text) {
        int start = 0;
        int end = text.length();
        while (start < end && isSpace(text.charAt(start))) {
            start++;
        }
        while (end > start && isSpace(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(start, end);
    }

    /// @param text  A text.
    /// @param chars The characters to take off its end.
    /// @return It without them at its end, as Python's `str.rstrip(chars)` has it.
    static String rstrip(final String text, final String chars) {
        int end = text.length();
        while (end > 0 && chars.indexOf(text.charAt(end - 1)) >= 0) {
            end--;
        }
        return text.substring(0, end);
    }

    /// @param text  A text.
    /// @param chars The characters to take off both ends.
    /// @return It without them at either end, as Python's `str.strip(chars)` has it.
    static String strip(final String text, final String chars) {
        int start = 0;
        int end = text.length();
        while (start < end && chars.indexOf(text.charAt(start)) >= 0) {
            start++;
        }
        while (end > start && chars.indexOf(text.charAt(end - 1)) >= 0) {
            end--;
        }
        return text.substring(start, end);
    }

    /// @param ch A character.
    /// @return True if Python's `str.isspace` holds for it.
    static boolean isSpace(final char ch) {
        return Character.isWhitespace(ch) || Character.isSpaceChar(ch) || (ch >= 0x1c && ch <= 0x1f)
               || ch == 0x85;
    }

    /// @param text A text.
    /// @param from Where to start.
    /// @return The index of its first character from there that isn't a space (`str.isspace`), or its length.
    static int skipSpace(final String text, final int from) {
        int at = from;
        while (at < text.length() && isSpace(text.charAt(at))) {
            at++;
        }
        return at;
    }
}
