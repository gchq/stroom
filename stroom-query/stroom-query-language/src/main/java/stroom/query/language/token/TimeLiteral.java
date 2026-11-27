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

package stroom.query.language.token;

import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// The one reader of time literals such as `30m` and `1M`, so that the same text means the same thing
/// in an expression, in a `where` term or time range, in a `window` clause and in the `within` clause of
/// a join.
///
/// Unit letters are case-sensitive: `m` is a minute and `M` is a month.
public final class TimeLiteral {

    /// A regular expression for one literal, with no capturing groups. The digits are possessive, as a
    /// unit never starts with a digit, which saves backtracking into a long run of digits with no unit.
    public static final String REGEX = "\\d++(?:ns|ms|s|m|h|d|w|M|y)";

    /// A regular expression for a literal of one or more numbers and units, such as `1d2h`, with no
    /// capturing groups.
    public static final String COMPOUND_REGEX = "(?:" + REGEX + ")+";

    private static final Pattern PART_PATTERN = Pattern.compile("(\\d++)([A-Za-z]++)");
    // Parts with spaces around them but not inside them.
    private static final Pattern SPACED_PATTERN = Pattern.compile("\\s*+-?\\s*+(?:\\d++[A-Za-z]++\\s*+)++");
    private static final Pattern WHITESPACE = Pattern.compile("\\s");
    private static final String UNITS_MESSAGE = "Use ms, s, m, h, d, w, M (month) or y";
    private static final long NANOS_PER_MILLI = 1_000_000L;
    private static final long MILLIS_PER_SECOND = 1_000L;
    private static final long MILLIS_PER_MINUTE = 60 * MILLIS_PER_SECOND;
    private static final long MILLIS_PER_HOUR = 60 * MILLIS_PER_MINUTE;
    private static final long MILLIS_PER_DAY = 24 * MILLIS_PER_HOUR;
    private static final long DAYS_PER_WEEK = 7L;

    private TimeLiteral() {
    }

    /// Reads a literal of one number and one unit, such as `30m`.
    ///
    /// @param text The text of the literal.
    /// @return The number and unit.
    /// @throws DateTimeException If the text is not one number and one known unit. The message is fit
    ///                           to show the query's author.
    public static SimpleDuration parse(final String text) {
        final List<SimpleDuration> parts = parseCompound(text);
        if (parts.size() != 1) {
            throw new DateTimeException("'" + text + "' is not one number and one unit, such as 1d");
        }
        return parts.getFirst();
    }

    /// Reads a literal of one or more numbers and units, such as `1d2h`.
    ///
    /// @param text The text of the literal.
    /// @return The numbers and units, in the order they are written.
    /// @throws DateTimeException If any part of the text is not a number and a known unit. The message
    ///                           is fit to show the query's author.
    public static List<SimpleDuration> parseCompound(final String text) {
        Objects.requireNonNull(text, "Text is null");
        final String trimmed = text.trim();
        final List<SimpleDuration> parts = new ArrayList<>();
        final Matcher matcher = PART_PATTERN.matcher(trimmed);
        int end = 0;
        while (matcher.find() && matcher.start() == end) {
            parts.add(createPart(trimmed, matcher.group(1), matcher.group(2)));
            end = matcher.end();
        }
        if (parts.isEmpty() || end != trimmed.length()) {
            throw new DateTimeException("'" + text + "' is not a number and a unit, such as 1d");
        }
        return parts;
    }

    private static SimpleDuration createPart(final String text, final String number, final String unit) {
        final TimeUnit timeUnit = TimeUnit.fromShortForm(unit);
        if (timeUnit == null) {
            throw new DateTimeException("Unknown unit '" + unit + "' in '" + text + "'. " + UNITS_MESSAGE);
        }
        try {
            return new SimpleDuration(Long.parseLong(number), timeUnit);
        } catch (final NumberFormatException e) {
            throw new DateTimeException("The number in '" + text + "' is too large", e);
        }
    }

    /// Tells whether a literal is an amount of calendar time, so is a period and not a duration.
    ///
    /// @param literal The literal.
    /// @return True if the literal is in days, weeks, months or years.
    public static boolean isPeriod(final SimpleDuration literal) {
        return switch (literal.getTimeUnit()) {
            case DAYS, WEEKS, MONTHS, YEARS -> true;
            case NANOSECONDS, MILLISECONDS, SECONDS, MINUTES, HOURS -> false;
        };
    }

    /// Gets the period that a literal means.
    ///
    /// @param literal The literal.
    /// @return The period.
    /// @throws DateTimeException If the literal is not in days, weeks, months or years, or is too long. The
    ///                           message is fit to show the query's author.
    public static Period toPeriod(final SimpleDuration literal) {
        try {
            final int amount = Math.toIntExact(literal.getTime());
            return switch (literal.getTimeUnit()) {
                case DAYS -> Period.ofDays(amount);
                case WEEKS -> Period.ofWeeks(amount);
                case MONTHS -> Period.ofMonths(amount);
                case YEARS -> Period.ofYears(amount);
                case NANOSECONDS, MILLISECONDS, SECONDS, MINUTES, HOURS -> throw new DateTimeException(
                        "'" + literal + "' is not a period");
            };
        } catch (final ArithmeticException e) {
            throw new DateTimeException("'" + literal + "' is too long", e);
        }
    }

