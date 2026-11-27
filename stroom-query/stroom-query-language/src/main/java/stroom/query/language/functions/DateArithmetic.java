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

import java.time.DateTimeException;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.function.Supplier;

/// The arithmetic of dates, durations and periods.
///
/// Durations are added on the instant line and periods on the local line of a time zone, as `java.time` does.
/// A period of only days stands in for a duration of that many 24 hours where the other value is a duration
/// or a number.
///
/// Each operation returns `null` if it has no rule for the two values, so they are treated as numbers, and
/// throws a [DateTimeException] or an [ArithmeticException], with a message fit to show the query's author,
/// if the values can't be used together.
final class DateArithmetic {

    private static final String MIXED = "Can't mix a duration and a period of months or years; add each to a date";

    private DateArithmetic() {
        // Static methods only.
    }

    /// Adds two values.
    ///
    /// @param left  The first value.
    /// @param right The value to add to it.
    /// @param zone  Gets the time zone that periods are added in.
    /// @return The sum, or `null` if there is no rule for the two types.
    static Val add(final Val left, final Val right, final Supplier<ZoneId> zone) {
        if (isNotNumeric(left) || isNotNumeric(right)) {
            return null;
        } else if (isDate(left) && isDate(right)) {
            throw new DateTimeException("Can't add a date to a date");
        } else if (isDate(left) && right instanceof final ValPeriod period) {
            return shift(left, period.toPeriod(), zone);
        } else if (left instanceof final ValPeriod period && isDate(right)) {
            return shift(right, period.toPeriod(), zone);
        } else if (left instanceof final ValPeriod period1 && right instanceof final ValPeriod period2) {
            return ValPeriod.create(period1.toPeriod().plus(period2.toPeriod()));
        } else if (isPeriod(left) || isPeriod(right)) {
            return addNumbers(toFixed(left, right), toFixed(right, left));
        }
        return addNumbers(left, right);
    }

    private static Val addNumbers(final Val left, final Val right) {
        if (isDate(left) && isDuration(right) || isDuration(left) && isDate(right)) {
            return ValDate.create(Math.addExact(left.toLong(), right.toLong()));
        } else if (isDuration(left) || isDuration(right)) {
            return ValDuration.create(Math.addExact(toMillis(left), toMillis(right)));
        }
        return null;
    }

    /// Subtracts one value from another.
    ///
    /// @param left  The value to subtract from.
    /// @param right The value to subtract.
    /// @param zone  Gets the time zone that periods are subtracted in.
    /// @return The difference, or `null` if there is no rule for the two types.
    static Val subtract(final Val left, final Val right, final Supplier<ZoneId> zone) {
        if (isDate(left) && right instanceof final ValPeriod period) {
            return shift(left, period.toPeriod().negated(), zone);
        } else if ((isPeriod(left) || isDuration(left)) && isDate(right)) {
            throw new DateTimeException("Can't subtract a date from a " + left.type().getName());
        } else if (left instanceof final ValPeriod period1 && right instanceof final ValPeriod period2) {
            return ValPeriod.create(period1.toPeriod().minus(period2.toPeriod()));
        } else if (isPeriod(left) || isPeriod(right)) {
            return subtractNumbers(toFixed(left, right), toFixed(right, left));
        }
        return subtractNumbers(left, right);
    }

    private static Val subtractNumbers(final Val left, final Val right) {
        if (isDate(left) && isDate(right)) {
            return ValDuration.create(Math.subtractExact(left.toLong(), right.toLong()));
        } else if (isDate(left) && isDuration(right)) {
            return ValDate.create(Math.subtractExact(left.toLong(), right.toLong()));
        } else if (isDuration(left) || isDuration(right)) {
            return ValDuration.create(Math.subtractExact(toMillis(left), toMillis(right)));
        }
        return null;
    }

    /// Multiplies two values.
    ///
    /// @param left  The first value.
    /// @param right The value to multiply it by.
    /// @return The product, or `null` if there is no rule for the two types.
    static Val multiply(final Val left, final Val right) {
        if (isDate(left) || isDate(right)) {
            throw new DateTimeException("Can't multiply a date");
        } else if (isAmount(left) && isAmount(right)) {
            throw new DateTimeException("Can't multiply a " + left.type().getName() + " by a "
                                        + right.type().getName());
        } else if (left instanceof final ValPeriod period) {
            return multiply(period, right);
        } else if (right instanceof final ValPeriod period) {
            return multiply(period, left);
        } else if (isDuration(left)) {
            return multiplyDuration(left, right);
        } else if (isDuration(right)) {
            return multiplyDuration(right, left);
        }
        return null;
    }

