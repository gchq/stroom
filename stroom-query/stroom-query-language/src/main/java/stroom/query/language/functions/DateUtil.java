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

package stroom.query.language.functions;

import stroom.util.shared.NullSafe;

import java.text.ParseException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.Objects;

public final class DateUtil {

    static final String DEFAULT_PATTERN = stroom.util.date.DateUtil.DEFAULT_PATTERN;

    private static final ZoneOffset DEFAULT_ZONE_OFFSET = stroom.util.date.DateUtil.DEFAULT_ZONE_OFFSET;

    /**
     * Should only use this for formatting, not parsing as it does not handle
     * variable length or optional millis part.
     */
    static final DateTimeFormatter DEFAULT_FORMATTER = stroom.util.date.DateUtil.NORMAL_STROOM_TIME_FORMATTER;

    /**
     * Should only use this for parsing as is supports variable length or optional millis part.
     */
    static final DateTimeFormatter DEFAULT_ISO_PARSER = stroom.util.date.DateUtil.DEFAULT_ISO_PARSER;

    private DateUtil() {
        // Private constructor.
    }

    /**
     * Create a 'normal' type date.
     *
     * @param ms The date/time to convert to a string, in milliseconds since the epoch
     * @return The 'normal' string representation of the passed date/time
     */
    public static String createNormalDateTimeString(final Long ms) {
        if (ms == null) {
            return "";
        }
        return DEFAULT_FORMATTER.format(Instant.ofEpochMilli(ms)
                .atZone(ZoneOffset.UTC));
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
     * "2010-01-01T23:59:59+0200" // +2hr zone offset
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
        if (date == null) {
            throw new IllegalArgumentException("Unable to parse null date");
        }

        try {
            // Our format requires a zone offset, e.g. 'Z', '+02', '+00:00', '-02:00', etc.
            final ZonedDateTime zonedDateTime = ZonedDateTime.parse(date, DEFAULT_ISO_PARSER);
            return zonedDateTime.toInstant().toEpochMilli();
        } catch (final DateTimeParseException e) {
            throw new IllegalArgumentException("Unable to parse date: \"" + date + "\": " + e.getMessage(), e);
        }
    }

    public static ZoneId getTimeZone(final String timeZone) throws ParseException {
        final ZoneId dateTimeZone;

        if (!NullSafe.isBlankString(timeZone)) {
            try {
                dateTimeZone = ZoneId.of(timeZone);
            } catch (final DateTimeException | IllegalArgumentException e) {
                throw new ParseException("Time Zone '" + timeZone + "' is not recognised", 0);
            }
        } else {
            dateTimeZone = ZoneOffset.UTC;
        }

        return dateTimeZone;
    }

    public static long parse(final String value,
                             final DateTimeFormatter formatter,
                             final ZoneId zoneId) {
        final ZonedDateTime dateTime = parseInternal(value, formatter, zoneId);
        if (dateTime == null) {
            throw new IllegalArgumentException("Unable to parse date: \"" + value + '"');
        }

        return dateTime.toInstant().toEpochMilli();
    }

    public static LocalDateTime parseLocal(final String value,
                                           final DateTimeFormatter formatter,
                                           final ZoneId zoneId) {
        final ZonedDateTime dateTime = parseInternal(value, formatter, zoneId);
        if (dateTime == null) {
            throw new IllegalArgumentException("Unable to parse date: \"" + value + '"');
        }

        return dateTime.toLocalDateTime();
    }

    public static String format(final Long value,
                                final DateTimeFormatter formatter,
                                final ZoneId zoneId) {
        final ZonedDateTime zonedDateTime = Instant.ofEpochMilli(value)
                .atZone(Objects.requireNonNullElse(zoneId, DEFAULT_ZONE_OFFSET));
        return Objects.requireNonNullElse(formatter, DEFAULT_FORMATTER)
                .format(zonedDateTime);
    }

    private static ZonedDateTime parseInternal(final String value,
                                               final DateTimeFormatter formatter,
                                               final ZoneId zoneId) {
        final TemporalAccessor temporalAccessor = Objects.requireNonNullElse(formatter, DEFAULT_ISO_PARSER)
                .parseBest(value,
                        ZonedDateTime::from,
                        LocalDateTime::from,
                        LocalDate::from);

        final ZoneId zoneId2 = Objects.requireNonNullElse(zoneId, ZoneOffset.UTC);
        return switch (temporalAccessor) {
            case final ZonedDateTime zonedDateTime -> zonedDateTime.withZoneSameInstant(zoneId2);
            case final LocalDateTime localDateTime -> localDateTime.atZone(zoneId2);
            case final LocalDate localDate -> localDate.atStartOfDay(zoneId2);
            default -> throw new RuntimeException("Unexpected type " + temporalAccessor.getClass().getName());
        };
    }
}
