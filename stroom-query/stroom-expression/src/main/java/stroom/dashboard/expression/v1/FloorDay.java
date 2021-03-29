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

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = FloorDay.NAME,
        commonCategory = FunctionCategory.DATE,
        commonSubCategories = RoundDate.FLOOR_SUB_CATEGORY,
        commonReturnType = ValLong.class,
        commonReturnDescription = "The time as milliseconds since the epoch (1st Jan 1970).",
        signatures = @FunctionSignature(
                description = "Rounds the supplied time down to the start of the current day.",
                args = @FunctionArg(
                        name = "time",
                        description = "The time to round in milliseconds since the epoch or as a string " +
                                "formatted using the default date format.",
                        argType = Val.class)))
class FloorDay extends RoundDate {

    static final String NAME = "floorDay";
    private static final Calc CALC = new Calc();

    public FloorDay(final String name) {
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
            return dateTime.toLocalDate().atStartOfDay();
        }
    }
}
