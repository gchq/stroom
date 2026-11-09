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

import stroom.ai.impl.tools.ToolText;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.ParsePosition;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQueries;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/// Timestamp patterns: a Java (`stroom:format-date`) pattern inferred from a value, and a pattern checked against
/// values.
///
/// The profiler used to know ten shapes; anything else was typed as a string and the model guessed a pattern,
/// which `stroom:format-date` then failed silently, leaving TimeCreated empty. Here a value is read left to right
/// into pattern letters, and any pattern (inferred or the model's) is tried on the sample's values before the XSLT
/// is stepped, with a parser built as `stroom:format-date` builds its own, so it reads what Stroom reads.
public final class TimePatterns {

    /// What profiling calls an epoch value, and its time_format.
    public static final Map<String, String> EPOCH_NAMES = epochNames();

    private static final Set<String> MONTHS = Set.of("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep",
            "oct", "nov", "dec");
    private static final Set<String> DAYS = Set.of("mon", "tue", "wed", "thu", "fri", "sat", "sun");
    private static final Set<String> MONTH_FULL = Set.of("january", "february", "march", "april", "may", "june",
            "july", "august", "september", "october", "november", "december");
    private static final Pattern ZONE_NAME = ToolText.re(
            "(?:UTC|GMT|Z(?![a-z])|[A-Z]{3,4}T|[A-Z][a-z]+/[A-Z][A-Za-z_]+)");
    // Epoch times are known by range, 1990 to 2100 (until 2026-11-07 by a leading 1 and a width: Sep 2001 to May
    // 2033 only). Seconds may carry a fraction.
    private static final Pattern EPOCH_DIGITS = ToolText.re("(\\d{9,19})(\\.\\d{1,9})?");
    private static final long EPOCH_FROM = 631_152_000L;
    private static final long EPOCH_TO = 4_102_444_800L;
    // Windows FILETIME: 100 ns ticks since 1601-01-01, also Active Directory's lastLogon and pwdLastSet.
    private static final long FILETIME_EPOCH_SECONDS = 11_644_473_600L;
    private static final long FILETIME_TICKS = 10_000_000L;
    // Digits read as a date only where they are one, in the years epoch times are taken in (1990 to 2099): seen in
    // testing, a file size of 14681927 was taken for a date (month 19), so a field of numbers holding it twice had it
    // put to the user as a stand-in for none.
    private static final String COMPACT_YMD = "(?:199\\d|20\\d\\d)(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])";
    private static final Pattern COMPACT_DATE_TIME = ToolText.re(COMPACT_YMD + "(?:[01]\\d|2[0-3])[0-5]\\d[0-5]\\d");
    private static final Pattern COMPACT_DATE = ToolText.re(COMPACT_YMD);
    private static final Pattern COMPACT_ISO = ToolText.re("\\d{8}T\\d{6}(\\.\\d+)?Z?");
    private static final Pattern FRACTION = ToolText.re("\\.(\\d+)");
    // A pattern's fraction of a second, with its dot: ".SSSSSS".
    private static final Pattern PATTERN_FRACTION = ToolText.re("\\.S+");
    private static final Pattern DIGITS = ToolText.re("\\d+");
    private static final Pattern LETTERS = ToolText.re("[A-Za-z]+");
    private static final Pattern YEAR_FIRST_DATE = ToolText.re("([-/.])(\\d{1,2})\\1(\\d{1,2})");
    private static final Pattern NUMERIC_DATE = ToolText.re("(\\d{1,2})([-/.])(\\d{1,2})\\2(\\d{2,4})");
    private static final Pattern DAY_MONTH_NAME = ToolText.re("(\\d{1,2})[ -/]([A-Za-z]{3,})[ -/,]+(\\d{2,4})");
    private static final Pattern TIME = ToolText.re("(\\d{1,2}):(\\d{2})(?::(\\d{2}))?(?:([.,])(\\d{1,9}))?");
    private static final Pattern AM_PM = ToolText.re("\\s?([AaPp][Mm])");
    private static final Pattern MONTH_NAME_DATE = ToolText.re("[A-Za-z]+\\s+(\\d{1,2})(?:,?\\s+(\\d{4}))?");
    private static final Pattern BRACKETED_ZONE = ToolText.re("\\[([A-Z][a-z]+/[A-Z][A-Za-z_]+)\\]");
    private static final Pattern OFFSET = ToolText.re("[+-]\\d{2}:?\\d{2}");

