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

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = RoundYear.NAME,
        commonCategory = FunctionCategory.DATE,
        commonSubCategories = RoundDate.ROUND_SUB_CATEGORY,
        commonReturnType = ValLong.class,
        commonReturnDescription = "The time as milliseconds since the epoch (1st Jan 1970).",
        signatures = @FunctionSignature(
                description = "Rounds the supplied time up or down to the nearest start of a year.",
                args = @FunctionArg(
                        name = "time",
                        description = "The time to round in milliseconds since the epoch or as a string " +
                                "formatted using the default date format.",
                        argType = Val.class)))
class RoundYear extends RoundDate {

    static final String NAME = "roundYear";
    private static final Calc CALC = new Calc();

    public RoundYear(final String name) {
        super(name);
    }

    @Override
    protected RoundCalculator getCalculator() {
        return CALC;
    }

    static class Calc extends RoundDateCalculator {


        @Override
        protected LocalDateTime adjust(final LocalDateTime dateTime) {
            LocalDateTime result = dateTime.toLocalDate().withDayOfYear(1).atStartOfDay();
            if (dateTime.isAfter(result.plusMonths(6))) {
                result = result.plusYears(1);
            }
            return result;
        }
    }
}
