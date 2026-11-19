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

package stroom.gwt.workbench.framework.client.args;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;

/// Converts args to and from the form React Storybook uses in the `args` URL parameter, e.g.
/// `loading:!false;variant:contained-primary;tags[0]:a;tags[1]:b`, following Storybook's
/// `buildArgsParam` and `parseArgsParam`.
///
/// As in Storybook:
/// * Booleans and null are written as `!true`, `!false` and `!null`, and `!undefined` is read.
/// * Hex colours are written as `!hex(rrggbb)` and `rgb(...)`/`hsl(...)` colours without spaces
///   or `%`, e.g. `!rgba(255,0,0,0.5)`.
/// * Dates (held as ISO 8601 strings) are written as `!date(...)`.
/// * Spaces are written as `+`.
/// * Potentially unsafe args are left out, i.e. those whose name isn't letters, digits, spaces,
///   `_` or `-`, or whose value isn't one of those, a number, a colour or a date (see
///   [ArgPatterns]).
///
/// Unlike Storybook, numbers are decoded as strings (the arg's [ArgType] converts them), and
/// `!null` and `!undefined` both decode as null, as [Args] doesn't distinguish them; either
/// unsets the arg when merged into the story's initial args.
///
/// The format and its rules are derived from Storybook, Copyright (c) 2024 Storybook, MIT
/// licence (see NOTICE.md).
public final class ArgsCodec {

    private static final char PAIR_SEPARATOR = ';';
    private static final char NAME_SEPARATOR = ':';
    private static final String TRUE = "!true";
    private static final String FALSE = "!false";
    private static final String NULL = "!null";
    private static final String UNDEFINED_VALUE = "!undefined";
    private static final String HEX_PREFIX = "!hex(";
    private static final String DATE_PREFIX = "!date(";
    // Characters that encodeURIComponent doesn't escape, plus those Storybook unescapes again
    private static final String UNESCAPED = "-_.!~*'()[],:";
    // The JavaScript 'undefined' within a decoded value, held separately from null while decoding
    private static final Object UNDEFINED = new Object();
    // Longer indices are ignored rather than overflowing an int
    private static final int MAX_INDEX_DIGITS = 9;

    private ArgsCodec() {
        // Static utility
    }

    /// @param args The args to encode.
    /// @return The encoded args, or an empty string if there are none. Unsafe args, and numbers
    /// that are NaN or infinite, are left out.
    public static String encode(final Args args) {
        final StringBuilder sb = new StringBuilder();
        for (final Entry<String, Object> entry : args.asMap().entrySet()) {
            final String name = entry.getKey();
            if (!ArgPatterns.isSafeKey(name)) {
                continue;
            }
            final Object value = entry.getValue();
            if (value instanceof List) {
                appendList(sb, name, (List<?>) value);
            } else {
                final String encoded = encodeValue(value);
                if (encoded != null) {
                    appendPair(sb, escape(name), encoded);
                }
            }
        }
        return sb.toString();
    }

    /// Appends a list as `name[0]:a;name[1]:b`, unless any item is unsafe.
    private static void appendList(final StringBuilder sb, final String name, final List<?> list) {
        final List<String> encodedItems = new ArrayList<>(list.size());
        for (final Object item : list) {
            final String encoded = encodeValue(item);
            if (encoded == null) {
                return;
            }
            encodedItems.add(encoded);
        }
        for (int i = 0; i < encodedItems.size(); i++) {
            appendPair(sb, escape(name) + "[" + i + "]", encodedItems.get(i));
        }
    }

    private static void appendPair(final StringBuilder sb, final String name, final String value) {
        if (sb.length() > 0) {
            sb.append(PAIR_SEPARATOR);
        }
        sb.append(name).append(NAME_SEPARATOR).append(value);
    }

    /// @return The encoded value, or null if it is unsafe or can't be encoded.
    private static String encodeValue(final Object value) {
        if (value == null) {
            return NULL;
        }
        if (value instanceof Boolean) {
            return (Boolean) value
                    ? TRUE
                    : FALSE;
        }
        if (value instanceof Double) {
            final String number = Args.formatNumber((Double) value);
            return number.isEmpty()
                    ? null
                    : number;
        }
        return encodeString(String.valueOf(value));
    }

    private static String encodeString(final String value) {
        if (ArgPatterns.isHexColour(value)) {
            return HEX_PREFIX + value.substring(1) + ")";
        }
        if (ArgPatterns.matchColour(value) != null) {
            return "!" + removeWhitespaceAndPercent(value);
        }
        if (ArgPatterns.isIsoDate(value)) {
            return DATE_PREFIX + value + ")";
        }
        if (ArgPatterns.isPlain(value) || ArgPatterns.isNumber(value)) {
            return escape(value);
        }
        return null;
    }

