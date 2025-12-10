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
        name = Subtract.NAME,
        aliases = Subtract.ALIAS,
        commonCategory = FunctionCategory.MATHEMATICS,
        commonReturnType = ValDouble.class,
        commonReturnDescription = "The result of subtracting each argument from the last argument or result of the " +
                "last subtraction.",
        commonDescription = "Subtracts arg2 from arg1. Minimum of two arguments. If more than two arguments are " +
                "supplied then each argument is subtracted from the previous result, e.g. subtract(10, 5, 2) returns " +
                "3. Can be expressed as '${field1}-${field2}'.",
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
    private static final Calculator CALC = new Calc();

    public Subtract(final String name) {
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
                    if (!current.type().isValue() || value.type().isError()) {
                        return value;
                    }
                    final long milliseconds = value.toLong();
                    final long diff = current.toLong() - milliseconds;
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
            return cur - val;
        }
    }
}
