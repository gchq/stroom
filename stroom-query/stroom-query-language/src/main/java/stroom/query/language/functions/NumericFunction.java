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

import stroom.query.language.functions.ref.StoredValues;
import stroom.util.logging.LogUtil;

import java.util.function.Supplier;

public abstract class NumericFunction extends AbstractManyChildFunction {

    // The most of a value to show in an error, as a value could be a whole event.
    private static final int MAX_ERROR_VALUE_LENGTH = 100;

    private final boolean usingOperator;

    public NumericFunction(final String name, final int minParams, final int maxParams) {
        super(name, minParams, maxParams);
        usingOperator = name.length() == 1;
    }

    @Override
    protected Generator createGenerator(final Generator[] childGenerators) {
        return new Gen(childGenerators, getCalculator());
    }

    protected abstract Calculator getCalculator();

    @Override
    public void appendString(final StringBuilder sb) {
        if (usingOperator) {
            appendParams(sb);
        } else {
            super.appendString(sb);
        }
    }

    @Override
    protected void appendParams(final StringBuilder sb) {
        if (usingOperator) {
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    final Param param = params[i];
                    appendParam(sb, param);
                    if (i < params.length - 1) {
                        sb.append(name);
                    }
                }
            }
        } else {
            super.appendParams(sb);
        }
    }

    /// Applies the calculator to each of the values in turn.
    ///
    /// @param vals       The values.
    /// @param calculator The calculator to apply.
    /// @return The result. If any of the values is not a number or a period then the first of them decides the result,
    /// i.e. null for a null, the same error for an error, or a new error for anything else.
    static Val calculate(final Val[] vals, final Calculator calculator) {
        Val value = ValNull.INSTANCE;
        for (final Val val : vals) {
            if (!isOperand(val)) {
                return getResultForNonNumber(val);
            }
            value = calculator.calc(value, val);
        }
        return value;
    }

    // A period is not a number, but can be added to a date and multiplied by a number.
    private static boolean isOperand(final Val val) {
        return val.type().isNumber() || Type.PERIOD.equals(val.type());
    }

    /// Gets the result of arithmetic on a value that is not a number.
    ///
    /// @param val The value, which is not a number.
    /// @return The same value if it is a null or an error, or a new error if it is anything else.
    static Val getResultForNonNumber(final Val val) {
        if (!val.type().isValue()) {
            return val;
        }
        return ValErr.create(LogUtil.message(
                "Expected a number but got '{}'",
                LogUtil.truncate(val.toString(), MAX_ERROR_VALUE_LENGTH)));
    }

    static final class Gen extends AbstractManyChildGenerator {

        private final Calculator calculator;

        Gen(final Generator[] childGenerators, final Calculator calculator) {
            super(childGenerators);
            this.calculator = calculator;
        }

        @Override
        public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
            final Val[] vals = new Val[childGenerators.length];
            for (int i = 0; i < vals.length; i++) {
                final Val val = childGenerators[i].eval(storedValues, childDataSupplier);
                if (!isOperand(val)) {
                    return getResultForNonNumber(val);
                }

                vals[i] = val;
            }

            return calculate(vals, calculator);
        }
    }
}
