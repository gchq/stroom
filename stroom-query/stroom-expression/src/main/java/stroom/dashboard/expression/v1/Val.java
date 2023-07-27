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

package stroom.dashboard.expression.v1;


import java.util.Objects;

public interface Val extends Param, Appendable {
    Val[] EMPTY_VALUES = new Val[0];

    Integer toInteger();

    Long toLong();

    Double toDouble();

    Boolean toBoolean();

    String toString();

    Type type();

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
}
