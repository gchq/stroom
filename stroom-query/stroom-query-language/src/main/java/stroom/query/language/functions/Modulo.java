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
        name = Modulo.NAME,
        aliases = {
                Modulo.ALIAS1,
                Modulo.ALIAS2},
        commonCategory = FunctionCategory.MATHEMATICS,
        commonReturnType = ValDouble.class,
        signatures = @FunctionSignature(
                description = "Calculates the modulus after dividing the dividend by the divisor. If more than one " +
                        "divisor is provided then the each subsequent divisor will be used to calculate the modulus " +
                        "using the previous result as the dividend, e.g. " + Modulo.ALIAS2 + "(100, 30, 4) " +
                        "returns 2, i.e. (100 % 30) % 4.",
                returnDescription = "The remainder (modulus) after dividing the dividend by the divisor.",
                args = {
                        @FunctionArg(
                                name = "dividend",
                                description = "The number to divide by the divisor.",
                                argType = ValNumber.class),
                        @FunctionArg(
                                name = "divisor",
                                description = "The number to divide the dividend by.",
                                argType = ValNumber.class,
                                isVarargs = true,
                                minVarargsCount = 1)
                }))
class Modulo extends NumericFunction {

    static final String NAME = "%";
    static final String ALIAS1 = "mod";
    static final String ALIAS2 = "modulo";
    private static final Calc CALC = new Calc();

    public Modulo(final String name) {
        super(name, 2, Integer.MAX_VALUE);
    }

    @Override
    protected Calculator getCalculator() {
        return CALC;
    }

    static class Calc extends Calculator {

        @Override
        double op(final double cur, final double val) {
            final double retVal = cur % val;
            if (Double.isNaN(retVal) || Double.isInfinite(retVal)) {
                throw new ArithmeticException("Result of " + cur + " % " + val + " is not a number");
            }
            return retVal;
        }
    }
}
