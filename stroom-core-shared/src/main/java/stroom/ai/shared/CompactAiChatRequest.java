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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * Asks for a chat to be compacted now: earlier turns are replaced, in what the model sees, by short digests and a
 * summary written by the model. Nothing is deleted.
 */
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class CompactAiChatRequest {

    @JsonProperty
    private final AiChat aiChat;
    @JsonProperty
    private final AskStroomAiConfig config;
    @JsonProperty
    private final String instructions;

    /**
     * @param aiChat       The chat.
     * @param config       The config, which gives the model to write the summary with.
     * @param instructions What the summary should focus on, or null.
     */
    @JsonCreator
    public CompactAiChatRequest(@JsonProperty("aiChat") final AiChat aiChat,
                                @JsonProperty("config") final AskStroomAiConfig config,
                                @JsonProperty("instructions") final String instructions) {
        this.aiChat = aiChat;
        this.config = config;
        this.instructions = instructions;
    }

    public AiChat getAiChat() {
        return aiChat;
    }

    public AskStroomAiConfig getConfig() {
        return config;
    }

    /**
     * @return What the summary should focus on, or null.
     */
    public String getInstructions() {
        return instructions;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final CompactAiChatRequest that = (CompactAiChatRequest) o;
        return Objects.equals(aiChat, that.aiChat)
               && Objects.equals(config, that.config)
               && Objects.equals(instructions, that.instructions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(aiChat, config, instructions);
    }
}
