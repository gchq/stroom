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

package stroom.dashboard.expression;

public class RoundGenerator extends AbstractSingleChildGenerator {
    private static final long serialVersionUID = -5360650022530956741L;

    private final RoundCalculator calculator;

    public RoundGenerator(final Generator childGenerator, final RoundCalculator calculator) {
        super(childGenerator);
        this.calculator = calculator;
    }

    @Override
    public void set(final String[] values) {
        childGenerator.set(values);
    }

    @Override
    public Object eval() {
        final Double dbl = TypeConverter.getDouble(childGenerator.eval());
        if (dbl != null) {
            return calculator.calc(dbl);
        }
        return null;
    }
}