    private TimePatterns() {
        // Static methods only.
    }

    private static Map<String, String> epochNames() {
        final Map<String, String> names = new LinkedHashMap<>();
        names.put("epoch seconds", "epoch_s");
        names.put("epoch milliseconds", "epoch_ms");
        names.put("epoch microseconds", "epoch_us");
        names.put("epoch nanoseconds", "epoch_ns");
        names.put("windows filetime", "windows_filetime");
        return Map.copyOf(names);
    }

    /// Infers a Java date pattern that parses a value, e.g. `28/Sep/2026:10:00:00 +0000` gives
    /// `dd/MMM/yyyy:HH:mm:ss Z`. Epoch values give `epoch seconds`, `epoch milliseconds` and so on.
    ///
    /// @param pattern A Java time pattern.
    /// @return Whether it reads a zone or an offset (X, x, Z, z, O or V outside quoted text).
    public static boolean hasZone(final String pattern) {
        final String unquoted = pattern.replaceAll("'[^']*'", "");
        return unquoted.chars().anyMatch(c -> "XxZzOV".indexOf(c) >= 0);
    }

    /// @param format A time_format: a pattern, a combined one (`[A][B]`, each field's own), or an epoch name.
    /// @return Whether some value it reads can carry no zone or offset: a pattern, or a section of a combined one,
    /// that reads none. Epoch times and FILETIME are UTC by definition.
    public static boolean zoneless(final String format) {
        if (format == null || format.isEmpty() || isEpoch(format)) {
            return false;
        }
        final List<String> sections = topSections(format);
        return sections.isEmpty()
                ? !hasZone(format)
                : sections.stream().anyMatch(section -> !hasZone(section));
    }

    // A combined format's top-level optional sections ([A][B]), or none when it isn't wholly made of them.
    private static List<String> topSections(final String format) {
        final List<String> out = new ArrayList<>();
        int depth = 0;
        int start = -1;
        boolean quoted = false;
        for (int i = 0; i < format.length(); i++) {
            final char c = format.charAt(i);
            if (c == '\'') {
                quoted = !quoted;
            } else if (!quoted && c == '[') {
                if (depth == 0) {
                    start = i + 1;
                }
                depth++;
            } else if (!quoted && c == ']') {
                depth--;
                if (depth == 0) {
                    out.add(format.substring(start, i));
                }
            } else if (depth == 0) {
                return List.of();
            }
        }
        return out;
    }

    /// @param value A value.
    /// @return The pattern, or null when the value doesn't look like a date and time.
    public static String inferTimePattern(final String value) {
        final String text = ToolText.strip(value);
        // Epoch seconds may carry a fraction (1791712800.123: a Bluecoat column, GELF's timestamp), which epoch_s
        // keeps. Seen in production: a fraction made the value a plain decimal, the time was mapped by hand, and its
        // milliseconds were dropped.
        final String epoch = epochName(text, false);
        if (epoch != null) {
            return epoch;
        }
        if (ToolText.fullmatch(COMPACT_DATE_TIME, text)) {
            return "yyyyMMddHHmmss";
        }
        if (ToolText.fullmatch(COMPACT_DATE, text)) {
            return "yyyyMMdd";
        }
        if (ToolText.fullmatch(COMPACT_ISO, text)) {
            final Matcher fraction = FRACTION.matcher(text);
            return "yyyyMMdd'T'HHmmss" + (fraction.find()
                    ? "." + "S".repeat(fraction.group(1).length())
                    : "") + (text.endsWith("Z")
                    ? "X"
                    : "");
        }
        return new Reading(text).pattern();
    }

    /// Infers a value's pattern as [#inferTimePattern(String)] does, for a field whose name says it holds a time:
    /// an epoch time before Sep 2001 too (a digit narrower, the width of many counts and ids), and a Windows
    /// FILETIME (`windows filetime`), which no unit of epoch time in range can be.
    ///
    /// @param value A value.
    /// @return The pattern, or null when the value doesn't look like a date and time.
    public static String inferNamedTimePattern(final String value) {
        final String text = ToolText.strip(value);
        final String epoch = epochName(text, true);
        return epoch != null
                ? epoch
                : inferTimePattern(text);
    }

