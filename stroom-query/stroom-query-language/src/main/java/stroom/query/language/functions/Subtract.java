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

import java.time.ZoneId;
import java.util.function.Supplier;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = Subtract.NAME,
        aliases = Subtract.ALIAS,
        commonCategory = FunctionCategory.MATHEMATICS,
        commonReturnType = Val.class,
        commonReturnDescription = "The result of subtracting each argument from the last argument or result of the " +
                "last subtraction.",
        commonDescription = "Subtracts arg2 from arg1. Minimum of two arguments. If more than two arguments are " +
                "supplied then each argument is subtracted from the previous result, e.g. subtract(10, 5, 2) returns " +
                "3. Can be expressed as '${field1}-${field2}'. A date minus a date is a duration. A duration, " +
                "e.g. 2h, or a period, e.g. 1M, can be subtracted from a date. A period is subtracted in the time " +
                "zone of the query.",
        signatures = @FunctionSignature(
                args = @FunctionArg(
                        name = "arg",
                        description = "Field, the result of another function or a constant.",
                        argType = ValNumber.class,
                        isVarargs = true,
                        minVarargsCount = 2)))
class Subtract extends NumericFunction {

    static final String NAME = "-";
    static final String ALIAS = "subtract";

    private final Calculator calculator;

    public Subtract(final ExpressionContext expressionContext, final String name) {
        super(name, 2, Integer.MAX_VALUE);
        calculator = new Calc(expressionContext);
    }

    @Override
    protected Generator createGenerator(final Generator[] childGenerators) {
        return new Gen(childGenerators, getCalculator());
    }

    @Override
    protected Calculator getCalculator() {
        return calculator;
    }

    private static final class Calc extends Calculator {

        private final Supplier<ZoneId> zone;

        private Calc(final ExpressionContext expressionContext) {
            zone = new QueryZone(expressionContext);
        }

        @Override
        Val calc(final Val current, final Val value) {
            if (!current.type().isValue() || !value.type().isValue()) {
                return super.calc(current, value);
            }
            try {
                final Val result = DateArithmetic.subtract(current, value, zone);
                return result != null
                        ? result
                        : super.calc(current, value);
            } catch (final RuntimeException e) {
                return ValErr.create(e.getMessage());
            }
        }

        @Override
        double op(final double cur, final double val) {
            return cur - val;
        }
    }
}
