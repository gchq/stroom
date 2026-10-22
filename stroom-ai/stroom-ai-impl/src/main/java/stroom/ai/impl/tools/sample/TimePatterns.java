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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/// Timestamp patterns: a Java (`stroom:format-date`) pattern inferred from a value, and a pattern checked against
/// values (the Stroom MCP server's `utils/timefmt.py`).
///
/// The profiler used to know ten shapes; anything else was typed as a string and the model guessed a pattern,
/// which `stroom:format-date` then failed silently, leaving TimeCreated empty. Here a value is read left to right
/// into pattern letters, and any pattern (inferred or the model's) is turned back into a regex and tried on the
/// sample's values before the XSLT is stepped.
public final class TimePatterns {

    /// What profiling calls an epoch value, and its time_format.
    public static final Map<String, String> EPOCH_NAMES = epochNames();

    private static final Set<String> MONTHS = Set.of("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep",
            "oct", "nov", "dec");
    private static final Set<String> DAYS = Set.of("mon", "tue", "wed", "thu", "fri", "sat", "sun");
    private static final Set<String> MONTH_FULL = Set.of("january", "february", "march", "april", "may", "june",
            "july", "august", "september", "october", "november", "december");
    private static final Pattern ZONE_NAME = PyText.re(
            "(?:UTC|GMT|Z(?![a-z])|[A-Z]{3,4}T|[A-Z][a-z]+/[A-Z][A-Za-z_]+)");
    private static final Pattern EPOCH_S = PyText.re("1\\d{9}(\\.\\d{1,9})?");
    private static final Pattern EPOCH_MS = PyText.re("1\\d{12}");
    private static final Pattern EPOCH_US = PyText.re("1\\d{15}");
    private static final Pattern EPOCH_NS = PyText.re("1\\d{18}");
    private static final Pattern COMPACT_DATE_TIME = PyText.re("\\d{14}");
    private static final Pattern COMPACT_DATE = PyText.re("\\d{8}");
    private static final Pattern COMPACT_ISO = PyText.re("\\d{8}T\\d{6}(\\.\\d+)?Z?");
    private static final Pattern FRACTION = PyText.re("\\.(\\d+)");
    private static final Pattern DIGITS = PyText.re("\\d+");
    private static final Pattern LETTERS = PyText.re("[A-Za-z]+");
    private static final Pattern YEAR_FIRST_DATE = PyText.re("([-/.])(\\d{1,2})\\1(\\d{1,2})");
    private static final Pattern NUMERIC_DATE = PyText.re("(\\d{1,2})([-/.])(\\d{1,2})\\2(\\d{2,4})");
    private static final Pattern DAY_MONTH_NAME = PyText.re("(\\d{1,2})[ -/]([A-Za-z]{3,})[ -/,]+(\\d{2,4})");
    private static final Pattern TIME = PyText.re("(\\d{1,2}):(\\d{2})(?::(\\d{2}))?(?:([.,])(\\d{1,9}))?");
    private static final Pattern AM_PM = PyText.re("\\s?([AaPp][Mm])");
    private static final Pattern MONTH_NAME_DATE = PyText.re("[A-Za-z]+\\s+(\\d{1,2})(?:,?\\s+(\\d{4}))?");
    private static final Pattern BRACKETED_ZONE = PyText.re("\\[([A-Z][a-z]+/[A-Z][A-Za-z_]+)\\]");
    private static final Pattern OFFSET = PyText.re("[+-]\\d{2}:?\\d{2}");
    private static final Map<Character, IntFunction<String>> LETTER = letters();

    private TimePatterns() {
        // Static methods only.
    }

    private static Map<String, String> epochNames() {
        final Map<String, String> names = new LinkedHashMap<>();
        names.put("epoch seconds", "epoch_s");
        names.put("epoch milliseconds", "epoch_ms");
        names.put("epoch microseconds", "epoch_us");
        names.put("epoch nanoseconds", "epoch_ns");
        return Map.copyOf(names);
    }

