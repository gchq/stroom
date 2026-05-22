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
 * Shared DTO representing an attachment associated with an AI chat.
 * Attachments hold downloaded table data and have a lifecycle status
 * (PENDING → DOWNLOADING → READY → ERROR).
 * <p>
 * The {@code dataMarkdown} field is intentionally excluded from JSON serialisation
 * to the client — it can be very large and is only used server-side for LLM processing.
 */
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class AiChatAttachment {

    @JsonProperty
    private final int id;
    @JsonProperty
    private final int chatId;
    @JsonProperty
    private final long createTimeMs;
    @JsonProperty
    private final long updateTimeMs;
    @JsonProperty
    private final AiAttachmentStatus status;
    @JsonProperty
    private final AiAttachmentType attachmentType;
    @JsonProperty
    private final String description;
    @JsonProperty
    private final Integer rowCount;
    @JsonProperty
    private final String errorMessage;

    @JsonCreator
    public AiChatAttachment(@JsonProperty("id") final int id,
                            @JsonProperty("chatId") final int chatId,
                            @JsonProperty("createTimeMs") final long createTimeMs,
                            @JsonProperty("updateTimeMs") final long updateTimeMs,
                            @JsonProperty("status") final AiAttachmentStatus status,
                            @JsonProperty("attachmentType") final AiAttachmentType attachmentType,
                            @JsonProperty("description") final String description,
                            @JsonProperty("rowCount") final Integer rowCount,
                            @JsonProperty("errorMessage") final String errorMessage) {
        this.id = id;
        this.chatId = chatId;
        this.createTimeMs = createTimeMs;
        this.updateTimeMs = updateTimeMs;
        this.status = status;
        this.attachmentType = attachmentType;
        this.description = description;
        this.rowCount = rowCount;
        this.errorMessage = errorMessage;
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

    public long getUpdateTimeMs() {
        return updateTimeMs;
    }

    public AiAttachmentStatus getStatus() {
        return status;
    }

    public AiAttachmentType getAttachmentType() {
        return attachmentType;
    }

    public String getDescription() {
        return description;
    }

    public Integer getRowCount() {
        return rowCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AiChatAttachment that = (AiChatAttachment) o;
        return id == that.id
               && chatId == that.chatId
               && createTimeMs == that.createTimeMs
               && updateTimeMs == that.updateTimeMs
               && status == that.status
               && attachmentType == that.attachmentType
               && Objects.equals(description, that.description)
               && Objects.equals(rowCount, that.rowCount)
               && Objects.equals(errorMessage, that.errorMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, chatId, createTimeMs, updateTimeMs, status,
                attachmentType, description, rowCount, errorMessage);
    }

    @Override
    public String toString() {
        return "AiChatAttachment{" +
               "id=" + id +
               ", chatId=" + chatId +
               ", status=" + status +
               ", attachmentType=" + attachmentType +
               ", description='" + description + '\'' +
               ", rowCount=" + rowCount +
               '}';
    }
}
