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

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/// Text handling the sample analysis and the tools' messages depend on, kept as it is because field documentation,
/// CEF regexes and many messages follow it: its regexes (Unicode `\w`, `\d`, `\s`; only `\n` ends a line),
/// stripping, splitting into lines, lengths and slices in code points, values in messages written in Python's
/// notation (`repr()`: `'a'`, `['x', 'y']`, `None`, `True`), and the rounding and printing of floats.
public final class ToolText {

    /// The flags a Python `str` regex behaves with: Unicode character classes, and only `\n` as a line end.
    public static final int FLAGS = Pattern.UNICODE_CHARACTER_CLASS | Pattern.UNIX_LINES;

    private ToolText() {
        // Static methods only.
    }

    /// @param regex A regex written for Python's `re`.
    /// @return It compiled to behave as Python's does.
    public static Pattern re(final String regex) {
        return Pattern.compile(regex, FLAGS);
    }

    /// @param regex A regex written for Python's `re`.
    /// @param flags Further flags, e.g. [Pattern#DOTALL] for `re.S`.
    /// @return It compiled to behave as Python's does.
    public static Pattern re(final String regex, final int flags) {
        return Pattern.compile(regex, FLAGS | flags);
    }

    /// @return True if the pattern matches at the start of the text, as `re.match` does.
    public static boolean match(final Pattern pattern, final CharSequence text) {
        return pattern.matcher(text).lookingAt();
    }

    /// @return The matcher after matching at the start of the text, as `re.match` does, or null if it didn't.
    public static Matcher matchAt(final Pattern pattern, final CharSequence text) {
        final Matcher matcher = pattern.matcher(text);
        return matcher.lookingAt()
                ? matcher
                : null;
    }

    /// @return The matcher after matching from `at`, as Python's `pattern.match(text, at)` does (lookbehind sees
    /// the text before it, `^` doesn't match there), or null if it didn't.
    public static Matcher matchFrom(final Pattern pattern, final CharSequence text, final int at) {
        final Matcher matcher = pattern.matcher(text);
        matcher.useTransparentBounds(true);
        matcher.useAnchoringBounds(false);
        matcher.region(at, text.length());
        return matcher.lookingAt()
                ? matcher
                : null;
    }

    /// @return True if the pattern matches the whole text, as `re.fullmatch` does.
    public static boolean fullmatch(final Pattern pattern, final CharSequence text) {
        return pattern.matcher(text).matches();
    }

    /// @return True if the pattern matches anywhere in the text, as `re.search` does.
    public static boolean search(final Pattern pattern, final CharSequence text) {
        return pattern.matcher(text).find();
    }

    /// @return The text with the first match of the pattern replaced by `replacement`, taken literally.
    public static String subFirst(final Pattern pattern, final String replacement, final String text) {
        return pattern.matcher(text).replaceFirst(Matcher.quoteReplacement(replacement));
    }

    /// @return The text with every match of the pattern replaced by `replacement`, taken literally.
    public static String sub(final Pattern pattern, final String replacement, final String text) {
        return pattern.matcher(text).replaceAll(Matcher.quoteReplacement(replacement));
    }

