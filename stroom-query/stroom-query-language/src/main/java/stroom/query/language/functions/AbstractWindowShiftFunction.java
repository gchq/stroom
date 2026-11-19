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

import stroom.query.language.functions.ref.StoredValues;
import stroom.query.language.functions.ref.ValReference;
import stroom.query.language.functions.ref.ValueReferenceIndex;

import java.text.ParseException;
import java.util.function.Supplier;

/// A function that gives the value an aggregate has in another window of the same group, as
/// `lag(count(), 1)` gives the count of the window before a row's own.
///
/// The value is gathered by projection. A result store adds each row to its own windows, and adds
/// it again to the window that is this function's number of windows away, marking that copy with
/// the distance by [StoredValues#setWindowShift(int)]. The aggregate inside this function takes
/// values only from the copies marked with its distance, and every other aggregate takes none from
/// them, so when the copies are grouped with the rows that are really in that window, the aggregate
/// inside this function holds the value of the other window.
abstract class AbstractWindowShiftFunction extends AbstractWindowFunction {

    private static final int AGGREGATE_PARAM = 0;
    private static final int WINDOWS_PARAM = 1;
    private static final int DEFAULT_PARAM = 2;

    private final boolean later;
    private Function aggregate;
    private int windowShift;
    private Val defaultValue;

    /// Creates the function.
    ///
    /// @param expressionContext The context the function is parsed in, which holds the query's windows.
    /// @param name              The name the function was written with.
    /// @param later             Whether the function reads a window later than a row's own, as `lead`
    ///                          does, and not an earlier one, as `lag` does.
    AbstractWindowShiftFunction(final ExpressionContext expressionContext,
                                final String name,
                                final boolean later) {
        super(expressionContext, name, 2, 3);
        this.later = later;
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        super.setParams(params);
        final Param[] ownParams = this.params;

        // The parameter must be an aggregate over the rows of a window. A function that reads the rows
        // of a group one by one, such as first(), or that reads another window itself, can't be
        // gathered from the copies of another window's rows.
        if (ownParams[AGGREGATE_PARAM] instanceof final Function function
            && function.isAggregate()
            && !function.requiresChildData()
            && !(function instanceof AbstractWindowFunction)) {
            aggregate = function;
        } else {
            throw new ParseException("The first parameter of '" + name + "' must be an aggregate such as "
                                     + "count(), sum() or max(), as in '" + name + "(count(), 1)'", 0);
        }

        final Long windows = ownParams[WINDOWS_PARAM] instanceof final Val val
                ? toWholeNumber(val)
                : null;
        if (windows == null || windows < 1 || windows > TimeWindows.MAX_WINDOWS_PER_TIME) {
            throw new ParseException("The second parameter of '" + name + "' must be a whole number of windows "
                                     + "from 1 to " + TimeWindows.MAX_WINDOWS_PER_TIME + ", as in '"
                                     + name + "(count(), 1)'", 0);
        }
        // A row is copied to the window that will read it. For `lag` that is a later window, and for
        // `lead` an earlier one.
        windowShift = later
                ? -windows.intValue()
                : windows.intValue();

        if (ownParams.length > DEFAULT_PARAM) {
            if (ownParams[DEFAULT_PARAM] instanceof final Val val) {
                defaultValue = val;
            } else {
                throw new ParseException("The third parameter of '" + name + "' must be a fixed value, as in '"
                                         + name + "(count(), 1, 0)'", 0);
            }
        }
    }

    private static Long toWholeNumber(final Val val) {
        final Double number = val.toDouble();
        if (number == null || number != Math.rint(number)) {
            return null;
        }
        return number.longValue();
    }

    @Override
    public void addValueReferences(final ValueReferenceIndex valueReferenceIndex) {
        valueReferenceIndex.addWindowShift(windowShift);
        super.addValueReferences(valueReferenceIndex);
    }

    @Override
    public Generator createGenerator() {
        return new Gen(getWindowKeyReference(), aggregate.createGenerator(), windowShift, defaultValue);
    }


    // --------------------------------------------------------------------------------


    private static final class Gen extends AbstractGenerator {

        private final ValReference windowKeyReference;
        private final Generator aggregateGenerator;
        private final int windowShift;
        private final Val defaultValue;

        private Gen(final ValReference windowKeyReference,
                    final Generator aggregateGenerator,
                    final int windowShift,
                    final Val defaultValue) {
            this.windowKeyReference = windowKeyReference;
            this.aggregateGenerator = aggregateGenerator;
            this.windowShift = windowShift;
            this.defaultValue = defaultValue;
        }

        // A row adds nothing to the value in its own window.
        @Override
        public void set(final Val[] values, final StoredValues storedValues) {
            windowKeyReference.set(storedValues, ValLong.create(storedValues.getWindowKey()));
        }

        @Override
        public void setShifted(final Val[] values, final StoredValues storedValues) {
            windowKeyReference.set(storedValues, ValLong.create(storedValues.getWindowKey()));
            if (storedValues.getWindowShift() == windowShift) {
                aggregateGenerator.set(values, storedValues);
            }
        }

        @Override
        public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
            final Val val = aggregateGenerator.eval(storedValues, childDataSupplier);
            if (defaultValue != null && (val == null || val.type().isNull())) {
                return defaultValue;
            }
            return val;
        }

        @Override
        public void merge(final StoredValues existingValues, final StoredValues newValues) {
            aggregateGenerator.merge(existingValues, newValues);
        }
    }
}
