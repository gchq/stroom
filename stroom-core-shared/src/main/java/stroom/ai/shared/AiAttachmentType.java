/*
 * Copyright 2026 Crown Copyright
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

/**
 * Classifies the source type of an {@link AiChatAttachment}.
 */
public enum AiAttachmentType implements HasPrimitiveValue {
    DASHBOARD(0),
    QUERY(1),
    GENERAL(2);

    public static final PrimitiveValueConverter<AiAttachmentType> PRIMITIVE_VALUE_CONVERTER =
            PrimitiveValueConverter.create(AiAttachmentType.class, AiAttachmentType.values());

    private final byte primitiveValue;

    AiAttachmentType(final int primitiveValue) {
        this.primitiveValue = (byte) primitiveValue;
    }

    @Override
    public byte getPrimitiveValue() {
        return primitiveValue;
    }
}
