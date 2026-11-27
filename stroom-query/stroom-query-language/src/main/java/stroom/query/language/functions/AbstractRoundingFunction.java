/*
 * Copyright 2026 Crown Copyright
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
import stroom.query.language.functions.TimeRounder.Origin;
import stroom.query.language.functions.ref.StoredValues;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.function.Supplier;

/// The shared part of `floor`, `ceiling` and `round`. What they do depends on their second argument. With none, or
/// a number of decimal places, they round numbers. With a duration or period they round dates and durations to
/// multiples of it, counted from an optional origin in an optional time zone.
abstract class AbstractRoundingFunction extends AbstractManyChildFunction {

    static final String ROUND_SUB_CATEGORY = "Rounding";
    private static final int BY_POS = 1;
    // Ten to the power of any more than this is infinite.
    private static final int MAX_PRECISION = 308;
    private static final int ORIGIN_POS = 2;
    private static final int ZONE_POS = 3;

    private final ExpressionContext expressionContext;

    AbstractRoundingFunction(final ExpressionContext expressionContext, final String name) {
        super(name, 1, 4);
        this.expressionContext = expressionContext;
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        super.setParams(params);

        // Report what can be seen to be wrong now, rather than as an error in every row. The values of query
        // parameters aren't known until later.
        try {
            if (params.length > BY_POS) {
                if (ConstantParam.isPerRow(params[BY_POS])) {
                    throw new IllegalArgumentException(
                            "Second argument of '" + name + "' is expected to be a numeric or time period precision");
                } else if (params[BY_POS] instanceof Val) {
                    final Param originParam = getOriginParam();
                    createRounder(originParam instanceof Val
                            ? originParam
                            : null);
                }
            }
            if (params.length > ORIGIN_POS && ConstantParam.isPerRow(params[ORIGIN_POS])) {
                throw new IllegalArgumentException("Invalid origin: " + params[ORIGIN_POS] + " - "
                                                   + ConstantParam.whyNotConstant(params[ORIGIN_POS]));
            }
            if (params.length > ZONE_POS && params[ZONE_POS] instanceof final Val zone) {
                UserTimeZoneUtil.getZoneId(zone);
            }
        } catch (final IllegalArgumentException | DateTimeException e) {
            throw new ParseException(e.getMessage(), 0);
        }
    }

    @Override
    protected Generator createGenerator(final Generator[] childGenerators) {
        Rounder rounder;
        try {
            rounder = createRounder(getOriginParam());
        } catch (final IllegalArgumentException | DateTimeException e) {
            final ValErr valErr = ValErr.create(e.getMessage());
            rounder = (val, zone) -> valErr;
        }
        final Supplier<ZoneId> queryZone = new QueryZone(expressionContext);
        return childGenerators.length > ZONE_POS
                ? new Gen(new Generator[]{childGenerators[0], childGenerators[ZONE_POS]}, rounder, queryZone)
                : new Gen(new Generator[]{childGenerators[0]}, rounder, queryZone);
    }

    private Param getOriginParam() {
        return params.length > ORIGIN_POS
                ? params[ORIGIN_POS]
                : null;
    }

    private Rounder createRounder(final Param originParam) {
        if (params.length <= BY_POS) {
            return createNumberRounder(null);
        }
        final Param param = params[BY_POS];
        final Val val = ConstantParam.getValue(param);
        if (val == null) {
            throw new IllegalArgumentException("Invalid second argument of '" + name + "': " + param + " - "
                                               + ConstantParam.whyNotConstant(param));
        }
        final Val by = val instanceof ValString
                ? parseBy(val.toString())
                : val;
        if (by instanceof ValDuration || by instanceof ValPeriod) {
            return createTimeRounder(by, originParam);
        }
        if (params.length > ORIGIN_POS) {
            throw new IllegalArgumentException("'" + name + "' can only have an origin and time zone if its second "
                                               + "argument is a duration or period");
        }
        if (by instanceof ValDate) {
            throw new IllegalArgumentException("Second argument of '" + name + "' can't be a date");
        }
        final Integer precision = by.toInteger();
        if (precision == null || precision <= 0) {
            throw new IllegalArgumentException(
                    "Precision argument of '" + name + "' must be positive number if specified");
        }
        if (precision > MAX_PRECISION) {
            throw new IllegalArgumentException("Precision argument of '" + name + "' is too large");
        }
        return createNumberRounder((double) precision);
    }

    private Rounder createTimeRounder(final Val by, final Param originParam) {
        final Origin origin = ConstantParam.getOrigin(originParam);
        final TimeRounder timeRounder = by instanceof final ValPeriod period
                ? TimeRounder.ofPeriod(getMode(), period.toPeriod(), origin)
                : TimeRounder.ofDuration(getMode(), by.toLong(), origin);
        return (val, zone) -> timeRounder.round(val, zone.get());
    }

    // Reads a number of decimal places, duration or period from text, which is how a query parameter gives them.
    private Val parseBy(final String text) {
        try {
            if (TimeRounder.isNumber(text.trim())) {
                return ValDouble.create(Double.parseDouble(text.trim()));
            }
            return TimeRounder.parseMultiple(text);
        } catch (final DateTimeException | ArithmeticException e) {
            throw new IllegalArgumentException("Second argument of '" + name + "' is not a number of decimal places, "
                                               + "a duration or a period: " + text, e);
        }
    }

    private Rounder createNumberRounder(final Double decimalPlaces) {
        final RoundCalculator calculator = createCalculator(decimalPlaces);
        return (val, zone) -> {
            if (Type.PERIOD.equals(val.type())) {
                return ValErr.create("Can't round a period");
            }
            // A date or duration is a whole number of milliseconds.
            return val instanceof ValDate || val instanceof ValDuration
                    ? val
                    : calculator.calc(val);
        };
    }

    /// Rounds a number to a number of decimal places of the decimal that it is shown as, so that 4.35 to two places is
    /// itself, although no double is exactly 4.35.
    ///
    /// @param number        The number, which must be finite.
    /// @param decimalPlaces The decimal places to keep.
    /// @param roundingMode  How to round it.
    /// @return The rounded number.
    static double round(final double number, final int decimalPlaces, final RoundingMode roundingMode) {
        final BigDecimal decimal = BigDecimal.valueOf(number);
        return decimal.scale() <= decimalPlaces
                ? number
                : decimal.setScale(decimalPlaces, roundingMode).doubleValue();
    }

    protected abstract RoundCalculator createCalculator(Double decimalPlaces);

    /// Gets which multiple of a duration or period to round to.
    ///
    /// @return The mode.
    abstract Mode getMode();


    // --------------------------------------------------------------------------------


    private interface Rounder {

        Val round(Val val, Supplier<ZoneId> zone);
    }


    // --------------------------------------------------------------------------------


    // Has the value to round and, if there is one, the time zone.
    private static final class Gen extends AbstractManyChildGenerator {

        private final Rounder rounder;
        private final Supplier<ZoneId> queryZone;

        Gen(final Generator[] childGenerators, final Rounder rounder, final Supplier<ZoneId> queryZone) {
            super(childGenerators);
            this.rounder = rounder;
            this.queryZone = queryZone;
        }

        @Override
        public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
            final Val val = childGenerators[0].eval(storedValues, childDataSupplier);
            if (!val.type().isValue()) {
                return val;
            }
            try {
                if (childGenerators.length > 1) {
                    final Val zone = childGenerators[1].eval(storedValues, childDataSupplier);
                    if (zone.type().isError()) {
                        return zone;
                    }
                    final ZoneId zoneId = UserTimeZoneUtil.getZoneId(zone);
                    return rounder.round(val, () -> zoneId);
                }
                return rounder.round(val, queryZone);
            } catch (final RuntimeException e) {
                return ValErr.create(e.getMessage());
            }
        }
    }
}
