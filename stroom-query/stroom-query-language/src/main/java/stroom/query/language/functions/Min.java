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
        name = Min.NAME,
        commonReturnType = ValDouble.class,
        commonReturnDescription = "The smallest of all values.",
        signatures = {
                @FunctionSignature(
                        category = FunctionCategory.AGGREGATE,
                        description = "Determines the minimum value across all grouped records.",
                        args = @FunctionArg(
                                name = "values",
                                description = "Grouped field or the result of another function",
                                argType = ValNumber.class)),
                @FunctionSignature(
                        category = FunctionCategory.MATHEMATICS,
                        description = "Determines the minimum value from all the arguments.",
                        args = @FunctionArg(
                                name = "arg",
                                description = "Field, the result of another function or a constant.",
                                argType = ValNumber.class,
                                isVarargs = true,
                                minVarargsCount = 2))})
class Min extends AbstractAggregateFunction {

    static final String NAME = "min";

    public Min(final String name) {
        super(name, new Calc());
    }


    // --------------------------------------------------------------------------------


    static class Calc extends Calculator {

        @Override
        Val calc(final Val current, final Val value) {
            try {
                if (value.type().isError() ||
                        current.type().isNull()) {
                    return value;
                } else if (current.type().isError()) {
                    return current;
                }

                if (ValComparators.GENERIC_CASE_SENSITIVE_COMPARATOR.compare(current, value) > 0) {
                    return value;
                }
                return current;

            } catch (final RuntimeException e) {
                return ValErr.create(e.getMessage());
            }
        }
    }
}
