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

import stroom.query.language.functions.ref.StoredValues;

import java.text.ParseException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.function.Supplier;

@FunctionDef(
        name = IsWeekend.NAME,
        commonCategory = FunctionCategory.DATE,
        commonSubCategories = "Variable",
        commonReturnType = ValDate.class,
        commonReturnDescription = "Is the date a weekend day or not.",
        signatures = @FunctionSignature(
                description = "Returns whether a date is part of the weekend or not.",
                args = {}))
class IsWeekend extends AbstractDateTimeFunction {

    static final String NAME = "isWeekend";
    private Function function;

    public IsWeekend(final ExpressionContext expressionContext, final String name) {
        super(expressionContext, name, 1, 1);
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        super.setParams(params);

        final Param param = params[0];
        if (param instanceof Function) {
            function = (Function) param;
        } else {
            function = new StaticValueFunction((Val) param);
        }
    }

    @Override
    public Generator createGenerator() {
        final Generator childGenerator = function.createGenerator();
        return new Generator() {
            public Val eval(final StoredValues storedValues,
                            final Supplier<ChildData> childDataSupplier) {
                final Val value = childGenerator.eval(storedValues, childDataSupplier);
                return calc(value);
            }

            @Override
            public void merge(final StoredValues existingValues, final StoredValues newValues) {

            }

            @Override
            public void set(final Val[] values, final StoredValues storedValues) {
                childGenerator.set(values, storedValues);
            }
        };
    }

    @Override
    public boolean hasAggregate() {
        return function.hasAggregate();
    }

    public Val calc(final Val value) {
        final Long val = value.toLong();
        if (val == null) {
            return ValNull.INSTANCE;
        }

        final ZonedDateTime zonedDateTime = Instant.ofEpochMilli(val).atZone(zoneId);
        final int dayOfWeek = zonedDateTime.get(java.time.temporal.ChronoField.DAY_OF_WEEK);
        final boolean isWeekend = (dayOfWeek == 6 || dayOfWeek == 7); // Saturday or Sunday
        return ValBoolean.create(isWeekend);
    }
}
