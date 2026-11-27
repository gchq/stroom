/*
 * Copyright 2020 Crown Copyright
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

import stroom.query.language.functions.ref.StoredValues;

import java.util.function.Supplier;

abstract class AbstractEqualityFunction extends AbstractManyChildFunction {

    private static final ValErr CHILD_ERROR = ValErr.create("Error evaluating child generator");

    private final boolean usingOperator;

    AbstractEqualityFunction(final String name, final String operator) {
        super(name, 2, 2);
        usingOperator = operator.equals(name);
    }

    @Override
    protected Generator createGenerator(final Generator[] childGenerators) {
        return new Gen(childGenerators, createEvaluator(), isOrdering());
    }

    abstract Evaluator createEvaluator();

    /// Tells whether the function tests which value is the greater, rather than whether they are equal.
    ///
    /// @return True if it does.
    boolean isOrdering() {
        return true;
    }

    @Override
    public void appendString(final StringBuilder sb) {
        if (usingOperator) {
            appendParams(sb);
        } else {
            super.appendString(sb);
        }
    }

    @Override
    protected void appendParams(final StringBuilder sb) {
        if (usingOperator) {
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    final Param param = params[i];
                    appendParam(sb, param);
                    if (i < params.length - 1) {
                        sb.append(name);
                    }
                }
            }
        } else {
            super.appendParams(sb);
        }
    }


    // --------------------------------------------------------------------------------


    private static final class Gen extends AbstractManyChildGenerator {

        private final Evaluator evaluator;
        private final boolean ordering;

        Gen(final Generator[] childGenerators, final Evaluator evaluator, final boolean ordering) {
            super(childGenerators);
            this.evaluator = evaluator;
            this.ordering = ordering;
        }

        @Override
        public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
            final Val[] values = new Val[childGenerators.length];

            for (int i = 0; i < childGenerators.length; i++) {
                final Val val;
                try {
                    val = childGenerators[i].eval(storedValues, childDataSupplier);
                } catch (final RuntimeException e) {
                    return CHILD_ERROR;
                }

                if (val.type().isError()) {
                    return val;
                }

                values[i] = val;
            }

            values[0] = DateArithmetic.readPeriodFor(values[1], values[0]);
            values[1] = DateArithmetic.readPeriodFor(values[0], values[1]);
            final ValErr notComparable = ordering
                    ? DateArithmetic.checkOrder(values[0], values[1])
                    : DateArithmetic.checkEquality(values[0], values[1]);
            if (notComparable != null) {
                return notComparable;
            }
            return evaluator.evaluate(values[0], values[1]);
        }
    }
}
