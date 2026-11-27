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

import java.time.ZoneId;
import java.time.temporal.ChronoField;
import java.util.function.Supplier;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = TimeOfDay.NAME,
        commonCategory = FunctionCategory.DATE,
        commonSubCategories = "Calendar",
        commonReturnType = ValDuration.class,
        commonReturnDescription = "The time on the clock since midnight, from 0h to 24h.",
        signatures = @FunctionSignature(
                description = "Gets the time of day of a date, as it is on the clock in a time " +
                              "zone. 09:00 is 9h, even on a day when the clocks change.",
                args = {
                        @FunctionArg(
                                name = "time",
                                argType = ValDate.class,
                                description = "The date and time."),
                        @FunctionArg(
                                name = "timezone",
                                argType = ValString.class,
                                isOptional = true,
                                description = "The time zone, e.g. 'Europe/London', which can be a field. The time " +
                                              "zone of the query if it is not given.")}))
class TimeOfDay extends AbstractCalendarFunction {

    static final String NAME = "timeOfDay";

    public TimeOfDay(final ExpressionContext expressionContext, final String name) {
        super(expressionContext, name, 1, 2);
    }

    @Override
    int getZonePos(final Param[] params) {
        return 1;
    }

    @Override
    void checkConstant(final int pos, final Val val) {
        DateReader.check(val);
    }

    @Override
    Val evaluate(final Val[] vals, final Supplier<ZoneId> zone) {
        return ValDuration.create(DateReader.read(vals[0], zone.get()).get(ChronoField.MILLI_OF_DAY));
    }
}
