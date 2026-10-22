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

package stroom.ai.impl.tools.sample;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/// JSON read and written as Python's `json` module does, which the sample analysis ported from the Stroom MCP
/// server relies on: which texts parse (a `NaN`, a big integer, a byte order mark refused), the messages a text that
/// doesn't parse gives ("Invalid \escape", "Expecting value"), reading one value from a place in a text
/// (`raw_decode`), and how a value is written back (`json.dumps`: `{"a": 1, "b": [true, null]}`, non-ASCII escaped).
///
/// Values are read as [Map] (in key order), [List], [String], [Long] or [BigInteger], [Double], [Boolean] and
/// null.
public final class PyJson {

    private PyJson() {
        // Static methods only.
    }

    /// Reads a JSON text as `json.loads` does.
    ///
    /// @param text The text.
    /// @return Its value.
    /// @throws JsonDecodeException if it isn't one JSON value, with Python's message.
    public static Object loads(final String text) {
        if (text.startsWith("﻿")) {
            throw new JsonDecodeException("Unexpected UTF-8 BOM (decode using utf-8-sig)", text, 0);
        }
        final Decoder decoder = new Decoder(text);
        final Decoded decoded = decoder.rawDecode(decoder.skipWhitespace(0));
        final int end = decoder.skipWhitespace(decoded.end());
        if (end != text.length()) {
            throw new JsonDecodeException("Extra data", text, end);
        }
        return decoded.value();
    }

    /// Reads one JSON value starting exactly at `at`, as `json.JSONDecoder().raw_decode(text, at)` does.
    ///
    /// @param text The text.
    /// @param at   Where the value starts.
    /// @return The value and where it ends.
    /// @throws JsonDecodeException if no JSON value starts there.
    public static Decoded rawDecode(final String text, final int at) {
        return new Decoder(text).rawDecode(at);
    }

    /// @param value A value read by [#loads(String)], or made of the same types.
    /// @return It written as `json.dumps(value)` writes it.
    public static String dumps(final Object value) {
        final StringBuilder out = new StringBuilder();
        write(out, value, -1, 0);
        return out.toString();
    }

    /// @param value  A value read by [#loads(String)], or made of the same types.
    /// @param indent The spaces each level is indented by.
    /// @return It written as `json.dumps(value, indent=indent)` writes it.
    public static String dumps(final Object value, final int indent) {
        final StringBuilder out = new StringBuilder();
        write(out, value, indent, 0);
        return out.toString();
    }

    /// @param value A value made of maps (with string keys), collections, strings, numbers, booleans or null.
    /// @return It written as `json.dumps(value, ensure_ascii=False, separators=(',', ':'))` writes it: compact, and
    /// characters past ASCII as they are, as the Stroom MCP server writes the JSON it keeps in a document's
    /// description.
    public static String dumpsCompact(final Object value) {
        final StringBuilder out = new StringBuilder();
        writeCompact(out, value);
        return out.toString();
    }

    /// @param value A value made of maps (with string keys), collections, strings, numbers, booleans or null.
    /// @return It written as `json.dumps(value, sort_keys=True)` writes it, as the Stroom MCP server writes the
    /// JSON whose text matters (a build's record, and digests of JSON, which must come out the same from both).
    public static String dumpsSortedKeys(final Object value) {
        return dumps(sortedKeys(value));
    }

    /// @param value  A value made of maps (with string keys), collections, strings, numbers, booleans or null.
    /// @param indent The spaces each level is indented by.
    /// @return It written as `json.dumps(value, indent=indent, sort_keys=True)` writes it.
    public static String dumpsSortedKeys(final Object value, final int indent) {
        return dumps(sortedKeys(value), indent);
    }

