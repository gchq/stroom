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

class Equals extends AbstractEqualityFunction {
    static final String NAME = "=";
    static final String ALIAS = "equals";
    private static final EqualsEvaluator EVALUATOR = new EqualsEvaluator();

    public Equals(final String name) {
        super(name, NAME);
    }

    @Override
    Evaluator createEvaluator() {
        return EVALUATOR;
    }

    private static class EqualsEvaluator extends Evaluator {
        @Override
        protected Val evaluate(final Val a, final Val b) {
            if (a.getClass().equals(b.getClass())) {
                if (a instanceof ValInteger) {
                    return ValBoolean.create(a.toInteger().equals(b.toInteger()));
                }
                if (a instanceof ValLong) {
                    return ValBoolean.create(a.toLong().equals(b.toLong()));
                }
                if (a instanceof ValBoolean) {
                    return ValBoolean.create(a.toBoolean().equals(b.toBoolean()));
                }
            } else {
                final Double da = a.toDouble();
                final Double db = b.toDouble();
                if (da != null && db != null) {
                    return ValBoolean.create(da.equals(db));
                }
            }

            return ValBoolean.create(a.toString().equals(b.toString()));
        }
    }
}