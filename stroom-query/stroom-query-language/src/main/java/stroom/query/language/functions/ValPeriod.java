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

import stroom.query.language.token.TimeLiteral;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;
import java.time.Period;
import java.util.Comparator;
import java.util.Objects;

/// An amount of calendar time in years, months and days, as [Period]. Unlike a [ValDuration] its length
/// depends on the date and time zone that it is added to.
///
/// A period of only days can stand in for a duration of that many 24 hours, so it has a numeric value, which
/// is that many milliseconds. A period with months or years never can, so it has none.
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ValPeriod implements Val {

    private static final long MONTHS_PER_YEAR = 12L;
    // The average lengths of the Gregorian calendar, in millionths of a day so that they are exact.
    private static final long MICRO_DAYS_PER_MONTH = 30_436_875L;
    private static final long MICRO_DAYS_PER_DAY = 1_000_000L;
    private static final double MILLIS_PER_MICRO_DAY = 86.4D;

    // Sorts by estimated length. Periods are only equal if every field is, so 1 year and 12 months, which
    // have the same estimated length, still have an order.
    static final Comparator<ValPeriod> PERIOD_COMPARATOR = Comparator
            .comparingLong(ValPeriod::getEstimatedMicroDays)
            .thenComparingInt(val -> val.years)
            .thenComparingInt(val -> val.months)
            .thenComparingInt(val -> val.days);
    private static final Comparator<Val> COMPARATOR = ValComparators.asGenericComparator(
            ValPeriod.class, Comparator.comparing(val -> (ValPeriod) val, PERIOD_COMPARATOR));

    public static final Type TYPE = Type.PERIOD;

    @JsonProperty
    private final int years;
    @JsonProperty
    private final int months;
    @JsonProperty
    private final int days;

    @JsonCreator
    private ValPeriod(@JsonProperty("years") final Integer years,
                      @JsonProperty("months") final Integer months,
                      @JsonProperty("days") final Integer days) {
        this.years = Objects.requireNonNullElse(years, 0);
        this.months = Objects.requireNonNullElse(months, 0);
        this.days = Objects.requireNonNullElse(days, 0);
    }

    /// Creates a period.
    ///
    /// @param years  The years.
    /// @param months The months.
    /// @param days   The days.
    /// @return The period.
    public static ValPeriod create(final int years, final int months, final int days) {
        return new ValPeriod(years, months, days);
    }

    /// Creates a period.
    ///
    /// @param period The years, months and days of the period.
    /// @return The period.
    public static ValPeriod create(final Period period) {
        Objects.requireNonNull(period);
        return new ValPeriod(period.getYears(), period.getMonths(), period.getDays());
    }

    /// Gets the period.
    ///
    /// @return The years, months and days of the period, as they were given.
    public Period toPeriod() {
        return Period.of(years, months, days);
    }

    /// Tells whether the period can stand in for a duration.
    ///
    /// @return True if the period has no years or months.
    public boolean hasOnlyDays() {
        return years == 0 && months == 0;
    }

    /// Estimates the length of the period, for sorting it among other lengths of time.
    ///
    /// @return The length in milliseconds. This is exact if the period has no years or months.
    public double estimateMillis() {
        return hasOnlyDays()
                ? toDouble()
                : getEstimatedMicroDays() * MILLIS_PER_MICRO_DAY;
    }

    private long getEstimatedMicroDays() {
        return (years * MONTHS_PER_YEAR + months) * MICRO_DAYS_PER_MONTH + days * MICRO_DAYS_PER_DAY;
    }

    @Override
    public Integer toInteger() {
        return hasOnlyDays()
                ? Integer.valueOf(toLong().intValue())
                : null;
    }

    @Override
    public Long toLong() {
        return hasOnlyDays()
                ? Long.valueOf(Duration.ofDays(days).toMillis())
                : null;
    }

    @Override
    public Float toFloat() {
        return hasOnlyDays()
                ? Float.valueOf(toLong().floatValue())
                : null;
    }

    @Override
    public Double toDouble() {
        return hasOnlyDays()
                ? Double.valueOf(toLong().doubleValue())
                : null;
    }

    @Override
    public Boolean toBoolean() {
        return years != 0 || months != 0 || days != 0;
    }

    @Override
    public String toString() {
        return toPeriod().toString();
    }

    @Override
    public void appendString(final StringBuilder sb) {
        // This is read again when an expression is compiled from its text.
        final String literal = TimeLiteral.formatPeriod(toPeriod());
        if (literal != null) {
            sb.append(literal);
        } else {
            sb.append(ParsePeriod.NAME).append("('").append(this).append("')");
        }
    }

    @Override
    public Type type() {
        return TYPE;
    }

    @Override
    public boolean hasNumericValue() {
        return hasOnlyDays();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ValPeriod that = (ValPeriod) o;
        return years == that.years && months == that.months && days == that.days;
    }

    @Override
    public int hashCode() {
        return Objects.hash(years, months, days);
    }

    @Override
    public Comparator<Val> getDefaultComparator(final boolean isCaseSensitive) {
        return COMPARATOR;
    }

    @Override
    public Object unwrap() {
        return toString();
    }
}
