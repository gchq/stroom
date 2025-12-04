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
        return new Gen(childGenerators, createEvaluator());
    }

    abstract Evaluator createEvaluator();

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

        Gen(final Generator[] childGenerators, final Evaluator evaluator) {
            super(childGenerators);
            this.evaluator = evaluator;
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

            return evaluator.evaluate(values[0], values[1]);
        }
    }
}
