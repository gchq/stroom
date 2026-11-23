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

package stroom.ai.impl.tools.sequence;

import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/// How the streaming join compares join keys: in the order Elasticsearch sorts them, so two sides read in key order
/// merge. A key is a value per join field. Text (a keyword field) is in Unicode code point order, which is the
/// order of its UTF-8 bytes, as Elasticsearch sorts keywords (Java's own string order differs past U+FFFF); numbers
/// (and dates, in epoch milliseconds) by value; IP addresses by their 16-byte form, as Elasticsearch sorts them
/// (an IPv4 address as its IPv6-mapped form, so before IPv6 addresses outside that range).
public final class JoinKeys {

    private static final Set<String> TEXT_TYPES = Set.of("keyword", "constant_keyword", "wildcard");
    private static final Set<String> NUMBER_TYPES = Set.of("long", "integer", "short", "byte", "unsigned_long",
            "double", "float", "half_float", "scaled_float", "date", "date_nanos");
    private static final String IP_TYPE = "ip";
    private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(?:\\.\\d{1,3}){3}");
    private static final Pattern IPV6_CHARS = Pattern.compile("[0-9A-Fa-f:.]+");

    private JoinKeys() {
        // Static helpers only.
    }

    /// @param nativeType A field's Elasticsearch type, or null if not known (then text).
    /// @return How its values compare, or null if it can't be a join key (e.g. text, boolean, geo).
    public static Kind kind(final String nativeType) {
        if (nativeType == null || TEXT_TYPES.contains(nativeType)) {
            return Kind.TEXT;
        } else if (NUMBER_TYPES.contains(nativeType)) {
            return Kind.NUMBER;
        } else if (IP_TYPE.equals(nativeType)) {
            return Kind.IP;
        }
        return null;
    }

    /// @param kinds How each join field's values compare.
    /// @return The order of keys of those fields.
    public static Comparator<List<Object>> comparator(final List<Kind> kinds) {
        return (first, second) -> {
            for (int i = 0; i < kinds.size(); i++) {
                final int compared = compare(kinds.get(i), value(first, i), value(second, i));
                if (compared != 0) {
                    return compared;
                }
            }
            return 0;
        };
    }

    private static Object value(final List<Object> key, final int i) {
        return key != null && i < key.size()
                ? key.get(i)
                : null;
    }

    /// @param kind   How the values compare.
    /// @param first  One value, or null (first).
    /// @param second The other, or null.
    /// @return Their order.
    static int compare(final Kind kind, final Object first, final Object second) {
        if (first == null || second == null) {
            return first == null
                    ? (second == null
                            ? 0
                            : -1)
                    : 1;
        }
        return switch (kind) {
            case TEXT -> compareText(text(first), text(second));
            case NUMBER -> compareNumbers(first, second);
            case IP -> compareIps(text(first), text(second));
        };
    }

    // Code point order: the order of the values' UTF-8 bytes.
    private static int compareText(final String first, final String second) {
        int i = 0;
        int j = 0;
        while (i < first.length() && j < second.length()) {
            final int a = first.codePointAt(i);
            final int b = second.codePointAt(j);
            if (a != b) {
                return Integer.compare(a, b);
            }
            i += Character.charCount(a);
            j += Character.charCount(b);
        }
        return Integer.compare(first.length() - i, second.length() - j);
    }

    private static int compareNumbers(final Object first, final Object second) {
        if (first instanceof final Long a && second instanceof final Long b) {
            return Long.compare(a, b);
        }
        return Double.compare(number(first), number(second));
    }

