/*
 * Copyright 2020 Crown Copyright
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

import stroom.query.language.functions.TimeRounder.Mode;

import java.time.Period;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = CeilingYear.NAME,
        commonCategory = FunctionCategory.DATE,
        commonSubCategories = AbstractRoundDateTime.CEILING_SUB_CATEGORY,
        commonReturnType = ValDate.class,
        commonReturnDescription = "The result date and time.",
        signatures = @FunctionSignature(
                description = "Rounds the supplied time up to the start of the next year, unless it is the start of " +
                        "one, in the time zone of the query.",
                args = @FunctionArg(
                        name = "time",
                        description = "The date, a number of milliseconds since the epoch (1 Jan 1970), or text of " +
                                "either. Text that is not a date gives a null.",
                        argType = Val.class)))
class CeilingYear extends AbstractRoundDateTime {

    static final String NAME = "ceilingYear";

    public CeilingYear(final ExpressionContext expressionContext, final String name) {
        super(expressionContext, name);
    }

    @Override
    protected DateTimeAdjuster getAdjuster() {
        final TimeRounder timeRounder = TimeRounder.ofPeriod(Mode.CEILING, Period.ofYears(1), null);
        return timeRounder::round;
    }
}
