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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

public final class DateUtil {

    private static final int DATE_LENGTH = "2000-01-01T00:00:00.000Z".length();
    private static final String DEFAULT_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSSXX";
    private static final DateTimeFormatter NORMAL_STROOM_TIME_FORMATTER =
            DateTimeFormatter.ofPattern(DEFAULT_PATTERN, Locale.ENGLISH);
    private static final DateTimeFormatter FILE_TIME_STROOM_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH'#'mm'#'ss,SSSXX", Locale.ENGLISH);

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
     * Parse a 'normal' type date.
     *
     * @param date string date
     * @return date as milliseconds since epoch
     * @throws IllegalArgumentException if date does not parse
     */
    public static long parseNormalDateTimeString(final String date) {
        return parseNormalDateTimeStringToInstant(date).toEpochMilli();
    }

    /**
     * Parse a 'normal' type date.
     *
     * @param date string date
     * @return A UTC {@link Instant}
     * @throws IllegalArgumentException if date does not parse
     */
    public static Instant parseNormalDateTimeStringToInstant(final String date) {
        if (date == null) {
            throw new IllegalArgumentException("Unable to parse null date");
        }

        if (!looksLikeDate(date)) {
            throw new IllegalArgumentException("Unable to parse date: \"" + date + '"');
        }

        final LocalDateTime dateTime = LocalDateTime.parse(date, NORMAL_STROOM_TIME_FORMATTER);
        return dateTime.toInstant(ZoneOffset.UTC);
    }

    public static long parseUnknownString(final String date) {
        if (date == null) {
            throw new IllegalArgumentException("Unable to parse null date");
        }

        if (!looksLikeDate(date)) {
            try {
                return Long.parseLong(date);
            } catch (final RuntimeException e) {
                // Ignore.
            }
        }

        try {
            // Try and parse the string an a standard ISO8601 date.
            final LocalDateTime dateTime = LocalDateTime.parse(date, NORMAL_STROOM_TIME_FORMATTER);
            return dateTime.toInstant(ZoneOffset.UTC).toEpochMilli();

        } catch (final RuntimeException e) {
            // If we were unable to parse the value as an ISO8601 date then try
            // and get it as a long.
            return Long.parseLong(date);
        }
    }

    private static boolean looksLikeDate(final String date) {
        return date != null && date.length() == DATE_LENGTH && date.charAt(date.length() - 1) == 'Z';
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
