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

/// Hand-written equivalents of the regular expressions React Storybook uses to decide which args
/// are safe to put in, or take from, a URL (see Storybook's `parseArgsParam.ts`). GWT doesn't
/// emulate `java.util.regex`, and its `Character` methods only know ASCII, so these check the
/// characters explicitly, which also means the JVM and the browser give the same results.
///
/// The patterns are derived from Storybook, Copyright (c) 2024 Storybook, MIT licence (see
/// NOTICE.md).
final class ArgPatterns {

    private static final String RGB = "rgb";
    private static final String HSL = "hsl";

    private ArgPatterns() {
        // Static utility
    }

    /// `VALIDATION_REGEXP`, i.e. `^[a-zA-Z0-9 _-]*$`.
    ///
    /// @param value The value.
    /// @return True if the value only has letters, digits, spaces, `_` and `-`. True if empty.
    static boolean isPlain(final String value) {
        for (int i = 0; i < value.length(); i++) {
            final char chr = value.charAt(i);
            if (!isAsciiLetterOrDigit(chr) && chr != ' ' && chr != '_' && chr != '-') {
                return false;
            }
        }
        return true;
    }

    /// @param key The name of an arg.
    /// @return True if the name is safe, i.e. not empty and [#isPlain(String)].
    static boolean isSafeKey(final String key) {
        return key != null && !key.isEmpty() && isPlain(key);
    }

    /// Storybook's check for a safe string value, extended to allow ISO 8601 dates, which are how
    /// a date arg is held here (Storybook holds a `Date`, which is always safe).
    ///
    /// @param value The value.
    /// @return True if the value is plain, a number, a colour or a date.
    static boolean isSafeString(final String value) {
        return isPlain(value)
               || isNumber(value)
               || isHexColour(value)
               || matchColour(value) != null
               || isIsoDate(value);
    }

    /// `NUMBER_REGEXP`, i.e. `^-?[0-9]+(\.[0-9]+)?$`.
    ///
    /// @param value The value.
    /// @return True if the value is a plain decimal number, e.g. `-1.5`.
    static boolean isNumber(final String value) {
        int pos = value.startsWith("-")
                ? 1
                : 0;
        final int intDigits = countDigits(value, pos, Integer.MAX_VALUE);
        if (intDigits == 0) {
            return false;
        }
        pos += intDigits;
        if (pos == value.length()) {
            return true;
        }
        if (value.charAt(pos) != '.') {
            return false;
        }
        final int fractionDigits = countDigits(value, pos + 1, Integer.MAX_VALUE);
        return fractionDigits > 0 && pos + 1 + fractionDigits == value.length();
    }

