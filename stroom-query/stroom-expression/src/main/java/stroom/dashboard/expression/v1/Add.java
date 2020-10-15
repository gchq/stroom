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

class Add extends NumericFunction {
    static final String NAME = "+";
    static final String ALIAS = "add";
    private static final Calc CALC = new Calc();

    public Add(final String name) {
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
        private static final long serialVersionUID = 1099553839843710283L;

        @Override
        protected double op(final double cur, final double val) {
            return cur + val;
        }
    }

    private static class Gen extends AbstractManyChildGenerator {
        private static final long serialVersionUID = 217968020285584214L;

        private final Calculator calculator;

        Gen(final Generator[] childGenerators, final Calculator calculator) {
            super(childGenerators);
            this.calculator = calculator;
        }

        @Override
        public void set(final Val[] values) {
            for (final Generator generator : childGenerators) {
                generator.set(values);
            }
        }

        @Override
        public Val eval() {
            boolean concat = false;
            final Val[] vals = new Val[childGenerators.length];
            for (int i = 0; i < vals.length; i++) {
                final Val val = childGenerators[i].eval();
                if (val.type().isError()) {
                    return val;
                } else if (val instanceof ValString) {
                    concat = true;
                }

                vals[i] = val;
            }

            // If any of the input values are strings then concatenate them all.
            if (concat) {
                final StringBuilder sb = new StringBuilder();
                for (final Val val : vals) {
                    if (val.type().isValue()) {
                        sb.append(val.toString());
                    }
                }
                return ValString.create(sb.toString());
            }

            Val value = ValNull.INSTANCE;
            for (final Val val : vals) {
                if (val.type().isValue()) {
                    value = calculator.calc(value, val);
                }
            }
            return value;
        }
    }
}
