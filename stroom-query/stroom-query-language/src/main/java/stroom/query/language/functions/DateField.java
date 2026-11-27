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
import java.time.temporal.ChronoField;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalField;
import java.util.Arrays;
import java.util.stream.Collectors;

/// A part of a date, as it is in a time zone. Weeks are ISO weeks, which start on a Monday.
enum DateField {
    YEAR("year", ChronoField.YEAR),
    QUARTER("quarter", IsoFields.QUARTER_OF_YEAR),
    MONTH("month", ChronoField.MONTH_OF_YEAR),
    DAY_OF_MONTH("dayOfMonth", ChronoField.DAY_OF_MONTH),
    DAY_OF_WEEK("dayOfWeek", ChronoField.DAY_OF_WEEK),
    DAY_OF_YEAR("dayOfYear", ChronoField.DAY_OF_YEAR),
    WEEK_OF_YEAR("weekOfYear", IsoFields.WEEK_OF_WEEK_BASED_YEAR),
    WEEK_BASED_YEAR("weekBasedYear", IsoFields.WEEK_BASED_YEAR),
    HOUR("hour", ChronoField.HOUR_OF_DAY),
    MINUTE("minute", ChronoField.MINUTE_OF_HOUR),
    SECOND("second", ChronoField.SECOND_OF_MINUTE),
    MILLISECOND("millisecond", ChronoField.MILLI_OF_SECOND);

    private final String name;
    private final TemporalField temporalField;

    DateField(final String name, final TemporalField temporalField) {
        this.name = name;
        this.temporalField = temporalField;
    }

    /// Finds a field by its name.
    ///
    /// @param name The name, such as `dayOfMonth`.
    /// @return The field.
    /// @throws DateTimeException If there is no field with that name.
    static DateField fromName(final String name) {
        for (final DateField field : values()) {
            if (field.name.equals(name)) {
                return field;
            }
        }
        throw new DateTimeException("Unknown part of a date '" + name + "'. Use one of "
                                    + Arrays.stream(values())
                                            .map(field -> field.name)
                                            .collect(Collectors.joining(", ")));
    }

    /// Gets the part of a date.
    ///
    /// @param date The date.
    /// @return The part.
    Val get(final ZonedDateTime date) {
        return ValInteger.create(date.get(temporalField));
    }
}
