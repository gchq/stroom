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
import stroom.query.language.functions.ref.ValueReferenceIndex;

import java.text.ParseException;
import java.util.function.Supplier;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = Period.NAME,
        commonCategory = FunctionCategory.AGGREGATE,
        commonReturnType = Val.class,
        commonDescription = "This function provides a way of selectively applying functions to values for a specific " +
                "period when using the window feature",
        signatures = @FunctionSignature(
                returnDescription = "A value computed by the nested functions for the specified period",
                args = {
                        @FunctionArg(
                                name = "period",
                                description = "The window period to apply functions to.",
                                argType = ValInteger.class),
                        @FunctionArg(
                                name = "function",
                                description = "The function to apply to values for the selected period.",
                                argType = Val.class)
                }))
public class Period extends AbstractFunction implements AggregateFunction {

    static final String NAME = "period";
    private int period;
    private Function function;

    public Period(final String name) {
        super(name, 1, 2);
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        super.setParams(params);
        period = Integer.parseInt(params[0].toString());

        if (params.length == 2) {
            final Param param = params[1];
            if (param instanceof Function) {
                function = (Function) param;
            } else {
                function = new StaticValueFunction((Val) param);
            }
        } else {
            function = new Count(Count.NAME);
        }
    }

    @Override
    public void addValueReferences(final ValueReferenceIndex valueReferenceIndex) {
        function.addValueReferences(valueReferenceIndex);
    }

    @Override
    public Generator createGenerator() {
        final Generator childGenerator = function.createGenerator();
        return new Gen(period, childGenerator);
    }

    @Override
    public boolean isAggregate() {
        return true;
    }

    @Override
    public boolean hasAggregate() {
        return isAggregate();
    }

    public static final class Gen extends AbstractNoChildGenerator {

        private final int period;
        private final Generator childGenerator;

        public Gen(final int period, final Generator childGenerator) {
            this.period = period;
            this.childGenerator = childGenerator;
        }

        @Override
        public void set(final Val[] values, final StoredValues storedValues) {
            // Filter on period.
            if (storedValues.getPeriod() == period) {
                childGenerator.set(values, storedValues);
            }
        }

        @Override
        public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
            return childGenerator.eval(storedValues, childDataSupplier);
        }

        @Override
        public void merge(final StoredValues existingValues, final StoredValues newValues) {
            childGenerator.merge(existingValues, newValues);
        }

        public int getPeriod() {
            return period;
        }
    }
}
