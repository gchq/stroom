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

package stroom.dashboard.expression.v1;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = GreaterThanOrEqualTo.NAME,
        aliases = GreaterThanOrEqualTo.ALIAS,
        commonCategory = FunctionCategory.LOGIC,
        commonReturnType = ValBoolean.class,
        commonReturnDescription = "True if arg1 is greater than or equal to arg2.",
        signatures = @FunctionSignature(
                description = "Tests if arg1 is greater than or equal to arg2. It will attempt to convert the " +
                        "type of the second parameter to that of the first. Can be expressed as " +
                        "'${field1}>${field2}.",
                args = {
                        @FunctionArg(
                                name = "arg1",
                                description = "Field, the result of another function or a constant.",
                                argType = Val.class),
                        @FunctionArg(
                                name = "arg2",
                                description = "Field, the result of another function or a constant.",
                                argType = Val.class)}))
class GreaterThanOrEqualTo extends AbstractEqualityFunction {

    static final String NAME = ">=";
    static final String ALIAS = "greaterThanOrEqualTo";
    private static final GreaterThanEvaluator EVALUATOR = new GreaterThanEvaluator();

    public GreaterThanOrEqualTo(final String name) {
        super(name, NAME);
    }

    @Override
    Evaluator createEvaluator() {
        return EVALUATOR;
    }

    private static class GreaterThanEvaluator extends Evaluator {

        @Override
        protected Val evaluate(final Val a, final Val b) {
            if (a.getClass().equals(b.getClass())) {
                if (a instanceof ValInteger) {
                    return ValBoolean.create(a.toInteger() >= b.toInteger());
                }
                if (a instanceof ValLong) {
                    return ValBoolean.create(a.toLong() >= b.toLong());
                }
                if (a instanceof ValBoolean) {
                    return ValBoolean.create(a.toBoolean().compareTo(b.toBoolean()) >= 0);
                }
            } else {
                final Double da = a.toDouble();
                final Double db = b.toDouble();
                if (da != null && db != null) {
                    return ValBoolean.create(da >= db);
                }
            }

            return ValBoolean.create(a.toString().compareTo(b.toString()) >= 0);
        }
    }
}
