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

package stroom.ai.impl.tools.fielddoc;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/// The few Python string and number behaviours the documentation and CEF text depend on, so that they come out as
/// the Stroom MCP server writes them: white space as `str.isspace()` has it, lengths and slices in characters (code
/// points), `str.splitlines()`, the `g` and `.0f` number formats, Python's truthiness of JSON-like values, and the
/// JSON `json.dumps(indent=1, ensure_ascii=False)` writes.
public final class PythonStrings {

    private PythonStrings() {
        // Static only.
    }

    /// @param regex A Python regular expression whose classes (`\w`, `\s`, `\d`, `\b`) are Unicode's, as in a
    ///              Python 3 `str` pattern.
    /// @return It compiled with Unicode character classes.
    public static Pattern re(final String regex) {
        return Pattern.compile(regex, Pattern.UNICODE_CHARACTER_CLASS);
    }

    /// @param regex A Python regular expression.
    /// @param flags Further [Pattern] flags.
    /// @return It compiled with Unicode character classes and the flags.
    public static Pattern re(final String regex, final int flags) {
        return Pattern.compile(regex, Pattern.UNICODE_CHARACTER_CLASS | flags);
    }

    /// @param cp A code point.
    /// @return True if Python's `str.isspace()` takes it for white space.
    public static boolean isSpace(final int cp) {
        return switch (cp) {
            case '\t', '\n', 0x0b, 0x0c, '\r', 0x1c, 0x1d, 0x1e, 0x1f, ' ', 0x85, 0xa0, 0x1680, 0x2028, 0x2029, 0x202f,
                 0x205f, 0x3000 -> true;
            default -> cp >= 0x2000 && cp <= 0x200a;
        };
    }

