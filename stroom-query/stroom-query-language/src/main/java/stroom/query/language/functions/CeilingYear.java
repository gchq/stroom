/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.query.language.functions;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = CeilingYear.NAME,
        commonCategory = FunctionCategory.DATE,
        commonSubCategories = RoundDate.CEILING_SUB_CATEGORY,
        commonReturnType = ValLong.class,
        commonReturnDescription = "The time as milliseconds since the epoch (1st Jan 1970).",
        signatures = @FunctionSignature(
                description = "Rounds the supplied time up to the start of the next year.",
                args = @FunctionArg(
                        name = "time",
                        description = "The time to round in milliseconds since the epoch or as a string " +
                                "formatted using the default date format.",
                        argType = Val.class)))
class CeilingYear extends RoundDate {

    static final String NAME = "ceilingYear";
    private static final Calc CALC = new Calc();

    private final ExpressionContext expressionContext;

    public CeilingYear(final String name, final ExpressionContext expressionContext) {
        super(name);
        this.expressionContext = expressionContext;
    }

    @Override
    protected RoundCalculator getCalculator() {
        return CALC;
    }

    protected ZonedDateTime toZonedDateTime(final long epochMillis) {
        final ZoneId zoneId = AbstractTimeFunction.getZoneId(expressionContext.getDateTimeSettings());
        return Instant.ofEpochMilli(epochMillis).atZone(zoneId);
    }

    static class Calc extends RoundDateCalculator {

        @Override
        protected LocalDateTime adjust(final LocalDateTime dateTime) {
            final ZonedDateTime zoned = dateTime.atZone(ZoneOffset.UTC);
            ZonedDateTime startOfYear = zoned
                    .withMonth(1)
                    .withDayOfMonth(1)
                    .withHour(0)
                    .withMinute(0)
                    .withSecond(0)
                    .withNano(0);
            if (zoned.isAfter(startOfYear)) {
                startOfYear = startOfYear.plusYears(1);
            }
            return startOfYear.toLocalDateTime();
        }
    }
}
