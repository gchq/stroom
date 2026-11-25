/*
 * Copyright 2024 Crown Copyright
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

package stroom.query.language.functions;

import stroom.query.api.DurationPattern;
import stroom.query.language.token.TimeLiteral;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.function.LongFunction;
import java.util.function.ToLongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ValDurationUtil {

    // Numbers and exact units such as 1d2h30m, -12h or 1h 30m, as a duration is shown.
    private static final Pattern EXACT_PATTERN = Pattern.compile("\\s*-?\\s*(?:\\d++(?:ms|[wdhms])\\s*+)++");
    private static final Pattern ISO_PATTERN = Pattern.compile("[+-]?[Pp][-+\\dTt].*");
    // ISO 8601 text that has months or years, with or without the time that makes it neither a period nor a duration.
    private static final Pattern ISO_MONTHS_PATTERN = Pattern.compile(
            "[+-]?P(?:[+-]?\\d++[WD])*+[+-]?\\d++[YM](?:[+-]?\\d++[YMWD])*+(?:T(?:[+-]?\\d++(?:\\.\\d++)?[HMS])++)?+",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern UNITS_PATTERN = Pattern.compile(
            "([+-]?)\\s*+((?:(?:\\d++(?:\\.\\d*+)?|\\.\\d++)\\s*+[A-Za-z]++\\s*+)++)");
    private static final Pattern UNIT_PATTERN = Pattern.compile("([\\d.]++)\\s*+([A-Za-z]++)");
    private static final Pattern QUOTED_PATTERN = Pattern.compile("['\"]?+(.*?)['\"]?");
    static final String PARSE_ERROR_MESSAGE = "Text cannot be parsed to a Duration";

    /// Formats a duration in its largest unit, with a decimal place if it is less than ten of them.
    ///
    /// @param value A duration, or any value that can be read as one.
    /// @return The text, such as `1.5h`, or whatever [#toDuration(Val)] gives if it is not a duration.
    public static Val formatDuration(final Val value) {
        return format(value, ValDurationUtil::formatLargestUnit);
    }

    /// Formats a duration with a pattern.
    ///
    /// @param value   A duration, or any value that can be read as one.
    /// @param pattern The pattern.
    /// @return The text, or whatever [#toDuration(Val)] gives if it is not a duration.
    public static Val formatDuration(final Val value, final DurationPattern pattern) {
        return format(value, pattern::format);
    }

    /// Formats a duration as ISO 8601 text.
    ///
    /// @param value A duration, or any value that can be read as one.
    /// @return The text, such as `PT1H30M`, or whatever [#toDuration(Val)] gives if it is not a duration.
    public static Val formatISODuration(final Val value) {
        return format(value, millis -> Duration.ofMillis(millis).toString());
    }

    private static Val format(final Val value, final LongFunction<String> formatter) {
        final Val duration = toDuration(value);
        return duration instanceof ValDuration
                ? ValString.create(formatter.apply(duration.toLong()))
                : duration;
    }

    private static String formatLargestUnit(final long millis) {
        if (millis >= 0) {
            return ModelStringUtil.formatDurationString(millis, true);
        }
        // ModelStringUtil would only use milliseconds. The lowest long has no positive equivalent, but is only a
        // millisecond from one that does.
        return "-" + ModelStringUtil.formatDurationString(-Math.max(millis, -Long.MAX_VALUE), true);
    }

    /// Reads a value as a duration.
    ///
    /// @param val A duration, a period of only days, a number of milliseconds or text of a duration.
    /// @return A duration, or an error if the value is not one. A null or an error is returned as it is and blank
    ///         text gives null.
    public static Val toDuration(final Val val) {
        return toDuration(val, ValDurationUtil::parseMilliseconds);
    }

    /// Reads a value as a duration whose text is in a pattern.
    ///
    /// @param val     Text in the pattern, a number of the unit of a pattern that is only one unit, or a duration or
    ///                period of only days.
    /// @param pattern The pattern, which must be one that can be read.
    /// @return A duration, or an error if the value is not one. A null or an error is returned as it is and blank
    ///         text gives null.
    public static Val toDuration(final Val val, final DurationPattern pattern) {
        final boolean isPlainNumber = val instanceof ValNumber
                                      && !(val instanceof ValDuration)
                                      && !(val instanceof ValDate)
                                      && DateReader.isLong(val);
        return isPlainNumber
                ? toDuration(val.toString(), text -> multiplyUnit(val, pattern))
                : toDuration(val, text -> parseMilliseconds(text, pattern));
    }

    // Multiplies as the * operator does.
    private static long multiplyUnit(final Val number, final DurationPattern pattern) {
        // With more than one unit, whether a number's text is in the pattern would depend on how many digits it has.
        final long unitMillis = pattern.getOnlyUnitMillis();
        if (unitMillis == 0) {
            throw new DateTimeException("A number can only be read with a pattern of one unit, such as m");
        }
        try {
            return DateArithmetic.multiply(ValDuration.create(unitMillis), number).toLong();
        } catch (final ArithmeticException e) {
            throw createTooLongException(number.toString(), e);
        }
    }

    private static long parseMilliseconds(final String text, final DurationPattern pattern) {
        final Long millis;
        try {
            millis = pattern.getOnlyUnitMillis() != 0 && TimeRounder.isNumber(text)
                    ? Long.valueOf(toLong(new BigDecimal(text).multiply(
                            BigDecimal.valueOf(pattern.getOnlyUnitMillis()))))
                    : pattern.read(text);
        } catch (final ArithmeticException e) {
            throw createTooLongException(text, e);
        }
        if (millis == null) {
            throw new DateTimeParseException(
                    "Text is not a duration in the pattern '" + pattern + "'", text, 0);
        }
        return millis;
    }

    /// Reads a value as the milliseconds of a duration.
    ///
    /// @param val A duration, or any value that [#toDuration(Val)] can read as one.
    /// @return The milliseconds, or `null` if it is not a duration.
    public static Long toMillis(final Val val) {
        if (val instanceof ValString) {
            try {
                return readMilliseconds(val.toString());
            } catch (final DateTimeParseException e) {
                return null;
            }
        }
        return toDuration(val) instanceof final ValDuration duration
                ? duration.toLong()
                : null;
    }

    /// Reads a value as a duration, as [#toDuration(Val)] does except that text must be in ISO 8601 format.
    ///
    /// @param val A duration, a period of only days, a number of milliseconds or ISO 8601 text of a duration.
    /// @return A duration, or an error if the value is not one. A null or an error is returned as it is and blank
    ///         text gives null.
    public static Val parseISODuration(final Val val) {
        return toDuration(val, ValDurationUtil::parseIsoMilliseconds);
    }

    private static Val toDuration(final Val val, final ToLongFunction<String> textReader) {
        return switch (val) {
            case null -> ValNull.INSTANCE;
            case final ValDuration duration -> duration;
            case final ValPeriod period -> period.hasOnlyDays()
                    ? ValDuration.create(period.toLong())
                    : ValErr.create("A period of months or years can't be used as a duration");
            case final ValString text -> toDuration(text.toString(), textReader);
            case final ValDate ignored -> ValErr.create("A date is not a duration");
            case final ValNumber number -> DateReader.isLong(number)
                    ? ValDuration.create(number.toLong())
                    : ValErr.create("A " + number.type().getName()
                                    + " that is too large or not finite is not a duration");
            default -> val.type().isValue()
                    ? ValErr.create("A " + val.type().getName() + " is not a duration")
                    : val;
        };
    }

    private static Val toDuration(final String text, final ToLongFunction<String> textReader) {
        if (NullSafe.isBlankString(text)) {
            return ValNull.INSTANCE;
        }
        try {
            return ValDuration.create(textReader.applyAsLong(text));
        } catch (final RuntimeException e) {
            return exceptionToValErr(e);
        }
    }

    private static ValErr exceptionToValErr(final Exception e) {
        if (e instanceof final DateTimeParseException dtpe) {
            // Not for text that isn't in the format asked for.
            final String advice = e.getCause() instanceof DateTimeException && isPeriod(dtpe.getParsedString())
                    ? ". Use parsePeriod to read it"
                    : "";
            String msg = e.getMessage().stripTrailing() + advice;
            msg = msg.endsWith(".")
                    ? msg
                    : msg + ".";
            msg = msg + " Text: '" + dtpe.getParsedString() + "'.";
            return ValErr.create(msg);
        } else {
            return ValErr.create(e);
        }
    }

    /// Reads the text of any value that is used as a number, so what it reads can't be changed without changing text
    /// that has nothing to do with durations, such as IDs. Text that is meant to be a duration is read by
    /// [#parseMilliseconds(String)].
    ///
    /// @param value The text.
    /// @return The duration in milliseconds.
    /// @throws DateTimeParseException If the text is null or can't be parsed for any reason.
    public static long parseToMilliseconds(final String value) {
        if (value == null) {
            throw new DateTimeParseException(PARSE_ERROR_MESSAGE, "null", 0);
        }
        if (NullSafe.isBlankString(value)) {
            throw new DateTimeParseException(PARSE_ERROR_MESSAGE, value, 0);
        }
        try {
            if (value.startsWith("P")) {
                // This is ISO 8601 format so use Duration to parse it
                return Duration.parse(value).toMillis();
            }
            if (EXACT_PATTERN.matcher(value).matches()) {
                return TimeLiteral.parseCompoundMillis(value);
            }
            // Not ISO 8601 so have a go with our ModelStringUtil format
            return ModelStringUtil.parseDurationString(value);
        } catch (final DateTimeParseException e) {
            throw e;
        } catch (final DateTimeException e) {
            // This says what is wrong in words that are fit for the query's author.
            throw new DateTimeParseException(e.getMessage(), value, 0, e);
        } catch (final Exception e) {
            throw new DateTimeParseException(PARSE_ERROR_MESSAGE, value, 0);
        }
    }

    /// Reads text as a duration.
    ///
    /// It can be in ISO 8601 format, a number of milliseconds, or numbers with the units `w`, `d`, `h`, `m`, `s`, `ms`,
    /// `us` and `ns`, such as `1d 2h`, `-1.5h` and `1500us`. A day is 24 hours and a week is 7 of those. Any part of a
    /// millisecond is dropped.
    ///
    /// @param value The text.
    /// @return The duration in milliseconds.
    /// @throws DateTimeParseException If the text is not a duration or is too long. The message is fit to show the
    ///                                query's author.
    public static long parseMilliseconds(final String value) {
        final Long millis = readMilliseconds(value);
        if (millis == null) {
            throw new DateTimeParseException(PARSE_ERROR_MESSAGE, value, 0);
        }
        return millis;
    }

    // Returns null if the text is nothing like a duration, which is quicker than an exception.
    private static Long readMilliseconds(final String value) {
        final String text = removeQuotes(value.trim());
        try {
            if (ISO_MONTHS_PATTERN.matcher(text).matches()) {
                throw createPeriodException(text);
            } else if (ISO_PATTERN.matcher(text).matches()) {
                return parseIso(text);
            } else if (TimeRounder.isNumber(text)) {
                return TimeRounder.parseMillis(text);
            }
            final Matcher matcher = UNITS_PATTERN.matcher(text);
            if (matcher.matches()) {
                final BigDecimal millis = sumUnits(text, matcher.group(2));
                return toLong("-".equals(matcher.group(1))
                        ? millis.negate()
                        : millis);
            }
        } catch (final ArithmeticException e) {
            throw createTooLongException(value, e);
        } catch (final DateTimeParseException e) {
            throw e;
        } catch (final DateTimeException e) {
            throw new DateTimeParseException(e.getMessage(), value, 0, e);
        }
        return null;
    }

    /// Removes the quotes that some sources put around their text.
    ///
    /// @param text The text.
    /// @return The text without quotes.
    static String removeQuotes(final String text) {
        final Matcher matcher = QUOTED_PATTERN.matcher(text);
        return matcher.matches()
                ? matcher.group(1).trim()
                : text;
    }

    private static long parseIsoMilliseconds(final String value) {
        try {
            return parseIso(value.trim());
        } catch (final ArithmeticException e) {
            throw createTooLongException(value, e);
        }
    }

    private static DateTimeParseException createTooLongException(final String value, final ArithmeticException e) {
        return new DateTimeParseException("The duration is too long", value, 0, e);
    }

    private static long parseIso(final String text) {
        try {
            return Duration.parse(text).toMillis();
        } catch (final DateTimeParseException e) {
            if (e.getCause() != null) {
                // It is a duration but one of its numbers is too large.
                throw new ArithmeticException(e.getMessage());
            }
            throw e;
        }
    }

    private static BigDecimal sumUnits(final String text, final String unsigned) {
        BigDecimal millis = BigDecimal.ZERO;
        final Matcher matcher = UNIT_PATTERN.matcher(unsigned);
        while (matcher.find()) {
            millis = millis.add(new BigDecimal(matcher.group(1)).multiply(getUnitMillis(text, matcher.group(2))));
        }
        return millis;
    }

    private static BigDecimal getUnitMillis(final String text, final String unit) {
        return switch (unit) {
            case "ns" -> new BigDecimal("0.000001");
            case "us" -> new BigDecimal("0.001");
            case "ms" -> BigDecimal.ONE;
            case "s" -> toBigDecimal(Duration.ofSeconds(1));
            case "m" -> toBigDecimal(Duration.ofMinutes(1));
            case "h" -> toBigDecimal(Duration.ofHours(1));
            case "d" -> toBigDecimal(Duration.ofDays(1));
            case "w" -> toBigDecimal(Duration.ofDays(7));
            case "M", "y" -> throw createPeriodException(text);
            default -> throw new DateTimeException(
                    "Unknown unit '" + unit + "' in a duration. Use ns, us, ms, s, m, h, d or w");
        };
    }

    private static BigDecimal toBigDecimal(final Duration duration) {
        return BigDecimal.valueOf(duration.toMillis());
    }

    private static DateTimeException createPeriodException(final String text) {
        return new DateTimeException("'" + text + "' " + (isPeriod(text)
                ? "is a period of months or years"
                : "has months or years")
                                     + ", so is not a duration");
    }

    private static boolean isPeriod(final String text) {
        return ValPeriodUtil.parsePeriod(ValString.create(text)) instanceof ValPeriod;
    }

    private static long toLong(final BigDecimal millis) {
        return millis.setScale(0, RoundingMode.DOWN).longValueExact();
    }
}
