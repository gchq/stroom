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


import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ValNumber.class, name = "number"),
        @JsonSubTypes.Type(value = ValString.class, name = "string"),
        @JsonSubTypes.Type(value = ValErr.class, name = "err"),
        @JsonSubTypes.Type(value = ValNull.class, name = "null"),
        @JsonSubTypes.Type(value = ValBoolean.class, name = "boolean"),
        @JsonSubTypes.Type(value = ValXml.class, name = "xml"),
        @JsonSubTypes.Type(value = ValByte.class, name = "byte"),
        @JsonSubTypes.Type(value = ValShort.class, name = "short"),
        @JsonSubTypes.Type(value = ValInteger.class, name = "integer"),
        @JsonSubTypes.Type(value = ValLong.class, name = "long"),
        @JsonSubTypes.Type(value = ValFloat.class, name = "float"),
        @JsonSubTypes.Type(value = ValDouble.class, name = "double"),
        @JsonSubTypes.Type(value = ValDate.class, name = "date"),
        @JsonSubTypes.Type(value = ValDuration.class, name = "duration")
})
public sealed interface Val
        extends Param, Appendable, Comparable<Val>
        permits ValNumber, ValString, ValErr, ValNull, ValBoolean, ValXml {

    Val[] EMPTY_VALUES = new Val[0];
    double FLOATING_POINT_EQUALITY_TOLERANCE = 0.00001;

    Integer toInteger();

    Long toLong();

    Float toFloat();

    Double toDouble();

    Boolean toBoolean();

    String toString();

    Object unwrap();

    /**
     * @return The {@link Val} as a number or null if it cannot be represented as a number.
     * Returns an impl of {@link Number} that is appropriate for this value. The returned type may differ for
     * different values of the same Val impl, e.g. "123" and "1.23"
     */
    default Number toNumber() {
        if (hasNumericValue()) {
            if (hasFractionalPart()) {
                return toDouble();
            } else {
                return toLong();
            }
        } else {
            return null;
        }
    }

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
     * @param isCaseSensitive Set to false for a case-insensitive comparator.
     *                        Some impls may ignore this parameter, e.g. numeric {@link Val}
     *                        impls.
     * @return A comparator that will compare {@link Val} instances using the
     * comparison method of the subclass in question. Only intended for use on
     * {@link Val} instances of the same class. To compare {@link Val} instances
     * of potentially mixed types in a null-safe way, see
     * {@link ValComparators#getComparator(boolean)}.
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

    static Val[] emptyArray() {
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

    static Val requireNonNullElse(final Val val, final Val other) {
        if (val != null && !val.type().isNull()) {
            return val;
        } else {
            return other;
        }
    }

    static boolean isNull(final Val val) {
        return val == null
                || val == ValNull.INSTANCE
                || Objects.equals(ValNull.INSTANCE, val)
                || val.type().isNull();
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
        return switch (object) {
            case null -> ValNull.INSTANCE;
            case final Boolean val -> ValBoolean.create(val);
            case final Double val -> ValDouble.create(val);
            case final Duration val -> ValDuration.create(val.toMillis());
            case final Float val -> ValFloat.create(val);
            case final Instant val -> ValDate.create(val.toEpochMilli());
            case final Integer val -> ValInteger.create(val);
            case final Long val -> ValLong.create(val);
            case final String val -> ValString.create(val);
            case final StroomDuration val -> ValDuration.create(val.toMillis());
            case final Throwable val -> ValErr.create(val.getMessage());
            case final Val val -> val;
            default -> throw new UnsupportedOperationException("Unsupported type " + object.getClass());
        };
    }

    @Override
    default int compareTo(final Val other) {
        return ValComparators.GENERIC_CASE_INSENSITIVE_COMPARATOR.compare(this, other);
    }
}
