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

import stroom.docref.HasDisplayValue;
import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.PrimitiveValueConverter;

/**
 * Who, or what, a piece of 'Ask Stroom AI' memory belongs to.
 */
public enum AiMemoryScope implements HasPrimitiveValue, HasDisplayValue {
    /**
     * The user's own memory, loaded into every chat they have.
     */
    USER(0, "User"),
    /**
     * Memory for one chat (e.g. its plan and notes), loaded only into that chat. Its scope key is the chat's id.
     */
    CHAT(1, "Chat"),
    /**
     * Reserved for memory about a document (e.g. a feed or pipeline). Its scope key is the document's UUID.
     */
    DOC(2, "Document");

    public static final PrimitiveValueConverter<AiMemoryScope> PRIMITIVE_VALUE_CONVERTER =
            PrimitiveValueConverter.create(AiMemoryScope.class, AiMemoryScope.values());

    private final byte primitiveValue;
    private final String displayValue;

    AiMemoryScope(final int primitiveValue, final String displayValue) {
        this.primitiveValue = (byte) primitiveValue;
        this.displayValue = displayValue;
    }

    @Override
    public byte getPrimitiveValue() {
        return primitiveValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
