/*
 * Copyright 2018 Crown Copyright
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

import com.google.common.math.DoubleMath;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Objects;

public final class ValString implements Val {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ValString.class);

    public static Comparator<Val> COMPARATOR = ValComparators.asGenericComparator(
            ValString.class, ValComparators.AS_DOUBLE_THEN_STRING_COMPARATOR);

    public static final Type TYPE = Type.STRING;
    static final ValString EMPTY = new ValString("");
    private final String value;

    // Permanent lazy cache of the slightly costly conversion to long/double
    private transient final LazyValue<Double> lazyDoubleValue;
    private transient final LazyValue<Long> lazyLongValue;
    private transient final LazyBoolean lazyHasFractionalPart;

    private ValString(final String value) {
        if (value == null) {
            // We should not be allowing null values, but not sure that we can risk a null check in case
            // it breaks existing content
            LOGGER.warn("null passed to ValString.create, should be using ValNull, enable DEBUG to see stack");
            if (LOGGER.isDebugEnabled()) {
                LogUtil.logStackTrace(
                        "null passed to ValString.create, should be using ValNull, stack trace:",
                        LOGGER::debug);
            }
        }
        this.value = value;
        // Suppliers are idempotent so do it without locking at the risk of
        // calling the supplier >1 times.
        this.lazyDoubleValue = LazyValue.initialisedBy(this::deriveDoubleValue);
        this.lazyLongValue = LazyValue.initialisedBy(this::deriveLongValue);
        this.lazyHasFractionalPart = LazyBoolean.initialisedBy(this::deriveHasFractionalPart);
    }

    public static ValString create(final String value) {
        if ("".equals(value)) {
            return EMPTY;
        }
        return new ValString(value);
    }

    @Override
    public Integer toInteger() {
        Long l = toLong();
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
        try {
            // See if it is a date string
            longValue = DateUtil.parseNormalDateTimeString(value);
        } catch (final RuntimeException e) {
            try {
                // See if it is a duration string
                longValue = ValDurationUtil.parseToMilliseconds(value);
            } catch (RuntimeException e2) {
                try {
                    // See if it is an integer part
                    longValue = Long.valueOf(value);
                } catch (final RuntimeException e3) {
                    // Not a date or a long so has no long value
                }
            }
        }
        return longValue;
    }

    @Override
    public Float toFloat() {
        Double d = toDouble();
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
        try {
            doubleValue = (double) DateUtil.parseNormalDateTimeString(value);
        } catch (final RuntimeException e) {
            try {
                doubleValue = (double) ValDurationUtil.parseToMilliseconds(value);
            } catch (RuntimeException e2) {
                try {
                    doubleValue = new BigDecimal(value).doubleValue();
                } catch (final RuntimeException e3) {
                    // Not a date or a double so has no double value
                }
            }
        }
        return doubleValue;
    }

    @Override
    public Boolean toBoolean() {
        try {
            return Boolean.valueOf(value);
        } catch (final RuntimeException e) {
            // Ignore.
        }
        return null;
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public void appendString(final StringBuilder sb) {
        // Assume that strings are single quoted even though they may actually be double quoted in source.
        sb.append("'");
        sb.append(value.replaceAll("'", "\\\\'"));
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
        return value != null
                && (toLong() != null || toDouble() != null);
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
    public Comparator<Val> getDefaultComparator() {
        return COMPARATOR;
    }
}