    /// Writes a period as one literal.
    ///
    /// @param period The period.
    /// @return The literal, such as `3M`, or `null` if the period needs more than one unit.
    public static String formatPeriod(final Period period) {
        if (period.getYears() == 0 && period.getMonths() == 0) {
            return period.getDays() + TimeUnit.DAYS.getShortForm();
        } else if (period.getYears() == 0 && period.getDays() == 0) {
            return period.getMonths() + TimeUnit.MONTHS.getShortForm();
        } else if (period.getMonths() == 0 && period.getDays() == 0) {
            return period.getYears() + TimeUnit.YEARS.getShortForm();
        }
        return null;
    }

    /// Writes a length as one literal, in the largest unit up to hours that is exact. Days are not used, as
    /// not every day is 24 hours long.
    ///
    /// @param millis The length in milliseconds.
    /// @return The literal, such as `90m`.
    public static String formatExactMillis(final long millis) {
        final TimeUnit timeUnit;
        final long divisor;
        if (millis == 0) {
            timeUnit = TimeUnit.MILLISECONDS;
            divisor = 1L;
        } else if (millis % MILLIS_PER_HOUR == 0) {
            timeUnit = TimeUnit.HOURS;
            divisor = MILLIS_PER_HOUR;
        } else if (millis % MILLIS_PER_MINUTE == 0) {
            timeUnit = TimeUnit.MINUTES;
            divisor = MILLIS_PER_MINUTE;
        } else if (millis % MILLIS_PER_SECOND == 0) {
            timeUnit = TimeUnit.SECONDS;
            divisor = MILLIS_PER_SECOND;
        } else {
            timeUnit = TimeUnit.MILLISECONDS;
            divisor = 1L;
        }
        return millis / divisor + timeUnit.getShortForm();
    }

    /// Writes a length exactly, with each unit from days down that it needs.
    ///
    /// @param millis The length in milliseconds.
    /// @return The length, such as `1d2h30m` or `-12h`.
    public static String formatCompoundMillis(final long millis) {
        if (millis == 0 || millis == Long.MIN_VALUE) {
            return millis + TimeUnit.MILLISECONDS.getShortForm();
        }
        final StringBuilder sb = new StringBuilder();
        if (millis < 0) {
            sb.append('-');
        }
        long remainder = Math.abs(millis);
        remainder = appendUnit(sb, remainder, MILLIS_PER_DAY, TimeUnit.DAYS);
        remainder = appendUnit(sb, remainder, MILLIS_PER_HOUR, TimeUnit.HOURS);
        remainder = appendUnit(sb, remainder, MILLIS_PER_MINUTE, TimeUnit.MINUTES);
        remainder = appendUnit(sb, remainder, MILLIS_PER_SECOND, TimeUnit.SECONDS);
        appendUnit(sb, remainder, 1L, TimeUnit.MILLISECONDS);
        return sb.toString();
    }

    private static long appendUnit(final StringBuilder sb,
                                   final long millis,
                                   final long unitMillis,
                                   final TimeUnit timeUnit) {
        if (millis >= unitMillis) {
            sb.append(millis / unitMillis).append(timeUnit.getShortForm());
        }
        return millis % unitMillis;
    }

    /// Reads a length written by [#formatCompoundMillis(long)], which may also have spaces between its parts.
    ///
    /// @param text The text.
    /// @return The length in milliseconds.
    /// @throws DateTimeException If any part of the text has no exact length. The message is fit to show the
    ///                           query's author.
    public static long parseCompoundMillis(final String text) {
        if (!SPACED_PATTERN.matcher(text).matches()) {
            throw new DateTimeException("'" + text + "' is not a number and a unit, such as 1d");
        }
        final String compact = WHITESPACE.matcher(text).replaceAll("");
        final boolean negative = compact.startsWith("-");
        long total = 0;
        try {
            for (final SimpleDuration part : parseCompound(negative
                    ? compact.substring(1)
                    : compact)) {
                total = Math.addExact(total, toExactMillis(part));
            }
        } catch (final ArithmeticException e) {
            throw new DateTimeException("'" + text + "' is too long", e);
        }
        return negative
                ? -total
                : total;
    }

    /// Gets the exact length of a literal, with a day as 24 hours and a week as 7 of those days.
    ///
    /// @param literal The literal.
    /// @return The length in milliseconds.
    /// @throws DateTimeException If the literal has no exact length in milliseconds, because it is in
    ///                           months or years, is finer than a millisecond, or is too long. The
    ///                           message is fit to show the query's author.
    public static long toExactMillis(final SimpleDuration literal) {
        final long time = literal.getTime();
        try {
            return switch (literal.getTimeUnit()) {
                case NANOSECONDS -> {
                    if (time % NANOS_PER_MILLI != 0) {
                        throw new DateTimeException("'" + literal + "' can't be used as a duration, as "
                                                    + "durations are held to the millisecond");
                    }
                    yield time / NANOS_PER_MILLI;
                }
                case MILLISECONDS -> time;
                case SECONDS -> Duration.ofSeconds(time).toMillis();
                case MINUTES -> Duration.ofMinutes(time).toMillis();
                case HOURS -> Duration.ofHours(time).toMillis();
                case DAYS -> Duration.ofDays(time).toMillis();
                case WEEKS -> Duration.ofDays(Math.multiplyExact(time, DAYS_PER_WEEK)).toMillis();
                case MONTHS, YEARS -> throw new DateTimeException("'" + literal + "' can't be used as a "
                                                                  + "duration, as months and years vary in "
                                                                  + "length");
            };
        } catch (final ArithmeticException e) {
            throw new DateTimeException("'" + literal + "' is too long", e);
        }
    }
}
