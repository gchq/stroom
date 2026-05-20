/*
 * Copyright 2016-2026 Crown Copyright
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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

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
    private final String message;

    @JsonCreator
    public AiChatMessage(@JsonProperty("id") final int id,
                         @JsonProperty("chatId") final int chatId,
                         @JsonProperty("createTimeMs") final long createTimeMs,
                         @JsonProperty("messageType") final AiMessageType messageType,
                         @JsonProperty("message") final String message) {
        this.id = id;
        this.chatId = chatId;
        this.createTimeMs = createTimeMs;
        this.messageType = messageType;
        this.message = message;
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

    public String getMessage() {
        return message;
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
               && Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, chatId, createTimeMs, messageType, message);
    }

    @Override
    public String toString() {
        return "AiChatMessage{" +
               "id=" + id +
               ", chatId=" + chatId +
               ", createTimeMs=" + createTimeMs +
               ", messageType=" + messageType +
               ", message='" + message + '\'' +
               '}';
    }
}
