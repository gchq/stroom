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

class Floor extends AbstractRoundingFunction {
    static final String NAME = "floor";

    public Floor(final String name) {
        super(name);
    }

    @Override
    protected RoundCalculator createCalculator(final Double decimalPlaces) {
        if (decimalPlaces == null) {
            return new NumericFloor();
        }

        final double multiplier = Math.pow(10D, decimalPlaces);
        return new DecimalPlaceFloor(multiplier);
    }

    private static class NumericFloor implements RoundCalculator {
        private static final long serialVersionUID = -2414316545075369054L;

        @Override
        public Val calc(final Val value) {
            final Double val = value.toDouble();
            if (val == null) {
                return ValNull.INSTANCE;
            }

            return ValDouble.create(Math.floor(val));
        }
    }

    private static class DecimalPlaceFloor implements RoundCalculator {
        private static final long serialVersionUID = -5893918049538006730L;

        private final double multiplier;

        DecimalPlaceFloor(final double multiplier) {
            this.multiplier = multiplier;
        }

        @Override
        public Val calc(final Val value) {
            final Double val = value.toDouble();
            if (val == null) {
                return ValNull.INSTANCE;
            }

            return ValDouble.create(Math.floor(val * multiplier) / multiplier);
        }
    }
}
