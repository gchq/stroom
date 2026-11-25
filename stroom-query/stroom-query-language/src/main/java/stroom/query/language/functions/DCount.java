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

import stroom.query.language.functions.hll.HyperLogLogPlusPlus;
import stroom.query.language.functions.hll.UniqueValHasher;
import stroom.query.language.functions.ref.HllReference;
import stroom.query.language.functions.ref.StoredValues;
import stroom.query.language.functions.ref.ValueReferenceIndex;

import java.text.ParseException;
import java.util.function.Supplier;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = DCount.NAME,
        commonCategory = FunctionCategory.AGGREGATE,
        commonDescription = "Estimates the number of unique values across all grouped records. This takes less space " +
                "than " + CountDistinct.NAME + " if there are many unique values.",
        commonReturnType = ValLong.class,
        commonReturnDescription = "An estimate of the number of unique values.",
        signatures = {
                @FunctionSignature(
                        description = "Estimates the number of unique values across all grouped records. The " +
                                "estimate is exact or within a few of it up to about five thousand values, and has " +
                                "a standard error of 0.8% above that. Use " + CountDistinct.NAME + " for a count " +
                                "that is exact up to a limit, which is " +
                                ExpressionContext.DEFAULT_MAX_COUNT_DISTINCT_VALUES + " unless it has been " +
                                "configured.",
                        args = @FunctionArg(
                                name = "values",
                                description = "Grouped field or the result of another function.",
                                argType = Val.class)),
                @FunctionSignature(
                        description = "Estimates the number of unique values across all grouped records, with the " +
                                "supplied accuracy. A lower accuracy takes less space, but has a greater error " +
                                "that applies from fewer values.",
                        args = {
                                @FunctionArg(
                                        name = "values",
                                        description = "Grouped field or the result of another function.",
                                        argType = Val.class),
                                @FunctionArg(
                                        name = "accuracy",
                                        description = "The accuracy of the estimate: 0 (1.6% standard error, up to " +
                                                "3 kilobytes per row), 1 (0.8%, up to 12 kilobytes) or 2 (0.4%, up " +
                                                "to 48 kilobytes).",
                                        argType = ValInteger.class,
                                        defaultValue = "1")
                        })
        })
class DCount extends AbstractFunction implements AggregateFunction {

    static final String NAME = "dcount";

    // The precision of each accuracy, which are the same as Kusto's.
    private static final int[] PRECISIONS = {12, 14, 16};
    private static final int DEFAULT_ACCURACY = 1;

    private int precision = PRECISIONS[DEFAULT_ACCURACY];
    private Generator gen;
    private Function function;
    private HllReference hllReference;

    /// Create the function.
    ///
    /// @param name The name the function was called by.
    public DCount(final String name) {
        super(name, 1, 2);
    }

    /// Set the values to count and, optionally, the accuracy.
    ///
    /// @param params The parameters of the function.
    /// @throws ParseException If the values are already aggregated or the accuracy is not an allowed one.
    @Override
    public void setParams(final Param[] params) throws ParseException {
        super.setParams(params);

        if (params.length >= 2) {
            precision = parsePrecision(params);
        }

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

    private int parsePrecision(final Param[] params) throws ParseException {
        final int accuracy = ParamParseUtil.parseIntParam(params, 1, name, false);
        // Numbers are parsed as doubles, which are cut to an int.
        final boolean whole = ((Val) params[1]).toDouble() == accuracy;
        if (!whole || accuracy < 0 || accuracy >= PRECISIONS.length) {
            throw new ParseException("Accuracy from 0 to " + (PRECISIONS.length - 1) +
                                     " expected as second argument of '" + name + "' function", 0);
        }
        return PRECISIONS[accuracy];
    }

    /// Add a reference to the stored sketch, unless the parameter is static.
    ///
    /// @param valueReferenceIndex The index to add the reference to.
    @Override
    public void addValueReferences(final ValueReferenceIndex valueReferenceIndex) {
        // A static value has nothing to store.
        if (function != null) {
            hllReference = valueReferenceIndex.addHll(name, precision);
        }
        super.addValueReferences(valueReferenceIndex);
    }

    /// Create a generator that keeps the sketch of each group.
    ///
    /// @return The generator.
    @Override
    public Generator createGenerator() {
        if (gen != null) {
            return gen;
        }

        final Generator childGenerator = function.createGenerator();
        return new Gen(childGenerator, hllReference);
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

        private final HllReference hllReference;

        private Gen(final Generator childGenerator,
                    final HllReference hllReference) {
            super(childGenerator);
            this.hllReference = hllReference;
        }

        /// Add the value of a row to the sketch, unless it is null or an error.
        ///
        /// @param values       The values of the row.
        /// @param storedValues The stored values that hold the sketch.
        @Override
        public void set(final Val[] values, final StoredValues storedValues) {
            childGenerator.set(values, storedValues);
            final Val val = childGenerator.eval(storedValues, null);
            if (val.type().isValue()) {
                final HyperLogLogPlusPlus sketch = hllReference.get(storedValues);
                sketch.addHash(UniqueValHasher.hash(val));
                hllReference.set(storedValues, sketch);
            }
        }

        /// Estimate the number of unique values.
        ///
        /// @param storedValues      The stored values that hold the sketch.
        /// @param childDataSupplier Not used.
        /// @return The estimate.
        @Override
        public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
            return ValLong.create(hllReference.get(storedValues).estimate());
        }

        /// Add the sketch of some new values to the sketch of the existing ones.
        ///
        /// @param existingValues The stored values to add to.
        /// @param newValues      The stored values to add.
        @Override
        public void merge(final StoredValues existingValues, final StoredValues newValues) {
            final HyperLogLogPlusPlus sketch = hllReference.get(existingValues);
            sketch.merge(hllReference.get(newValues));
            hllReference.set(existingValues, sketch);
            super.merge(existingValues, newValues);
        }
    }
}