    private static double number(final Object value) {
        if (value instanceof final Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(text(value));
        } catch (final NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static int compareIps(final String first, final String second) {
        final byte[] a = ipBytes(first);
        final byte[] b = ipBytes(second);
        if (a == null || b == null) {
            return compareText(first, second);
        }
        for (int i = 0; i < a.length; i++) {
            final int compared = Integer.compare(a[i] & 0xff, b[i] & 0xff);
            if (compared != 0) {
                return compared;
            }
        }
        return 0;
    }

    /// @param ip An IP address as text.
    /// @return Its 16 bytes (an IPv4 address IPv6-mapped), or null if it isn't one. Parsed here, so nothing is ever
    ///         looked up by name.
    static byte[] ipBytes(final String ip) {
        if (NullSafe.isBlankString(ip)) {
            return null;
        }
        final String text = ip.strip();
        if (IPV4.matcher(text).matches()) {
            final byte[] mapped = new byte[16];
            mapped[10] = (byte) 0xff;
            mapped[11] = (byte) 0xff;
            return ipv4(text, mapped, 12)
                    ? mapped
                    : null;
        }
        return text.indexOf(':') >= 0
                ? ipv6(text)
                : null;
    }

    // Writes a dotted IPv4 address's 4 bytes at `at`; false if a part is over 255.
    private static boolean ipv4(final String text, final byte[] bytes, final int at) {
        final String[] parts = text.split("\\.");
        for (int i = 0; i < 4; i++) {
            final int part = Integer.parseInt(parts[i]);
            if (part > 255) {
                return false;
            }
            bytes[at + i] = (byte) part;
        }
        return true;
    }

    // An IPv6 address's 16 bytes (`::` for a run of zero groups, a dotted IPv4 tail), or null if it isn't one.
    private static byte[] ipv6(final String text) {
        final int gap = text.indexOf("::");
        if (gap != text.lastIndexOf("::") || !IPV6_CHARS.matcher(text).matches()) {
            return null;
        }
        final List<String> head = hexGroups(gap < 0
                ? text
                : text.substring(0, gap));
        final List<String> tail = gap < 0
                ? new ArrayList<>()
                : hexGroups(text.substring(gap + 2));
        if (head == null || tail == null) {
            return null;
        }
        final int zeros = 8 - head.size() - tail.size();
        if ((gap < 0 && zeros != 0) || (gap >= 0 && zeros < 1)) {
            return null;
        }
        final List<String> all = new ArrayList<>(head);
        for (int i = 0; i < zeros && gap >= 0; i++) {
            all.add("0");
        }
        all.addAll(tail);
        final byte[] bytes = new byte[16];
        for (int i = 0; i < 8; i++) {
            final String group = all.get(i);
            if (group.isEmpty() || group.length() > 4) {
                return null;
            }
            final int value = Integer.parseInt(group, 16);
            bytes[i * 2] = (byte) (value >> 8);
            bytes[i * 2 + 1] = (byte) value;
        }
        return bytes;
    }

    // The 16-bit hex groups of part of an IPv6 address, a dotted IPv4 tail as two; null if that tail isn't one.
    private static List<String> hexGroups(final String part) {
        final List<String> groups = new ArrayList<>();
        if (part.isEmpty()) {
            return groups;
        }
        final String[] split = part.split(":", -1);
        for (int i = 0; i < split.length; i++) {
            if (i == split.length - 1 && split[i].contains(".")) {
                final byte[] four = new byte[4];
                if (!IPV4.matcher(split[i]).matches() || !ipv4(split[i], four, 0)) {
                    return null;
                }
                groups.add(Integer.toHexString(((four[0] & 0xff) << 8) | (four[1] & 0xff)));
                groups.add(Integer.toHexString(((four[2] & 0xff) << 8) | (four[3] & 0xff)));
            } else if (split[i].contains(".")) {
                return null;
            } else {
                groups.add(split[i]);
            }
        }
        return groups;
    }

    /// @param value A key's value.
    /// @return It as text: a whole number without a decimal point.
    static String text(final Object value) {
        if (value == null) {
            return "";
        } else if (value instanceof final Double number && number == Math.rint(number) && !number.isInfinite()
                   && Math.abs(number) < 1e15) {
            return String.valueOf(number.longValue());
        }
        return String.valueOf(value);
    }


    // --------------------------------------------------------------------------------


    /// How a join field's values compare.
    public enum Kind {
        /// Text, in code point order.
        TEXT,
        /// Numbers (and dates as epoch milliseconds), by value.
        NUMBER,
        /// IP addresses, by their 16-byte form.
        IP
    }
}
