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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/// JSON written exactly as the Stroom MCP server's Python `json.dumps` writes it (`sort_keys`, `ensure_ascii`, and
/// with or without `indent`), where the text itself matters: a build's record is kept as such JSON in a
/// Documentation doc that either server may write, and digests of JSON (a step's code fingerprint) must come out
/// the same from both.
final class PythonJson {

    // Stands in for a null value in a map's entries, which Map.entry doesn't take.
    private static final Object NULL = new Object();

    private PythonJson() {
        // Static methods only.
    }

    /// @param value A JSON-able value: maps (with string keys), collections, strings, numbers, booleans or null.
    /// @return It as `json.dumps(value, sort_keys=True)` writes it: on one line, `", "` between items and `": "`
    /// after keys.
    static String dumps(final Object value) {
        final StringBuilder sb = new StringBuilder();
        write(sb, value, -1, 0);
        return sb.toString();
    }

    /// @param value  A JSON-able value.
    /// @param indent The spaces each level is indented by.
    /// @return It as `json.dumps(value, indent=indent, sort_keys=True)` writes it: an item a line, `","` after
    /// each but the last and `": "` after keys; an empty map or list as `{}` or `[]`.
    static String dumps(final Object value, final int indent) {
        final StringBuilder sb = new StringBuilder();
        write(sb, value, Math.max(0, indent), 0);
        return sb.toString();
    }

    private static void write(final StringBuilder sb, final Object value, final int indent, final int depth) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof final Boolean bool) {
            sb.append(bool
                    ? "true"
                    : "false");
        } else if (value instanceof final Number number) {
            sb.append(number);
        } else if (value instanceof final Map<?, ?> map) {
            writeMap(sb, map, indent, depth);
        } else if (value instanceof final Collection<?> collection) {
            writeList(sb, new ArrayList<>(collection), indent, depth);
        } else if (value instanceof final Object[] array) {
            writeList(sb, List.of(array), indent, depth);
        } else {
            writeString(sb, String.valueOf(value));
        }
    }

    private static void writeMap(final StringBuilder sb, final Map<?, ?> map, final int indent, final int depth) {
        if (map.isEmpty()) {
            sb.append("{}");
            return;
        }
        final List<Entry<String, Object>> entries = new ArrayList<>();
        map.forEach((key, item) -> entries.add(Map.entry(String.valueOf(key), item == null
                ? NULL
                : item)));
        // Python sorts by code point; Java's String order is by UTF-16 unit, which differs only past the BMP.
        entries.sort(Entry.comparingByKey());
        sb.append('{');
        for (int i = 0; i < entries.size(); i++) {
            separate(sb, i, indent, depth + 1);
            writeString(sb, entries.get(i).getKey());
            sb.append(": ");
            final Object item = entries.get(i).getValue();
            write(sb, item == NULL
                    ? null
                    : item, indent, depth + 1);
        }
        close(sb, indent, depth);
        sb.append('}');
    }

    private static void writeList(final StringBuilder sb, final List<?> list, final int indent, final int depth) {
        if (list.isEmpty()) {
            sb.append("[]");
            return;
        }
        sb.append('[');
        for (int i = 0; i < list.size(); i++) {
            separate(sb, i, indent, depth + 1);
            write(sb, list.get(i), indent, depth + 1);
        }
        close(sb, indent, depth);
        sb.append(']');
    }

    private static void separate(final StringBuilder sb, final int index, final int indent, final int depth) {
        if (indent < 0) {
            if (index > 0) {
                sb.append(", ");
            }
            return;
        }
        if (index > 0) {
            sb.append(',');
        }
        sb.append('\n').append(" ".repeat(indent * depth));
    }

    private static void close(final StringBuilder sb, final int indent, final int depth) {
        if (indent >= 0) {
            sb.append('\n').append(" ".repeat(indent * depth));
        }
    }

    private static void writeString(final StringBuilder sb, final String text) {
        sb.append('"');
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    // ensure_ascii: anything but printable ASCII as \\uXXXX (past the BMP, as its surrogates).
                    if (c < ' ' || c > '~') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
