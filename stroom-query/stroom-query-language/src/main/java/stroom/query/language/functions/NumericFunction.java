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

    static final class Gen extends AbstractManyChildGenerator {

        private final Calculator calculator;

        Gen(final Generator[] childGenerators, final Calculator calculator) {
            super(childGenerators);
            this.calculator = calculator;
        }

        @Override
        public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
            final Val[] vals = new Val[childGenerators.length];
            for (int i = 0; i < vals.length; i++) {
                final Val val = childGenerators[i].eval(storedValues, childDataSupplier);

                if (!val.type().isValue()) {
                    return val;
                } else if (!val.type().isNumber()) {
                    return ValErr.INSTANCE;
                }

                vals[i] = val;
            }

            Val value = ValNull.INSTANCE;
            for (final Val val : vals) {
                value = calculator.calc(value, val);
            }
            return value;
        }
    }
}
