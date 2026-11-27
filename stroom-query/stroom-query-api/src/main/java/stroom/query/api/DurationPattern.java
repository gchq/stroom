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

package stroom.query.api;

import java.util.ArrayList;
import java.util.List;

/// A pattern that a duration is written with or read by, such as `d.hh:mm:ss`.
///
/// These letters are units, and how many there are is the fewest digits to write:
///
/// * `d` to `dddddddd`: days of 24 hours
/// * `h` and `hh`: hours
/// * `m` and `mm`: minutes
/// * `s` and `ss`: seconds
/// * `f`, `ff` and `fff`: tenths, hundredths and thousandths of a second
///
/// Each unit has what the larger units in the pattern leave, so 2 days and 90 minutes is `49:30` with `hh:mm` and
/// `2.01:30` with `d.hh:mm`. What is too small for the smallest unit is left out. Other letters must be in single
/// quotes, and two single quotes are one. A negative duration starts with `-`.
///
/// Text can have fewer digits than there are letters, and if there are seconds a fraction can have any number of
/// digits, with any part of a millisecond dropped. Where units are written together, as in `hhmmss`, only the first can
/// have a different number of digits, or none of them if there is a fraction, as in `ssfff`.
///
/// The client uses this too, to check a pattern that is typed.
public final class DurationPattern {

    private static final char QUOTE = '\'';
    private static final int MAX_DAY_DIGITS = 8;
    private static final long SECOND_MILLIS = 1_000L;
    private static final int MILLIS_DIGITS = 3;
    private static final String HELP = "Use d, h, hh, m, mm, s, ss, f, ff and fff, and put other text in single quotes";

    private final String pattern;
    private final List<Part> parts;

    private DurationPattern(final String pattern, final List<Part> parts) {
        this.pattern = pattern;
        this.parts = parts;
    }

    /// Reads a pattern.
    ///
    /// @param pattern The pattern.
    /// @return What to format durations with.
    /// @throws IllegalArgumentException If it is not a pattern. The message is fit to show the user.
    public static DurationPattern parse(final String pattern) {
        if (pattern == null || pattern.trim().isEmpty()) {
            throw new IllegalArgumentException("A duration pattern is needed, such as d.hh:mm:ss");
        }
        final List<Part> parts = new ArrayList<>();
        final StringBuilder text = new StringBuilder();
        boolean hasUnit = false;
        int pos = 0;
        while (pos < pattern.length()) {
            final char c = pattern.charAt(pos);
            if (c == QUOTE) {
                pos = readQuoted(pattern, pos, text);
            } else if (isLetter(c)) {
                int end = pos + 1;
                while (end < pattern.length() && pattern.charAt(end) == c) {
                    end++;
                }
                addText(parts, text);
                parts.add(createUnit(pattern, c, end - pos));
                hasUnit = true;
                pos = end;
            } else {
                text.append(c);
                pos++;
            }
        }
        addText(parts, text);
        if (!hasUnit) {
            throw new IllegalArgumentException("The duration pattern \"" + pattern + "\" has no units. " + HELP);
        }
        return new DurationPattern(pattern, parts);
    }

    // Returns the position after the quoted text.
    private static int readQuoted(final String pattern, final int start, final StringBuilder text) {
        int pos = start + 1;
        if (isQuote(pattern, pos)) {
            text.append(QUOTE);
            return pos + 1;
        }
        while (pos < pattern.length()) {
            if (!isQuote(pattern, pos)) {
                text.append(pattern.charAt(pos));
                pos++;
            } else if (isQuote(pattern, pos + 1)) {
                text.append(QUOTE);
                pos += 2;
            } else {
                return pos + 1;
            }
        }
        throw new IllegalArgumentException("The duration pattern \"" + pattern + "\" has a quote with no end");
    }

    private static boolean isQuote(final String pattern, final int pos) {
        return pos < pattern.length() && pattern.charAt(pos) == QUOTE;
    }

