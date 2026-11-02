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

/// How the "Ask Stroom AI" agent works in a chat.
public enum AiAgentMode implements HasPrimitiveValue, HasDisplayValue {
    /// Answers, and does what is asked, with all the tools it is given.
    AGENT(0, "Agent"),
    /// Answers questions, with only the tools that cannot change anything.
    ASK(1, "Ask"),
    /// Works out a plan for what is asked, with only the tools that cannot change anything, without carrying it
    /// out.
    PLAN(2, "Plan");

    public static final PrimitiveValueConverter<AiAgentMode> PRIMITIVE_VALUE_CONVERTER =
            PrimitiveValueConverter.create(AiAgentMode.class, AiAgentMode.values());

    private final byte primitiveValue;
    private final String displayValue;

    AiAgentMode(final int primitiveValue, final String displayValue) {
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

    /// @return True if only the tools that cannot change anything may be used.
    public boolean isReadOnly() {
        return this != AGENT;
    }
}