    // The epoch time (or with named, FILETIME too) the digits are in 1990 to 2100, by their width; null if none.
    private static String epochName(final String text, final boolean named) {
        final Matcher m = EPOCH_DIGITS.matcher(text);
        if (!m.matches()) {
            return null;
        }
        final String digits = m.group(1);
        final int width = digits.length();
        if (m.group(2) != null) {
            return (width == 10 || (named && width == 9)) && inRange(digits, 1)
                    ? "epoch seconds"
                    : null;
        }
        // Each unit's widths in range: 2001 on (the full width), and before 2001 (a digit narrower) when named.
        final String[] units = {"epoch seconds", "epoch milliseconds", "epoch microseconds", "epoch nanoseconds"};
        final long[] scales = {1L, 1_000L, 1_000_000L, 1_000_000_000L};
        for (int i = 0; i < units.length; i++) {
            final int full = 10 + 3 * i;
            if ((width == full || (named && width == full - 1)) && inRange(digits, scales[i])) {
                return units[i];
            }
        }
        if (named && (width == 17 || width == 18)) {
            final long seconds = Long.parseLong(digits) / FILETIME_TICKS - FILETIME_EPOCH_SECONDS;
            if (seconds >= EPOCH_FROM && seconds < EPOCH_TO) {
                return "windows filetime";
            }
        }
        return null;
    }

    private static boolean inRange(final String digits, final long scale) {
        final long seconds = new BigInteger(digits).divide(BigInteger.valueOf(scale)).longValue();
        return seconds >= EPOCH_FROM && seconds < EPOCH_TO;
    }

