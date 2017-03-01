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

package stroom.dashboard.expression;

public abstract class NumericFunction extends AbstractManyChildFunction {
    private final boolean usingOperator;

    public NumericFunction(final String name, final int minParams, final int maxParams) {
        super(name, minParams, maxParams);
        usingOperator = name.length() == 1;
    }

    @Override
    protected Generator createGenerator(final Generator[] childGenerators) {
        return new Gen(childGenerators, getCalculator());
    }

    protected abstract Calculator getCalculator();

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
                    final Object param = params[i];
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

    private static class Gen extends AbstractManyChildGenerator {
        private static final long serialVersionUID = 217968020285584214L;

        private final Calculator calculator;

        public Gen(final Generator[] childGenerators, final Calculator calculator) {
            super(childGenerators);
            this.calculator = calculator;
        }

        @Override
        public void set(final String[] values) {
            for (final Generator generator : childGenerators) {
                generator.set(values);
            }
        }

        @Override
        public Object eval() {
            Double value = null;
            for (final Generator gen : childGenerators) {
                value = calculator.calc(value, gen.eval());
            }

            return value;
        }
    }
}
