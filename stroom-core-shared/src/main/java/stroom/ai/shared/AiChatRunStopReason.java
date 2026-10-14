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
 * Why an "Ask Stroom AI" agent run stopped or paused before finishing.
 */
public enum AiChatRunStopReason implements HasPrimitiveValue, HasDisplayValue {

    USER(0, "Stopped by you"),
    SIGN_OUT(1, "Stopped because you signed out"),
    SESSION_EXPIRED(2, "Paused because your session ended"),
    TOKEN_EXPIRED(3, "Paused because your sign-in expired"),
    RUN_LIMIT(4, "Paused because it reached the time limit"),
    NODE_LOST(5, "Interrupted because the Stroom node running it stopped"),
    TERMINATED(6, "Stopped by an administrator"),
    ACCOUNT_DISABLED(7, "Stopped because the account was disabled"),
    EDITED(8, "Stopped because an earlier message was edited"),
    NEEDS_APPROVAL(9, "Waiting for your approval");

    public static final PrimitiveValueConverter<AiChatRunStopReason> PRIMITIVE_VALUE_CONVERTER =
            PrimitiveValueConverter.create(AiChatRunStopReason.class, AiChatRunStopReason.values());

    private final byte primitiveValue;
    private final String displayValue;

    AiChatRunStopReason(final int primitiveValue, final String displayValue) {
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