    private static String repeat(final String letters, final int length, final int whole) {
        return length == whole
                ? letters
                : letters.substring(0, 1);
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
                case "windows_filetime", "windows filetime" -> "\\d{17,18}";
                default -> "\\d{9,13}";
            };
            final Pattern regex = ToolText.re(allowed);
            final List<String> bad = new ArrayList<>();
            for (final String value : values) {
                if (!ToolText.isBlank(value) && !ToolText.fullmatch(regex, ToolText.strip(value))) {
                    bad.add(value);
                }
            }
            return bad.isEmpty()
                    ? null
                    : "time_format " + ToolText.repr(pattern) + " expects digits only" + (allowed.contains(".")
                            ? " (a fraction of a second allowed)"
                            : "") + "; the sample has " + ToolText.repr(bad.subList(0, Math.min(3, bad.size())));
        }
        final DateTimeFormatter formatter = stroomFormatter(pattern);
        if (formatter == null) {
            return "time_format " + ToolText.repr(pattern) + " is not a Java date pattern Stroom can read";
        }
        final List<String> bad = misfits(formatter, values);
        if (bad.isEmpty()) {
            return null;
        }
        final String inferred = inferTimePattern(bad.getFirst());
        // Only where the pattern reads some values: where it reads none, the values' own pattern is the advice.
        final long given = values.stream().filter(value -> !ToolText.isBlank(value)).count();
        final String oneForAll = bad.size() < given
                ? oneForAll(pattern, inferred, values)
                : null;
        return "time_format " + ToolText.repr(pattern) + " does not match the sample's values, e.g. "
               + ToolText.repr(bad.subList(0, Math.min(3, bad.size())))
               + (oneForAll != null
                ? "; one pattern reads them all: " + ToolText.repr(oneForAll)
                : inferred != null && !inferred.equals(pattern)
                        ? "; the values look like " + ToolText.repr(inferred)
                        : "");
    }

    /// One format reading every value of several fields that each write a time their own way: their patterns as
    /// optional sections, in order (`[MMM dd yyyy HH:mm:ss][yyyy-MM-dd'T'HH:mm:ssXXX]`), checked on the values as
    /// Stroom reads them. Epoch times combine with nothing but themselves.
    ///
    /// @param patterns Each field's pattern (or epoch name), in the order they are read.
    /// @param values   Every field's values.
    /// @return The format, or null when no one format reads them all.
    public static String combined(final List<String> patterns, final List<String> values) {
        final List<String> distinct = patterns.stream().distinct().toList();
        if (distinct.isEmpty()) {
            return null;
        }
        if (distinct.stream().anyMatch(TimePatterns::isEpoch)) {
            return distinct.size() == 1
                    ? distinct.getFirst()
                    : null;
        }
        final String format = distinct.size() == 1
                ? distinct.getFirst()
                : distinct.stream().map(pattern -> "[" + pattern + "]").collect(Collectors.joining());
        final DateTimeFormatter formatter = stroomFormatter(format);
        return formatter != null && misfits(formatter, values).isEmpty()
                ? format
                : null;
    }

    /// @param pattern A pattern or an epoch name (in words or as a time_format).
    /// @return Whether it is an epoch time (or a FILETIME), which a number holds.
    public static boolean isEpoch(final String pattern) {
        return EPOCH_NAMES.containsKey(pattern) || EPOCH_NAMES.containsValue(pattern);
    }

    /// A value's time as epoch milliseconds, for telling times apart (how far one field's time is from another's),
    /// not for writing: a time without a zone is read as UTC.
    ///
    /// @param pattern The value's pattern, or an epoch name.
    /// @param value   The value.
    /// @return Its epoch milliseconds, or null when it doesn't parse or names no date (a time without a year).
    public static Long epochMillis(final String pattern, final String value) {
        if (pattern == null || ToolText.isBlank(value)) {
            return null;
        }
        final String text = ToolText.strip(value);
        try {
            final String format = EPOCH_NAMES.getOrDefault(pattern, pattern);
            switch (format) {
                case "epoch_s" -> {
                    return new BigDecimal(text).movePointRight(3).longValue();
                }
                case "epoch_ms" -> {
                    return Long.parseLong(text);
                }
                case "epoch_us" -> {
                    return Long.parseLong(text) / 1_000L;
                }
                case "epoch_ns" -> {
                    return new BigInteger(text).divide(BigInteger.valueOf(1_000_000L)).longValue();
                }
                case "windows_filetime" -> {
                    return Long.parseLong(text) / (FILETIME_TICKS / 1_000L) - FILETIME_EPOCH_SECONDS * 1_000L;
                }
                default -> {
                    return parsedMillis(format, text);
                }
            }
        } catch (final RuntimeException e) {
            return null;
        }
    }

    private static Long parsedMillis(final String pattern, final String text) {
        final DateTimeFormatter formatter = stroomFormatter(pattern);
        if (formatter == null) {
            return null;
        }
        final TemporalAccessor parsed = formatter.parse(text);
        final LocalDate date = parsed.query(TemporalQueries.localDate());
        final LocalTime time = parsed.query(TemporalQueries.localTime());
        if (date == null) {
            return null;
        }
        final ZoneId zone = Objects.requireNonNullElse(parsed.query(TemporalQueries.zone()), ZoneOffset.UTC);
        return date.atTime(Objects.requireNonNullElse(time, LocalTime.MIDNIGHT)).atZone(zone).toInstant()
                .toEpochMilli();
    }

    /// A parser as Stroom's `stroom:format-date` builds one: lenient (a fraction of a second of any width for `S`),
    /// case-insensitive, English. Seen in testing: the check read `[.SSSSSS]` (an optional fraction, which Stroom
    /// reads) as literal brackets and refused it, and the agent stripped the fraction with an xpath instead.
    ///
    /// @param pattern A Java date pattern.
    /// @return The parser, or null if the pattern isn't one.
    static DateTimeFormatter stroomFormatter(final String pattern) {
        try {
            return new DateTimeFormatterBuilder()
                    .parseLenient()
                    .parseCaseInsensitive()
                    .appendPattern(pattern)
                    .toFormatter(Locale.ENGLISH);
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }

    // The values the parser doesn't read whole (blank ones aside).
    private static List<String> misfits(final DateTimeFormatter formatter, final List<String> values) {
        final List<String> bad = new ArrayList<>();
        for (final String value : values) {
            if (!ToolText.isBlank(value) && !reads(formatter, ToolText.strip(value))) {
                bad.add(value);
            }
        }
        return bad;
    }

    // Whether the parser reads the whole value: its text, not whether the date it names is complete (Stroom fills a
    // missing year or date from the stream).
    private static boolean reads(final DateTimeFormatter formatter, final String value) {
        final ParsePosition position = new ParsePosition(0);
        try {
            formatter.parseUnresolved(value, position);
        } catch (final RuntimeException e) {
            return false;
        }
        return position.getErrorIndex() < 0 && position.getIndex() == value.length();
    }

    /// One pattern that reads every value where the pattern given reads some: its fraction of a second made
    /// optional (`[.SSSSSS]`), a fraction added as optional, or the two layouts as alternatives
    /// (`[pattern][other]`), each tried on the values as Stroom reads them.
    ///
    /// @param pattern  The pattern given.
    /// @param inferred The pattern a value it doesn't read looks like, or null.
    /// @param values   The sample's values.
    /// @return The pattern, or null when none of these reads them all.
    static String oneForAll(final String pattern, final String inferred, final List<String> values) {
        final List<String> candidates = new ArrayList<>();
        final Matcher fraction = PATTERN_FRACTION.matcher(pattern);
        if (fraction.find()) {
            candidates.add(pattern.substring(0, fraction.start()) + "[" + fraction.group() + "]"
                           + pattern.substring(fraction.end()));
        } else if (pattern.contains("ss")) {
            final int at = pattern.lastIndexOf("ss") + 2;
            candidates.add(pattern.substring(0, at) + "[.SSSSSSSSS]" + pattern.substring(at));
        }
        if (inferred != null && !inferred.equals(pattern) && !inferred.startsWith("epoch")) {
            candidates.add("[" + pattern + "][" + inferred + "]");
        }
        for (final String candidate : candidates) {
            final DateTimeFormatter formatter = stroomFormatter(candidate);
            if (formatter != null && misfits(formatter, values).isEmpty()) {
                return candidate;
            }
        }
        return null;
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
                final Matcher digits = ToolText.matchAt(DIGITS, rest);
                final Boolean read;
                if (digits != null) {
                    read = readDigits(rest, digits.group());
                } else {
                    final Matcher letters = ToolText.matchAt(LETTERS, rest);
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
            return ToolText.strip(String.join("", out));
        }

        private boolean readDigits(final String rest, final String digits) {
            if (!seenDate && digits.length() == 4) {
                out.add("yyyy");
                // yyyy-MM-dd or yyyy/MM/dd follows.
                final Matcher dm = ToolText.matchAt(YEAR_FIRST_DATE, rest.substring(4));
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
            final Matcher dm = ToolText.matchAt(NUMERIC_DATE, rest);
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
            final Matcher dm = ToolText.matchAt(DAY_MONTH_NAME, rest);
            if (dm == null || !MONTHS.contains(ToolText.head(dm.group(2).toLowerCase(Locale.ROOT), 3))) {
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
            final Matcher tm = ToolText.matchAt(TIME, rest);
            if (tm == null) {
                return false;
            }
            final String hour = tm.group(1);
            final String seconds = tm.group(3);
            final String fractionSeparator = tm.group(4);
            final String fraction = tm.group(5);
            final Matcher amPm = ToolText.matchAt(AM_PM, rest.substring(tm.end()));
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
            final Matcher zone = ToolText.matchAt(ZONE_NAME, rest);
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
            if (MONTHS.contains(ToolText.head(low, 3)) && (word.length() == 3 || MONTH_FULL.contains(low))
                && !seenDate) {
                // 'Sep 28 ...' or 'September 28, 2026': month, day, maybe year.
                final Matcher dm = ToolText.matchAt(MONTH_NAME_DATE, rest);
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
            if (DAYS.contains(ToolText.head(low, 3)) && (word.length() == 3 || word.length() > 5)) {
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
            final Matcher bracketed = ToolText.matchAt(BRACKETED_ZONE, rest);
            if (bracketed != null && seenTime) {
                // An offset with its region after it, as Java writes a zoned time:
                // 2026-10-01T18:00:00+10:00[Australia/Sydney].
                out.add("'['VV']'");
                i += bracketed.end();
                return true;
            }
            if ((ch == '+' || ch == '-') && seenTime && ToolText.match(OFFSET, rest)) {
                final boolean colon = ToolText.head(rest, 6).contains(":");
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
