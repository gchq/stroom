/*
 * Copyright 2016-2026 Crown Copyright
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


import stroom.docref.HasDisplayValue;
import stroom.util.shared.string.CIKey;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class CaseInsensitiveDisplayValueConverter<T extends HasDisplayValue> {

    private final Map<CIKey, T> mapByDisplayValue;
    private final Class<T> itemType;

    private CaseInsensitiveDisplayValueConverter(final Class<T> itemType,
                                                 final Map<CIKey, T> mapByDisplayValue) {
        this.itemType = itemType;
        this.mapByDisplayValue = mapByDisplayValue;
    }

    public static <T extends HasDisplayValue> CaseInsensitiveDisplayValueConverter<T> create(
            final Class<T> itemType,
            final T[] values) {
        Objects.requireNonNull(itemType);
        Objects.requireNonNull(values);

        final Map<CIKey, T> map = new HashMap<>(values.length);
        for (final T value : values) {
            final String displayValue = value.getDisplayValue();
            final CIKey ciKey = CIKey.internStaticKey(displayValue);
            final T prevVal = map.put(ciKey, value);
            if (prevVal != null) {
                throw new IllegalArgumentException(
                        "Two values have the same display value '" + displayValue
                        + "' (ignoring case), value1: '" + prevVal + "', value2: '" + value + "'");
            }
        }
        return new CaseInsensitiveDisplayValueConverter<>(itemType, Collections.unmodifiableMap(map));
    }

    public static <T extends Enum<T> & HasDisplayValue> CaseInsensitiveDisplayValueConverter<T> create(
            final Class<T> itemType) {
        return create(itemType, itemType.getEnumConstants());
    }

    /**
     * Case-insensitive lookup by displayValue.
     */
    public T fromDisplayValue(final CIKey displayValue) {
        if (displayValue == null) {
            return null;
        } else {
            return mapByDisplayValue.get(displayValue);
        }
    }

    /**
     * Case-insensitive lookup by displayValue.
     */
    public T fromDisplayValue(final String displayValue) {
        if (displayValue == null) {
            return null;
        } else {
            return mapByDisplayValue.get(CIKey.of(displayValue));
        }
    }

    public Class<T> getItemType() {
        return itemType;
    }
}
