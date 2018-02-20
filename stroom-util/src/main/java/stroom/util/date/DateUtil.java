/*
 * Copyright 2016 Crown Copyright
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;

public final class DateUtil {
    public static final int DATE_LENGTH = "2000-01-01T00:00:00.000Z".length();
    public static final java.time.format.DateTimeFormatter NORMAL_STROOM_TIME_FORMATTER = java.time.format.DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXX");
    public static final long MIN_MS = 1000 * 60;
    public static final long HOUR_MS = MIN_MS * 60;
    public static final long DAY_MS = HOUR_MS * 24;
    private static final Logger LOGGER = LoggerFactory.getLogger(DateUtil.class);
    private static final String NULL = "NULL";
    private static final java.time.format.DateTimeFormatter FILE_TIME_STROOM_TIME_FORMATTER = java.time.format.DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH'#'mm'#'ss,SSSXX");
    private static final String GMT_BST_GUESS = "GMT/BST";
    private static final ZoneId EUROPE_LONDON_TIME_ZONE = ZoneId.of("Europe/London");

    private DateUtil() {
        // Private constructor.
    }

    /**
     * Parse a date using a format.
     *
     * @param pattern    pattern to match
     * @param timeZoneId if provided the pattern will append Z and the time zone will
     *                   be added
     * @param value      value to parse
     * @return the result
     * @throws IllegalArgumentException if date does not parse
     */
    public static long parseDate(final String pattern, final String timeZoneId, final String value) {
        ZoneId dateTimeZone = null;
        ZonedDateTime dateTime = null;

        if (value == null || value.trim().length() == 0) {
            throw new IllegalArgumentException("Unable to parse date: \"" + value + '"');
        }

        // Try to parse the time zone first.
        try {
            if (timeZoneId != null) {
                if (GMT_BST_GUESS.equals(timeZoneId)) {
                    dateTimeZone = EUROPE_LONDON_TIME_ZONE;
                } else {
                    dateTimeZone = ZoneId.of(timeZoneId);
                }
            }
        } catch (final IllegalArgumentException e) {
            LOGGER.debug("Unable to parse time zone!", e);
        }

        if (dateTimeZone == null) {
            dateTimeZone = ZoneOffset.UTC;
        }

        final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern(pattern);
        try {
            dateTime = parse(dateFormat, value, dateTimeZone);

        } catch (final IllegalArgumentException e) {
            LOGGER.debug("Unable to parse date!", e);

            // We failed to use the time zone so try UTC.
            dateTime = parse(dateFormat, value, ZoneOffset.UTC);
        }

        if (dateTime == null) {
            throw new IllegalArgumentException("Unable to parse date: \"" + value + '"');
        }

        return dateTime.toInstant().toEpochMilli();
    }

    private static ZonedDateTime parse(final DateTimeFormatter formatter, final String value, final ZoneId zoneId) {
        final TemporalAccessor temporalAccessor = formatter.parseBest(value, ZonedDateTime::from, LocalDateTime::from, LocalDate::from);
        if (temporalAccessor instanceof ZonedDateTime) {
            return ((ZonedDateTime) temporalAccessor).withZoneSameInstant(zoneId);
        }
        if (temporalAccessor instanceof LocalDateTime) {
            return ((LocalDateTime) temporalAccessor).atZone(zoneId);
        }
        return ((LocalDate) temporalAccessor).atStartOfDay(zoneId);
    }

    /**
     * Create a 'normal' type date with the current system time.
     */
    public static String createNormalDateTimeString() {
        return NORMAL_STROOM_TIME_FORMATTER.format(ZonedDateTime.now(ZoneOffset.UTC));
    }

    /**
     * Create a 'normal' type date in UTC
     */
    public static String createNormalDateTimeString(final Long ms) {
        if (ms == null) {
            return "";
        }
        return createNormalDateTimeString(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC));
    }

    /**
     * Create a 'normal' type date in UTC
     */
    public static String createNormalDateTimeString(final Instant instant) {
        if (instant == null) {
            return "";
        }
        return createNormalDateTimeString(instant.atZone(ZoneOffset.UTC));
    }

    /**
     * Create a 'normal' type date in UTC
     */
    public static String createNormalDateTimeString(final ZonedDateTime zonedDateTime) {
        if (zonedDateTime == null) {
            return "";
        }
        return NORMAL_STROOM_TIME_FORMATTER.format(zonedDateTime);
    }

    /**
     * Create a 'file' format date string witht he current system time.
     *
     * @return string The date as a 'file' format date string.
     */
    public static String createFileDateTimeString() {
        return FILE_TIME_STROOM_TIME_FORMATTER.format(ZonedDateTime.now(ZoneOffset.UTC));
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
     * Parse a 'normal' type date.
     *
     * @param date string date
     * @return date as milliseconds since epoch
     * @throws IllegalArgumentException if date does not parse
     */
    public static long parseNormalDateTimeString(final String date) {
        if (date == null || date.length() != DATE_LENGTH) {
            throw new IllegalArgumentException("Unable to parse date: \"" + date + '"');
        }

        final ZonedDateTime dateTime = parse(NORMAL_STROOM_TIME_FORMATTER, date, ZoneOffset.UTC);
        if (dateTime == null) {
            throw new IllegalArgumentException("Unable to parse date: \"" + date + '"');
        }

        return dateTime.toInstant().toEpochMilli();
    }

    public static long parseUnknownString(final String date) {
        if (date == null || date.length() != DATE_LENGTH) {
            Long.parseLong(date);
        }

        try {
            // Try and parse the string an a standard ISO8601 date.
            final ZonedDateTime dateTime = parse(NORMAL_STROOM_TIME_FORMATTER, date, ZoneOffset.UTC);
            return dateTime.toInstant().toEpochMilli();

        } catch (final Exception e) {
            // If we were unable to parse the value as an ISO8601 date then try
            // and get it as a long.
            return Long.parseLong(date);
        }
    }
}
