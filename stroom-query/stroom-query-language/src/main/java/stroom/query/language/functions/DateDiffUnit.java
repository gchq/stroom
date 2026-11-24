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
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalUnit;
import java.util.Arrays;
import java.util.stream.Collectors;

/// A unit that the difference between dates is counted in. Days and longer are counted on the local line of a time
/// zone, and shorter units are exact.
enum DateDiffUnit {
    YEAR("year", ChronoUnit.YEARS),
    QUARTER("quarter", IsoFields.QUARTER_YEARS),
    MONTH("month", ChronoUnit.MONTHS),
    WEEK("week", ChronoUnit.WEEKS),
    DAY("day", ChronoUnit.DAYS),
    HOUR("hour", ChronoUnit.HOURS),
    MINUTE("minute", ChronoUnit.MINUTES),
    SECOND("second", ChronoUnit.SECONDS),
    MILLISECOND("millisecond", ChronoUnit.MILLIS);

    private final String name;
    private final TemporalUnit temporalUnit;

    DateDiffUnit(final String name, final TemporalUnit temporalUnit) {
        this.name = name;
        this.temporalUnit = temporalUnit;
    }

    /// Finds a unit by its name.
    ///
    /// @param name The name, such as `month`.
    /// @return The unit.
    /// @throws DateTimeException If there is no unit with that name.
    static DateDiffUnit fromName(final String name) {
        for (final DateDiffUnit unit : values()) {
            if (unit.name.equals(name)) {
                return unit;
            }
        }
        throw new DateTimeException("Unknown unit '" + name + "'. Use one of "
                                    + Arrays.stream(values())
                                            .map(unit -> unit.name)
                                            .collect(Collectors.joining(", ")));
    }

    /// Tells whether the unit is an exact length of time, rather than one that depends on the calendar.
    ///
    /// @return True if it is.
    boolean isExact() {
        return temporalUnit.isTimeBased();
    }

    /// Counts the whole units that can be added to one date without going past another.
    ///
    /// As in java.time, a month that is too short for the day of the month is not whole until it is over, so there is
    /// no whole month from 31 January to 29 February.
    ///
    /// @param start The date to count from.
    /// @param end   The date to count to, in the same time zone. The count is negative if it is before the start.
    /// @return The number of whole units.
    long between(final ZonedDateTime start, final ZonedDateTime end) {
        final int direction = end.isBefore(start)
                ? -1
                : 1;
        // This only looks at the local times, so it can be out when clocks change.
        long units = temporalUnit.between(start, end);
        while (units != 0 && isPast(start, units, end, direction)) {
            units -= direction;
        }
        while (isWhole(start, units + direction) && !isPast(start, units + direction, end, direction)) {
            units += direction;
        }
        return units;
    }

    private boolean isWhole(final ZonedDateTime start, final long units) {
        return units < 0
               || !isMonths()
               || add(start, units).toLocalDate().getDayOfMonth() == start.getDayOfMonth();
    }

    private ZonedDateTime add(final ZonedDateTime start, final long units) {
        // Java adds quarters as years and then months, which can lose a day at the end of a short February in between.
        return this == QUARTER
                ? start.plusMonths(Math.multiplyExact(units, 3L))
                : start.plus(units, temporalUnit);
    }

    private boolean isMonths() {
        return this == YEAR || this == QUARTER || this == MONTH;
    }

    private boolean isPast(final ZonedDateTime start,
                           final long units,
                           final ZonedDateTime end,
                           final int direction) {
        return add(start, units).compareTo(end) * direction > 0;
    }
}
