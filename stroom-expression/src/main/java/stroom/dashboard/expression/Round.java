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

public class Round extends AbstractRoundingFunction {
    public static final String NAME = "round";

    public Round(final String name) {
        super(name);
    }

    @Override
    protected RoundCalculator createCalculator(final Double decimalPlaces) {
        if (decimalPlaces == null) {
            return new NumericRound();
        }

        final Double multiplier = Double.valueOf(Math.pow(10D, decimalPlaces));
        return new DecimalPlaceRound(multiplier);
    }

    private static class NumericRound implements RoundCalculator {
        private static final long serialVersionUID = -2414316545075369054L;

        @Override
        public Double calc(final Double value) {
            return Double.valueOf(Math.round(value));
        }
    }

    private static class DecimalPlaceRound implements RoundCalculator {
        private static final long serialVersionUID = -5893918049538006730L;

        private final Double multiplier;

        public DecimalPlaceRound(final Double multiplier) {
            this.multiplier = multiplier;
        }

        @Override
        public Double calc(final Double value) {
            return Double.valueOf(Math.round(value * multiplier) / multiplier);
        }
    }
}
