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

import java.util.Map;
import java.util.Objects;

/**
 * Answers a form an MCP tool asked the user to fill in (MCP elicitation).
 */
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class AnswerAiElicitationRequest {

    @JsonProperty
    private final AiChat aiChat;
    @JsonProperty
    private final int messageId;
    @JsonProperty
    private final String action;
    @JsonProperty
    private final Map<String, String> answer;

    /**
     * @param aiChat    The chat.
     * @param messageId The ELICITATION message.
     * @param action    ACCEPT to send the answer, DECLINE to refuse, or CANCEL to not answer.
     * @param answer    For ACCEPT, each field's value as text; a choice of several as a JSON list.
     */
    @JsonCreator
    public AnswerAiElicitationRequest(@JsonProperty("aiChat") final AiChat aiChat,
                                      @JsonProperty("messageId") final Integer messageId,
                                      @JsonProperty("action") final String action,
                                      @JsonProperty("answer") final Map<String, String> answer) {
        this.aiChat = aiChat;
        this.messageId = Objects.requireNonNullElse(messageId, 0);
        this.action = action;
        this.answer = answer;
    }

    /**
     * @return The chat.
     */
    public AiChat getAiChat() {
        return aiChat;
    }

    /**
     * @return The ELICITATION message.
     */
    public int getMessageId() {
        return messageId;
    }

    /**
     * @return ACCEPT to send the answer, DECLINE to refuse, or CANCEL to not answer.
     */
    public String getAction() {
        return action;
    }

    /**
     * @return For ACCEPT, each field's value as text; a choice of several as a JSON list.
     */
    public Map<String, String> getAnswer() {
        return answer;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AnswerAiElicitationRequest that = (AnswerAiElicitationRequest) o;
        return Objects.equals(aiChat, that.aiChat)
               && messageId == that.messageId
               && Objects.equals(action, that.action)
               && Objects.equals(answer, that.answer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(aiChat, messageId, action, answer);
    }
}
