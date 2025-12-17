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

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = Ceiling.NAME,
        commonCategory = FunctionCategory.MATHEMATICS,
        commonSubCategories = AbstractRoundingFunction.ROUND_SUB_CATEGORY,
        commonReturnType = ValDouble.class,
        commonReturnDescription = "The rounded value.",
        signatures = {
                @FunctionSignature(
                        description = "Rounds the supplied value up to the next whole number. e.g. ceiling(1.2) " +
                                "returns 2.",
                        args = @FunctionArg(
                                name = "value",
                                argType = ValNumber.class,
                                description = "The number to round up to the next whole number.")),
                @FunctionSignature(
                        description = "Rounds the supplied value up to the next digit keeping the specified " +
                                "number of decimal places. e.g. ceiling(1.22345, 3) returns 1.224",
                        args = {
                                @FunctionArg(
                                        name = "value",
                                        argType = ValNumber.class,
                                        description = "The number to round up."),
                                @FunctionArg(
                                        name = "decimalPlaces",
                                        argType = ValInteger.class,
                                        description = "The maximum number of decimal places to round to.")}
                )})
class Ceiling extends AbstractRoundingFunction {

    static final String NAME = "ceiling";

    public Ceiling(final String name) {
        super(name);
    }

    @Override
    protected RoundCalculator createCalculator(final Double decimalPlaces) {
        if (decimalPlaces == null) {
            return new NumericCeiling();
        }

        final double multiplier = Math.pow(10D, decimalPlaces);
        return new DecimalPlaceCeiling(multiplier);
    }

    private static class NumericCeiling implements RoundCalculator {

        @Override
        public Val calc(final Val value) {
            final Double val = value.toDouble();
            if (val == null) {
                return ValNull.INSTANCE;
            }

            return ValDouble.create(Math.ceil(val));
        }
    }

    private static class DecimalPlaceCeiling implements RoundCalculator {

        private final double multiplier;

        DecimalPlaceCeiling(final double multiplier) {
            this.multiplier = multiplier;
        }

        @Override
        public Val calc(final Val value) {
            final Double val = value.toDouble();
            if (val == null) {
                return ValNull.INSTANCE;
            }

            return ValDouble.create(Math.ceil(val * multiplier) / multiplier);
        }
    }
}
