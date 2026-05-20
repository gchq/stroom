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

package stroom.ai.shared;

import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.PrimitiveValueConverter;

public enum AiMessageType implements HasPrimitiveValue {
    USER_MESSAGE(0),
    AI_RESPONSE(1),
    THINKING(2),
    ERROR(3),
    DASHBOARD_DATA(4),
    QUERY_DATA(5),
    TABLE_DATA(6);

    public static final PrimitiveValueConverter<AiMessageType> PRIMITIVE_VALUE_CONVERTER =
            PrimitiveValueConverter.create(AiMessageType.class, AiMessageType.values());

    private final byte primitiveValue;

    AiMessageType(final int primitiveValue) {
        this.primitiveValue = (byte) primitiveValue;
    }

    @Override
    public byte getPrimitiveValue() {
        return primitiveValue;
    }
}