    /// `HEX_REGEXP`, i.e. `^#([a-f0-9]{3,4}|[a-f0-9]{6}|[a-f0-9]{8})$`, ignoring case.
    ///
    /// @param value The value.
    /// @return True if the value is a hex colour, e.g. `#ff4785`.
    static boolean isHexColour(final String value) {
        final int digits = value.length() - 1;
        if (!value.startsWith("#") || (digits != 3 && digits != 4 && digits != 6 && digits != 8)) {
            return false;
        }
        for (int i = 1; i < value.length(); i++) {
            if (!isHexDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /// `COLOR_REGEXP`, i.e.
    /// `^(rgba?|hsla?)\(([0-9]{1,3}),\s?([0-9]{1,3})%?,\s?([0-9]{1,3})%?,?\s?([0-9](\.[0-9]{1,2})?)?\)$`,
    /// ignoring case. Where the expression could match in more than one way, the groups are those
    /// a regular expression engine would find, i.e. trying the longest match of each part first.
    ///
    /// @param value The value.
    /// @return The groups 1 to 5 of the expression at indices 0 to 4, i.e. the function name, the
    /// three numbers and the alpha value (null if there isn't one), or null if the value doesn't
    /// match.
    static String[] matchColour(final String value) {
        final String name = matchColourName(value);
        if (name == null) {
            return null;
        }
        int pos = name.length() + 1;
        final int first = countDigits(value, pos, 3);
        if (first == 0 || !isCharAt(value, pos + first, ',')) {
            return null;
        }
        final String group2 = value.substring(pos, pos + first);
        pos += first + 1;
        if (isJsWhitespace(charAt(value, pos))) {
            pos++;
        }
        final int second = countDigits(value, pos, 3);
        if (second == 0) {
            return null;
        }
        final String group3 = value.substring(pos, pos + second);
        pos += second;
        if (isCharAt(value, pos, '%')) {
            pos++;
        }
        if (!isCharAt(value, pos, ',')) {
            return null;
        }
        pos++;
        if (isJsWhitespace(charAt(value, pos))) {
            pos++;
        }
        final String[] tail = matchColourTail(value, pos);
        if (tail == null) {
            return null;
        }
        return new String[]{name, group2, group3, tail[0], tail[1]};
    }

    /// @return The `rgba?` or `hsla?` function name followed by `(`, in its original case, or null.
    private static String matchColourName(final String value) {
        if (value.length() < 4) {
            return null;
        }
        final String start = toAsciiLowerCase(value.substring(0, 3));
        if (!RGB.equals(start) && !HSL.equals(start)) {
            return null;
        }
        if (isCharAt(value, 3, '(')) {
            return value.substring(0, 3);
        }
        if ((isCharAt(value, 3, 'a') || isCharAt(value, 3, 'A')) && isCharAt(value, 4, '(')) {
            return value.substring(0, 4);
        }
        return null;
    }

    /// Matches `([0-9]{1,3})%?,?\s?([0-9](\.[0-9]{1,2})?)?\)$` from a position, backtracking as
    /// a regular expression engine would.
    ///
    /// @return Groups 4 and 5, or null if there's no match.
    private static String[] matchColourTail(final String value, final int start) {
        for (int digits = countDigits(value, start, 3); digits >= 1; digits--) {
            final int afterDigits = start + digits;
            for (final int percent : optional(isCharAt(value, afterDigits, '%'))) {
                final int afterPercent = afterDigits + percent;
                for (final int comma : optional(isCharAt(value, afterPercent, ','))) {
                    final int afterComma = afterPercent + comma;
                    for (final int space : optional(isJsWhitespace(charAt(value, afterComma)))) {
                        final int alphaStart = afterComma + space;
                        for (final int alphaLength : alphaLengths(value, alphaStart)) {
                            final int end = alphaStart + alphaLength;
                            if (end == value.length() - 1 && isCharAt(value, end, ')')) {
                                return new String[]{
                                        value.substring(start, afterDigits),
                                        alphaLength > 0
                                                ? value.substring(alphaStart, end)
                                                : null};
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /// @return The lengths that `([0-9](\.[0-9]{1,2})?)?` could match at the position, longest
    /// first.
    private static int[] alphaLengths(final String value, final int pos) {
        if (!isAsciiDigit(charAt(value, pos))) {
            return new int[]{0};
        }
        if (isCharAt(value, pos + 1, '.')) {
            final int fractionDigits = countDigits(value, pos + 2, 2);
            if (fractionDigits == 2) {
                return new int[]{4, 3, 1, 0};
            } else if (fractionDigits == 1) {
                return new int[]{3, 1, 0};
            }
        }
        return new int[]{1, 0};
    }

    /// @return The choices for an optional single character, taking it first if it is there.
    private static int[] optional(final boolean present) {
        return present
                ? new int[]{1, 0}
                : new int[]{0};
    }

    /// The ISO 8601 form JavaScript's `Date.toISOString()` produces, i.e.
    /// `^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{3})?Z$`.
    ///
    /// @param value The value.
    /// @return True if the value is a date and time, e.g. `2026-10-05T12:00:00.000Z`.
    static boolean isIsoDate(final String value) {
        final String withoutMillis = "0000-00-00T00:00:00Z";
        final String withMillis = "0000-00-00T00:00:00.000Z";
        return matchesDigitTemplate(value, withoutMillis) || matchesDigitTemplate(value, withMillis);
    }

    /// @return True if the value is the template with each `0` replaced by any digit.
    private static boolean matchesDigitTemplate(final String value, final String template) {
        if (value.length() != template.length()) {
            return false;
        }
        for (int i = 0; i < template.length(); i++) {
            final char expected = template.charAt(i);
            final char actual = value.charAt(i);
            if (expected == '0'
                    ? !isAsciiDigit(actual)
                    : expected != actual) {
                return false;
            }
        }
        return true;
    }

    /// JavaScript's `\s`, which `trim()` also removes.
    ///
    /// @param chr The character.
    /// @return True if the character is white space or a line terminator.
    static boolean isJsWhitespace(final char chr) {
        switch (chr) {
            case '\t':
            case '\n':
            case '\u000B':
            case '\f':
            case '\r':
            case ' ':
            case ' ':
            case ' ':
            case ' ':
            case ' ':
            case ' ':
            case ' ':
            case '　':
            case '﻿':
                return true;
            default:
                return chr >= ' ' && chr <= ' ';
        }
    }

    /// @param chr The character.
    /// @return True if the character is `0` to `9`.
    static boolean isAsciiDigit(final char chr) {
        return chr >= '0' && chr <= '9';
    }

    /// @param chr The character.
    /// @return True if the character is `a` to `z` or `A` to `Z` or `0` to `9`.
    static boolean isAsciiLetterOrDigit(final char chr) {
        return (chr >= 'a' && chr <= 'z') || (chr >= 'A' && chr <= 'Z') || isAsciiDigit(chr);
    }

    /// @param chr The character.
    /// @return True if the character is a hex digit, in either case.
    static boolean isHexDigit(final char chr) {
        return isAsciiDigit(chr) || (chr >= 'a' && chr <= 'f') || (chr >= 'A' && chr <= 'F');
    }

    private static String toAsciiLowerCase(final String value) {
        final StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            final char chr = value.charAt(i);
            sb.append(chr >= 'A' && chr <= 'Z'
                    ? (char) (chr + ('a' - 'A'))
                    : chr);
        }
        return sb.toString();
    }

    /// @return The number of consecutive digits from the position, up to the maximum.
    private static int countDigits(final String value, final int start, final int max) {
        int count = 0;
        while (count < max && start + count < value.length() && isAsciiDigit(value.charAt(start + count))) {
            count++;
        }
        return count;
    }

    /// @return The character at the position, or 0 if past the end.
    private static char charAt(final String value, final int pos) {
        return pos >= 0 && pos < value.length()
                ? value.charAt(pos)
                : 0;
    }

    private static boolean isCharAt(final String value, final int pos, final char chr) {
        return pos < value.length() && value.charAt(pos) == chr;
    }
}
