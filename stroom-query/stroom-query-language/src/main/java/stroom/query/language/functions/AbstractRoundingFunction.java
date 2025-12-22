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

import java.text.ParseException;

abstract class AbstractRoundingFunction extends AbstractFunction {

    static final String ROUND_SUB_CATEGORY = "Rounding";
    private RoundCalculator calculator;
    private Function function;

    AbstractRoundingFunction(final String name) {
        super(name, 1, 2);
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        super.setParams(params);

        if (params.length == 2) {
            if (params[1] instanceof Val) {
                final Integer precision = ((Val) params[1]).toInteger();
                if (precision != null && precision > 0) {
                    calculator = createCalculator((double) precision);
                } else {
                    throw new ParseException(
                            "Precision argument of '" + name + "' must be positive number if specified", 0);
                }
            } else {
                throw new ParseException(
                        "Second argument of '" + name + "' is expected to be a numeric or time period precision", 0);
            }
        } else {
            calculator = createCalculator(null);
        }

        final Param param = params[0];
        if (param instanceof Function) {
            function = (Function) param;
        } else {
            function = new StaticValueFunction((Val) param);
        }
    }

    @Override
    public Generator createGenerator() {
        final Generator childGenerator = function.createGenerator();
        return new RoundGenerator(childGenerator, calculator);
    }

    protected abstract RoundCalculator createCalculator(Double decimalPlaces);

    @Override
    public boolean hasAggregate() {
        return function.hasAggregate();
    }

    @Override
    public boolean requiresChildData() {
        if (function != null) {
            return function.requiresChildData();
        }
        return super.requiresChildData();
    }
}
