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

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = CeilingWeek.NAME,
        commonCategory = FunctionCategory.DATE,
        commonSubCategories = RoundDate.CEILING_SUB_CATEGORY,
        commonReturnType = ValLong.class,
        commonReturnDescription = "The time as milliseconds since the epoch (1st Jan 1970).",
        signatures = @FunctionSignature(
                description = "Rounds the supplied time up to the end of the current week.",
                args = @FunctionArg(
                        name = "time",
                        description = "The time to round in milliseconds since the epoch or as a string " +
                                "formatted using the default date format.",
                        argType = Val.class)))
class CeilingWeek extends RoundDate {

    static final String NAME = "ceilingWeek";

    private final ZoneId zoneId;

    public CeilingWeek(final ExpressionContext expressionContext, final String name) {
        super(name);
        zoneId = AbstractTimeFunction.getZoneId(expressionContext.getDateTimeSettings());
    }

    @Override
    protected RoundCalculator getCalculator() {
        return new Calc(zoneId);
    }

    static class Calc extends RoundDateCalculator {

        private final ZoneId zoneId;

        public Calc(final ZoneId zoneId) {
            this.zoneId = zoneId;
        }

        @Override
        protected LocalDateTime adjust(final LocalDateTime dateTime) {
            LocalDateTime result = dateTime.with(WeekFields.ISO.getFirstDayOfWeek())
                    .truncatedTo(ChronoUnit.DAYS);
            if (dateTime.isAfter(result)) {
                result = result.plusWeeks(1);
            }
            return result;
        }
    }
}
