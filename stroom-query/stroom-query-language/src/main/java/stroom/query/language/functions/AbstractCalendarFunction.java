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

import stroom.query.language.functions.ref.StoredValues;

import java.text.ParseException;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.function.Supplier;

/// A function that works out a value from others of the same row, and may have a time zone as its last argument.
/// The time zone can be a field, so that it is different in each row. Without one the time zone is the query's.
///
/// The result is null if any value is null or blank, but a time zone that is given must be one.
abstract class AbstractCalendarFunction extends AbstractManyChildFunction {

    // The position of the time zone of a function that has none.
    static final int NO_ZONE = -1;

    private final QueryZone queryZone;

    /// Creates the function.
    ///
    /// @param expressionContext The context of the expression, which has the time zone of the query.
    /// @param name              The name the function was called by.
    /// @param minParams         The fewest parameters it can have.
    /// @param maxParams         The most parameters it can have.
    AbstractCalendarFunction(final ExpressionContext expressionContext,
                             final String name,
                             final int minParams,
                             final int maxParams) {
        super(name, minParams, maxParams);
        queryZone = new QueryZone(expressionContext);
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        super.setParams(params);

        // Report what can be seen to be wrong now, rather than as an error in every row.
        final int zonePos = getZonePos(params);
        try {
            for (int i = 0; i < params.length; i++) {
                if (params[i] instanceof final Val val) {
                    if (i == zonePos) {
                        UserTimeZoneUtil.getZoneId(val);
                    } else if (!isBlank(val)) {
                        checkConstant(i, val);
                    }
                }
            }
        } catch (final DateTimeException | IllegalArgumentException | ArithmeticException e) {
            throw new ParseException(e.getMessage(), 0);
        }
    }

    /// Gets the position of the time zone parameter.
    ///
    /// @param params The parameters of the function.
    /// @return The position, which can be past the last parameter, or [#NO_ZONE].
    abstract int getZonePos(Param[] params);

    /// Checks a constant parameter when the expression is parsed.
    ///
    /// @param pos The position of the parameter.
    /// @param val Its value, which is not null, blank or an error.
    /// @throws DateTimeException        If the value can't be used.
    /// @throws IllegalArgumentException If the value can't be used.
    /// @throws ArithmeticException      If the value can't be used.
    void checkConstant(final int pos, final Val val) {
        // Override to check.
    }

    /// Works out the value of the function.
    ///
    /// @param vals The values of the parameters, without the time zone. None is null or an error.
    /// @param zone Gets the time zone.
    /// @return The value.
    /// @throws DateTimeException        If there is no value. The message is fit to show the query's author.
    /// @throws IllegalArgumentException If there is no value. The message is fit to show the query's author.
    /// @throws ArithmeticException      If there is no value. The message is fit to show the query's author.
    abstract Val evaluate(Val[] vals, Supplier<ZoneId> zone);

    @Override
    protected Generator createGenerator(final Generator[] childGenerators) {
        // The value of a query parameter is known by now.
        final Param[] resolved = Arrays.copyOf(params, params.length);
        final int last = params.length - 1;
        final Val val = ConstantParam.getValue(params[last]);
        if (val != null) {
            resolved[last] = val;
        }
        return new Gen(this, childGenerators, getZonePos(resolved));
    }

    private static boolean isBlank(final Val val) {
        return !val.type().isValue() || (val instanceof ValString && val.toString().isBlank());
    }

    /// Reads a value as a whole number.
    ///
    /// @param val  The value.
    /// @param what What the value is, for an error.
    /// @return The number.
    /// @throws IllegalArgumentException If it isn't one.
    static int toInt(final Val val, final String what) {
        final Double number = switch (val) {
            case final ValDate ignored -> null;
            case final ValDuration ignored -> null;
            case final ValNumber valNumber -> valNumber.toDouble();
            // Not toDouble(), as that also reads text such as '2h'.
            case final ValString text -> TimeRounder.isNumber(text.toString().trim())
                    ? Double.valueOf(text.toString().trim())
                    : null;
            default -> null;
        };
        if (number == null || number != Math.rint(number) || Math.abs(number) > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("The " + what + " '" + val + "' is not a whole number");
        }
        return number.intValue();
    }


    // --------------------------------------------------------------------------------


    private static final class Gen extends AbstractManyChildGenerator {

        private final AbstractCalendarFunction function;
        private final int zonePos;

        Gen(final AbstractCalendarFunction function, final Generator[] childGenerators, final int zonePos) {
            super(childGenerators);
            this.function = function;
            this.zonePos = zonePos;
        }

        @Override
        public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
            final boolean hasZone = zonePos >= 0 && zonePos < childGenerators.length;
            final Val[] vals = new Val[childGenerators.length];
            boolean hasNull = false;
            for (int i = 0; i < vals.length; i++) {
                final Val val = childGenerators[i].eval(storedValues, childDataSupplier);
                if (val.type().isError()) {
                    return val;
                }
                hasNull |= i != zonePos && isBlank(val);
                vals[i] = val;
            }
            if (hasNull) {
                return ValNull.INSTANCE;
            }
            try {
                if (!hasZone) {
                    return function.evaluate(vals, function.queryZone);
                }
                // The time zone is always the last parameter.
                final ZoneId zoneId = UserTimeZoneUtil.getZoneId(vals[zonePos]);
                return function.evaluate(Arrays.copyOf(vals, zonePos), () -> zoneId);
            } catch (final RuntimeException e) {
                return ValErr.create(e.getMessage());
            }
        }
    }
}
