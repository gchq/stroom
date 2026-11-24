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
import java.util.function.Supplier;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = GetDayOfWeek.NAME,
        commonCategory = FunctionCategory.DATE,
        commonSubCategories = "Calendar",
        commonReturnType = ValInteger.class,
        commonReturnDescription = "The day of the week, from 1 for Monday to 7 for Sunday.",
        signatures = @FunctionSignature(
                description = "Gets the day of the week of a date, as it is in a time zone.",
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
class GetDayOfWeek extends AbstractCalendarFunction {

    static final String NAME = "getDayOfWeek";

    public GetDayOfWeek(final ExpressionContext expressionContext, final String name) {
        super(expressionContext, name, 1, 2);
    }

    @Override
    int getZonePos(final Param[] params) {
        return 1;
    }

    @Override
    Val evaluate(final Val[] vals, final Supplier<ZoneId> zone) {
        return DateField.DAY_OF_WEEK.get(DateReader.read(vals[0], zone.get()));
    }
}
