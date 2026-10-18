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
 * Asks for one of the user's earlier questions to be edited (or asked again unchanged), which removes it and
 * everything after it from the chat and answers the new text, with the user's active tab as it is now.
 */
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class EditAiMessageRequest {

    @JsonProperty
    private final AiChat aiChat;
    @JsonProperty
    private final AskStroomAiConfig config;
    @JsonProperty
    private final int messageId;
    @JsonProperty
    private final String message;
    @JsonProperty
    private final AiTabSnapshot tabSnapshot;

    /**
     * @param aiChat    The chat.
     * @param config    The config to answer with.
     * @param messageId The id of the question (a USER_MESSAGE) to edit.
     * @param message     The new text, or null to ask the question again unchanged.
     * @param tabSnapshot What the user sees in their active tab now, in full, or null if no tab is shared.
     */
    @JsonCreator
    public EditAiMessageRequest(@JsonProperty("aiChat") final AiChat aiChat,
                                @JsonProperty("config") final AskStroomAiConfig config,
                                @JsonProperty("messageId") final Integer messageId,
                                @JsonProperty("message") final String message,
                                @JsonProperty("tabSnapshot") final AiTabSnapshot tabSnapshot) {
        this.aiChat = aiChat;
        this.config = config;
        this.messageId = Objects.requireNonNullElse(messageId, 0);
        this.message = message;
        this.tabSnapshot = tabSnapshot;
    }

    public AiChat getAiChat() {
        return aiChat;
    }

    public AskStroomAiConfig getConfig() {
        return config;
    }

    public int getMessageId() {
        return messageId;
    }

    /**
     * @return The new text, or null to ask the question again unchanged.
     */
    public String getMessage() {
        return message;
    }

    /**
     * @return What the user sees in their active tab now, or null if no tab is shared.
     */
    public AiTabSnapshot getTabSnapshot() {
        return tabSnapshot;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final EditAiMessageRequest that = (EditAiMessageRequest) o;
        return messageId == that.messageId
               && Objects.equals(aiChat, that.aiChat)
               && Objects.equals(config, that.config)
               && Objects.equals(message, that.message)
               && Objects.equals(tabSnapshot, that.tabSnapshot);
    }

    @Override
    public int hashCode() {
        return Objects.hash(aiChat, config, messageId, message, tabSnapshot);
    }
}
