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

package stroom.preferences.client;

/// Checks a date-time pattern as Java's `DateTimeFormatter.ofPattern` reads it, which the server
/// formats dates with, so a user's own pattern can be refused before it is applied. Moment.js,
/// which the UI formats with, accepts anything, so the UI can't tell a broken pattern by using it.
///
/// It follows `DateTimeFormatterBuilder.appendPattern`'s rules, including a padded number followed
/// straight by another, which Java fails on as it builds the formatter. It also refuses padding
/// narrower than its field can be (for years to 9999), which Java only fails on as it formats a date.
/// Its test checks it agrees with `java.time` for every letter and count, and for many random
/// patterns.
public final class JavaDateTimePattern {

    // What a run of pattern letters adds: no number, a number of any width, or one of fixed width
    private static final int NOT_A_NUMBER = 0;
    private static final int NUMBER = 1;
    private static final int FIXED_WIDTH_NUMBER = 2;

    // The number a following number joins (adjacent value parsing): none, or whether it is padded
    private static final int NO_NUMBER_BEFORE = 0;
    private static final int UNPADDED_NUMBER_BEFORE = 1;
    private static final int PADDED_NUMBER_BEFORE = 2;

    // The width of a field that has no widest, e.g. a time zone's name
    private static final int ANY_WIDTH = -1;

    private JavaDateTimePattern() {
        // Static utility
    }

    /// Says why a pattern can't be used, or nothing if it can.
    ///
    /// @param pattern The pattern, e.g. `dd/MM/yyyy HH:mm:ss`, or null.
    /// @return Why it can't be used, e.g. `Unknown pattern letter 'b'`, or null if it can.
    public static String invalidReason(final String pattern) {
        if (pattern == null || pattern.trim().isEmpty()) {
            return "Enter a format";
        }
        int optionalDepth = 0;
        int numberBefore = NO_NUMBER_BEFORE;
        int pos = 0;
        while (pos < pattern.length()) {
            final char cur = pattern.charAt(pos);
            if (isLetter(cur)) {
                int end = runEnd(pattern, pos);
                char letter = cur;
                int count = end - pos;
                final boolean padded = letter == 'p';
                final int padWidth = count;
                if (padded) {
                    // Pads the field whose letters follow it
                    if (end >= pattern.length() || !isLetter(pattern.charAt(end))) {
                        return "'p' must be followed by pattern letters";
                    }
                    letter = pattern.charAt(end);
                    final int padEnd = runEnd(pattern, end);
                    count = padEnd - end;
                    end = padEnd;
                }
                final String reason = letterReason(letter, count);
                if (reason != null) {
                    return reason;
                }
                if (padded) {
                    final String paddingReason = paddingReason(letter, count, padWidth);
                    if (paddingReason != null) {
                        return paddingReason;
                    }
                    // Padding starts afresh: the padded field joins no number before it
                    numberBefore = NO_NUMBER_BEFORE;
                }
                final int number = number(letter, count);
                if (number == NOT_A_NUMBER) {
                    numberBefore = NO_NUMBER_BEFORE;
                } else if (numberBefore == PADDED_NUMBER_BEFORE) {
                    // Java fails (a ClassCastException) joining a number to a padded one
                    return "A padded number can't be followed straight by another";
                } else if (numberBefore == NO_NUMBER_BEFORE || number == NUMBER) {
                    numberBefore = padded
                            ? PADDED_NUMBER_BEFORE
                            : UNPADDED_NUMBER_BEFORE;
                }
                // Otherwise a fixed width number joins the unpadded one before it, which stays
                // the one a following number joins
                pos = end;
            } else if (cur == '\'') {
                pos = literalEnd(pattern, pos);
                if (pos < 0) {
                    return "A quote isn't closed";
                }
                numberBefore = NO_NUMBER_BEFORE;
            } else {
                if (cur == '[') {
                    optionalDepth++;
                } else if (cur == ']') {
                    if (optionalDepth == 0) {
                        return "']' has no '[' before it";
                    }
                    optionalDepth--;
                } else if (cur == '{' || cur == '}' || cur == '#') {
                    return "'" + cur + "' is reserved";
                }
                // A literal, or an optional section's start or end, is between any numbers
                numberBefore = NO_NUMBER_BEFORE;
                pos++;
            }
        }
        return null;
    }

    // Why a run of one pattern letter can't be used, or null if it can
    private static String letterReason(final char letter, final int count) {
        switch (letter) {
            case 'u':
            case 'y':
            case 'g':
            case 'A':
            case 'n':
            case 'N':
                return atMost(letter, count, 19);
            case 'Y':
                return null;
            case 'c':
                if (count == 2) {
                    return "'cc' isn't a pattern";
                }
                return atMost(letter, count, 5);
            case 'L':
            case 'q':
            case 'M':
            case 'Q':
            case 'E':
            case 'e':
            case 'G':
            case 'Z':
            case 'X':
            case 'x':
                return atMost(letter, count, 5);
            case 'z':
                return atMost(letter, count, 4);
            case 'D':
                return atMost(letter, count, 3);
            case 'd':
            case 'h':
            case 'H':
            case 'k':
            case 'K':
            case 'm':
            case 's':
            case 'w':
                return atMost(letter, count, 2);
            case 'a':
            case 'F':
            case 'W':
                return atMost(letter, count, 1);
            case 'S':
                return atMost(letter, count, 9);
            case 'V':
                return count == 2
                        ? null
                        : "'V' must be 'VV'";
            case 'v':
            case 'O':
                return count == 1 || count == 4
                        ? null
                        : "'" + letter + "' must be 1 or 4 letters";
            case 'B':
                return count == 1 || count == 4 || count == 5
                        ? null
                        : "'B' must be 1, 4 or 5 letters";
            default:
                return "Unknown pattern letter '" + letter + "'";
        }
    }