    private static String removeWhitespaceAndPercent(final String value) {
        final StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            final char chr = value.charAt(i);
            if (chr != '%' && !ArgPatterns.isJsWhitespace(chr)) {
                sb.append(chr);
            }
        }
        return sb.toString();
    }

    /// @param encoded Args encoded by [#encode(Args)] or React Storybook, may be null.
    /// @return The args. Values are strings, booleans, lists of strings or null; use
    /// [ArgType#convert(Object)] to get the arg's type. Unsafe args are left out, as are pairs
    /// that use object notation (`a.b` or `a[b]`), which args here can't hold.
    public static Args decode(final String encoded) {
        final Map<String, Object> raw = new LinkedHashMap<>();
        if (encoded != null) {
            for (final String pair : split(encoded)) {
                decodePair(pair, raw);
            }
        }

        final Map<String, Object> safe = new LinkedHashMap<>();
        for (final Entry<String, Object> entry : raw.entrySet()) {
            final Object value = entry.getValue() instanceof IndexedList
                    ? ((IndexedList) entry.getValue()).toList()
                    : entry.getValue();
            if (ArgPatterns.isSafeKey(entry.getKey()) && isSafeValue(value)) {
                safe.put(entry.getKey(), toArgValue(value));
            }
        }
        return Args.fromMap(safe);
    }

    /// Adds one `name:value` pair to the args being decoded, with the same results as the
    /// `picoquery` library Storybook uses: `name[n]` puts an item at index `n` of a list and
    /// `name[]` appends one; a plain `name` sets a value, unless the name already has one, in
    /// which case both are put in a list (or the value is appended to an existing list).
    private static void decodePair(final String pair, final Map<String, Object> raw) {
        final int separator = pair.indexOf(NAME_SEPARATOR);
        // As in Storybook, a name without a value has an empty value
        final String rawName = separator >= 0
                ? pair.substring(0, separator)
                : pair;
        final String rawValue = separator >= 0
                ? pair.substring(separator + 1)
                : "";
        // Brackets and dots are only special before unescaping, so `a%5B0%5D` is the name `a[0]`
        if (rawName.indexOf('.') >= 0) {
            return;
        }
        final int bracket = rawName.indexOf('[');
        final String name = unescape(bracket >= 0
                ? rawName.substring(0, bracket)
                : rawName);
        final Object value = decodeValue(unescape(rawValue));
        if (bracket < 0) {
            addValue(raw, name, value);
            return;
        }
        final String indexText = rawName.substring(bracket + 1);
        if ("]".equals(indexText)) {
            addValue(raw, name, value);
            final Object existing = raw.get(name);
            if (!(existing instanceof IndexedList)) {
                // `name[]` always gives a list
                final IndexedList list = new IndexedList();
                list.add(existing);
                raw.put(name, list);
            }
            return;
        }
        final int index = parseIndex(indexText);
        if (index < 0) {
            return;
        }
        final Object existing = raw.get(name);
        final IndexedList list;
        if (existing instanceof IndexedList) {
            list = (IndexedList) existing;
        } else {
            // As in Storybook, an index replaces a plain value
            list = new IndexedList();
            raw.put(name, list);
        }
        list.set(index, value);
    }

    /// Sets a value, or adds it to a list if the name already has a value.
    private static void addValue(final Map<String, Object> raw, final String name, final Object value) {
        final Object existing = raw.get(name);
        if (existing == UNDEFINED || !raw.containsKey(name)) {
            raw.put(name, value);
        } else if (existing instanceof IndexedList) {
            ((IndexedList) existing).add(value);
        } else {
            final IndexedList list = new IndexedList();
            list.add(existing);
            list.add(value);
            raw.put(name, list);
        }
    }

    /// @param text The text after `[`, e.g. `12]`.
    /// @return The index, or -1 if the text isn't digits followed by `]`.
    private static int parseIndex(final String text) {
        final int digits = text.length() - 1;
        if (digits < 1 || digits > MAX_INDEX_DIGITS || text.charAt(digits) != ']') {
            return -1;
        }
        int index = 0;
        for (int i = 0; i < digits; i++) {
            final char chr = text.charAt(i);
            if (!ArgPatterns.isAsciiDigit(chr)) {
                return -1;
            }
            index = index * 10 + (chr - '0');
        }
        return index;
    }

    private static List<String> split(final String encoded) {
        final List<String> parts = new ArrayList<>();
        int start = 0;
        for (int i = 0; i <= encoded.length(); i++) {
            if (i == encoded.length() || encoded.charAt(i) == PAIR_SEPARATOR) {
                if (i > start) {
                    parts.add(encoded.substring(start, i));
                }
                start = i + 1;
            }
        }
        return parts;
    }

    /// Storybook's `valueDeserializer`, apart from numbers, which are left as strings.
    private static Object decodeValue(final String value) {
        if (!value.startsWith("!")) {
            return value;
        }
        switch (value) {
            case UNDEFINED_VALUE:
                return UNDEFINED;
            case NULL:
                return null;
            case TRUE:
                return true;
            case FALSE:
                return false;
            default:
                break;
        }
        if (value.startsWith(DATE_PREFIX) && value.endsWith(")")) {
            // A '+' in the date will have been decoded as a space
            return value.substring(DATE_PREFIX.length(), value.length() - 1).replace(' ', '+');
        }
        if (value.startsWith(HEX_PREFIX) && value.endsWith(")")) {
            return "#" + value.substring(HEX_PREFIX.length(), value.length() - 1);
        }
        final String[] colour = ArgPatterns.matchColour(value.substring(1));
        if (colour != null) {
            return formatColour(value, colour);
        }
        return value;
    }

    /// Formats a colour as Storybook does, which depends on the case of the function name.
    private static String formatColour(final String value, final String[] groups) {
        final String name = groups[0];
        final String alpha = groups[4] != null
                ? groups[4]
                : "undefined";
        if (value.startsWith("!rgba") || value.startsWith("!RGBA")) {
            return name + "(" + groups[1] + ", " + groups[2] + ", " + groups[3] + ", " + alpha + ")";
        } else if (value.startsWith("!hsla") || value.startsWith("!HSLA")) {
            return name + "(" + groups[1] + ", " + groups[2] + "%, " + groups[3] + "%, " + alpha + ")";
        } else if (value.startsWith("!rgb") || value.startsWith("!RGB")) {
            return name + "(" + groups[1] + ", " + groups[2] + ", " + groups[3] + ")";
        }
        return name + "(" + groups[1] + ", " + groups[2] + "%, " + groups[3] + "%)";
    }

    /// Storybook's `validateArgs` for a decoded value.
    private static boolean isSafeValue(final Object value) {
        if (value == null || value == UNDEFINED || value instanceof Boolean) {
            return true;
        }
        if (value instanceof String) {
            return ArgPatterns.isSafeString((String) value);
        }
        if (value instanceof List) {
            for (final Object item : (List<?>) value) {
                if (!isSafeValue(item)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /// Converts a decoded value to an arg value: undefined becomes null and lists hold strings,
    /// without any null or undefined items (Storybook removes undefined items when it combines
    /// args).
    private static Object toArgValue(final Object value) {
        if (value == UNDEFINED) {
            return null;
        }
        if (value instanceof List) {
            final List<String> items = new ArrayList<>();
            for (final Object item : (List<?>) value) {
                if (item != null && item != UNDEFINED) {
                    items.add(String.valueOf(item));
                }
            }
            return Collections.unmodifiableList(items);
        }
        return value;
    }

    /// Escapes a value as Storybook does. Only used for safe values, which are ASCII.
    private static String escape(final String value) {
        final StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            final char chr = value.charAt(i);
            if (ArgPatterns.isAsciiLetterOrDigit(chr) || UNESCAPED.indexOf(chr) >= 0) {
                sb.append(chr);
            } else if (chr == ' ') {
                sb.append('+');
            } else {
                final String hex = Integer.toHexString(chr & 0xFF).toUpperCase();
                sb.append('%');
                if (hex.length() < 2) {
                    sb.append('0');
                }
                sb.append(hex);
            }
        }
        return sb.toString();
    }

    /// Unescapes a name or value as Storybook does: `+` is a space and `%XX` escapes are UTF-8
    /// bytes. If any escape is malformed the value is left as it is (apart from `+`), as
    /// `decodeURIComponent` would fail. Non-ASCII bytes are also left escaped, as a value with
    /// non-ASCII characters would be unsafe anyway.
    static String unescape(final String value) {
        final String withSpaces = value.replace('+', ' ');
        if (withSpaces.indexOf('%') < 0) {
            return withSpaces;
        }
        final StringBuilder sb = new StringBuilder(withSpaces.length());
        int i = 0;
        while (i < withSpaces.length()) {
            final char chr = withSpaces.charAt(i);
            if (chr != '%') {
                sb.append(chr);
                i++;
                continue;
            }
            if (i + 2 >= withSpaces.length()
                || !ArgPatterns.isHexDigit(withSpaces.charAt(i + 1))
                || !ArgPatterns.isHexDigit(withSpaces.charAt(i + 2))) {
                return withSpaces;
            }
            final int code = Character.digit(withSpaces.charAt(i + 1), 16) * 16
                             + Character.digit(withSpaces.charAt(i + 2), 16);
            if (code >= 0x80) {
                return withSpaces;
            }
            sb.append((char) code);
            i += 3;
        }
        return sb.toString();
    }


    // --------------------------------------------------------------------------------


    /// A list being decoded, whose items may be set at any index, leaving gaps, as with a
    /// JavaScript array.
    private static final class IndexedList {

        private final SortedMap<Integer, Object> items = new TreeMap<>();

        private void set(final int index, final Object value) {
            // Storybook nests a repeated index in another list; the last value is used here
            items.put(index, value);
        }

        private void add(final Object value) {
            items.put(items.isEmpty()
                    ? 0
                    : items.lastKey() + 1, value);
        }

        /// @return The items in index order, without the gaps.
        private List<Object> toList() {
            return new ArrayList<>(items.values());
        }
    }
}
