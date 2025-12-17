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
        name = LessThan.NAME,
        aliases = {LessThan.ALIAS, LessThan.SHORT_ALIAS},
        commonCategory = FunctionCategory.LOGIC,
        commonReturnType = ValBoolean.class,
        commonReturnDescription = "True if arg1 is less than than arg2.",
        signatures = @FunctionSignature(
                description = "Tests if arg1 is less than than arg2. It will attempt to convert the type of the " +
                        "second parameter to that of the first. Can be expressed as '${field1}>${field2}.",
                args = {
                        @FunctionArg(
                                name = "arg1",
                                description = "Field, the result of another function or a constant.",
                                argType = Val.class),
                        @FunctionArg(
                                name = "arg2",
                                description = "Field, the result of another function or a constant.",
                                argType = Val.class)}))
class LessThan extends AbstractEqualityFunction {

    static final String NAME = "<";
    static final String ALIAS = "lessThan";
    static final String SHORT_ALIAS = "lt";
    private static final LessThanEvaluator EVALUATOR = new LessThanEvaluator();

    public LessThan(final String name) {
        super(name, NAME);
    }

    @Override
    Evaluator createEvaluator() {
        return EVALUATOR;
    }


    // --------------------------------------------------------------------------------


    private static class LessThanEvaluator extends Evaluator {

        @Override
        protected Val evaluate(final Val a, final Val b) {
            if (a.type().isNull() || b.type().isNull()) {
                return ValBoolean.FALSE;
            }

            final int compareResult = ValComparators.GENERIC_CASE_SENSITIVE_COMPARATOR.compare(a, b);
            return compareResult < 0
                    ? ValBoolean.TRUE
                    : ValBoolean.FALSE;
        }
    }
}
