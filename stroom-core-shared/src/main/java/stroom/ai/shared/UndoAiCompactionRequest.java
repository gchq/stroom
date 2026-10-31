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
 * Asks for a chat's latest compaction to be undone, so the model sees the turns it covered in full again (or as
 * an earlier compaction left them).
 */
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class UndoAiCompactionRequest {

    @JsonProperty
    private final AiChat aiChat;
    @JsonProperty
    private final int messageId;

    /**
     * @param aiChat    The chat.
     * @param messageId The id of the compaction (a COMPACTION_SUMMARY message) to undo.
     */
    @JsonCreator
    public UndoAiCompactionRequest(@JsonProperty("aiChat") final AiChat aiChat,
                                   @JsonProperty("messageId") final Integer messageId) {
        this.aiChat = aiChat;
        this.messageId = Objects.requireNonNullElse(messageId, 0);
    }

    public AiChat getAiChat() {
        return aiChat;
    }

    public int getMessageId() {
        return messageId;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final UndoAiCompactionRequest that = (UndoAiCompactionRequest) o;
        return messageId == that.messageId && Objects.equals(aiChat, that.aiChat);
    }

    @Override
    public int hashCode() {
        return Objects.hash(aiChat, messageId);
    }
}
