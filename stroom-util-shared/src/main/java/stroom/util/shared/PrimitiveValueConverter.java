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

package stroom.util.shared;

import java.util.Arrays;
import java.util.IntSummaryStatistics;
import java.util.Objects;

public interface PrimitiveValueConverter<E extends HasPrimitiveValue> {

    /**
     * Factory method to create an instance of a {@link PrimitiveValueConverter} appropriate
     * to the values passed.
     */
    static <T extends HasPrimitiveValue> PrimitiveValueConverter<T> create(final Class<T> clazz,
                                                                           final T[] values) {
        Objects.requireNonNull(values);
        if (values.length == 0) {
            throw new IllegalArgumentException("Empty values supplied");
        }
        final IntSummaryStatistics stats = Arrays.stream(values)
                .map(HasPrimitiveValue::getPrimitiveValue)
                .mapToInt(Byte::intValue)
                .summaryStatistics();

        final int min = stats.getMin();
        final int max = stats.getMax();

        // We don't want to hold massive arrays and most of our primitives are in this
        // range
        final PrimitiveValueConverter<T> converter;
        if (min >= 0 && max <= 100) {
            converter = new PrimitiveValueConverterArrayImpl<>(clazz, values);
        } else {
            converter = new PrimitiveValueConverterMapImpl<>(clazz, values);
        }
        return converter;
    }

    /**
     * @return The value corresponding to primitive value i or null if an unknown
     * primitive value is supplied.
     */
    E fromPrimitiveValue(final byte i);

    /**
     * @return The value corresponding to primitive value i or null if an unknown
     * primitive value is supplied or i is null.
     */
    E fromPrimitiveValue(final Byte i);

    /**
     * Converts a primitive or returns defaultValue if i is null.
     * If i is an unknown primitive value then null will be returned.
     */
    E fromPrimitiveValue(final Byte i, final E defaultValue);
}
