/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
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
        name = Divide.NAME,
        aliases = Divide.ALIAS,
        commonCategory = FunctionCategory.MATHEMATICS,
        commonReturnType = ValDouble.class,
        commonReturnDescription = "The value of arg1 divided by arg2.",
        commonDescription = "The value of arg1 divided by arg2. If more than two arguments are supplied then " +
                "it will divide by each argument in turn, e.g. divide(1000, 10, 5, 2) returns 10. " +
                "Can be expressed as '${field1}/${field2}'.",
        signatures = @FunctionSignature(
                args = @FunctionArg(
                        name = "arg",
                        description = "Field, the result of another function or a constant.",
                        argType = ValNumber.class,
                        isVarargs = true,
                        minVarargsCount = 2)))
class Divide extends NumericFunction {

    static final String NAME = "/";
    static final String ALIAS = "divide";
    private static final Calc CALC = new Calc();

    public Divide(final String name) {
        super(name, 2, Integer.MAX_VALUE);
    }

    @Override
    protected Calculator getCalculator() {
        return CALC;
    }

    static class Calc extends Calculator {

        @Override
        double op(final double cur, final double val) {
            final double retVal = cur / val;
            if (Double.isNaN(retVal) || Double.isInfinite(retVal)) {
                throw new ArithmeticException(String.format("Result of %s / %s is not a number", cur, val));
            }
            return retVal;
        }
    }
}
