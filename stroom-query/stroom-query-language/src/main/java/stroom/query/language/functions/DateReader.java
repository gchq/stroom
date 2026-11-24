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

package stroom.query.language.functions;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.function.Supplier;

/// Reads values as dates. The time zone is only asked for if it is needed, so that a date that doesn't depend on it can
/// be read even if it is not a known time zone.
final class DateReader {

    private static final double MAX_LONG = 0x1p63;

    private DateReader() {
        // Static methods only.
    }

    /// Reads a value as a date.
    ///
    /// @param val  A date, a number of milliseconds since the epoch, or text of either.
    /// @param zone Gets the time zone of text that has no offset.
    /// @return The milliseconds since the epoch.
    /// @throws DateTimeException If the value is not a date. The message is fit to show the query's author.
    static long readMillis(final Val val, final Supplier<ZoneId> zone) {
        final Long millis = switch (val) {
            case final ValDate date -> date.toLong();
            case final ValString text -> parse(text.toString().trim(), zone);
            default -> isNumber(val)
                    ? val.toLong()
                    : null;
        };
        if (millis == null) {
            throw new DateTimeException(val instanceof ValString
                    ? "'" + val + "' is not a date"
                    : "A " + val.type().getName() + " is not a date");
        }
        return millis;
    }

    /// Reads a value as a date in a time zone.
    ///
    /// @param val  A date, a number of milliseconds since the epoch, or text of either.
    /// @param zone The time zone of text that has no offset, and of the date that is returned.
    /// @return The date.
    /// @throws DateTimeException If the value is not a date. The message is fit to show the query's author.
    static ZonedDateTime read(final Val val, final ZoneId zone) {
        return Instant.ofEpochMilli(readMillis(val, () -> zone)).atZone(zone);
    }

    private static boolean isNumber(final Val val) {
        return val instanceof ValNumber
               && !(val instanceof ValDuration)
               && isLong(val);
    }

    /// Reads a value as a date in a time zone, for functions that have always given a null for text that is not a date.
    ///
    /// @param val  A date, a number of milliseconds since the epoch, or text of either.
    /// @param zone The time zone of text that has no offset, and of the date that is returned.
    /// @return The date, or `null` if the value is null, blank or text that is not a date.
    /// @throws DateTimeException If the value is anything else that is not a date, such as a duration.
    static ZonedDateTime readIfDate(final Val val, final ZoneId zone) {
        if (isBlank(val)) {
            return null;
        }
        try {
            return read(val, zone);
        } catch (final DateTimeException e) {
            if (val instanceof ValString) {
                return null;
            }
            throw e;
        }
    }

    /// Tells whether a value has nothing to read.
    ///
    /// @param val The value.
    /// @return True if it is null, an error or blank text.
    static boolean isBlank(final Val val) {
        return !val.type().isValue() || (val instanceof ValString && val.toString().isBlank());
    }

    /// Tells whether a number can be turned into a long. One that is too large, is infinite or is not a number would
    /// become a long that has nothing to do with it.
    ///
    /// @param val The number.
    /// @return True if it can.
    static boolean isLong(final Val val) {
        return switch (val) {
            case final ValDouble number -> isLong(number.toDouble());
            case final ValFloat number -> isLong(number.toDouble());
            default -> true;
        };
    }

    private static boolean isLong(final double number) {
        // False for NaN too.
        return Math.abs(number) < MAX_LONG;
    }

    /// Checks that a constant can be read as a date, whatever the time zone.
    ///
    /// @param val The constant.
    /// @throws DateTimeException If it is not a date. The message is fit to show the query's author.
    static void check(final Val val) {
        readMillis(val, () -> ZoneOffset.UTC);
    }

    /// Reads text as a date.
    ///
    /// @param text An ISO 8601 date with an optional time and offset, or a number of milliseconds since the epoch.
    /// @param zone Gets the time zone of text that has no offset.
    /// @return The milliseconds since the epoch, or `null` if the text is not a date.
    /// @throws DateTimeException If it is a date or number that is too large.
    static Long parse(final String text, final Supplier<ZoneId> zone) {
        if (TimeRounder.isNumber(text)) {
            try {
                return TimeRounder.parseMillis(text);
            } catch (final ArithmeticException e) {
                throw new DateTimeException("'" + text + "' is too large to be a date", e);
            }
        }
        try {
            return parseDateTime(text, zone);
        } catch (final DateTimeParseException e) {
            // Not a date and time.
        }
        final LocalDate date;
        try {
            date = LocalDate.parse(text);
        } catch (final DateTimeParseException e) {
            return null;
        }
        return toMillis(date.atStartOfDay(zone.get()).toInstant(), text);
    }

    /// Reads text as a date and time.
    ///
    /// @param text An ISO 8601 date and time with an optional offset.
    /// @param zone Gets the time zone of text that has no offset.
    /// @return The milliseconds since the epoch.
    /// @throws DateTimeParseException If the text is not a date and time.
    /// @throws DateTimeException      If the date is too large.
    static long parseDateTime(final String text, final Supplier<ZoneId> zone) {
        final TemporalAccessor temporal = DateUtil.DEFAULT_ISO_PARSER.parseBest(
                text, ZonedDateTime::from, LocalDateTime::from);
        return temporal instanceof final LocalDateTime local
                ? toMillis(local.atZone(zone.get()).toInstant(), text)
                : toMillis(Instant.from(temporal), text);
    }

    /// Gets the milliseconds of a date.
    ///
    /// @param instant The date.
    /// @param text    The date as it is shown if it is too large.
    /// @return The milliseconds since the epoch.
    /// @throws DateTimeException If there are too many. The message is fit to show the query's author.
    static long toMillis(final Instant instant, final String text) {
        try {
            return instant.toEpochMilli();
        } catch (final ArithmeticException e) {
            throw new DateTimeException("'" + text + "' is too large to be a date", e);
        }
    }
}
