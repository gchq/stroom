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
 * A decision about a tool that lasts for a chat.
 */
public enum AiToolApprovalDecision implements HasPrimitiveValue {
    /**
     * Run the tool without asking.
     */
    ALLOW(0),
    /**
     * Do not run the tool, without asking.
     */
    DENY(1);

    public static final PrimitiveValueConverter<AiToolApprovalDecision> PRIMITIVE_VALUE_CONVERTER =
            PrimitiveValueConverter.create(AiToolApprovalDecision.class, AiToolApprovalDecision.values());

    private final byte primitiveValue;

    AiToolApprovalDecision(final int primitiveValue) {
        this.primitiveValue = (byte) primitiveValue;
    }

    @Override
    public byte getPrimitiveValue() {
        return primitiveValue;
    }
}
