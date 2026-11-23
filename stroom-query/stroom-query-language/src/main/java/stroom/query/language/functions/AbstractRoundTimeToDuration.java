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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.zone.ZoneOffsetTransition;

/// The shared part of `floorTime`, `ceilingTime` and `roundTime`, which move a time onto a
/// multiple of a duration given as their second argument.
///
/// Multiples of the duration are counted from midnight on 1 January 1970 in the query's time
/// zone, not in UTC, so a duration of a day starts at local midnight just as `floorDay` does.
///
/// The duration is read and checked once, when the generator is created, rather than for every
/// row. A bad duration still gives an error value in every row, as it always has, because an
/// error value is how a function reports a problem with its arguments.
abstract class AbstractRoundTimeToDuration extends AbstractRoundDateTime {

    private static final String DURATION_PARAM_NAME = "duration";

    /// @param expressionContext the context, which supplies the query's time zone
    /// @param name              the name the function was called by
    AbstractRoundTimeToDuration(final ExpressionContext expressionContext, final String name) {
        super(expressionContext, name, 2, 2);
    }

    @Override
    protected DateTimeAdjuster getAdjuster() {
        final long durationMillis;
        try {
            durationMillis = parseDurationMillis(params[1]);
        } catch (final IllegalArgumentException e) {
            return zonedDateTime -> {
                throw e;
            };
        }
        return zonedDateTime -> {
            final long localMillis = toLocalMillis(zonedDateTime.toLocalDateTime());
            final long adjustedLocalMillis = adjust(localMillis, durationMillis);
            return toZonedDateTime(fromLocalMillis(adjustedLocalMillis), zonedDateTime.getOffset());
        };
    }

    /// Turns a local date-time into a time in the query's zone.
    ///
    /// Where the local time happens twice, as when clocks go back, the one with the same offset
    /// as the time being adjusted is used, so the result stays on the same side of the change.
    /// Where the local time never happens, as when clocks go forward, the moment of the change is
    /// used: it lies between the time being adjusted and the local time it was moved to, so a
    /// floor never moves forwards and a ceiling never moves back.
    ///
    /// @param localDateTime   the adjusted local date-time
    /// @param preferredOffset the offset of the time that was adjusted
    /// @return the adjusted time in the query's zone
    private ZonedDateTime toZonedDateTime(final LocalDateTime localDateTime, final ZoneOffset preferredOffset) {
        final ZoneOffsetTransition transition = zoneId.getRules().getTransition(localDateTime);
        if (transition != null && transition.isGap()) {
            return ZonedDateTime.ofInstant(transition.getInstant(), zoneId);
        }
        return ZonedDateTime.ofLocal(localDateTime, zoneId, preferredOffset);
    }

    // Local wall-clock time as milliseconds, measured as if the zone were UTC, so that multiples
    // of a day fall on local midnight and multiples of an hour on the local hour.
    private static long toLocalMillis(final LocalDateTime localDateTime) {
        return localDateTime.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    private static LocalDateTime fromLocalMillis(final long localMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(localMillis), ZoneOffset.UTC);
    }

    /// Moves a time onto a multiple of the duration.
    ///
    /// @param millis         the local time to move, in milliseconds since the local epoch
    /// @param durationMillis the duration, in milliseconds, always greater than zero
    /// @return the moved local time, in milliseconds since the local epoch
    abstract long adjust(long millis, long durationMillis);

    /// How far a time is past the multiple of the duration at or before it, always in the range
    /// zero (inclusive) to the duration (exclusive).
    ///
    /// Java's `%` would give a negative remainder for a time before 1970, which moves the time the
    /// wrong way, so this uses `Math.floorMod`.
    ///
    /// @param millis         the time, in milliseconds since the epoch
    /// @param durationMillis the duration, in milliseconds, always greater than zero
    /// @return the remainder, in milliseconds
    static long remainder(final long millis, final long durationMillis) {
        return Math.floorMod(millis, durationMillis);
    }

    // Reads the argument via AbstractRoundDateTime.constantString; reading it with
    // param.toString() would silently break param('key') here.
    private long parseDurationMillis(final Param param) {
        final String value = constantString(param);
        if (value == null) {
            throw new IllegalArgumentException(
                    "Invalid " + DURATION_PARAM_NAME + ": " + param + " - " + whyNotConstant(param));
        }
        final Duration duration;
        try {
            duration = Duration.parse(value);
        } catch (final DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid " + DURATION_PARAM_NAME + " format: " + value);
        }
        final long durationMillis;
        try {
            durationMillis = duration.toMillis();
        } catch (final ArithmeticException e) {
            throw new IllegalArgumentException(
                    "Invalid " + DURATION_PARAM_NAME + ": " + value + " - it is too long");
        }
        // A duration under a millisecond is zero once in milliseconds, so it is refused too.
        if (durationMillis <= 0) {
            throw new IllegalArgumentException(
                    "Invalid " + DURATION_PARAM_NAME + ": " + value + " - it must be at least one millisecond");
        }
        return durationMillis;
    }
}
