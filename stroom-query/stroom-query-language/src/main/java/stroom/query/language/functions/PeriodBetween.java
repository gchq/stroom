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

import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.function.Supplier;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = PeriodBetween.NAME,
        commonCategory = FunctionCategory.DATE,
        commonSubCategories = "Calendar",
        commonReturnType = ValPeriod.class,
        commonReturnDescription = "The years, months and days between the dates.",
        signatures = @FunctionSignature(
                description = "Gets the most years, months and days that can be added to one " +
                              "date without going past another, on the calendar in a time zone.",
                args = {
                        @FunctionArg(
                                name = "start",
                                argType = ValDate.class,
                                description = "The date to start from."),
                        @FunctionArg(
                                name = "end",
                                argType = ValDate.class,
                                description = "The date to end at. The period is negative if it is before the start."),
                        @FunctionArg(
                                name = "timezone",
                                argType = ValString.class,
                                isOptional = true,
                                description = "The time zone, e.g. 'Europe/London', which can be a field. The time " +
                                              "zone of the query if it is not given.")}))
class PeriodBetween extends AbstractCalendarFunction {

    static final String NAME = "periodBetween";

    public PeriodBetween(final ExpressionContext expressionContext, final String name) {
        super(expressionContext, name, 2, 3);
    }

    @Override
    int getZonePos(final Param[] params) {
        return 2;
    }

    @Override
    Val evaluate(final Val[] vals, final Supplier<ZoneId> zone) {
        final ZoneId zoneId = zone.get();
        final ZonedDateTime start = DateReader.read(vals[0], zoneId);
        final ZonedDateTime end = DateReader.read(vals[1], zoneId);
        return ValPeriod.create(between(start, end));
    }

    /// Gets the most years, months and days that can be added to one date without going past another.
    ///
    /// @param start The date to start from.
    /// @param end   The date to end at, in the same time zone.
    /// @return The period, which is negative if the end is before the start.
    static Period between(final ZonedDateTime start, final ZonedDateTime end) {
        final int direction = end.isBefore(start)
                ? -1
                : 1;
        // Whole months and then days. Period.between can't be used, as backwards it works out the days from a month
        // that may be too short for the day of the month, so that adding its period to the start goes past the end.
        final Period months = Period.ofMonths(Math.toIntExact(DateDiffUnit.MONTH.between(start, end)));
        long days = ChronoUnit.DAYS.between(start.plus(months).toLocalDate(), end.toLocalDate());
        // The last day only counts if its time of day has come.
        while (days != 0 && isPast(start, months.plusDays(days), end, direction)) {
            days -= direction;
        }
        while (!isPast(start, months.plusDays(days + direction), end, direction)) {
            days += direction;
        }
        return months.plusDays(days).normalized();
    }

    private static boolean isPast(final ZonedDateTime start,
                                  final Period period,
                                  final ZonedDateTime end,
                                  final int direction) {
        return start.plus(period).compareTo(end) * direction > 0;
    }
}
