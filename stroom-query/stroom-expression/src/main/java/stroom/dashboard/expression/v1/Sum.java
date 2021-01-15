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

package stroom.dashboard.expression.v1;

@FunctionDef(
        name = Sum.NAME,
        category = FunctionCategory.AGGREGATE,
        commonReturnType = ValDouble.class,
        commonReturnDescription = "Sum of all values",
        signatures = {
                @FunctionSignature(
                        description = "Determines the sum of the value of expression across all grouped records",
                        args = @FunctionArg(
                                name = "expression",
                                description = "Field or the result of another function",
                                argType = ValDouble.class)),
                @FunctionSignature(
                        description = "Determines the maximum value from all the arguments.",
                        args = @FunctionArg(
                                name = "value",
                                description = "Field, the result of another function or a constant.",
                                argType = ValDouble.class,
                                isVarargs = true,
                                minVarargsCount = 2))
        })
class Sum extends AbstractAggregateFunction {
    static final String NAME = "sum";

    public Sum(final String name) {
        super(name, new Add.Calc());
    }
}
