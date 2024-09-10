/*
 * Copyright 2016 Crown Copyright
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

public class PrimitiveValueConverter<E extends HasPrimitiveValue> {

    private final Map<Byte, E> map;

    public PrimitiveValueConverter(E[] values) {
        map = new HashMap<>(values.length);
        for (E value : values) {
            final byte primitiveValue = value.getPrimitiveValue();
            final E previousValue = map.put(primitiveValue, value);
            if (previousValue != null) {
                throw new IllegalArgumentException(
                        "Values: " + previousValue
                                + " and " + value
                                + " have the same primitive value " + primitiveValue);
            }
        }
    }

    public E fromPrimitiveValue(final Byte i) {
        if (i == null) {
            return null;
        }
        return map.get(i);
    }

    /**
     * Converts a primitive or returns defaultValue if it is null
     */
    public E fromPrimitiveValue(final Byte i, final E defaultValue) {
        if (i == null) {
            return defaultValue;
        }
        return map.get(i);
    }

    public void put(final Byte key, final E value) {
        map.put(key, value);
    }
}