    /// @return Each match's groups, as `re.findall` gives them for a pattern of two or more groups (a group that
    /// took no part is "").
    public static List<String[]> findallGroups(final Pattern pattern, final CharSequence text) {
        final List<String[]> out = new ArrayList<>();
        final Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            final String[] groups = new String[matcher.groupCount()];
            for (int i = 0; i < groups.length; i++) {
                final String group = matcher.group(i + 1);
                groups[i] = group == null
                        ? ""
                        : group;
            }
            out.add(groups);
        }
        return out;
    }

    /// @return Each whole match, as `re.findall` gives them for a pattern of no groups.
    public static List<String> findall(final Pattern pattern, final CharSequence text) {
        final List<String> out = new ArrayList<>();
        final Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            out.add(matcher.group());
        }
        return out;
    }

    /// @return True if the character is whitespace as Python's `str.isspace()` has it.
    public static boolean isSpace(final int codePoint) {
        return Character.isWhitespace(codePoint)
               || Character.isSpaceChar(codePoint)
               || (codePoint >= 0x1C && codePoint <= 0x1F)
               || codePoint == 0x85;
    }

    /// @return The text without leading and trailing whitespace, as `str.strip()`.
    public static String strip(final String text) {
        int start = 0;
        int end = text.length();
        while (start < end && isSpace(text.codePointAt(start))) {
            start += Character.charCount(text.codePointAt(start));
        }
        while (end > start && isSpace(text.codePointBefore(end))) {
            end -= Character.charCount(text.codePointBefore(end));
        }
        return text.substring(start, end);
    }

    /// @return The text without leading and trailing characters that are in `chars`, as `str.strip(chars)`.
    public static String strip(final String text, final String chars) {
        return rstrip(lstrip(text, chars), chars);
    }

    /// @return The text without leading whitespace, as `str.lstrip()`.
    public static String lstrip(final String text) {
        int start = 0;
        while (start < text.length() && isSpace(text.codePointAt(start))) {
            start += Character.charCount(text.codePointAt(start));
        }
        return text.substring(start);
    }

    /// @return The text without leading characters that are in `chars`, as `str.lstrip(chars)`.
    public static String lstrip(final String text, final String chars) {
        int start = 0;
        while (start < text.length() && chars.indexOf(text.charAt(start)) >= 0) {
            start++;
        }
        return text.substring(start);
    }

    /// @return The text without trailing characters that are in `chars`, as `str.rstrip(chars)`.
    public static String rstrip(final String text, final String chars) {
        int end = text.length();
        while (end > 0 && chars.indexOf(text.charAt(end - 1)) >= 0) {
            end--;
        }
        return text.substring(0, end);
    }

    /// @return The text without trailing whitespace, as `str.rstrip()`.
    public static String rstrip(final String text) {
        int end = text.length();
        while (end > 0 && isSpace(text.codePointBefore(end))) {
            end -= Character.charCount(text.codePointBefore(end));
        }
        return text.substring(0, end);
    }

    /// @return True if the text is empty or only whitespace.
    public static boolean isBlank(final String text) {
        return strip(text).isEmpty();
    }

    /// @return The text's lines without their line ends, as `str.splitlines()`: split at `\n`, `\r`, `\r\n` and
    /// the other line boundaries Python knows.
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

    private static boolean isLineBoundary(final char c) {
        return c == '\n' || c == '\r' || c == 0x0B || c == 0x0C || c == 0x1C || c == 0x1D || c == 0x1E
               || c == 0x85 || c == 0x2028 || c == 0x2029;
    }

    /// @return The text's lines that aren't blank: `[line for line in text.splitlines() if line.strip()]`.
    public static List<String> nonBlankLines(final String text) {
        final List<String> lines = new ArrayList<>();
        for (final String line : splitlines(text)) {
            if (!isBlank(line)) {
                lines.add(line);
            }
        }
        return lines;
    }

    /// @return The text's words, split at runs of whitespace, as `str.split()`.
    public static List<String> split(final String text) {
        final List<String> words = new ArrayList<>();
        int i = 0;
        final int length = text.length();
        while (i < length) {
            while (i < length && isSpace(text.codePointAt(i))) {
                i += Character.charCount(text.codePointAt(i));
            }
            final int start = i;
            while (i < length && !isSpace(text.codePointAt(i))) {
                i += Character.charCount(text.codePointAt(i));
            }
            if (i > start) {
                words.add(text.substring(start, i));
            }
        }
        return words;
    }

    /// @return The text split at each `separator`, as `str.split(separator)`: empty parts kept.
    public static List<String> split(final String text, final String separator) {
        return List.of(text.split(Pattern.quote(separator), -1));
    }

    /// @return How many times `part` is in the text, not overlapping, as `str.count(part)`.
    public static int count(final String text, final String part) {
        if (part.isEmpty()) {
            return len(text) + 1;
        }
        int count = 0;
        int at = text.indexOf(part);
        while (at >= 0) {
            count++;
            at = text.indexOf(part, at + part.length());
        }
        return count;
    }

    /// @return The text's length in code points, as `len(text)`.
    public static int len(final String text) {
        return text.codePointCount(0, text.length());
    }

    /// @return The first `n` code points of the text, as `text[:n]`.
    public static String head(final String text, final int n) {
        if (n <= 0) {
            return "";
        }
        if (text.length() <= n) {
            return text;
        }
        final int end = text.offsetByCodePoints(0, Math.min(n, len(text)));
        return text.substring(0, end);
    }

    /// @return The code points from `from` to `to` of the text, as `text[from:to]` with both in range.
    public static String slice(final String text, final int from, final int to) {
        final int length = len(text);
        final int start = Math.max(0, Math.min(from, length));
        final int end = Math.max(start, Math.min(to, length));
        return text.substring(text.offsetByCodePoints(0, start), text.offsetByCodePoints(0, end));
    }

    /// @return True if the text has a cased character and every cased character is lower case, as
    /// `str.islower()`.
    public static boolean isLower(final String text) {
        boolean cased = false;
        for (int i = 0; i < text.length(); ) {
            final int c = text.codePointAt(i);
            if (Character.isUpperCase(c) || Character.isTitleCase(c)) {
                return false;
            }
            if (Character.isLowerCase(c)) {
                cased = true;
            }
            i += Character.charCount(c);
        }
        return cased;
    }

    /// @param text A text.
    /// @return True if the text has a cased character and every cased character is upper case, as
    /// `str.isupper()`.
    public static boolean isUpper(final String text) {
        boolean cased = false;
        for (int i = 0; i < text.length(); ) {
            final int c = text.codePointAt(i);
            if (Character.isLowerCase(c) || Character.isTitleCase(c)) {
                return false;
            }
            if (Character.isUpperCase(c)) {
                cased = true;
            }
            i += Character.charCount(c);
        }
        return cased;
    }

    /// @return The order Python sorts strings in: by code point.
    public static int compare(final String a, final String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            final int ca = a.codePointAt(i);
            final int cb = b.codePointAt(j);
            if (ca != cb) {
                return Integer.compare(ca, cb);
            }
            i += Character.charCount(ca);
            j += Character.charCount(cb);
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }

    /// @param text A text.
    /// @return It lower-cased, as Python's `str.lower` does for the names these tools see.
    public static String lower(final String text) {
        return text.toLowerCase(Locale.ROOT);
    }

    /// @param name      A name.
    /// @param separator The separator.
    /// @return The part after the last separator (`name.rsplit(separator, 1)[-1]`).
    public static String lastPart(final String name, final char separator) {
        return name.substring(name.lastIndexOf(separator) + 1);
    }

    /// @param regex A regex written for Python's `re`.
    /// @return It compiled case-insensitively (`re.I`).
    public static Pattern ignoringCase(final String regex) {
        return re(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    /// @return The number with thousands separated by commas, as `f'{n:,}'`.
    public static String thousands(final long number) {
        return String.format(Locale.ROOT, "%,d", number);
    }

    /// @return The value as Python's `str()` writes it: True and False, None, a float as `repr()`, a list or dict
    /// as its `repr()`.
    public static String str(final Object value) {
        if (value instanceof final String text) {
            return text;
        }
        return repr(value);
    }

    /// @param items Strings.
    /// @return Them as Python writes a list of them, e.g. `['a', 'b']`.
    public static String list(final Collection<String> items) {
        return items.stream().map(ToolText::repr).collect(Collectors.joining(", ", "[", "]"));
    }

    /// @return The value as Python's `repr()` writes it, e.g. `'a'`, `['x', 'y']`, `{'k': 1}`, `None`, `True`.
    public static String repr(final Object value) {
        if (value == null) {
            return "None";
        }
        if (value instanceof final String text) {
            return reprString(text);
        }
        if (value instanceof final Boolean bool) {
            return bool
                    ? "True"
                    : "False";
        }
        if (value instanceof final Double number) {
            return floatRepr(number);
        }
        if (value instanceof final Float number) {
            return floatRepr(number.doubleValue());
        }
        if (value instanceof final Map<?, ?> map) {
            final List<String> items = new ArrayList<>();
            map.forEach((k, v) -> items.add(repr(k) + ": " + repr(v)));
            return "{" + String.join(", ", items) + "}";
        }
        if (value instanceof final Collection<?> list) {
            final List<String> items = new ArrayList<>();
            list.forEach(item -> items.add(repr(item)));
            return "[" + String.join(", ", items) + "]";
        }
        return String.valueOf(value);
    }

    private static String reprString(final String text) {
        final char quote = text.indexOf('\'') >= 0 && text.indexOf('"') < 0
                ? '"'
                : '\'';
        final StringBuilder out = new StringBuilder().append(quote);
        for (int i = 0; i < text.length(); ) {
            final int c = text.codePointAt(i);
            i += Character.charCount(c);
            if (c == quote || c == '\\') {
                out.append('\\').appendCodePoint(c);
            } else if (c == '\t') {
                out.append("\\t");
            } else if (c == '\n') {
                out.append("\\n");
            } else if (c == '\r') {
                out.append("\\r");
            } else if (isPrintable(c)) {
                out.appendCodePoint(c);
            } else if (c < 0x100) {
                out.append(String.format(Locale.ROOT, "\\x%02x", c));
            } else if (c < 0x10000) {
                out.append(String.format(Locale.ROOT, "\\u%04x", c));
            } else {
                out.append(String.format(Locale.ROOT, "\\U%08x", c));
            }
        }
        return out.append(quote).toString();
    }

    private static boolean isPrintable(final int c) {
        if (c == ' ') {
            return true;
        }
        final int type = Character.getType(c);
        return type != Character.CONTROL && type != Character.FORMAT && type != Character.SURROGATE
               && type != Character.UNASSIGNED && type != Character.PRIVATE_USE
               && type != Character.LINE_SEPARATOR && type != Character.PARAGRAPH_SEPARATOR
               && type != Character.SPACE_SEPARATOR;
    }

    /// @return The float as Python's `repr()` writes it: the shortest digits that read back as it, e.g. `1.0`,
    /// `0.1`, `1e+16`, `1.5e-07`.
    public static String floatRepr(final double value) {
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
                    ? "-0.0"
                    : "0.0";
        }
        final BigDecimal decimal = new BigDecimal(Double.toString(Math.abs(value))).stripTrailingZeros();
        final String digits = decimal.unscaledValue().toString();
        final int exponent = digits.length() - 1 - decimal.scale();
        final String sign = value < 0
                ? "-"
                : "";
        if (exponent >= -4 && exponent < 16) {
            final String plain = decimal.toPlainString();
            return sign + (plain.contains(".")
                    ? plain
                    : plain + ".0");
        }
        final String mantissa = digits.length() > 1
                ? digits.charAt(0) + "." + digits.substring(1)
                : digits;
        return sign + mantissa + "e" + (exponent < 0
                ? "-"
                : "+") + String.format(Locale.ROOT, "%02d", Math.abs(exponent));
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
                    : "+") + String.format(Locale.ROOT, "%02d", Math.abs(exponent));
        }
        return trimZeros(rounded.toPlainString());
    }

    /// @param value A number.
    /// @return It as Python's `f'{value:.0f}'` writes it: rounded half to even on its exact value.
    public static String formatFixed0(final double value) {
        return new BigDecimal(value).setScale(0, RoundingMode.HALF_EVEN).toPlainString();
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

    /// @return The value rounded to `digits` decimal places as Python's `round(value, digits)` does: half to even,
    /// on the value the double holds.
    public static double round(final double value, final int digits) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return value;
        }
        return new BigDecimal(value).setScale(digits, RoundingMode.HALF_EVEN).doubleValue();
    }

    /// @return The value rounded to a whole number as Python's `round(value)` does: half to even.
    public static long round(final double value) {
        return (long) Math.rint(value);
    }

    /// @return A regex that matches the text literally, as Python's `re.escape` writes it (which Java's regexes
    /// read the same way).
    public static String escape(final String text) {
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if ("()[]{}?*+-|^$\\.&~# \t\n\r\u000B\f".indexOf(c) >= 0) {
                out.append('\\');
            }
            out.append(c);
        }
        return out.toString();
    }
}
