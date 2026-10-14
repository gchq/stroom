/*
 * Copyright 2025 Crown Copyright
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
 * Classifies the source type of an {@link AiChatAttachment}. Attachments are also the index of the files in a
 * chat's workspace, so a type is either table data the user attached to the chat, or a file made while
 * answering.
 */
public enum AiAttachmentType implements HasPrimitiveValue {
    DASHBOARD(0),
    QUERY(1),
    GENERAL(2),
    /**
     * The full output of a tool call, too large to give the model in full.
     */
    TOOL_OUTPUT(3),
    /**
     * Any other workspace file, e.g. the profile of an attached table, or a file written by a data tool.
     */
    WORKSPACE_FILE(4);

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

    /**
     * @return True if this is table data the user attached to the chat (from a dashboard, query or elsewhere),
     * rather than a file made while answering.
     */
    public boolean isTableContext() {
        return this == DASHBOARD || this == QUERY || this == GENERAL;
    }
}
