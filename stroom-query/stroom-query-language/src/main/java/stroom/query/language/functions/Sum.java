/*
 * Copyright 2016 Crown Copyright
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
        name = Sum.NAME,
        commonReturnType = ValDouble.class,
        commonReturnDescription = "Sum of all values",
        signatures = {
                @FunctionSignature(
                        category = FunctionCategory.AGGREGATE,
                        description = "Determines the sum of the value of expression across all grouped records. " +
                                      "Nulls are ignored and a boolean gives an error.",
                        args = @FunctionArg(
                                name = "expression",
                                description = "Grouped field or the result of another function",
                                argType = ValNumber.class)),
                @FunctionSignature(
                        category = FunctionCategory.MATHEMATICS,
                        description = "Determines the sum of all the arguments. " +
                                      "Nulls are ignored and a boolean gives an error.",
                        args = @FunctionArg(
                                name = "arg",
                                description = "Field, the result of another function or a constant.",
                                argType = ValNumber.class,
                                isVarargs = true,
                                minVarargsCount = 2))
        })
class Sum extends AbstractAggregateFunction {

    static final String NAME = "sum";

    public Sum(final String name) {
        super(name, new Calc());
    }


    // --------------------------------------------------------------------------------


    private static final class Calc extends Add.Calc {

        @Override
        Val calc(final Val current, final Val value) {
            // As for the + operator, a boolean is not a number. Text is still allowed as it may hold a number.
            if (Type.BOOLEAN.equals(value.type())) {
                return current.type().isError()
                        ? current
                        : NumericFunction.getResultForNonNumber(value);
            }
            return super.calc(current, value);
        }
    }
}
