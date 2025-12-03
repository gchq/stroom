/*
 * Copyright 2016-2025 Crown Copyright
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

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalField;
import java.time.temporal.WeekFields;
import java.util.Locale;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = Week.NAME,
        commonCategory = FunctionCategory.DATE,
        commonSubCategories = "Variable",
        commonReturnType = ValDate.class,
        commonReturnDescription = "The current date and time truncated to the start of the week.",
        signatures = @FunctionSignature(
                description = "Returns the current date and time truncated to the start of the week.",
                args = {}))
class Week extends AbstractCurrentDateTimeFunction {

    static final String NAME = "week";

    private final Generator generator;

    public Week(final ExpressionContext expressionContext, final String name) {
        super(expressionContext, name);
        final ZonedDateTime referenceTime = getReferenceTime();
        final TemporalField fieldISO = WeekFields.of(Locale.UK).dayOfWeek();
        ZonedDateTime time = referenceTime.with(fieldISO, 1);
        time = time.truncatedTo(ChronoUnit.DAYS);
        generator = new StaticValueGen(ValDate.create(time.toInstant().toEpochMilli()));
    }

    @Override
    public Generator createGenerator() {
        return generator;
    }
}
