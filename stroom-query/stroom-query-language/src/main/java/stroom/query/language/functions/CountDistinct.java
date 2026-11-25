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

import stroom.query.language.functions.hll.DistinctCounter;
import stroom.query.language.functions.hll.UniqueValHasher;
import stroom.query.language.functions.ref.DistinctCounterReference;
import stroom.query.language.functions.ref.StoredValues;
import stroom.query.language.functions.ref.ValueReferenceIndex;

import java.text.ParseException;
import java.util.function.Supplier;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = CountDistinct.NAME,
        commonCategory = FunctionCategory.AGGREGATE,
        commonDescription = "Counts the unique values across all grouped records. The count is exact up to a limit " +
                "and is an estimate above it.",
        commonReturnType = ValLong.class,
        commonReturnDescription = "The number of unique values.",
        signatures = @FunctionSignature(
                description = "Counts the unique values across all grouped records. The count is exact up to a limit " +
                        "for each group, which is " + ExpressionContext.DEFAULT_MAX_COUNT_DISTINCT_VALUES +
                        " unless it has been configured. Above the limit it is the same estimate as " + DCount.NAME +
                        ", which has a standard error of 0.8%, so a count just above the limit can be given as one " +
                        "just below it. Values are compared by a 64-bit hash, so the chance that any two of 10000 " +
                        "values are counted as one is about 3 in a trillion.",
                args = @FunctionArg(
                        name = "values",
                        description = "Grouped field or the result of another function.",
                        argType = Val.class)))
class CountDistinct extends AbstractFunction implements AggregateFunction {

    static final String NAME = "countDistinct";

    private final int maxValues;
    private Generator gen;
    private Function function;
    private DistinctCounterReference counterReference;

    /// Create the function.
    ///
    /// @param context Gives the most unique values to count exactly, or null for the default.
    /// @param name    The name the function was called by.
    public CountDistinct(final ExpressionContext context, final String name) {
        super(name, 1, 1);
        // The limit could be too low if the configuration is not valid, which must not fail every event.
        maxValues = context == null
                ? ExpressionContext.DEFAULT_MAX_COUNT_DISTINCT_VALUES
                : Math.max(1, context.getMaxCountDistinctValues());
    }

    /// Set the values to count.
    ///
    /// @param params The parameters of the function.
    /// @throws ParseException If the values are already aggregated.
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
            // A static value is always the one unique value.
            gen = new StaticValueGen(ValLong.create(1));
        }
    }

    /// Add a reference to the stored counter, unless the parameter is static.
    ///
    /// @param valueReferenceIndex The index to add the reference to.
    @Override
    public void addValueReferences(final ValueReferenceIndex valueReferenceIndex) {
        // A static value has nothing to store.
        if (function != null) {
            counterReference = valueReferenceIndex.addDistinctCounter(name, maxValues);
        }
        super.addValueReferences(valueReferenceIndex);
    }

    /// Create a generator that keeps the counter of each group.
    ///
    /// @return The generator.
    @Override
    public Generator createGenerator() {
        if (gen != null) {
            return gen;
        }

        final Generator childGenerator = function.createGenerator();
        return new Gen(childGenerator, counterReference);
    }

    /// Find out if this function aggregates values.
    ///
    /// @return True, as it always does.
    @Override
    public boolean isAggregate() {
        return true;
    }

    /// Find out if this function or any in its parameters aggregates values.
    ///
    /// @return True, as this one always does.
    @Override
    public boolean hasAggregate() {
        return isAggregate();
    }

    /// Find out if the rows of child groups are needed.
    ///
    /// @return True if the parameter of this function needs them.
    @Override
    public boolean requiresChildData() {
        if (function != null) {
            return function.requiresChildData();
        }
        return super.requiresChildData();
    }


    // --------------------------------------------------------------------------------


    private static final class Gen extends AbstractSingleChildGenerator {

        private final DistinctCounterReference counterReference;

        private Gen(final Generator childGenerator,
                    final DistinctCounterReference counterReference) {
            super(childGenerator);
            this.counterReference = counterReference;
        }

        /// Count the value of a row, unless it is null or an error.
        ///
        /// @param values       The values of the row.
        /// @param storedValues The stored values that hold the counter.
        @Override
        public void set(final Val[] values, final StoredValues storedValues) {
            childGenerator.set(values, storedValues);
            final Val val = childGenerator.eval(storedValues, null);
            if (val.type().isValue()) {
                final DistinctCounter counter = counterReference.get(storedValues);
                counter.add(UniqueValHasher.hash(val));
                counterReference.set(storedValues, counter);
            }
        }

        /// Get the number of unique values.
        ///
        /// @param storedValues      The stored values that hold the counter.
        /// @param childDataSupplier Not used.
        /// @return The number of unique values.
        @Override
        public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
            return ValLong.create(counterReference.get(storedValues).count());
        }

        /// Add what has been counted for some new values to the counter of the existing ones.
        ///
        /// @param existingValues The stored values to add to.
        /// @param newValues      The stored values to add.
        @Override
        public void merge(final StoredValues existingValues, final StoredValues newValues) {
            final DistinctCounter counter = counterReference.get(existingValues);
            counter.merge(counterReference.get(newValues));
            counterReference.set(existingValues, counter);
            super.merge(existingValues, newValues);
        }
    }
}