    /// Infers a Java date pattern that parses a value, e.g. `28/Sep/2026:10:00:00 +0000` gives
    /// `dd/MMM/yyyy:HH:mm:ss Z`. Epoch values give `epoch seconds`, `epoch milliseconds` and so on.
    ///
    /// @param value A value.
    /// @return The pattern, or null when the value doesn't look like a date and time.
    public static String inferTimePattern(final String value) {
        final String text = PyText.strip(value);
        // Epoch seconds may carry a fraction (1791712800.123: a Bluecoat column, GELF's timestamp), which epoch_s
        // keeps. Seen in production: a fraction made the value a plain decimal, the time was mapped by hand, and its
        // milliseconds were dropped.
        if (PyText.fullmatch(EPOCH_S, text)) {
            return "epoch seconds";
        }
        if (PyText.fullmatch(EPOCH_MS, text)) {
            return "epoch milliseconds";
        }
        if (PyText.fullmatch(EPOCH_US, text)) {
            return "epoch microseconds";
        }
        if (PyText.fullmatch(EPOCH_NS, text)) {
            return "epoch nanoseconds";
        }
        if (PyText.fullmatch(COMPACT_DATE_TIME, text)) {
            return "yyyyMMddHHmmss";
        }
        if (PyText.fullmatch(COMPACT_DATE, text)) {
            return "yyyyMMdd";
        }
        if (PyText.fullmatch(COMPACT_ISO, text)) {
            final Matcher fraction = FRACTION.matcher(text);
            return "yyyyMMdd'T'HHmmss" + (fraction.find()
                    ? "." + "S".repeat(fraction.group(1).length())
                    : "") + (text.endsWith("Z")
                    ? "X"
                    : "");
        }
        return new Reading(text).pattern();
    }

    private static String repeat(final String letters, final int length, final int whole) {
        return length == whole
                ? letters
                : letters.substring(0, 1);
    }

    private static Map<Character, IntFunction<String>> letters() {
        final Map<Character, IntFunction<String>> letters = new LinkedHashMap<>();
        letters.put('y', n -> n >= 4
                ? "\\d{4}"
                : "\\d{2}");
        letters.put('u', n -> n >= 4
                ? "\\d{4}"
                : "\\d{2}");
        letters.put('M', n -> n <= 2
                ? "\\d{1,2}"
                : n == 3
                        ? "[A-Za-z]{3}"
                        : "[A-Za-z]{3,}");
        letters.put('L', n -> n <= 2
                ? "\\d{1,2}"
                : "[A-Za-z]{3,}");
        letters.put('d', n -> n == 1
                ? "\\d{1,2}"
                : "[ 0-3]?\\d");
        letters.put('D', n -> "\\d{1,3}");
        for (final char c : "HkKhms".toCharArray()) {
            letters.put(c, n -> "\\d{1,2}");
        }
        letters.put('S', n -> "\\d{1,9}");
        letters.put('n', n -> "\\d+");
        letters.put('N', n -> "\\d+");
        letters.put('a', n -> "(?:[AaPp]\\.?[Mm]\\.?)");
        letters.put('E', n -> "[A-Za-z]{3,}");
        letters.put('e', n -> "\\d|[A-Za-z]{3,}");
        letters.put('c', n -> "\\d|[A-Za-z]{3,}");
        letters.put('z', n -> "[A-Za-z][A-Za-z0-9/_+:-]*");
        letters.put('O', n -> "GMT(?:[+-]\\d{1,2}(?::\\d{2})?)?");
        letters.put('V', n -> "[A-Za-z][A-Za-z0-9/_+-]*");
        letters.put('v', n -> "[A-Za-z][A-Za-z ]*");
        letters.put('X', n -> "(?:Z|[+-]\\d{2}(?::?\\d{2})?)");
        letters.put('x', n -> "[+-]\\d{2}(?::?\\d{2})?");
        letters.put('Z', n -> "[+-]\\d{4}|Z|GMT[+-]\\d{2}:\\d{2}");
        letters.put('G', n -> "[A-Za-z]+");
        letters.put('Q', n -> "\\d|Q\\d|[A-Za-z ]+");
        letters.put('q', n -> "\\d|Q\\d");
        letters.put('w', n -> "\\d{1,2}");
        letters.put('W', n -> "\\d");
        letters.put('F', n -> "\\d");
        letters.put('Y', n -> n >= 4
                ? "\\d{4}"
                : "\\d{2}");
        letters.put('p', n -> " *");
        return Map.copyOf(letters);
    }

