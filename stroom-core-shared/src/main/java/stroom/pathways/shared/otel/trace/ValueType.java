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

package stroom.pathways.shared.otel.trace;

import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.PrimitiveValueConverter;

public enum ValueType implements HasPrimitiveValue {
    STRING(0),
    BOOLEAN(1),
    INTEGER(2),
    DOUBLE(3),
    ARRAY_VALUE(4),
    KEY_VALUE_LIST(5),
    BYTES(6),
    LONG(7);

    public static final PrimitiveValueConverter<ValueType> PRIMITIVE_VALUE_CONVERTER =
            PrimitiveValueConverter.create(ValueType.class, ValueType.values());

    private final byte primitiveValue;

    ValueType(final int primitiveValue) {
        this.primitiveValue = (byte) primitiveValue;
    }

    @Override
    public byte getPrimitiveValue() {
        return primitiveValue;
    }
}
