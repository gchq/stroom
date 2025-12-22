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

import stroom.query.language.functions.ref.DoubleListReference;
import stroom.query.language.functions.ref.StoredValues;
import stroom.query.language.functions.ref.ValueReferenceIndex;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = StDev.NAME,
        helpAnchor = "stdev-standard-deviation",
        commonReturnType = ValDouble.class,
        commonReturnDescription = "The standard deviation of all values.",
        signatures = {
                @FunctionSignature(
                        category = FunctionCategory.AGGREGATE,
                        description = "Determines the standard deviation across all grouped records.",
                        args = @FunctionArg(
                                name = "values",
                                description = "Grouped field or the result of another function",
                                argType = ValNumber.class)),
                @FunctionSignature(
                        category = FunctionCategory.MATHEMATICS,
                        subCategories = "Statistical",
                        description = "Determines the standard deviation from all the arguments.",
                        args = @FunctionArg(
                                name = "value",
                                description = "Field, the result of another function or a constant.",
                                argType = ValNumber.class,
                                isVarargs = true,
                                minVarargsCount = 2))
        })
class StDev extends AbstractManyChildFunction implements AggregateFunction {

    static final String NAME = "stDev";

    private DoubleListReference doubleListReference;

    public StDev(final String name) {
        super(name, 1, Integer.MAX_VALUE);
    }

    @Override
    public void addValueReferences(final ValueReferenceIndex valueReferenceIndex) {
        doubleListReference = valueReferenceIndex.addDoubleList(name);
        super.addValueReferences(valueReferenceIndex);
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        super.setParams(params);

        for (final Function function : functions) {
            if (function.hasAggregate()) {
                throw new ParseException("Inner param of '" + name + "' cannot be an aggregating function", 0);
            }
        }
    }

    @Override
    public Generator createGenerator() {
        // If we only have a single param then we are operating in aggregate
        // mode.
        if (isAggregate()) {
            final Generator childGenerator = functions[0].createGenerator();
            return new AggregateGen(childGenerator, doubleListReference);
        }

        return super.createGenerator();
    }

    @Override
    protected Generator createGenerator(final Generator[] childGenerators) {
        return new Gen(childGenerators);
    }

    @Override
    public boolean isAggregate() {
        return functions.length == 1;
    }

    private static final class AggregateGen extends AbstractSingleChildGenerator {

        private final DoubleListReference doubleListReference;

        AggregateGen(final Generator childGenerator,
                     final DoubleListReference doubleListReference) {
            super(childGenerator);
            this.doubleListReference = doubleListReference;
        }

        @Override
        public void set(final Val[] values, final StoredValues storedValues) {
            childGenerator.set(values, storedValues);

            final List<Double> list = doubleListReference.get(storedValues);
            final Double d = childGenerator.eval(storedValues, null).toDouble();
            if (d != null) {
                list.add(d);
                doubleListReference.set(storedValues, list);
            }
        }

        @Override
        public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
            final List<Double> list = doubleListReference.get(storedValues);
            if (list.size() == 0) {
                return ValNull.INSTANCE;
            }

            // Isolate list.
            final Double[] arr = list.toArray(new Double[0]);

            // calculate variance
            return ValDouble.create(Statistics.standardDeviation(arr));
        }

        @Override
        public void merge(final StoredValues existingValues, final StoredValues newValues) {
            final List<Double> existingList = doubleListReference.get(existingValues);
            final List<Double> newList = doubleListReference.get(newValues);
            if (newList.size() > 0) {
                existingList.addAll(newList);
                doubleListReference.set(existingValues, existingList);
            }
            super.merge(existingValues, newValues);
        }
    }

    private static final class Gen extends AbstractManyChildGenerator {

        Gen(final Generator[] generators) {
            super(generators);
        }

        @Override
        public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
            final List<Double> list = new ArrayList<>(childGenerators.length);
            for (final Generator gen : childGenerators) {
                final Val val = gen.eval(storedValues, childDataSupplier);
                if (!val.type().isValue()) {
                    return val;
                }
                final Double value = val.toDouble();
                if (value != null) {
                    list.add(value);
                }
            }

            if (list.size() == 0) {
                return ValNull.INSTANCE;
            }

            // Isolate list.
            final Double[] arr = list.toArray(new Double[0]);

            // calculate variance
            return ValDouble.create(Statistics.standardDeviation(arr));
        }
    }
}