    /// @param text Text, or null (taken as empty).
    /// @return It without leading and trailing white space, as `str.strip()`.
    public static String strip(final String text) {
        final String value = text == null
                ? ""
                : text;
        int start = 0;
        int end = value.length();
        while (start < end && isSpace(value.charAt(start))) {
            start++;
        }
        while (end > start && isSpace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }

    /// @param text  Text.
    /// @param chars The characters to take off.
    /// @return It without leading and trailing characters that are in `chars`, as `str.strip(chars)`.
    public static String strip(final String text, final String chars) {
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

    /// @param text  Text.
    /// @param chars The characters to take off.
    /// @return It without trailing characters that are in `chars`, as `str.rstrip(chars)`.
    public static String rstrip(final String text, final String chars) {
        int end = text.length();
        while (end > 0 && chars.indexOf(text.charAt(end - 1)) >= 0) {
            end--;
        }
        return text.substring(0, end);
    }

    /// @param text Text.
    /// @return It without trailing white space, as `str.rstrip()`.
    public static String rstrip(final String text) {
        int end = text.length();
        while (end > 0 && isSpace(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end);
    }

    /// @param text Text.
    /// @return Its length in characters, as Python's `len` counts them (code points).
    public static int len(final String text) {
        return text.codePointCount(0, text.length());
    }

    /// @param text Text.
    /// @param n    How many characters.
    /// @return Its first `n` characters, as `text[:n]`.
    public static String head(final String text, final int n) {
        if (n <= 0) {
            return "";
        }
        if (len(text) <= n) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, n));
    }

    /// @param text Text.
    /// @return Its lines without their line ends, as `str.splitlines()`.
    public static List<String> splitlines(final String text) {
        final List<String> lines = new ArrayList<>();
        int start = 0;
        int i = 0;
        final int length = text.length();
        while (i < length) {
            final char c = text.charAt(i);
            if (isLineBoundary(c)) {
                lines.add(text.substring(start, i));
                if (c == '\r' && i + 1 < length && text.charAt(i + 1) == '\n') {
                    i++;
                }
                start = i + 1;
            }
            i++;
        }
        if (start < length) {
            lines.add(text.substring(start));
        }
        return lines;
    }

    /// @param text      Text.
    /// @param separator What it is split at.
    /// @return Its parts, as `text.split(separator)`: empty parts kept, a trailing one too.
    public static List<String> split(final String text, final String separator) {
        final List<String> parts = new ArrayList<>();
        int start = 0;
        int at = text.indexOf(separator);
        while (at >= 0) {
            parts.add(text.substring(start, at));
            start = at + separator.length();
            at = text.indexOf(separator, start);
        }
        parts.add(text.substring(start));
        return parts;
    }

    /// @param value A number.
    /// @return It as Python's `f'{value:g}'` writes it: six significant digits, trailing zeros gone, an exponent
    /// only for the very large and the very small.
    public static String formatG(final double value) {
        if (Double.isNaN(value)) {
            return "nan";
        }
        if (Double.isInfinite(value)) {
            return value > 0
                    ? "inf"
                    : "-inf";
        }
        if (value == 0) {
            return 1 / value < 0
                    ? "-0"
                    : "0";
        }
        final BigDecimal rounded = new BigDecimal(value).round(new MathContext(6, RoundingMode.HALF_EVEN));
        final int exponent = rounded.precision() - rounded.scale() - 1;
        if (exponent < -4 || exponent >= 6) {
            final BigDecimal mantissa = rounded.movePointLeft(exponent);
            final String digits = trimZeros(mantissa.toPlainString());
            return digits + "e" + (exponent < 0
                    ? "-"
                    : "+") + String.format("%02d", Math.abs(exponent));
        }
        return trimZeros(rounded.toPlainString());
    }

    /// @param value A number.
    /// @return It as Python's `f'{value:.0f}'` writes it: rounded half to even on its exact value.
    public static String formatFixed0(final double value) {
        return new BigDecimal(value).setScale(0, RoundingMode.HALF_EVEN).toPlainString();
    }

    /// @param value A JSON-like value.
    /// @return Its truth in Python: null, false, zero and empty text, lists and maps are false.
    public static boolean truthy(final Object value) {
        return switch (value) {
            case null -> false;
            case final Boolean bool -> bool;
            case final Number number -> number.doubleValue() != 0;
            case final String text -> !text.isEmpty();
            case final Collection<?> items -> !items.isEmpty();
            case final Map<?, ?> map -> !map.isEmpty();
            default -> true;
        };
    }

    /// @param value A JSON-like value.
    /// @return It as Python's `str()` writes it in an f-string: text as it is, numbers and booleans as Python
    /// writes them.
    public static String str(final Object value) {
        return switch (value) {
            case null -> "None";
            case final String text -> text;
            case final Boolean bool -> bool
                    ? "True"
                    : "False";
            default -> String.valueOf(value);
        };
    }

    /// @param map Text by text.
    /// @return It as `json.dumps(map, indent=1, ensure_ascii=False)` writes it.
    public static String jsonIndented(final Map<String, String> map) {
        if (map.isEmpty()) {
            return "{}";
        }
        final StringBuilder sb = new StringBuilder("{\n");
        boolean first = true;
        for (final Map.Entry<String, String> entry : map.entrySet()) {
            if (!first) {
                sb.append(",\n");
            }
            first = false;
            sb.append(' ');
            jsonString(sb, entry.getKey());
            sb.append(": ");
            jsonString(sb, entry.getValue());
        }
        return sb.append("\n}").toString();
    }

    private static void jsonString(final StringBuilder sb, final String text) {
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
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    private static String trimZeros(final String digits) {
        if (!digits.contains(".")) {
            return digits;
        }
        final String trimmed = rstrip(digits, "0");
        return trimmed.endsWith(".")
                ? trimmed.substring(0, trimmed.length() - 1)
                : trimmed;
    }

    private static boolean isLineBoundary(final char c) {
        return c == '\n' || c == '\r' || c == 0x0B || c == 0x0C || c == 0x1C || c == 0x1D || c == 0x1E
               || c == 0x85 || c == 0x2028 || c == 0x2029;
    }
}
