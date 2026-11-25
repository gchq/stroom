/*
 * Copyright 2020 Crown Copyright
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
import java.time.temporal.TemporalQueries;
import java.util.Objects;
import java.util.function.Supplier;

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
        return stroom.util.date.DateUtil.parseNormalDateTimeString(date);
    }

    public static ZoneId getTimeZone(final String timeZone) throws ParseException {
        final ZoneId dateTimeZone;

        if (!NullSafe.isBlankString(timeZone)) {
            try {
                dateTimeZone = UserTimeZoneUtil.getZoneId(timeZone);
            } catch (final DateTimeException | IllegalArgumentException e) {
                throw new ParseException(e.getMessage(), 0);
            }
        } else {
            dateTimeZone = ZoneOffset.UTC;
        }

        return dateTimeZone;
    }

    public static long parse(final String value,
                             final DateTimeFormatter formatter,
                             final ZoneId zoneId) {
        return parse(value, formatter, () -> zoneId);
    }

    /// Reads text as a date.
    ///
    /// @param value     The text.
    /// @param formatter The format of the text, or `null` for ISO 8601 with a time.
    /// @param zone      Gets the time zone of text that has no offset, which is UTC if it is `null`. It is not asked
    ///                  for if the text has an offset.
    /// @return The milliseconds since the epoch.
    /// @throws DateTimeException If the text is not a date in the format.
    public static long parse(final String value,
                             final DateTimeFormatter formatter,
                             final Supplier<ZoneId> zone) {
        return DateReader.toMillis(parseInternal(value, formatter, zone).toInstant(), value);
    }

    public static LocalDateTime parseLocal(final String value,
                                           final DateTimeFormatter formatter,
                                           final ZoneId zoneId) {
        return parseInternal(value, formatter, () -> zoneId)
                .withZoneSameInstant(Objects.requireNonNullElse(zoneId, ZoneOffset.UTC))
                .toLocalDateTime();
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
                                               final Supplier<ZoneId> zone) {
        final DateTimeFormatter parser = Objects.requireNonNullElse(formatter, DEFAULT_ISO_PARSER);
        final TemporalAccessor temporalAccessor = parser.parseBest(value,
                ZonedDateTime::from,
                LocalDateTime::from,
                DateUtil::toDate);
        stroom.util.date.DateUtil.checkDay(value, parser, LocalDate.from(temporalAccessor));

        return switch (temporalAccessor) {
            case final ZonedDateTime zonedDateTime -> zonedDateTime;
            case final LocalDateTime localDateTime -> localDateTime.atZone(getZone(zone));
            case final LocalDate localDate -> localDate.atStartOfDay(getZone(zone));
            default -> throw new RuntimeException("Unexpected type " + temporalAccessor.getClass().getName());
        };
    }

    // A date with no time can still have an offset.
    private static TemporalAccessor toDate(final TemporalAccessor temporalAccessor) {
        final LocalDate localDate = LocalDate.from(temporalAccessor);
        final ZoneId zoneId = temporalAccessor.query(TemporalQueries.zone());
        return zoneId == null
                ? localDate
                : localDate.atStartOfDay(zoneId);
    }

    private static ZoneId getZone(final Supplier<ZoneId> zone) {
        return Objects.requireNonNullElse(zone.get(), ZoneOffset.UTC);
    }
}
