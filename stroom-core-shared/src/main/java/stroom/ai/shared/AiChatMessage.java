/*
 * Copyright 2021 Crown Copyright
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

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class AiChatMessage {

    @JsonProperty
    private final int id;
    @JsonProperty
    private final int chatId;
    @JsonProperty
    private final long createTimeMs;
    @JsonProperty
    private final AiMessageType messageType;
    @JsonProperty
    private final Integer attachmentId;
    @JsonProperty
    private final String message;
    @JsonProperty
    private final int turnId;
    @JsonProperty
    private final Integer tokenCount;

    @JsonCreator
    public AiChatMessage(@JsonProperty("id") final Integer id,
                         @JsonProperty("chatId") final Integer chatId,
                         @JsonProperty("createTimeMs") final Long createTimeMs,
                         @JsonProperty("messageType") final AiMessageType messageType,
                         @JsonProperty("attachmentId") final Integer attachmentId,
                         @JsonProperty("message") final String message,
                         @JsonProperty("turnId") final Integer turnId,
                         @JsonProperty("tokenCount") final Integer tokenCount) {
        this.id = Objects.requireNonNullElse(id, 0);
        this.chatId = Objects.requireNonNullElse(chatId, 0);
        this.createTimeMs = Objects.requireNonNullElse(createTimeMs, 0L);
        this.messageType = messageType;
        this.attachmentId = attachmentId;
        this.message = message;
        this.turnId = Objects.requireNonNullElse(turnId, 0);
        this.tokenCount = tokenCount;
    }

    public int getId() {
        return id;
    }

    public int getChatId() {
        return chatId;
    }

    public long getCreateTimeMs() {
        return createTimeMs;
    }

    public AiMessageType getMessageType() {
        return messageType;
    }

    public Integer getAttachmentId() {
        return attachmentId;
    }

    public String getMessage() {
        return message;
    }

    /**
     * @return The id of the USER_MESSAGE that started the turn this message belongs to, or 0 if the message
     * came before any question (e.g. an attachment).
     */
    public int getTurnId() {
        return turnId;
    }

    /**
     * @return An estimate of the number of tokens in the message, or null if not known.
     */
    public Integer getTokenCount() {
        return tokenCount;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AiChatMessage that = (AiChatMessage) o;
        return id == that.id
               && chatId == that.chatId
               && createTimeMs == that.createTimeMs
               && messageType == that.messageType
               && Objects.equals(attachmentId, that.attachmentId)
               && turnId == that.turnId
               && Objects.equals(message, that.message)
               && Objects.equals(tokenCount, that.tokenCount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, chatId, createTimeMs, messageType, attachmentId, message, turnId, tokenCount);
    }

    @Override
    public String toString() {
        return "AiChatMessage{" +
               "id=" + id +
               ", chatId=" + chatId +
               ", createTimeMs=" + createTimeMs +
               ", messageType=" + messageType +
               ", attachmentId=" + attachmentId +
               ", message='" + message + '\'' +
               ", turnId=" + turnId +
               ", tokenCount=" + tokenCount +
               '}';
    }
}