    private static Val multiply(final ValPeriod period, final Val number) {
        final Double value = number.toDouble();
        if (value == null || value != Math.rint(value)) {
            throw new DateTimeException("A period can only be multiplied by a whole number");
        }
        return ValPeriod.create(period.toPeriod().multipliedBy(toInt(value)));
    }

    private static Val multiplyDuration(final Val duration, final Val number) {
        if (number.hasFractionalPart()) {
            return createDuration(duration.toDouble() * number.toDouble());
        }
        return ValDuration.create(Math.multiplyExact(duration.toLong(), number.toLong()));
    }

    /// Divides one value by another.
    ///
    /// @param left  The value to divide.
    /// @param right The value to divide it by.
    /// @return The result, or `null` if there is no rule for the two types.
    static Val divide(final Val left, final Val right) {
        if (isDate(right)) {
            throw new DateTimeException("Can't divide by a date");
        } else if (isDate(left)) {
            if (isAmount(right)) {
                throw new DateTimeException("Can't divide a date by a " + right.type().getName());
            }
            // The milliseconds of the date, as in 'EventTime / 1000' for seconds.
            return null;
        } else if (isPeriod(left) && !isAmount(right)) {
            throw new DateTimeException("Can't divide a period by a number");
        } else if (isPeriod(left) || isPeriod(right)) {
            // One length of time goes into another a number of times.
            final double result = toFixed(left, right).toDouble() / toFixed(right, left).toDouble();
            return ValDouble.create(checkNumber(result, left, right, "/"));
        } else if (isDuration(left) && !isDuration(right)) {
            return createDuration(checkNumber(left.toDouble() / right.toDouble(), left, right, "/"));
        }
        return null;
    }

    /// Gets the remainder of dividing one value by another.
    ///
    /// @param left  The value to divide.
    /// @param right The value to divide it by.
    /// @return The remainder, or `null` if there is no rule for the two types.
    static Val modulo(final Val left, final Val right) {
        if (!isAmount(left) || isDate(right)) {
            return null;
        }
        final Val dividend = toFixed(left, right);
        final Val divisor = toFixed(right, left);
        if (divisor.hasFractionalPart()) {
            return createDuration(checkNumber(dividend.toDouble() % divisor.toDouble(), dividend, divisor, "%"));
        } else if (divisor.toLong() == 0) {
            throw notANumber(dividend, divisor, "%");
        }
        return ValDuration.create(dividend.toLong() % divisor.toLong());
    }

    /// Gets the average of some values from their total.
    ///
    /// @param total The total of the values, which is not null or an error.
    /// @param count The number of values, which is not zero.
    /// @return A duration if the total is a duration or a period of only days, otherwise a number, or `null` if the
    ///         total is not a number.
    static Val average(final Val total, final long count) {
        final Double number = total.toDouble();
        if (number == null) {
            return null;
        }
        return isAmount(total)
                ? createDuration(number / count)
                : ValDouble.create(number / count);
    }

    /// Gets the standard deviation of some values, as a duration if that is what they are.
    ///
    /// @param values      The values as numbers.
    /// @param hasDuration Whether any of them is a duration.
    /// @return A duration or a number.
    static Val standardDeviation(final List<Double> values, final boolean hasDuration) {
        final double result = Statistics.standardDeviation(values.toArray(new Double[0]));
        if (!hasDuration) {
            return ValDouble.create(result);
        }
        try {
            return createDuration(result);
        } catch (final ArithmeticException e) {
            return ValErr.create(e.getMessage());
        }
    }

    /// Changes the sign of a value.
    ///
    /// @param val The value.
    /// @return The negated value, or `null` if there is no rule for its type.
    static Val negate(final Val val) {
        if (val instanceof final ValPeriod period) {
            return ValPeriod.create(period.toPeriod().negated());
        } else if (isDuration(val)) {
            return ValDuration.create(Math.negateExact(val.toLong()));
        }
        return null;
    }

    /// Checks that it makes sense to test whether two values are equal.
    ///
    /// @param left  The first value.
    /// @param right The second value.
    /// @return An error, or `null` if they can be tested.
    static ValErr checkEquality(final Val left, final Val right) {
        if (left instanceof final ValPeriod period) {
            return checkEquality(period, right);
        } else if (right instanceof final ValPeriod period) {
            return checkEquality(period, left);
        }
        return null;
    }

