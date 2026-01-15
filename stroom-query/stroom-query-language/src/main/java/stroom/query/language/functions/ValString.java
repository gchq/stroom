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

import stroom.util.concurrent.LazyBoolean;
import stroom.util.concurrent.LazyValue;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.math.DoubleMath;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Objects;
import java.util.regex.Pattern;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ValString implements Val {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ValString.class);

    private static final Comparator<Val> CASE_SENSITIVE_COMPARATOR = ValComparators.asGenericComparator(
            ValString.class,
            ValComparators.AS_DOUBLE_THEN_CASE_SENSITIVE_STRING_COMPARATOR,
            ValComparators.GENERIC_CASE_SENSITIVE_COMPARATOR);
    private static final Comparator<Val> CASE_INSENSITIVE_COMPARATOR = ValComparators.asGenericComparator(
            ValString.class,
            ValComparators.AS_DOUBLE_THEN_CASE_INSENSITIVE_STRING_COMPARATOR,
            ValComparators.GENERIC_CASE_INSENSITIVE_COMPARATOR);
    private static final Pattern BACK_SLASH_PATTERN = Pattern.compile("\\\\");
    private static final Pattern SINGLE_QUOTE_PATTERN = Pattern.compile("'");

    public static final Type TYPE = Type.STRING;
    public static final ValString EMPTY = new ValString("");
    @JsonProperty
    private final String value;

    // Permanent lazy cache of the slightly costly conversion to long/double
    @JsonIgnore
    private final transient LazyValue<Double> lazyDoubleValue;
    @JsonIgnore
    private final transient LazyValue<Long> lazyLongValue;
    @JsonIgnore
    private final transient LazyBoolean lazyHasFractionalPart;

    @JsonCreator
    private ValString(@JsonProperty("value") final String value) {
        if (value == null) {
            // We should not be allowing null values, but not sure that we can risk a null check in case
            // it breaks existing content
            LOGGER.warn("null passed to ValString.create, code should be using ValNull. " +
                        "Enable DEBUG to see stack trace.");
            if (LOGGER.isDebugEnabled()) {
                LogUtil.logStackTrace(
                        "null passed to ValString.create, should be using ValNull, stack trace:",
                        LOGGER::debug);
            }
            throw new NullPointerException("Null value passed to ValString");
        }
        this.value = value;
        // Suppliers are idempotent so do it without locking at the risk of
        // calling the supplier >1 times.
        this.lazyDoubleValue = LazyValue.initialisedBy(this::deriveDoubleValue);
        this.lazyLongValue = LazyValue.initialisedBy(this::deriveLongValue);
        this.lazyHasFractionalPart = LazyBoolean.initialisedBy(this::deriveHasFractionalPart);
    }

    public static ValString create(final String value) {
        if (value.isEmpty()) {
            return EMPTY;
        }
        return new ValString(value);
    }

    @Override
    public Integer toInteger() {
        final Long l = toLong();
        if (l != null) {
            return l.intValue();
        }
        return null;
    }

    @Override
    public Long toLong() {
        return lazyLongValue.getValueWithoutLocks();
    }

    private Long deriveLongValue() {
        Long longValue = null;
        final String trimmedVal = NullSafe.get(value, String::trim);
        try {
            // See if it is an integer part
            longValue = Long.valueOf(trimmedVal);
        } catch (final RuntimeException e) {
            try {
                // See if it is a date string
                longValue = DateUtil.parseNormalDateTimeString(trimmedVal);
            } catch (final RuntimeException e2) {
                try {
                    // See if it is a duration string
                    longValue = ValDurationUtil.parseToMilliseconds(trimmedVal);
                } catch (final RuntimeException e3) {
                    // Not a date or a long so has no long value
                }
            }
        }
        return longValue;
    }

    @Override
    public Float toFloat() {
        final Double d = toDouble();
        if (d != null) {
            return d.floatValue();
        }
        return null;
    }

    @Override
    public Double toDouble() {
        return lazyDoubleValue.getValueWithoutLocks();
    }

    private Double deriveDoubleValue() {
        Double doubleValue = null;
        final String trimmedVal = NullSafe.get(value, String::trim);
        try {
            doubleValue = new BigDecimal(trimmedVal).doubleValue();
        } catch (final RuntimeException e) {
            try {
                // If parsable as a date then get the millis value
                doubleValue = (double) DateUtil.parseNormalDateTimeString(trimmedVal);
            } catch (final RuntimeException e2) {
                try {
                    // If parsable as a duration then get the millis value
                    doubleValue = (double) ValDurationUtil.parseToMilliseconds(trimmedVal);
                } catch (final RuntimeException e3) {
                    // Not a date/duration/double so has no double value
                }
            }
        }
        return doubleValue;
    }

    @Override
    public Boolean toBoolean() {
        try {
            // Any non-zero number is true
            return Long.parseLong(value) != 0;
        } catch (final RuntimeException e1) {
            try {
                return Boolean.valueOf(value);
            } catch (final RuntimeException e2) {
                // Ignore.
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public void appendString(final StringBuilder sb) {
        String val = value;
        val = BACK_SLASH_PATTERN.matcher(val).replaceAll("\\\\\\\\");
        val = SINGLE_QUOTE_PATTERN.matcher(val).replaceAll("\\\\'");

        // Assume that strings are single quoted even though they may actually be double quoted in source.
        sb.append("'");
        sb.append(val);
        sb.append("'");
    }

    @Override
    public Type type() {
        return TYPE;
    }

    @Override
    public boolean hasFractionalPart() {
        return lazyHasFractionalPart.getValueWithoutLocks();
    }

    private boolean deriveHasFractionalPart() {
        final Double dbl = toDouble();
        return dbl != null && !DoubleMath.isMathematicalInteger(dbl);
    }

    @Override
    public boolean hasNumericValue() {
        // Even if it has fractional parts, toLong will tell us if it is numeric
        return value != null && (toLong() != null || toDouble() != null);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ValString valString = (ValString) o;
        return Objects.equals(value, valString.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public Comparator<Val> getDefaultComparator(final boolean isCaseSensitive) {
        return isCaseSensitive
                ? CASE_SENSITIVE_COMPARATOR
                : CASE_INSENSITIVE_COMPARATOR;
    }

    @Override
    public Object unwrap() {
        return value;
    }
}