    /// A regex matching the strings a Java date pattern parses (roughly: counts of digits and letter words).
    ///
    /// @param pattern A Java date pattern.
    /// @return The regex, or null for a pattern with letters this doesn't know (or an unclosed quote).
    public static Pattern patternRegex(final String pattern) {
        final StringBuilder out = new StringBuilder("^");
        int i = 0;
        while (i < pattern.length()) {
            final char ch = pattern.charAt(i);
            if (ch == '\'') {
                final int end = pattern.indexOf('\'', i + 1);
                if (end == i + 1) {
                    // '' is a literal quote.
                    out.append('\'');
                    i += 2;
                    continue;
                }
                if (end < 0) {
                    return null;
                }
                out.append(PyText.escape(pattern.substring(i + 1, end)));
                i = end + 1;
                continue;
            }
            if (Character.isLetter(ch)) {
                final IntFunction<String> letter = LETTER.get(ch);
                if (letter == null) {
                    return null;
                }
                int n = 1;
                while (i + n < pattern.length() && pattern.charAt(i + n) == ch) {
                    n++;
                }
                out.append(letter.apply(n));
                i += n;
                continue;
            }
            out.append(PyText.escape(String.valueOf(ch)));
            i++;
        }
        try {
            return PyText.re(out + "$");
        } catch (final PatternSyntaxException e) {
            return null;
        }
    }

    /// Checks a time format against a sample's values.
    ///
    /// @param pattern A Java date pattern, or an epoch name (`epoch seconds` or `epoch_s` and so on).
    /// @param values  The sample's values.
    /// @return Why the pattern doesn't fit the values, or null when every non-empty value matches it.
    public static String checkTimeFormat(final String pattern, final List<String> values) {
        if (EPOCH_NAMES.containsKey(pattern) || EPOCH_NAMES.containsValue(pattern)) {
            // Seconds may carry a fraction (GELF's timestamp, 1791676803.7), which epoch_s reads as a decimal;
            // milliseconds and finer are whole.
            final String allowed = switch (pattern) {
                case "epoch_s", "epoch seconds" -> "\\d{9,13}(\\.\\d+)?";
                case "epoch_us", "epoch microseconds" -> "\\d{12,16}";
                case "epoch_ns", "epoch nanoseconds" -> "\\d{15,19}";
                default -> "\\d{9,13}";
            };
            final Pattern regex = PyText.re(allowed);
            final List<String> bad = new ArrayList<>();
            for (final String value : values) {
                if (!PyText.isBlank(value) && !PyText.fullmatch(regex, PyText.strip(value))) {
                    bad.add(value);
                }
            }
            return bad.isEmpty()
                    ? null
                    : "time_format " + PyText.repr(pattern) + " expects digits only" + (allowed.contains(".")
                            ? " (a fraction of a second allowed)"
                            : "") + "; the sample has " + PyText.repr(bad.subList(0, Math.min(3, bad.size())));
        }
        final Pattern regex = patternRegex(pattern);
        if (regex == null) {
            return "time_format " + PyText.repr(pattern) + " is not a Java date pattern this check understands";
        }
        final List<String> bad = new ArrayList<>();
        for (final String value : values) {
            if (!PyText.isBlank(value) && !PyText.match(regex, PyText.strip(value))) {
                bad.add(value);
            }
        }
        if (bad.isEmpty()) {
            return null;
        }
        final String inferred = inferTimePattern(bad.getFirst());
        return "time_format " + PyText.repr(pattern) + " does not match the sample's values, e.g. "
               + PyText.repr(bad.subList(0, Math.min(3, bad.size())))
               + (inferred != null && !inferred.equals(pattern)
                ? "; the values look like " + PyText.repr(inferred)
                : "");
    }


    // --------------------------------------------------------------------------------


    // One value read left to right into pattern letters.
    private static final class Reading {

        private final String text;
        private final List<String> out = new ArrayList<>();
        private int i;
        private boolean seenDate;
        private boolean seenTime;

