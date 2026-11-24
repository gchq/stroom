/*
 * Copyright 2020 Crown Copyright
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

import stroom.query.language.functions.TimeRounder.Mode;

import java.math.RoundingMode;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = Floor.NAME,
        commonCategory = FunctionCategory.MATHEMATICS,
        commonSubCategories = AbstractRoundingFunction.ROUND_SUB_CATEGORY,
        commonReturnType = Val.class,
        commonReturnDescription = "The rounded value.",
        signatures = {
                @FunctionSignature(
                        description = "Rounds the supplied value down to the next whole number. e.g. floor(1.8) " +
                                "returns 1.",
                        args = @FunctionArg(
                                name = "value",
                                argType = ValNumber.class,
                                description = "The number to round down to the next whole number.")),
                @FunctionSignature(
                        description = "Rounds the supplied value down to the next digit keeping the specified " +
                                "number of decimal places. e.g. floor(1.22385, 3) returns 1.223",
                        args = {
                                @FunctionArg(
                                        name = "value",
                                        argType = ValNumber.class,
                                        description = "The number to round down."),
                                @FunctionArg(
                                        name = "decimalPlaces",
                                        argType = ValInteger.class,
                                        description = "The maximum number of decimal places to round to.")}
                ),
                @FunctionSignature(
                        description = "Rounds the supplied date or duration down to a multiple of a duration or " +
                                "period. " +
                                "Multiples of a date are counted in the time zone of the query. Weeks start on a " +
                                "Monday, as does any whole number of weeks, such as 7d.",
                        args = {
                                @FunctionArg(
                                        name = "value",
                                        argType = Val.class,
                                        description = "The date or duration to round."),
                                @FunctionArg(
                                        name = "by",
                                        argType = Val.class,
                                        description = "The duration or period to round to a multiple of, e.g. " +
                                                "15m, 1d or 3M.")}),
                @FunctionSignature(
                        description = "Rounds the supplied date down to a multiple of a duration or period, " +
                                "counted from the " +
                                "origin in the time zone of the query.",
                        args = {
                                @FunctionArg(
                                        name = "value",
                                        argType = Val.class,
                                        description = "The date to round."),
                                @FunctionArg(
                                        name = "by",
                                        argType = Val.class,
                                        description = "The duration or period to round to a multiple of, e.g. " +
                                                "15m, 1d or 3M."),
                                @FunctionArg(
                                        name = "origin",
                                        argType = ValDate.class,
                                        description = "The date to count multiples from, or null() for the " +
                                                "default.")}),
                @FunctionSignature(
                        description = "Rounds the supplied date down to a multiple of a duration or period, " +
                                "counted from the " +
                                "origin in the supplied time zone.",
                        args = {
                                @FunctionArg(
                                        name = "value",
                                        argType = Val.class,
                                        description = "The date to round."),
                                @FunctionArg(
                                        name = "by",
                                        argType = Val.class,
                                        description = "The duration or period to round to a multiple of, e.g. " +
                                                "15m, 1d or 3M."),
                                @FunctionArg(
                                        name = "origin",
                                        argType = ValDate.class,
                                        description = "The date to count multiples from, or null() for the " +
                                                "default. Quote a date with no offset, e.g. " +
                                                "'2024-04-01T00:00:00', for it to be in the timezone."),
                                @FunctionArg(
                                        name = "timezone",
                                        argType = ValString.class,
                                        description = "The time zone to count multiples in, e.g. " +
                                                "'Europe/London'.")})})
class Floor extends AbstractRoundingFunction {

    static final String NAME = "floor";

    public Floor(final ExpressionContext expressionContext, final String name) {
        super(expressionContext, name);
    }

    @Override
    Mode getMode() {
        return Mode.FLOOR;
    }

    @Override
    protected RoundCalculator createCalculator(final Double decimalPlaces) {
        if (decimalPlaces == null) {
            return new NumericFloor();
        }

        return new DecimalPlaceFloor(decimalPlaces.intValue());
    }

    private static class NumericFloor implements RoundCalculator {


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


        private final int decimalPlaces;

        DecimalPlaceFloor(final int decimalPlaces) {
            this.decimalPlaces = decimalPlaces;
        }

        @Override
        public Val calc(final Val value) {
            final Double val = value.toDouble();
            if (val == null) {
                return ValNull.INSTANCE;
            }

            return ValDouble.create(Double.isFinite(val)
                    ? round(val, decimalPlaces, RoundingMode.FLOOR)
                    : val);
        }
    }
}
