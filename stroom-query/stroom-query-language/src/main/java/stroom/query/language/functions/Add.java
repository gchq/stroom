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

import java.util.function.Supplier;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = Add.NAME,
        aliases = Add.ALIAS,
        commonCategory = FunctionCategory.MATHEMATICS,
        commonReturnType = ValDouble.class,
        commonReturnDescription = "The sum of all values",
        commonDescription = "Adds the value of all arguments together. Minimum of two arguments. Can be expressed as " +
                            "'${field1}+${field2}'.",
        signatures = @FunctionSignature(
                args = @FunctionArg(
                        name = "arg",
                        description = "Field, the result of another function or a constant.",
                        argType = ValNumber.class,
                        isVarargs = true,
                        minVarargsCount = 2)))
class Add extends NumericFunction {

    static final String NAME = "add";
    static final String ALIAS = "+";
    private static final Calculator CALC = new Calc();

    private boolean durationMode;

    public Add(final String name) {
        super(name, 2, Integer.MAX_VALUE);
    }

    @Override
    protected Generator createGenerator(final Generator[] childGenerators) {
        return new Gen(childGenerators, getCalculator());
    }

    @Override
    protected Calculator getCalculator() {
        return CALC;
    }

    static class Calc extends Calculator {

        @Override
        Val calc(final Val current, final Val value) {
            try {
                if (Type.DURATION.equals(value.type())) {
                    if (value.type().isError() ||
                        current.type().isNull()) {
                        return value;
                    } else if (current.type().isError()) {
                        return current;
                    }

                    final long milliseconds = value.toLong();
                    final long diff = current.toLong() + milliseconds;
                    if (Type.DATE.equals(current.type())) {
                        return ValDate.create(diff);
                    } else {
                        return ValDuration.create(diff);
                    }
                } else {
                    return super.calc(current, value);
                }

            } catch (final RuntimeException e) {
                return ValErr.create(e.getMessage());
            }
        }

        @Override
        double op(final double cur, final double val) {
            return cur + val;
        }
    }

    private static final class Gen extends AbstractManyChildGenerator {

        private final Calculator calculator;

        Gen(final Generator[] childGenerators, final Calculator calculator) {
            super(childGenerators);
            this.calculator = calculator;
        }

        @Override
        public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
            boolean concat = false;
            final Val[] vals = new Val[childGenerators.length];
            for (int i = 0; i < vals.length; i++) {
                final Val val = childGenerators[i].eval(storedValues, childDataSupplier);
                if (val.type().isError()) {
                    return val;
                } else if (val instanceof ValString) {
                    concat = true;
                }

                vals[i] = val;
            }

            // If any of the input values are strings then concatenate them all.
            if (concat) {
                final StringBuilder sb = new StringBuilder();
                for (final Val val : vals) {
                    if (val.type().isValue()) {
                        sb.append(val);
                    }
                }
                return ValString.create(sb.toString());
            }

            Val value = ValNull.INSTANCE;
            for (final Val val : vals) {
                if (val.type().isValue()) {
                    value = calculator.calc(value, val);
                }
            }
            return value;
        }
    }
}