    // Why a run of one pattern letter, already checked, can't be padded to a width, or null if it can
    private static String paddingReason(final char letter, final int count, final int padWidth) {
        final int widest = widest(letter, count);
        if (widest == ANY_WIDTH) {
            return "'" + letter + "' is a time zone's name, which can't be padded";
        }
        return padWidth >= widest
                ? null
                : "Too few 'p' letters to pad '" + letter + "' (" + widest + " at least)";
    }

    // The most characters a run of one pattern letter, already checked, can be in English
    private static int widest(final char letter, final int count) {
        switch (letter) {
            case 'y':
            case 'u':
            case 'Y':
                return count == 2
                        ? 2
                        : Math.max(count, 4);
            case 'M':
            case 'L':
                return widestText(count, 2, 3, 9);
            case 'Q':
            case 'q':
                return count == 1
                        ? 1
                        : widestText(count, 2, 2, 11);
            case 'E':
                return widestText(count, 3, 3, 9);
            case 'e':
            case 'c':
                return count == 1
                        ? 1
                        : widestText(count, 2, 3, 9);
            case 'G':
                return widestText(count, 2, 2, 11);
            case 'a':
            case 'd':
            case 'h':
            case 'H':
            case 'k':
            case 'K':
            case 'm':
            case 's':
            case 'w':
                return 2;
            case 'F':
            case 'W':
                return 1;
            case 'D':
                return 3;
            case 'S':
                return count;
            case 'g':
                return Math.max(count, 7);
            case 'A':
                return Math.max(count, 8);
            case 'n':
                return Math.max(count, 9);
            case 'N':
                return Math.max(count, 14);
            case 'B':
                return 16;
            case 'O':
                return count == 1
                        ? 11
                        : 12;
            case 'X':
            case 'x':
                return count <= 2
                        ? 5
                        : widestOffset(count);
            case 'Z':
                if (count <= 3) {
                    return 5;
                }
                return count == 4
                        ? 12
                        : 9;
            default:
                return ANY_WIDTH;
        }
    }

    // An offset's most characters with three to five letters, e.g. `+01:00`, `+010000`, `+01:00:00`
    private static int widestOffset(final int count) {
        switch (count) {
            case 3:
                return 6;
            case 4:
                return 7;
            default:
                return 9;
        }
    }

    // The most characters of what is a number (or short) with one or two letters, then short, full
    // and a single character with three, four and five
    private static int widestText(final int count,
                                  final int upToTwo,
                                  final int three,
                                  final int four) {
        switch (count) {
            case 1:
            case 2:
                return upToTwo;
            case 3:
                return three;
            case 4:
                return four;
            default:
                return 1;
        }
    }

    // Whether a run of one pattern letter, already checked, adds a number, and whether of a fixed
    // width (as `DateTimeFormatterBuilder.parseField` adds them)
    private static int number(final char letter, final int count) {
        switch (letter) {
            case 'y':
            case 'u':
            case 'Y':
            case 'w':
                return count == 2
                        ? FIXED_WIDTH_NUMBER
                        : NUMBER;
            case 'M':
            case 'L':
            case 'Q':
            case 'q':
            case 'd':
            case 'h':
            case 'H':
            case 'k':
            case 'K':
            case 'm':
            case 's':
                if (count > 2) {
                    return NOT_A_NUMBER;
                }
                return count == 2
                        ? FIXED_WIDTH_NUMBER
                        : NUMBER;
            case 'e':
                return count <= 2
                        ? FIXED_WIDTH_NUMBER
                        : NOT_A_NUMBER;
            case 'c':
                return count == 1
                        ? FIXED_WIDTH_NUMBER
                        : NOT_A_NUMBER;
            case 'W':
            case 'S':
                return FIXED_WIDTH_NUMBER;
            case 'D':
                return count == 3
                        ? FIXED_WIDTH_NUMBER
                        : NUMBER;
            case 'A':
            case 'n':
            case 'N':
                return count == 19
                        ? FIXED_WIDTH_NUMBER
                        : NUMBER;
            case 'F':
            case 'g':
                return NUMBER;
            default:
                return NOT_A_NUMBER;
        }
    }

    private static String atMost(final char letter, final int count, final int max) {
        return count <= max
                ? null
                : "Too many '" + letter + "' letters (" + max + " at most)";
    }

    // Where a run of the letter at a position ends
    private static int runEnd(final String pattern, final int start) {
        final char letter = pattern.charAt(start);
        int end = start + 1;
        while (end < pattern.length() && pattern.charAt(end) == letter) {
            end++;
        }
        return end;
    }

    // Where the quoted literal starting at a position ends (after its closing quote), or -1 if it
    // isn't closed; two quotes together inside it are a quote
    private static int literalEnd(final String pattern, final int start) {
        int pos = start + 1;
        while (pos < pattern.length()) {
            if (pattern.charAt(pos) == '\'') {
                if (pos + 1 < pattern.length() && pattern.charAt(pos + 1) == '\'') {
                    pos += 2;
                } else {
                    return pos + 1;
                }
            } else {
                pos++;
            }
        }
        return -1;
    }

    private static boolean isLetter(final char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }
}