    // Not Character.isLetter, as the client doesn't have it for every letter.
    private static boolean isLetter(final char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static void addText(final List<Part> parts, final StringBuilder text) {
        if (text.length() > 0) {
            parts.add(new Part(text.toString(), 0, 0));
            text.setLength(0);
        }
    }

    private static Part createUnit(final String pattern, final char letter, final int count) {
        final int maxCount;
        final long millis;
        switch (letter) {
            case 'd':
                maxCount = MAX_DAY_DIGITS;
                millis = 86_400_000L;
                break;
            case 'h':
                maxCount = 2;
                millis = 3_600_000L;
                break;
            case 'm':
                maxCount = 2;
                millis = 60_000L;
                break;
            case 's':
                maxCount = 2;
                millis = SECOND_MILLIS;
                break;
            case 'f':
                maxCount = 3;
                millis = count == 1
                        ? 100L
                        : count == 2
                                ? 10L
                                : 1L;
                break;
            default:
                throw new IllegalArgumentException("The duration pattern \"" + pattern + "\" has a letter '" + letter
                                                   + "' that is not a unit. " + HELP);
        }
        if (count > maxCount) {
            throw new IllegalArgumentException("The duration pattern \"" + pattern + "\" has too many of '" + letter
                                               + "' together. " + HELP);
        }
        return new Part(null, count, millis);
    }

    /// Writes a duration.
    ///
    /// @param millis The duration in milliseconds.
    /// @return The text.
    public String format(final long millis) {
        final StringBuilder sb = new StringBuilder();
        boolean allZero = true;
        for (final Part part : parts) {
            if (part.text != null) {
                sb.append(part.text);
            } else {
                final long larger = getNextLargerUnit(part.millis);
                final long remainder = larger == 0
                        ? millis
                        : millis % larger;
                final long amount = remainder / part.millis;
                allZero = allZero && amount == 0;
                appendDigits(sb, amount, part.minDigits);
            }
        }
        return millis < 0 && !allZero
                ? "-" + sb
                : sb.toString();
    }

    /// Checks that text in the pattern can only be read one way. Any pattern can be written.
    ///
    /// @throws IllegalArgumentException If it can't. The message is fit to show the user.
    public void checkCanRead() {
        for (int i = 0; i < parts.size(); i++) {
            final Part part = parts.get(i);
            final Part previous = i == 0
                    ? null
                    : parts.get(i - 1);
            if (part.text == null) {
                for (final Part other : parts.subList(0, i)) {
                    if (other.text == null
                        && (other.millis == part.millis || (isFraction(other) && isFraction(part)))) {
                        throw createCantReadException("has a unit more than once");
                    }
                }
                // Only the first of some units that are together can have more digits than letters.
                if (previous != null && previous.text == null && getMaxDigits(part) > part.minDigits) {
                    throw createCantReadException("has units together that can't be told apart");
                }
            } else if (previous != null && isDigit(part.text.charAt(0))) {
                throw createCantReadException("has a digit straight after a unit");
            }
        }
    }

    private IllegalArgumentException createCantReadException(final String reason) {
        return new IllegalArgumentException("The duration pattern \"" + pattern + "\" " + reason
                                            + ", so it can't be used to read a duration");
    }

    private static boolean isFraction(final Part part) {
        return part.millis < SECOND_MILLIS;
    }

    // Gets the most digits that a unit can be written with.
    private int getMaxDigits(final Part part) {
        final long larger = getNextLargerUnit(part.millis);
        return larger == 0
                ? Integer.MAX_VALUE
                : String.valueOf(larger / part.millis - 1).length();
    }

    /// Reads a duration. Use [#checkCanRead()] first.
    ///
    /// @param text The text.
    /// @return The duration in milliseconds, or `null` if the text is not in the pattern.
    /// @throws ArithmeticException If the duration is too long.
    public Long read(final String text) {
        // The pattern could start with a sign of its own.
        final Long millis = read(text, 0, false);
        if (millis != null || text.isEmpty()) {
            return millis;
        }
        final char sign = text.charAt(0);
        return sign == '-' || sign == '+'
                ? read(text, 1, sign == '-')
                : null;
    }

    private Long read(final String text, final int start, final boolean negative) {
        final List<String> values = split(text, start);
        if (values == null) {
            return null;
        }
        long millis = 0;
        for (int i = 0; i < parts.size(); i++) {
            final Part part = parts.get(i);
            if (part.text == null) {
                millis = Math.addExact(millis, isFractionOfSecond(part)
                        ? parseLong((values.get(i) + "00").substring(0, MILLIS_DIGITS), negative)
                        : Math.multiplyExact(parseLong(values.get(i), negative), part.millis));
            }
        }
        return millis;
    }

    // Gets the text of each part, or null if the text is not in the pattern. This is all checked first, as only text
    // that is in the pattern can be too long.
    private List<String> split(final String text, final int start) {
        final List<String> values = new ArrayList<>();
        int pos = start;
        for (int i = 0; i < parts.size(); i++) {
            final Part part = parts.get(i);
            String value = part.text;
            if (value == null) {
                final int end = getEndOfUnit(text, pos, i);
                if (end <= pos || isTooMany(part, text.substring(pos, end))) {
                    return null;
                }
                value = text.substring(pos, end);
            } else if (!text.startsWith(value, pos)) {
                return null;
            }
            pos += value.length();
            values.add(value);
        }
        return pos == text.length()
                ? values
                : null;
    }

    // Gets the position after a unit's digits, or the start if there aren't the right number of them. That of the first
    // of some units that are together leaves enough for the others, which have as many digits as letters.
    private int getEndOfUnit(final String text, final int start, final int index) {
        int end = start;
        while (end < text.length() && isDigit(text.charAt(end))) {
            end++;
        }
        boolean hasFraction = false;
        int last = index;
        while (isUnit(last + 1)) {
            last++;
            end -= parts.get(last).minDigits;
            hasFraction = hasFraction || isFraction(parts.get(index)) || isFraction(parts.get(last));
        }
        // More digits could be of a larger first unit or a longer fraction.
        return hasFraction && end - start != parts.get(index).minDigits
                ? start
                : end;
    }

    private boolean isUnit(final int index) {
        return index < parts.size() && parts.get(index).text == null;
    }

    private boolean isFractionOfSecond(final Part part) {
        return getNextLargerUnit(part.millis) == SECOND_MILLIS;
    }

    // Tells whether there are enough of a unit to make the next larger one.
    private boolean isTooMany(final Part part, final String digits) {
        final long larger = getNextLargerUnit(part.millis);
        if (larger == 0 || isFractionOfSecond(part)) {
            return false;
        }
        int first = 0;
        while (first < digits.length() - 1 && digits.charAt(first) == '0') {
            first++;
        }
        final String significant = digits.substring(first);
        return significant.length() > getMaxDigits(part)
               || Long.parseLong(significant) >= larger / part.millis;
    }

    private static long parseLong(final String digits, final boolean negative) {
        try {
            return Long.parseLong(negative
                    ? "-" + digits
                    : digits);
        } catch (final NumberFormatException e) {
            throw new ArithmeticException("The duration is too long");
        }
    }

    /// Gets the length of the unit of a pattern that is nothing but one unit.
    ///
    /// @return The milliseconds of the unit, or zero if there is anything else in the pattern.
    public long getOnlyUnitMillis() {
        return parts.size() == 1
                ? parts.get(0).millis
                : 0;
    }

    private static boolean isDigit(final char c) {
        return c >= '0' && c <= '9';
    }

    // Gets the smallest unit in the pattern that is larger, or zero if there isn't one.
    private long getNextLargerUnit(final long unitMillis) {
        long larger = 0;
        for (final Part part : parts) {
            if (part.millis > unitMillis && (larger == 0 || part.millis < larger)) {
                larger = part.millis;
            }
        }
        return larger;
    }

    /// Gets the pattern as it was typed.
    ///
    /// @return The pattern.
    @Override
    public String toString() {
        return pattern;
    }

    private static void appendDigits(final StringBuilder sb, final long amount, final int minDigits) {
        // The sign is removed from the text, as the lowest long has no positive equivalent.
        final String text = String.valueOf(amount);
        final String digits = amount < 0
                ? text.substring(1)
                : text;
        for (int i = digits.length(); i < minDigits; i++) {
            sb.append('0');
        }
        sb.append(digits);
    }


    // --------------------------------------------------------------------------------


    // Some text, or a unit if the text is null.
    private static final class Part {

        private final String text;
        private final int minDigits;
        private final long millis;

        Part(final String text, final int minDigits, final long millis) {
            this.text = text;
            this.minDigits = minDigits;
            this.millis = millis;
        }
    }
}
