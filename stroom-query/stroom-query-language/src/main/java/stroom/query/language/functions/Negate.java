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

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = Negate.NAME,
        commonCategory = FunctionCategory.MATHEMATICS,
        commonReturnType = ValDouble.class,
        commonReturnDescription = "The result of multiplying the supplied number by -1",
        signatures = @FunctionSignature(
                description = "Multiplies the supplied value by -1",
                args = {
                        @FunctionArg(
                                name = "value",
                                description = "Numeric field, function or a constant.",
                                argType = ValNumber.class)
                }))
class Negate extends NumericFunction {

    static final String NAME = "negate";
    private static final Calc CALC = new Calc();

    public Negate(final String name) {
        super(name, 1, 1);
    }

    @Override
    protected Calculator getCalculator() {
        return CALC;
    }

    @Override
    protected void appendParams(final StringBuilder sb) {
        // Negate differs from the other NumericFunctions in that it only allows 1 param and the name ("-")
        // goes before the first (and only) param and not after it.
        sb.append(name);
        appendParam(sb, params[0]);
    }

    static class Calc extends Calculator {

        private static final ValInteger MINUS_ONE = ValInteger.create(-1);

        @Override
        Val calc(final Val current, final Val value) {
            // super.calc does a null check
            return super.calc(MINUS_ONE, value);
        }

        @Override
        double op(final double cur, final double val) {
            return val * cur;
        }
    }
}