        Reading(final String text) {
            this.text = text;
        }

        String pattern() {
            while (i < text.length()) {
                final String rest = text.substring(i);
                final Matcher digits = PyText.matchAt(DIGITS, rest);
                final Boolean read;
                if (digits != null) {
                    read = readDigits(rest, digits.group());
                } else {
                    final Matcher letters = PyText.matchAt(LETTERS, rest);
                    read = letters != null
                            ? readWord(rest, letters.group())
                            : readOther(rest);
                }
                if (!read) {
                    return null;
                }
            }
            if (!seenDate) {
                return null;
            }
            // Letters that are not pattern letters must be quoted; here only T and Z appear, already quoted.
            return PyText.strip(String.join("", out));
        }

        private boolean readDigits(final String rest, final String digits) {
            if (!seenDate && digits.length() == 4) {
                out.add("yyyy");
                // yyyy-MM-dd or yyyy/MM/dd follows.
                final Matcher dm = PyText.matchAt(YEAR_FIRST_DATE, rest.substring(4));
                if (dm != null) {
                    out.add(dm.group(1));
                    out.add(dm.group(2).length() == 2
                            ? "MM"
                            : "M");
                    out.add(dm.group(1));
                    out.add(dm.group(3).length() == 2
                            ? "dd"
                            : "d");
                    i += 4 + dm.group().length();
                } else {
                    i += 4;
                }
                seenDate = true;
                return true;
            }
            if (!seenDate && digits.length() <= 2) {
                if (readDayFirstDate(rest) || readDayMonthName(rest)) {
                    return true;
                }
                if (out.size() >= 2 && " ".equals(out.getLast())
                    && ("MMM".equals(out.get(out.size() - 2)) || "MMMM".equals(out.get(out.size() - 2)))) {
                    // 'Sep 28' / 'Sep  8': a year may follow after the time (RFC 3164 has none; 'Sep 28 10:00:00
                    // 2026' has).
                    out.add(digits.length() == 2
                            ? "dd"
                            : "d");
                    i += digits.length();
                    return true;
                }
            }
            if (!seenTime && digits.length() <= 2 && readTime(rest)) {
                return true;
            }
            if (seenTime && digits.length() == 4 && !out.contains("yyyy")) {
                // 'Sep 28 10:00:00 2026'.
                out.add("yyyy");
                i += 4;
                seenDate = true;
                return true;
            }
            return false;
        }

        // Day first (dd/MM/yyyy, dd-MM-yyyy), else MM/dd/yyyy.
        private boolean readDayFirstDate(final String rest) {
            final Matcher dm = PyText.matchAt(NUMERIC_DATE, rest);
            if (dm == null) {
                return false;
            }
            final String a = dm.group(1);
            final String separator = dm.group(2);
            final String b = dm.group(3);
            final String year = dm.group(4);
            final boolean firstIsDay = Integer.parseInt(a) > 12
                                       || (Integer.parseInt(b) <= 12 && Integer.parseInt(a) <= 12
                                           && !"/".equals(separator));
            final String day = firstIsDay
                    ? "dd"
                    : "MM";
            final String month = firstIsDay
                    ? "MM"
                    : "dd";
            out.add(repeat(day, a.length(), 2));
            out.add(separator);
            out.add(repeat(month, b.length(), 2));
            out.add(separator);
            out.add(year.length() == 4
                    ? "yyyy"
                    : "yy");
            i += dm.group().length();
            seenDate = true;
            return true;
        }

        // dd-MMM-yyyy, dd MMM yyyy.
        private boolean readDayMonthName(final String rest) {
            final Matcher dm = PyText.matchAt(DAY_MONTH_NAME, rest);
            if (dm == null || !MONTHS.contains(PyText.head(dm.group(2).toLowerCase(Locale.ROOT), 3))) {
                return false;
            }
            final int dayLength = dm.group(1).length();
            final String separator1 = String.valueOf(rest.charAt(dayLength));
            final String separator2 = rest.substring(dayLength + 1 + dm.group(2).length(),
                    dm.end(3) - dm.group(3).length());
            out.add(dayLength == 2
                    ? "dd"
                    : "d");
            out.add(separator1);
            out.add(dm.group(2).length() == 3
                    ? "MMM"
                    : "MMMM");
            out.add(separator2);
            out.add(dm.group(3).length() == 4
                    ? "yyyy"
                    : "yy");
            i += dm.group().length();
            seenDate = true;
            return true;
        }

