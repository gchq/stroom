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

/**
 * Implementation of {@link PrimitiveValueConverter} that uses a sparse array to
 * resolve a primitive value into an enum value.
 * The primitive value is used as the array index.
 * This impl is used when the spread of primitive values means an array of 100 ish bytes.
 */
public class PrimitiveValueConverterArrayImpl<E extends HasPrimitiveValue>
        implements PrimitiveValueConverter<E> {

    private final HasPrimitiveValue[] sparseArray;
    private final Class<E> itemType;

    PrimitiveValueConverterArrayImpl(final Class<E> itemType,
                                     final E[] values) {

        final int maxPrimitive = Arrays.stream(values)
                .map(HasPrimitiveValue::getPrimitiveValue)
                .mapToInt(Byte::intValue)
                .max()
                .orElseThrow(() -> new RuntimeException("Empty values array supplied"));

        this.sparseArray = new HasPrimitiveValue[maxPrimitive + 1];
        this.itemType = itemType;
        for (final HasPrimitiveValue value : values) {
            final int idx = value.getPrimitiveValue();
            sparseArray[idx] = value;
        }
    }

    @Override
    public E fromPrimitiveValue(final byte i) {
        return getValue(i);
    }

    public E fromPrimitiveValue(final Byte i) {
        if (i == null) {
            return null;
        }
        return getValue(i);
    }

    /**
     * Converts a primitive or returns defaultValue if it is null
     */
    public E fromPrimitiveValue(final Byte i, final E defaultValue) {
        if (i == null) {
            return defaultValue;
        }
        return getValue(i);
    }

    private E getValue(final byte i) {
        try {
            final HasPrimitiveValue value = sparseArray[i];
            if (value == null) {
                // An unknown primitive value
                return null;
            } else {
                try {
                    //noinspection unchecked // GWT, so limited options for checking type of items
                    return (E) value;
                } catch (final Exception e) {
                    throw new RuntimeException(
                            "Unable to cast " + value.getClass().getName()
                            + " to " + itemType.getName(), e);
                }
            }
        } catch (final IndexOutOfBoundsException e) {
            // An unknown primitive value
            return null;
        }
    }
}
