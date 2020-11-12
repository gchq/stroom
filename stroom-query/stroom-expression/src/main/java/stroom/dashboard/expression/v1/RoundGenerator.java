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

final class RoundGenerator extends AbstractSingleChildGenerator {
    private static final long serialVersionUID = -5360650022530956741L;

    private final RoundCalculator calculator;

    RoundGenerator(final Generator childGenerator, final RoundCalculator calculator) {
        super(childGenerator);
        this.calculator = calculator;
    }

    @Override
    public void set(final Val[] values) {
        childGenerator.set(values);
    }

    @Override
    public Val eval() {
        final Val val = childGenerator.eval();
        if (!val.type().isValue()) {
            return val;
        }
        return calculator.calc(val);
    }
}