    private static ValErr checkEquality(final ValPeriod period, final Val other) {
        if (isDate(other)) {
            return ValErr.create("Can't compare a date and a period");
        } else if (!period.hasOnlyDays() && other.type().isNumber()) {
            return ValErr.create("Can't compare a " + other.type().getName() + " and a period of months or years");
        }
        return null;
    }

    /// Checks that it makes sense to test which of two values is the greater.
    ///
    /// @param left  The first value.
    /// @param right The second value.
    /// @return An error, or `null` if they can be tested, or if either is null as then there is nothing to test.
    static ValErr checkOrder(final Val left, final Val right) {
        if (left.type().isNull() || right.type().isNull()) {
            return null;
        } else if (hasNoOrder(left) || hasNoOrder(right)) {
            return noOrder();
        }
        return checkEquality(left, right);
    }

    /// Gets a value that can be put in order with others. A period of only days becomes a duration.
    ///
    /// @param val The value.
    /// @return The value to use, or an error for a period of months or years.
    static Val toOrdered(final Val val) {
        if (hasNoOrder(val)) {
            return noOrder();
        }
        return val instanceof final ValPeriod period
                ? ValDuration.create(period.toLong())
                : val;
    }

    /// Reads text that is compared with a period, so that `1M` is equal to `'1M'` as well as to `'P1M'`.
    ///
    /// @param other The value that it is compared with.
    /// @param val   The value.
    /// @return A period if it is text of one and the other value is a period, otherwise the value.
    static Val readPeriodFor(final Val other, final Val val) {
        return other instanceof ValPeriod
               && val instanceof ValString
               && ValPeriodUtil.parsePeriod(val) instanceof final ValPeriod period
                ? period
                : val;
    }

    private static boolean hasNoOrder(final Val val) {
        return val instanceof final ValPeriod period && !period.hasOnlyDays();
    }

    private static ValErr noOrder() {
        return ValErr.create("A period of months or years can't be compared, as its length depends on the date it "
                             + "is added to");
    }

    // A value as it is, or a period of only days as a duration.
    private static Val toFixed(final Val val, final Val other) {
        if (val instanceof final ValPeriod period) {
            if (!period.hasOnlyDays()) {
                throw new DateTimeException(isDuration(other)
                        ? MIXED
                        : "A period of months or years can't be used as a number");
            }
            return ValDuration.create(period.toLong());
        }
        return val;
    }

    private static Val shift(final Val date, final Period period, final Supplier<ZoneId> zone) {
        final ZonedDateTime zonedDateTime = Instant.ofEpochMilli(date.toLong()).atZone(zone.get());
        return ValDate.create(zonedDateTime.plus(period).toInstant().toEpochMilli());
    }

    // A value such as text that can't be used as a number.
    private static boolean isNotNumeric(final Val val) {
        return !isPeriod(val) && val.toDouble() == null;
    }

    private static long toMillis(final Val val) {
        return val.hasFractionalPart()
                ? Math.round(val.toDouble())
                : val.toLong();
    }

    private static Val createDuration(final double millis) {
        if (Double.isNaN(millis) || Math.abs(millis) >= Long.MAX_VALUE) {
            throw new ArithmeticException("The duration is too long or not a number");
        }
        return ValDuration.create(Math.round(millis));
    }

    private static double checkNumber(final double result,
                                      final Val left,
                                      final Val right,
                                      final String operator) {
        if (Double.isNaN(result) || Double.isInfinite(result)) {
            throw notANumber(left, right, operator);
        }
        return result;
    }

    private static ArithmeticException notANumber(final Val left, final Val right, final String operator) {
        return new ArithmeticException("Result of " + left + " " + operator + " " + right + " is not a number");
    }

    private static int toInt(final double value) {
        if (Math.abs(value) > Integer.MAX_VALUE) {
            throw new ArithmeticException("The period is too long");
        }
        return (int) value;
    }

    private static boolean isDate(final Val val) {
        return Type.DATE.equals(val.type());
    }

    private static boolean isDuration(final Val val) {
        return Type.DURATION.equals(val.type());
    }

    private static boolean isPeriod(final Val val) {
        return Type.PERIOD.equals(val.type());
    }

    /// Tells whether a value is a length of time.
    ///
    /// @param val The value.
    /// @return True if it is a duration or a period.
    static boolean isAmount(final Val val) {
        return isDuration(val) || isPeriod(val);
    }
}