    // The value with every map's keys in order. Python sorts by code point, Java's String order by UTF-16 unit,
    // which differ only past the Basic Multilingual Plane.
    private static Object sortedKeys(final Object value) {
        if (value instanceof final Map<?, ?> map) {
            final Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, item) -> sorted.put(String.valueOf(key), sortedKeys(item)));
            return sorted;
        }
        if (value instanceof final Collection<?> collection) {
            final List<Object> items = new ArrayList<>();
            collection.forEach(item -> items.add(sortedKeys(item)));
            return items;
        }
        if (value instanceof final Object[] array) {
            return sortedKeys(Arrays.asList(array));
        }
        return value;
    }

    private static void write(final StringBuilder out, final Object value, final int indent, final int level) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof final Boolean bool) {
            out.append(bool
                    ? "true"
                    : "false");
        } else if (value instanceof final String text) {
            writeString(out, text);
        } else if (value instanceof final Double number) {
            out.append(floatText(number));
        } else if (value instanceof final Float number) {
            out.append(floatText(number.doubleValue()));
        } else if (value instanceof Number) {
            out.append(value);
        } else if (value instanceof final Map<?, ?> map) {
            writeMap(out, map, indent, level);
        } else if (value instanceof final Collection<?> list) {
            writeList(out, list, indent, level);
        } else {
            writeString(out, String.valueOf(value));
        }
    }

    private static void writeCompact(final StringBuilder out, final Object value) {
        if (value instanceof final Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (final Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeString(out, String.valueOf(entry.getKey()), false);
                out.append(':');
                writeCompact(out, entry.getValue());
            }
            out.append('}');
        } else if (value instanceof final Collection<?> list) {
            out.append('[');
            boolean first = true;
            for (final Object item : list) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeCompact(out, item);
            }
            out.append(']');
        } else if (value instanceof final String text) {
            writeString(out, text, false);
        } else if (value == null || value instanceof Boolean || value instanceof Number) {
            write(out, value, -1, 0);
        } else {
            writeString(out, String.valueOf(value), false);
        }
    }

    private static void writeMap(final StringBuilder out, final Map<?, ?> map, final int indent, final int level) {
        if (map.isEmpty()) {
            out.append("{}");
            return;
        }
        out.append('{');
        boolean first = true;
        for (final Map.Entry<?, ?> entry : map.entrySet()) {
            separate(out, first, indent, level + 1);
            first = false;
            writeString(out, String.valueOf(entry.getKey()));
            out.append(": ");
            write(out, entry.getValue(), indent, level + 1);
        }
        close(out, indent, level);
        out.append('}');
    }

    private static void writeList(final StringBuilder out, final Collection<?> list, final int indent,
                                  final int level) {
        if (list.isEmpty()) {
            out.append("[]");
            return;
        }
        out.append('[');
        boolean first = true;
        for (final Object item : list) {
            separate(out, first, indent, level + 1);
            first = false;
            write(out, item, indent, level + 1);
        }
        close(out, indent, level);
        out.append(']');
    }

    private static void separate(final StringBuilder out, final boolean first, final int indent, final int level) {
        if (indent < 0) {
            if (!first) {
                out.append(", ");
            }
            return;
        }
        if (!first) {
            out.append(',');
        }
        out.append('\n').append(" ".repeat(indent * level));
    }

    private static void close(final StringBuilder out, final int indent, final int level) {
        if (indent >= 0) {
            out.append('\n').append(" ".repeat(indent * level));
        }
    }

    private static String floatText(final double value) {
        if (Double.isNaN(value)) {
            return "NaN";
        }
        if (Double.isInfinite(value)) {
            return value > 0
                    ? "Infinity"
                    : "-Infinity";
        }
        return PyText.floatRepr(value);
    }

    private static void writeString(final StringBuilder out, final String text) {
        writeString(out, text, true);
    }

    // As json.dumps writes a string: with ensureAscii, every character past ASCII escaped too.
    private static void writeString(final StringBuilder out, final String text, final boolean ensureAscii) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < ' ' || (ensureAscii && c > '~')) {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }


    // --------------------------------------------------------------------------------


    /// A value read from a text, and where it ends.
    ///
    /// @param value The value.
    /// @param end   The index after its last character.
    public record Decoded(Object value, int end) {

    }

    /// A text that isn't JSON, said as Python's `json.JSONDecodeError` says it.
    public static final class JsonDecodeException extends RuntimeException {

        private final String msg;
        private final int pos;

        /// @param msg  What is wrong, e.g. `Expecting value`.
        /// @param text The text read.
        /// @param pos  The index in it where it went wrong.
        JsonDecodeException(final String msg, final String text, final int pos) {
            super(message(msg, text, pos));
            this.msg = msg;
            this.pos = pos;
        }

        private static String message(final String msg, final String text, final int pos) {
            final int at = Math.min(Math.max(0, pos), text.length());
            int line = 1;
            for (int i = 0; i < at; i++) {
                if (text.charAt(i) == '\n') {
                    line++;
                }
            }
            final int column = at - text.lastIndexOf('\n', at - 1);
            return msg + ": line " + line + " column " + column + " (char " + pos + ")";
        }

        /// @return What is wrong, without where, e.g. `Expecting value` (Python's `e.msg`).
        public String msg() {
            return msg;
        }

        /// @return The index in the text where it went wrong (Python's `e.pos`).
        public int pos() {
            return pos;
        }
    }

    private static final class Decoder {

        private final String s;
        private final int length;

        Decoder(final String s) {
            this.s = s;
            this.length = s.length();
        }

        int skipWhitespace(final int from) {
            int i = from;
            while (i < length && " \t\n\r".indexOf(s.charAt(i)) >= 0) {
                i++;
            }
            return i;
        }

        Decoded rawDecode(final int at) {
            final Decoded decoded = scanOnce(at);
            if (decoded == null) {
                throw new JsonDecodeException("Expecting value", s, at);
            }
            return decoded;
        }

        // A value at idx, or null when none starts there (Python's StopIteration).
        private Decoded scanOnce(final int idx) {
            if (idx >= length) {
                return null;
            }
            final char c = s.charAt(idx);
            return switch (c) {
                case '"' -> scanString(idx + 1);
                case '{' -> parseObject(idx + 1);
                case '[' -> parseArray(idx + 1);
                case 'n' -> constant(idx, "null", null);
                case 't' -> constant(idx, "true", Boolean.TRUE);
                case 'f' -> constant(idx, "false", Boolean.FALSE);
                case 'N' -> constant(idx, "NaN", Double.NaN);
                case 'I' -> constant(idx, "Infinity", Double.POSITIVE_INFINITY);
                case '-' -> s.startsWith("-Infinity", idx)
                        ? new Decoded(Double.NEGATIVE_INFINITY, idx + 9)
                        : number(idx);
                default -> number(idx);
            };
        }

        private Decoded constant(final int idx, final String word, final Object value) {
            return s.startsWith(word, idx)
                    ? new Decoded(value, idx + word.length())
                    : null;
        }

        private Decoded number(final int start) {
            int idx = start;
            if (idx < length && s.charAt(idx) == '-') {
                idx++;
            }
            if (idx < length && s.charAt(idx) >= '1' && s.charAt(idx) <= '9') {
                idx++;
                while (idx < length && isDigit(s.charAt(idx))) {
                    idx++;
                }
            } else if (idx < length && s.charAt(idx) == '0') {
                idx++;
            } else {
                return null;
            }
            boolean isFloat = false;
            if (idx + 1 < length && s.charAt(idx) == '.' && isDigit(s.charAt(idx + 1))) {
                isFloat = true;
                idx += 2;
                while (idx < length && isDigit(s.charAt(idx))) {
                    idx++;
                }
            }
            if (idx < length && (s.charAt(idx) == 'e' || s.charAt(idx) == 'E')) {
                final int exponentStart = idx;
                idx++;
                if (idx < length && (s.charAt(idx) == '+' || s.charAt(idx) == '-')) {
                    idx++;
                }
                final int digitsStart = idx;
                while (idx < length && isDigit(s.charAt(idx))) {
                    idx++;
                }
                if (idx > digitsStart) {
                    isFloat = true;
                } else {
                    idx = exponentStart;
                }
            }
            final String text = s.substring(start, idx);
            if (isFloat) {
                return new Decoded(Double.parseDouble(text), idx);
            }
            final BigInteger big = new BigInteger(text);
            return new Decoded(big.bitLength() < 64
                    ? (Object) big.longValue()
                    : big, idx);
        }

        private static boolean isDigit(final char c) {
            return c >= '0' && c <= '9';
        }

        private Decoded scanString(final int afterQuote) {
            final int begin = afterQuote - 1;
            final StringBuilder out = new StringBuilder();
            int end = afterQuote;
            while (true) {
                int next = end;
                char c = 0;
                while (next < length) {
                    c = s.charAt(next);
                    if (c == '"' || c == '\\') {
                        break;
                    }
                    if (c <= 0x1F) {
                        throw new JsonDecodeException("Invalid control character at", s, next);
                    }
                    next++;
                }
                if (next >= length) {
                    throw new JsonDecodeException("Unterminated string starting at", s, begin);
                }
                out.append(s, end, next);
                next++;
                if (c == '"') {
                    return new Decoded(out.toString(), next);
                }
                if (next >= length) {
                    throw new JsonDecodeException("Unterminated string starting at", s, begin);
                }
                final char escape = s.charAt(next);
                if (escape != 'u') {
                    end = next + 1;
                    final char unescaped = switch (escape) {
                        case '"' -> '"';
                        case '\\' -> '\\';
                        case '/' -> '/';
                        case 'b' -> '\b';
                        case 'f' -> '\f';
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        default -> throw new JsonDecodeException("Invalid \\escape", s, end - 2);
                    };
                    out.append(unescaped);
                    continue;
                }
                next++;
                end = next + 4;
                char unit = hex4(next);
                if (Character.isHighSurrogate(unit) && end + 6 <= length && s.charAt(end) == '\\'
                    && s.charAt(end + 1) == 'u') {
                    final char low = hex4(end + 2);
                    if (Character.isLowSurrogate(low)) {
                        out.append(unit);
                        unit = low;
                        end += 6;
                    }
                }
                out.append(unit);
            }
        }

        private char hex4(final int at) {
            if (at + 4 > length) {
                throw new JsonDecodeException("Invalid \\uXXXX escape", s, at - 1);
            }
            int value = 0;
            for (int i = at; i < at + 4; i++) {
                final int digit = Character.digit(s.charAt(i), 16);
                if (digit < 0 || s.charAt(i) > 'f') {
                    throw new JsonDecodeException("Invalid \\uXXXX escape", s, at - 1);
                }
                value = value * 16 + digit;
            }
            return (char) value;
        }

        private Decoded parseObject(final int start) {
            final Map<String, Object> map = new LinkedHashMap<>();
            int idx = skipWhitespace(start);
            if (idx < length && s.charAt(idx) == '}') {
                return new Decoded(map, idx + 1);
            }
            while (true) {
                if (idx >= length || s.charAt(idx) != '"') {
                    throw new JsonDecodeException("Expecting property name enclosed in double quotes", s, idx);
                }
                final Decoded key = scanString(idx + 1);
                idx = skipWhitespace(key.end());
                if (idx >= length || s.charAt(idx) != ':') {
                    throw new JsonDecodeException("Expecting ':' delimiter", s, idx);
                }
                idx = skipWhitespace(idx + 1);
                final Decoded value = scanOnce(idx);
                if (value == null) {
                    throw new JsonDecodeException("Expecting value", s, idx);
                }
                map.put((String) key.value(), value.value());
                idx = skipWhitespace(value.end());
                if (idx < length && s.charAt(idx) == '}') {
                    return new Decoded(map, idx + 1);
                }
                if (idx >= length || s.charAt(idx) != ',') {
                    throw new JsonDecodeException("Expecting ',' delimiter", s, idx);
                }
                idx = skipWhitespace(idx + 1);
            }
        }

        private Decoded parseArray(final int start) {
            final List<Object> list = new ArrayList<>();
            int idx = skipWhitespace(start);
            if (idx < length && s.charAt(idx) == ']') {
                return new Decoded(list, idx + 1);
            }
            while (true) {
                final Decoded value = scanOnce(idx);
                if (value == null) {
                    throw new JsonDecodeException("Expecting value", s, idx);
                }
                list.add(value.value());
                idx = skipWhitespace(value.end());
                if (idx < length && s.charAt(idx) == ']') {
                    return new Decoded(list, idx + 1);
                }
                if (idx >= length || s.charAt(idx) != ',') {
                    throw new JsonDecodeException("Expecting ',' delimiter", s, idx);
                }
                idx = skipWhitespace(idx + 1);
            }
        }
    }
}
