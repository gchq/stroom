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

package stroom.ai.impl;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/// Standing instructions (AGENTS docs) the agent suggests from a user's own Stroom AI chats: how far back they are
/// read, and how often a choice must be made the same way before it is suggested as a rule.
@JsonPropertyOrder(alphabetic = true)
public class AiInstructionSuggestionsConfig extends AbstractConfig implements IsStroomConfig {

    static final StroomDuration DEFAULT_EVIDENCE_WINDOW = StroomDuration.ofDays(90);
    static final int DEFAULT_MIN_CHATS = 2;

    private final StroomDuration evidenceWindow;
    private final int minChats;

    public AiInstructionSuggestionsConfig() {
        evidenceWindow = DEFAULT_EVIDENCE_WINDOW;
        minChats = DEFAULT_MIN_CHATS;
    }

    @JsonCreator
    public AiInstructionSuggestionsConfig(@JsonProperty("evidenceWindow") final StroomDuration evidenceWindow,
                                          @JsonProperty("minChats") final Integer minChats) {
        this.evidenceWindow = Objects.requireNonNullElse(evidenceWindow, DEFAULT_EVIDENCE_WINDOW);
        this.minChats = Math.max(1, Objects.requireNonNullElse(minChats, DEFAULT_MIN_CHATS));
    }

    /// @return How far back the user's chats are read.
    @JsonPropertyDescription("How far back a user's Stroom AI chats are read when the agent suggests standing "
                             + "instructions (AGENTS docs) from them. Chats deleted before then (chatRetention) "
                             + "are not read.")
    @JsonProperty("evidenceWindow")
    public StroomDuration getEvidenceWindow() {
        return evidenceWindow;
    }

    /// @return How many chats must make a choice the same way before it is suggested as a rule.
    @JsonPropertyDescription("How many of a user's chats must make a choice the same way (and none another way) "
                             + "before the agent suggests it as a standing instruction.")
    @JsonProperty("minChats")
    public int getMinChats() {
        return minChats;
    }

    @Override
    public String toString() {
        return "AiInstructionSuggestionsConfig{" +
               "evidenceWindow=" + evidenceWindow +
               ", minChats=" + minChats +
               '}';
    }
}
