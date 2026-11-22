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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/// The layout of a JSON object saved to the workspace as too large to show, said with where it is saved, so that the
/// agent's jq queries find its parts. Seen in chats 184-186 (Haiku): a kept mapping saved with the mapping under
/// `.mapping`, queried some 15 times as if at the top level (`.events[]`, `.common[]`), as the draft file holds it,
/// eight of them failing or empty, before the agent searched the whole file for an object with `events`.
final class SavedJsonLayout {

    // The most keys said of an object, and of a list's items.
    private static final int MOST_KEYS = 12;
    private static final Pattern PLAIN_KEY = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private SavedJsonLayout() {
        // Static only.
    }

    /// @param saved The object saved, as JSON-like values (maps, lists, texts, numbers).
    /// @return Its layout in a sentence or two: its top-level keys; for each that is an object, the parts it holds by
    /// path; for each list, its size and its items' keys; and a jq query that works on it, e.g.
    /// `.mapping.events[] | {name, fields: [.fields[] | .path]}`.
    static String describe(final Map<String, Object> saved) {
        if (saved.isEmpty()) {
            return "It is an empty JSON object.";
        }
        final StringBuilder sb = new StringBuilder("Its top-level keys: ")
                .append(capped(List.copyOf(saved.keySet()))).append('.');
        for (final Map.Entry<String, Object> entry : saved.entrySet()) {
            if (entry.getValue() instanceof final Map<?, ?> inner && !inner.isEmpty()) {
                final String path = "." + jqKey(entry.getKey());
                final List<String> parts = new ArrayList<>();
                inner.forEach((key, value) -> parts.add(said(path + "." + jqKey(String.valueOf(key)), value)));
                sb.append(' ').append(path).append(" holds ").append(capped(parts)).append('.');
            }
        }
        final List<String> lists = new ArrayList<>();
        saved.forEach((key, value) -> {
            if (value instanceof List<?>) {
                lists.add(said("." + jqKey(key), value));
            }
        });
        if (!lists.isEmpty()) {
            sb.append(" Lists: ").append(capped(lists)).append('.');
        }
        return sb.append(" A query that works on it: `").append(example(saved)).append("`.").toString();
    }

    // A part, by its path: a list as `path[]` with its size and its items' keys; anything else by its path alone.
    private static String said(final String path, final Object value) {
        if (!(value instanceof final List<?> list)) {
            return path;
        }
        final Set<String> keys = itemKeys(list);
        return path + "[] (" + list.size() + (list.size() == 1
                ? " item"
                : " items") + (keys.isEmpty()
                ? ""
                : ", each {" + capped(List.copyOf(keys)) + "}") + ")";
    }

    // The keys of a list's object items, in the order first seen.
    private static Set<String> itemKeys(final List<?> list) {
        final Set<String> keys = new LinkedHashSet<>();
        for (final Object item : list) {
            if (item instanceof final Map<?, ?> map) {
                map.keySet().forEach(key -> keys.add(String.valueOf(key)));
            }
        }
        return keys;
    }

    // A query that works on the object: over the first list of objects whose items hold a list of objects of their
    // own, each item's first text and, of its largest such list, the nested items' first text (`.mapping.events[] |
    // {name, fields: [.fields[] | .path]}`); else over the first list of objects, its items' first two keys; else
    // its keys.
    private static String example(final Map<String, Object> saved) {
        final List<Map.Entry<String, List<?>>> lists = listsOfObjects(saved);
        for (final Map.Entry<String, List<?>> list : lists) {
            final Map<?, ?> item = (Map<?, ?>) list.getValue().getFirst();
            final String label = firstTextKey(item);
            final Map.Entry<?, ?> nested = largestListOfObjects(item);
            if (label != null && nested != null) {
                final String nestedKey = jqKey(String.valueOf(nested.getKey()));
                final String nestedLabel = firstTextKey((Map<?, ?>) ((List<?>) nested.getValue()).getFirst());
                return list.getKey() + "[] | {" + picked(label) + ", " + nestedKey + ": [." + nestedKey + "[]"
                       + (nestedLabel == null
                        ? ""
                        : " | ." + jqKey(nestedLabel)) + "]}";
            }
        }
        if (!lists.isEmpty()) {
            final Map.Entry<String, List<?>> list = lists.getFirst();
            final List<String> keys = List.copyOf(itemKeys(list.getValue()));
            return list.getKey() + "[] | {" + String.join(", ", keys.subList(0, Math.min(2, keys.size())).stream()
                    .map(SavedJsonLayout::picked)
                    .toList()) + "}";
        }
        return "keys";
    }

    // The lists of objects at the top level and one level in, by their jq paths, in order.
    private static List<Map.Entry<String, List<?>>> listsOfObjects(final Map<String, Object> saved) {
        final List<Map.Entry<String, List<?>>> found = new ArrayList<>();
        saved.forEach((key, value) -> {
            final String path = "." + jqKey(key);
            if (isListOfObjects(value)) {
                found.add(Map.entry(path, (List<?>) value));
            } else if (value instanceof final Map<?, ?> inner) {
                inner.forEach((innerKey, innerValue) -> {
                    if (isListOfObjects(innerValue)) {
                        found.add(Map.entry(path + "." + jqKey(String.valueOf(innerKey)), (List<?>) innerValue));
                    }
                });
            }
        });
        return found;
    }

    // An object's list of objects with the most items (its fields rather than its conditions), or null if none.
    private static Map.Entry<?, ?> largestListOfObjects(final Map<?, ?> item) {
        Map.Entry<?, ?> largest = null;
        for (final Map.Entry<?, ?> field : item.entrySet()) {
            if (isListOfObjects(field.getValue())
                && (largest == null || ((List<?>) field.getValue()).size() > ((List<?>) largest.getValue()).size())) {
                largest = field;
            }
        }
        return largest;
    }

    private static boolean isListOfObjects(final Object value) {
        return value instanceof final List<?> list && !list.isEmpty() && list.getFirst() instanceof Map;
    }

    // The first key whose value is a text, or null if none is.
    private static String firstTextKey(final Map<?, ?> map) {
        for (final Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getValue() instanceof String) {
                return String.valueOf(entry.getKey());
            }
        }
        return null;
    }

    // A key as jq takes it after a dot: as it is when plain, else quoted.
    private static String jqKey(final String key) {
        return PLAIN_KEY.matcher(key).matches()
                ? key
                : "\"" + key.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    // A key picked into a jq object: `name` when plain, else `"type id": ."type id"`.
    private static String picked(final String key) {
        final String quoted = jqKey(key);
        return quoted.equals(key)
                ? key
                : quoted + ": ." + quoted;
    }

    // The first so many items joined, then how many more.
    private static String capped(final List<String> items) {
        return String.join(", ", items.subList(0, Math.min(MOST_KEYS, items.size())))
               + (items.size() > MOST_KEYS
                ? " and " + (items.size() - MOST_KEYS) + " more"
                : "");
    }
}
