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

/// A pattern that a duration is written with, such as `d.hh:mm:ss`.
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
/// The client uses this too, to check a pattern that is typed.
public final class DurationPattern {

    private static final char QUOTE = '\'';
    private static final int MAX_DAY_DIGITS = 8;
    private static final String HELP = "Use d, h, hh, m, mm, s, ss, f, ff and fff, and put other text in single quotes";

    private final List<Part> parts;

    private DurationPattern(final List<Part> parts) {
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
        return new DurationPattern(parts);
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
                millis = 1_000L;
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
