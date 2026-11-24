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

import stroom.query.language.functions.ref.FunctionWarnings;
import stroom.query.language.functions.ref.StoredValues;
import stroom.query.language.functions.ref.ValListReference;
import stroom.query.language.functions.ref.ValueReferenceIndex;

import java.text.ParseException;
import java.util.List;
import java.util.function.Supplier;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = CountUnique.NAME,
        commonCategory = FunctionCategory.AGGREGATE,
        commonReturnType = ValInteger.class,
        commonReturnDescription = "The number of unique values",
        signatures = @FunctionSignature(
                category = FunctionCategory.AGGREGATE,
                description = "Determines the number of unique values across all grouped records.",
                args = @FunctionArg(
                        name = "values",
                        description = "Grouped field or the result of another function",
                        argType = Val.class)))
class CountUnique extends AbstractFunction implements AggregateFunction {

    static final String NAME = "countUnique";

    private final int maxValues;
    private Generator gen;
    private Function function;
    private ValListReference valListReference;
    private FunctionWarnings functionWarnings;

    /// Create the function.
    ///
    /// @param context Gives the most unique values to count, or null for the default.
    /// @param name    The name the function was called by.
    public CountUnique(final ExpressionContext context,
                       final String name) {
        super(name, 1, 1);
        maxValues = context == null
                ? ExpressionContext.DEFAULT_MAX_COUNT_UNIQUE_VALUES
                : context.getMaxCountUniqueValues();
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        super.setParams(params);

        final Param param = params[0];
        if (param instanceof Function) {
            function = (Function) param;

            if (function.hasAggregate()) {
                throw new ParseException(name + " cannot be applied to aggregating function", 0);
            }

        } else {
            /*
             * Optimise replacement of static input in case user does something
             * stupid.
             */
            gen = new StaticValueGen(ValInteger.create(1));
        }
    }

    @Override
    public void addValueReferences(final ValueReferenceIndex valueReferenceIndex) {
        valListReference = valueReferenceIndex.addValList(name);
        functionWarnings = valueReferenceIndex.getFunctionWarnings();
        super.addValueReferences(valueReferenceIndex);
    }

    @Override
    public Generator createGenerator() {
        if (gen != null) {
            return gen;
        }

        final Generator childGenerator = function.createGenerator();
        return new Gen(childGenerator, valListReference, maxValues, functionWarnings);
    }

    @Override
    public boolean isAggregate() {
        return true;
    }

    @Override
    public boolean hasAggregate() {
        return isAggregate();
    }

    @Override
    public boolean requiresChildData() {
        if (function != null) {
            return function.requiresChildData();
        }
        return super.requiresChildData();
    }

    private static final class Gen extends AbstractSingleChildGenerator {

        private final ValListReference valListReference;
        private final int maxValues;
        private final FunctionWarnings functionWarnings;
        private final String limitMessage;

        Gen(final Generator childGenerator,
            final ValListReference valListReference,
            final int maxValues,
            final FunctionWarnings functionWarnings) {
            super(childGenerator);
            this.valListReference = valListReference;
            this.maxValues = maxValues;
            this.functionWarnings = functionWarnings;
            this.limitMessage = NAME + "() stopped counting at its limit of " + maxValues +
                                " unique values for one or more groups. Use " + CountUniqueApprox.NAME +
                                "() to estimate higher counts.";
        }

        @Override
        public void set(final Val[] values, final StoredValues storedValues) {
            childGenerator.set(values, storedValues);
            final Val val = childGenerator.eval(storedValues, null);
            if (val.type().isValue()) {
                final List<Val> list = valListReference.get(storedValues);
                if (!list.contains(val) && checkLimit(list)) {
                    list.add(val);
                    valListReference.set(storedValues, list);
                }
            }
        }

        // Find out if there is room for another value, and warn if not. A row that was stored with a higher limit can
        // already have more values than this one, which it keeps.
        private boolean checkLimit(final List<Val> list) {
            if (list.size() < maxValues) {
                return true;
            }
            functionWarnings.warn(limitMessage);
            return false;
        }

        @Override
        public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
            final List<Val> list = valListReference.get(storedValues);
            return ValInteger.create(list.size());
        }

        @Override
        public void merge(final StoredValues existingValues, final StoredValues newValues) {
            final List<Val> existingList = valListReference.get(existingValues);
            final List<Val> newList = valListReference.get(newValues);
            boolean changed = false;
            for (final Val value : newList) {
                if (!existingList.contains(value)) {
                    if (!checkLimit(existingList)) {
                        // None of the other values can be added either.
                        break;
                    }
                    existingList.add(value);
                    changed = true;
                }
            }
            if (changed) {
                valListReference.set(existingValues, existingList);
            }
            super.merge(existingValues, newValues);
        }
    }
}
