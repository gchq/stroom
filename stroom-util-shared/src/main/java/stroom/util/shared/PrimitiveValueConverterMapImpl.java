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

import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of {@link PrimitiveValueConverter} that uses a {@link java.util.HashMap} to
 * resolve a primitive value into an enum value.
 * This impl is used when the spread of primitive values is too large for a sparse array.
 */
public class PrimitiveValueConverterMapImpl<E extends HasPrimitiveValue>
        implements PrimitiveValueConverter<E> {

    private final Map<Byte, E> mapByPrimitiveValue;
    private final Class<E> itemType;

    PrimitiveValueConverterMapImpl(final Class<E> itemType,
                                   final E[] values) {
        this.mapByPrimitiveValue = new HashMap<>(values.length);
        this.itemType = itemType;
        for (final E value : values) {
            final byte primitiveValue = value.getPrimitiveValue();
            final E previousValue = mapByPrimitiveValue.put(primitiveValue, value);
            if (previousValue != null) {
                throw new IllegalArgumentException(
                        "Values: " + previousValue
                        + " and " + value
                        + " have the same primitive value " + primitiveValue);
            }
        }
    }

    @Override
    public E fromPrimitiveValue(final byte i) {
        return mapByPrimitiveValue.get(i);
    }

    public E fromPrimitiveValue(final Byte i) {
        if (i == null) {
            return null;
        }
        return mapByPrimitiveValue.get(i);
    }

    /**
     * Converts a primitive or returns defaultValue if it is null
     */
    public E fromPrimitiveValue(final Byte i, final E defaultValue) {
        if (i == null) {
            return defaultValue;
        }
        return mapByPrimitiveValue.get(i);
    }
}