        private boolean readTime(final String rest) {
            final Matcher tm = PyText.matchAt(TIME, rest);
            if (tm == null) {
                return false;
            }
            final String hour = tm.group(1);
            final String seconds = tm.group(3);
            final String fractionSeparator = tm.group(4);
            final String fraction = tm.group(5);
            final Matcher amPm = PyText.matchAt(AM_PM, rest.substring(tm.end()));
            final String h = amPm != null
                    ? repeat("hh", hour.length(), 2)
                    : repeat("HH", hour.length(), 2);
            out.add(h);
            out.add(":");
            out.add("mm");
            if (seconds != null) {
                out.add(":");
                out.add("ss");
            }
            if (fraction != null) {
                out.add(fractionSeparator);
                out.add("S".repeat(fraction.length()));
            }
            i += tm.end();
            if (amPm != null) {
                out.add(amPm.group().startsWith(" ")
                        ? " "
                        : "");
                out.add("a");
                i += amPm.end();
            }
            seenTime = true;
            return true;
        }

        private boolean readWord(final String rest, final String word) {
            final String low = word.toLowerCase(Locale.ROOT);
            final Matcher zone = PyText.matchAt(ZONE_NAME, rest);
            if (seenTime && (zone != null || "z".equals(low))) {
                // X parses a literal Z as UTC, as a zone offset would be; a region (Australia/Sydney) is a zone id,
                // VV; an abbreviation (AEST, UTC) a zone name, z.
                out.add("Z".equals(word)
                        ? "X"
                        : zone != null && zone.group().contains("/")
                                ? "VV"
                                : "z");
                i += zone != null
                        ? zone.end()
                        : 1;
                return true;
            }
            if ("T".equals(word) && seenDate && !seenTime) {
                out.add("'T'");
                i += 1;
                return true;
            }
            if (MONTHS.contains(PyText.head(low, 3)) && (word.length() == 3 || MONTH_FULL.contains(low))
                && !seenDate) {
                // 'Sep 28 ...' or 'September 28, 2026': month, day, maybe year.
                final Matcher dm = PyText.matchAt(MONTH_NAME_DATE, rest);
                if (dm == null) {
                    return false;
                }
                out.add(word.length() == 3
                        ? "MMM"
                        : "MMMM");
                final String gap = " ".repeat(Math.max(0, dm.start(1) - word.length()));
                out.add(gap.isEmpty()
                        ? " "
                        : gap);
                out.add(dm.group(1).length() == 2
                        ? "dd"
                        : "d");
                if (dm.group(2) != null) {
                    out.add(rest.substring(dm.end(1), dm.start(2)));
                    out.add("yyyy");
                }
                i += dm.end();
                seenDate = true;
                return true;
            }
            if (DAYS.contains(PyText.head(low, 3)) && (word.length() == 3 || word.length() > 5)) {
                out.add(word.length() == 3
                        ? "EEE"
                        : "EEEE");
                i += word.length();
                return true;
            }
            return false;
        }

        private boolean readOther(final String rest) {
            final char ch = text.charAt(i);
            final Matcher bracketed = PyText.matchAt(BRACKETED_ZONE, rest);
            if (bracketed != null && seenTime) {
                // An offset with its region after it, as Java writes a zoned time:
                // 2026-10-01T18:00:00+10:00[Australia/Sydney].
                out.add("'['VV']'");
                i += bracketed.end();
                return true;
            }
            if ((ch == '+' || ch == '-') && seenTime && PyText.match(OFFSET, rest)) {
                final boolean colon = PyText.head(rest, 6).contains(":");
                out.add(colon
                        ? "XXX"
                        : "Z");
                i += colon
                        ? 6
                        : 5;
                return true;
            }
            if (" -/:.,T".indexOf(ch) >= 0) {
                out.add(String.valueOf(ch));
                i += 1;
                return true;
            }
            return false;
        }
    }
}
