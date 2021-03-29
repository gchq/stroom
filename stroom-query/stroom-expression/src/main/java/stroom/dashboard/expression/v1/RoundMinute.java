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

package stroom.dashboard.expression.v1;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = RoundMinute.NAME,
        commonCategory = FunctionCategory.DATE,
        commonSubCategories = RoundDate.ROUND_SUB_CATEGORY,
        commonReturnType = ValLong.class,
        commonReturnDescription = "The time as milliseconds since the epoch (1st Jan 1970).",
        signatures = @FunctionSignature(
                description = "Rounds the supplied time up or down to the nearest start of a minute.",
                args = @FunctionArg(
                        name = "time",
                        description = "The time to round in milliseconds since the epoch or as a string " +
                                "formatted using the default date format.",
                        argType = Val.class)))
class RoundMinute extends RoundDate {

    static final String NAME = "roundMinute";
    private static final Calc CALC = new Calc();

    public RoundMinute(final String name) {
        super(name);
    }

    @Override
    protected RoundCalculator getCalculator() {
        return CALC;
    }

    static class Calc extends RoundDateCalculator {

        private static final long serialVersionUID = -5893918049538006730L;

        @Override
        protected LocalDateTime adjust(final LocalDateTime dateTime) {
            LocalDateTime result = dateTime.truncatedTo(ChronoUnit.MINUTES);
            if (dateTime.isAfter(result.plusSeconds(30))) {
                result = result.plusMinutes(1);
            }
            return result;
        }
    }
}
