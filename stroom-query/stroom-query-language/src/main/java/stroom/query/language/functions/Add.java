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

import java.time.ZoneId;
import java.util.function.Supplier;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = Add.NAME,
        aliases = Add.ALIAS,
        commonCategory = FunctionCategory.MATHEMATICS,
        commonReturnType = Val.class,
        commonReturnDescription = "The sum of all values",
        commonDescription = "Adds the value of all arguments together. Minimum of two arguments. Can be expressed as " +
                "'${field1}+${field2}'. If any argument is a string then the arguments that are not null are joined " +
                "together. Otherwise returns null if any argument is null, or an error if any argument is not a " +
                "number, date, duration or period. A duration, e.g. 2h, or a period, e.g. 1d or 1M, can be added to " +
                "a date. A period is added on the calendar in the time zone of the query, so adding 1d keeps the " +
                "local time when the clocks change, and adding 24h does not.",
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

    private final Calculator calculator;

    public Add(final ExpressionContext expressionContext, final String name) {
        super(name, 2, Integer.MAX_VALUE);
        calculator = Calc.create(expressionContext);
    }

    @Override
    protected Generator createGenerator(final Generator[] childGenerators) {
        return new Gen(childGenerators, getCalculator());
    }

    @Override
    protected Calculator getCalculator() {
        return calculator;
    }

    static class Calc extends Calculator {

        private final Supplier<ZoneId> zone;
        private final boolean summing;

        Calc(final Supplier<ZoneId> zone, final boolean summing) {
            this.zone = zone;
            this.summing = summing;
        }

        /// Creates a calculator for an expression that adds values together.
        ///
        /// @param expressionContext The context of the expression, whose time zone periods are added in.
        /// @return The calculator.
        static Calc create(final ExpressionContext expressionContext) {
            return new Calc(new QueryZone(expressionContext), false);
        }

        /// Creates a calculator for a function that totals the values of a column, which can be dates as an
        /// average is worked out from a total.
        ///
        /// @param expressionContext The context of the expression, whose time zone periods are added in.
        /// @return The calculator.
        static Calc createForSum(final ExpressionContext expressionContext) {
            return new Calc(new QueryZone(expressionContext), true);
        }

        @Override
        Val calc(final Val current, final Val value) {
            if (!current.type().isValue() || !value.type().isValue()
                || (summing && Type.DATE.equals(current.type()) && Type.DATE.equals(value.type()))) {
                return super.calc(current, value);
            }
            try {
                final Val result = DateArithmetic.add(current, value, zone);
                return result != null
                        ? result
                        : super.calc(current, value);
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

            // If any of the input values are strings then concatenate them all, leaving out nulls.
            if (concat) {
                final StringBuilder sb = new StringBuilder();
                for (final Val val : vals) {
                    if (val.type().isValue()) {
                        sb.append(val);
                    }
                }
                return ValString.create(sb.toString());
            }

            return calculate(vals, calculator);
        }
    }
}
