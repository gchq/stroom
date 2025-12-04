/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.util.date;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

public final class DateUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DateUtil.class);

    public static final ZoneOffset DEFAULT_ZONE_OFFSET = ZoneOffset.UTC;
    // We always want to output in a fixed format, unlike the DEFAULT_PARSER below
    public static final String DEFAULT_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSSXX";
    /**
     * Use for FORMATTING ONLY, not parsing, as it is too rigid for parsing.
     */
    public static final DateTimeFormatter NORMAL_STROOM_TIME_FORMATTER =
            DateTimeFormatter.ofPattern(DEFAULT_PATTERN, Locale.ENGLISH);
    private static final DateTimeFormatter FILE_TIME_STROOM_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH'#'mm'#'ss,SSSXX", Locale.ENGLISH);

    private static final String NO_OFFSET_TEXT = "Z";
    /**
     * This differs from NORMAL_STROOM_TIME_FORMATTER in that it supports 0-9 milli digits.
     * Use for PARSING ONLY, not formatting, due to optional elements.
     * {@link DateTimeFormatter#ISO_OFFSET_DATE_TIME} does not support '+HHMM' (aka 'XX') which
     * is what our normal formatter outputs so make a more flexible one
     */
    public static final DateTimeFormatter DEFAULT_ISO_PARSER = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .optionalStart().appendOffset("+HH:MM", NO_OFFSET_TEXT).optionalEnd() // Like 'XXX'
            .optionalStart().appendOffset("+HHMM", NO_OFFSET_TEXT).optionalEnd() // Like 'XX'
            .optionalStart().appendOffset("+HH", NO_OFFSET_TEXT).optionalEnd() // Like 'X'
            .toFormatter();

    private DateUtil() {
        // Private constructor.
    }

    /**
     * Create a 'normal' type date with the current system time.
     */
    public static String createNormalDateTimeString() {
        return NORMAL_STROOM_TIME_FORMATTER.format(Instant.now().atZone(ZoneOffset.UTC));
    }

    /**
     * Create a 'normal' type date.
     *
     * @param ms The date to create the string for.
     */
    public static String createNormalDateTimeString(final Long ms) {
        if (ms == null) {
            return "";
        }
        return NORMAL_STROOM_TIME_FORMATTER.format(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC));
    }

    /**
     * Create a 'normal' type date.
     *
     * @param instant The date to create the string for.
     */
    public static String createNormalDateTimeString(final Instant instant) {
        if (instant == null) {
            return "";
        } else {
            return NORMAL_STROOM_TIME_FORMATTER.format(instant.atZone(ZoneOffset.UTC));
        }
    }

    /**
     * Create a 'file' format date string.
     *
     * @param ms The date to create the string for.
     * @return string The date as a 'file' format date string.
     */
    public static String createFileDateTimeString(final long ms) {
        return FILE_TIME_STROOM_TIME_FORMATTER.format(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC));
    }

    /**
     * Create a 'file' format date string.
     *
     * @param instant The date to create the string for.
     * @return string The date as a 'file' format date string.
     */
    public static String createFileDateTimeString(final Instant instant) {
        return FILE_TIME_STROOM_TIME_FORMATTER.format(instant.atZone(ZoneOffset.UTC));
    }

    /**
     * Parse a 'normal' type date in ISO 8601 format with a zone offset and a variable and optional
     * number of fractional second digits (0-9).
     * <p>
     * For example:
     * <pre>{@code
     * "2010-01-01T23:59:59Z" // No millis
     * "2010-01-01T23:59:59.123Z" // Millis
     * "2010-01-01T23:59:59.123456Z" // Nanos
     * "2010-01-01T23:59:59.123+00:00" // Zulu/UTC
     * "2010-01-01T23:59:59.123+02" // +2hr zone offset
     * "2010-01-01T23:59:59+02:00" // +2hr zone offset
     * "2010-01-01T23:59:59+02:30" // +2hr30min zone offset
     * "2010-01-01T23:59:59.123-03:00" // -3hr zone offset
     * }</pre>
     *
     * @param date string date
     * @return date as milliseconds since epoch
     * @throws IllegalArgumentException if date does not parse
     */
    public static long parseNormalDateTimeString(final String date) {
        return parseNormalDateTimeStringToInstant(date).toEpochMilli();
    }

    /**
     * Parse a 'normal' type date in ISO 8601 format with a zone offset and a variable and optional
     * number of fractional second digits (0-9).
     * <p>
     * For example:
     * <pre>{@code
     * "2010-01-01T23:59:59Z" // No millis
     * "2010-01-01T23:59:59.123Z" // Millis
     * "2010-01-01T23:59:59.123456Z" // Nanos
     * "2010-01-01T23:59:59.123+00:00" // Zulu/UTC
     * "2010-01-01T23:59:59.123+02" // +2hr zone offset
     * "2010-01-01T23:59:59+02:00" // +2hr zone offset
     * "2010-01-01T23:59:59+02:30" // +2hr30min zone offset
     * "2010-01-01T23:59:59.123-03:00" // -3hr zone offset
     * }</pre>
     *
     * @param date string date
     * @return A UTC {@link Instant}
     * @throws IllegalArgumentException if date does not parse
     */
    public static Instant parseNormalDateTimeStringToInstant(final String date) {
        if (date == null) {
            throw new IllegalArgumentException("Unable to parse null date");
        }

        try {
            // Our format requires a zone offset, e.g. 'Z', '+02', '+00:00', '-02:00', etc.
            final ZonedDateTime zonedDateTime = ZonedDateTime.parse(date, DEFAULT_ISO_PARSER);
            return zonedDateTime.toInstant();
        } catch (final DateTimeParseException e) {
            throw new IllegalArgumentException("Unable to parse date: \"" + date + "\": " + e.getMessage(), e);
        }
    }

    public static long parseUnknownString(final String date) {
        if (date == null) {
            throw new IllegalArgumentException("Unable to parse null date");
        }

        if (!looksLikeISODate(date)) {
            return parseDateAsMillisSinceEpoch(date);
        } else {
            try {
                return parseNormalDateTimeString(date);
            } catch (final RuntimeException e) {
                // If we were unable to parse the value as an ISO8601 date then try
                // and get it as a long.
                return parseDateAsMillisSinceEpoch(date);
            }
        }
    }

    /**
     * Takes an ISO 8601 date string and converts it into a consistent ISO 8601 format.
     * This is to deal with the fact that the ISO 8601 format is a bit woolly (e.g. 'Z'
     * and '+00:00' are both ok). This method will ensure that all dates returned are in a
     * predictable and consistent format. Only intended for use on dates known to be in
     * ISO 8601 format.
     * <p>
     * Will also normalise an epoch millis number into a date string.
     *
     * <pre>{@code
     * "2010-01-01T23:59:59Z" => "2010-01-01T23:59:59Z" // No millis
     * "2010-01-01T23:59:59.123Z" => "2010-01-01T23:59:59.123Z" // Millis
     * "2010-01-01T23:59:59.123456Z" => "2010-01-01T23:59:59.123" // Nanos
     * "2010-01-01T23:59:59.123+00:00" => "2010-01-01T23:59:59.123Z" // Zulu/UTC
     * "2010-01-01T23:59:59.123+02" => "2010-01-01T23:59:59.123+0200" // +2hr zone offset
     * "2010-01-01T23:59:59+02:00" => "2010-01-01T23:59:59.000+0200" // +2hr zone offset
     * "2010-01-01T23:59:59+02:30" => "2010-01-01T23:59:59.000+0230" // +2hr30min zone offset
     * "2010-01-01T23:59:59.123-03:00" => "2010-01-01T23:59:59.123-0300" // -3hr zone offset
     * }</pre>
     *
     * @param date         The date string to normalise.
     * @param ignoreErrors If true, any exception when parsing/converting the date is swallowed
     *                     and data is returned unchanged.
     * @return The date normalised to the same output as {@link DateUtil#createNormalDateTimeString(Long)}.
     * If date is null or blank returns it as is with no exception.
     */
    public static String normaliseDate(final String date, final boolean ignoreErrors) {
        if (NullSafe.isBlankString(date)) {
            return date;
        } else {
            final ZonedDateTime zonedDateTime;
            try {
                if (!looksLikeISODate(date)) {
                    final long epochMs = parseDateAsMillisSinceEpoch(date);
                    zonedDateTime = Instant.ofEpochMilli(epochMs).atZone(DEFAULT_ZONE_OFFSET);
                } else {
                    zonedDateTime = ZonedDateTime.parse(date, DEFAULT_ISO_PARSER);
                }
                return NORMAL_STROOM_TIME_FORMATTER.format(zonedDateTime);
            } catch (final Exception e) {
                LOGGER.debug("Unable to parse date: \"" + date + "\": " + LogUtil.exceptionMessage(e), e);
                if (ignoreErrors) {
                    return date;
                } else {
                    throw new IllegalArgumentException(
                            "Unable to parse date: \"" + date + "\": " + LogUtil.exceptionMessage(e), e);
                }
            }
        }
    }

    private static long parseDateAsMillisSinceEpoch(final String dateStr) {
        try {
            return Long.parseLong(dateStr);
        } catch (final NumberFormatException e) {
            throw new IllegalArgumentException(LogUtil.message(
                    "Unable to parse date '{}' as numeric milliseconds since epoch: {}",
                    dateStr,
                    e.getMessage()), e);
        }
    }

    public static boolean looksLikeISODate(final String date) {
        // Can't check the length as Instant.parse() accepts strings with variable numbers
        // of mills
        return date != null
               && (date.charAt(date.length() - 1) == 'Z'
                   || date.contains("-")
                   || date.contains(":")
                   || date.contains("+"));
    }

    public static Instant roundDown(final Instant instant, final Duration duration) {
        Instant result;
        if (duration.toMillis() < 1000) {
            result = instant.truncatedTo(ChronoUnit.SECONDS);
        } else if (duration.toSeconds() < 60) {
            result = instant.truncatedTo(ChronoUnit.MINUTES);
        } else if (duration.toMinutes() < 60) {
            result = instant.truncatedTo(ChronoUnit.HOURS);
        } else if (duration.toHours() < 24) {
            result = instant.truncatedTo(ChronoUnit.DAYS);
        } else {
            result = instant.truncatedTo(ChronoUnit.YEARS);
        }

        // Add duration until we surpass time.
        Instant adjusted = result;
        while (adjusted.isBefore(instant)) {
            result = adjusted;
            adjusted = adjusted.plus(duration);
        }

        return result;
    }
}
