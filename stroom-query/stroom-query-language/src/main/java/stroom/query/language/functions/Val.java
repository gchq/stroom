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


import stroom.query.language.token.Param;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;

public sealed interface Val
        extends Param, Appendable, Comparable<Val>
        permits ValNumber, ValString, ValErr, ValNull, ValBoolean {

    Val[] EMPTY_VALUES = new Val[0];
    double FLOATING_POINT_EQUALITY_TOLERANCE = 0.00001;

    Integer toInteger();

    Long toLong();

    Float toFloat();

    Double toDouble();

    Boolean toBoolean();

    String toString();

    Type type();

    /**
     * @return True if the underlying value is non-null, numeric and has a
     * fractional part, e.g. "1.2" or 1.2.
     */
    default boolean hasFractionalPart() {
        // Most Vals don't have fractional parts
        return false;
    }

    /**
     * @return True if the underlying value is non-null, has a numeric value,
     * e.g. "1", "1.2", 300, etc, or can be represented as a number, e.g. a date string
     * represented as millis since epoch.
     */
    default boolean hasNumericValue() {
        return false;
    }

    /**
     * @return A comparator that will compare {@link Val} instances using the
     * comparison method of the subclass in question. Only intended for use on
     * {@link Val} instances of the same class. To compare {@link Val} instances
     * of potentially mixed types in a null-safe way, see
     * {@link ValComparators#getComparator(boolean)}.
     * @param isCaseSensitive Set to false for a case-insensitive comparator.
     *                        Some impls may ignore this parameter, e.g. numeric {@link Val}
     *                        impls.
     */
    Comparator<Val> getDefaultComparator(final boolean isCaseSensitive);

    static Val[] of(final Val... values) {
        return values;
    }

    static Val[] of(final String... str) {
        final Val[] result = new Val[str.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = ValString.create(str[i]);
        }
        return Val.of(result);
    }

    static Val[] of(final double... d) {
        final Val[] result = new Val[d.length];
        for (int i = 0; i < d.length; i++) {
            result[i] = ValDouble.create(d[i]);
        }
        return Val.of(result);
    }

    static Val[] empty() {
        return EMPTY_VALUES;
    }

    /**
     * Creates a Val in a null safe way, either returning {@link ValNull} or the product
     * of the {@code creator} function.
     */
    static <T> Val nullSafeCreate(final T value,
                                  final java.util.function.Function<T, Val> creator) {
        Objects.requireNonNull(creator);
        if (value == null) {
            return ValNull.INSTANCE;
        } else {
            return creator.apply(value);
        }
    }

    /**
     * Creates a Val in a null safe way, either returning {@link ValNull} or the product
     * of the {@code creator} function.
     */
    static <T1, T2> Val nullSafeCreate(final T1 value,
                                       final java.util.function.Function<T1, T2> converter,
                                       final java.util.function.Function<T2, Val> creator) {
        if (value == null) {
            return ValNull.INSTANCE;
        } else {
            final T2 value2 = Objects.requireNonNull(converter).apply(value);
            if (value2 == null) {
                return ValNull.INSTANCE;
            } else {
                return Objects.requireNonNull(creator).apply(value2);
            }
        }
    }

    /**
     * Create a {@link Val} of the appropriate subclass for the java
     * type passed, e.g.
     * <pre>
     * {@link String} => {@link ValString},
     * {@link Long} => {@link ValLong},
     * {@link Instant} => {@link ValDate},
     * {@link Duration} => {@link ValDuration},
     * {@link Throwable} => {@link ValErr},
     * null => {@link ValNull},
     * etc
     * </pre>
     */
    static Val create(final Object object) {
        // TODO consider using java pattern match switch in J21+
        if (object == null) {
            return ValNull.INSTANCE;
        } else if (object instanceof String val) {
            return ValString.create(val);
        } else if (object instanceof Integer val) {
            return ValInteger.create(val);
        } else if (object instanceof Long val) {
            return ValLong.create(val);
        } else if (object instanceof Double val) {
            return ValDouble.create(val);
        } else if (object instanceof Float val) {
            return ValFloat.create(val);
        } else if (object instanceof Boolean val) {
            return ValBoolean.create(val);
        } else if (object instanceof Instant val) {
            return ValDate.create(val.toEpochMilli());
        } else if (object instanceof Duration val) {
            return ValDuration.create(val.toMillis());
        } else if (object instanceof Throwable val) {
            return ValErr.create(val.getMessage());
        } else if (object instanceof Val val) {
            return val;
        } else {
            throw new UnsupportedOperationException("Unsupported type " + object.getClass());
        }
    }

    @Override
    default int compareTo(Val other) {
        return ValComparators.GENERIC_CASE_INSENSITIVE_COMPARATOR.compare(this, other);
    }
}
