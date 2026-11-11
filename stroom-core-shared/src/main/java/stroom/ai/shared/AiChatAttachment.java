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

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * Shared DTO representing an attachment associated with an AI chat.
 * Attachments track downloaded table data stored on the local filesystem
 * and have a lifecycle status (PENDING → DOWNLOADING → READY → ERROR).
 * <p>
 * The actual table data (markdown) is stored on the local node filesystem,
 * not in the database.
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
    private final boolean truncated;
    @JsonProperty
    private final String errorMessage;
    @JsonProperty
    private final DocRef docRef;
    @JsonProperty
    private final String docPath;

    /// An attachment that holds data of its own, e.g. a table, rather than referring to a document.
    public AiChatAttachment(final Integer id,
                            final Integer chatId,
                            final Long createTimeMs,
                            final Long updateTimeMs,
                            final AiAttachmentStatus status,
                            final AiAttachmentType attachmentType,
                            final String description,
                            final Integer rowCount,
                            final Boolean truncated,
                            final String errorMessage) {
        this(id, chatId, createTimeMs, updateTimeMs, status, attachmentType, description, rowCount, truncated,
                errorMessage, null, null);
    }

    @JsonCreator
    public AiChatAttachment(@JsonProperty("id") final Integer id,
                            @JsonProperty("chatId") final Integer chatId,
                            @JsonProperty("createTimeMs") final Long createTimeMs,
                            @JsonProperty("updateTimeMs") final Long updateTimeMs,
                            @JsonProperty("status") final AiAttachmentStatus status,
                            @JsonProperty("attachmentType") final AiAttachmentType attachmentType,
                            @JsonProperty("description") final String description,
                            @JsonProperty("rowCount") final Integer rowCount,
                            @JsonProperty("truncated") final Boolean truncated,
                            @JsonProperty("errorMessage") final String errorMessage,
                            @JsonProperty("docRef") final DocRef docRef,
                            @JsonProperty("docPath") final String docPath) {
        this.id = Objects.requireNonNullElse(id, 0);
        this.chatId = Objects.requireNonNullElse(chatId, 0);
        this.createTimeMs = Objects.requireNonNullElse(createTimeMs, 0L);
        this.updateTimeMs = Objects.requireNonNullElse(updateTimeMs, 0L);
        this.status = status;
        this.attachmentType = attachmentType;
        this.description = description;
        this.rowCount = rowCount;
        this.truncated = Objects.requireNonNullElse(truncated, false);
        this.errorMessage = errorMessage;
        this.docRef = docRef;
        this.docPath = docPath;
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

    public boolean isTruncated() {
        return truncated;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    /// @return The document a [AiAttachmentType#DOCUMENT] attachment refers to, or null for any other kind.
    public DocRef getDocRef() {
        return docRef;
    }

    /// @return The Explorer path of the folder holding the document a [AiAttachmentType#DOCUMENT] attachment
    /// refers to, as it was when attached, or null if not known.
    public String getDocPath() {
        return docPath;
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
               && truncated == that.truncated
               && status == that.status
               && attachmentType == that.attachmentType
               && Objects.equals(description, that.description)
               && Objects.equals(rowCount, that.rowCount)
               && Objects.equals(errorMessage, that.errorMessage)
               && Objects.equals(docRef, that.docRef)
               && Objects.equals(docPath, that.docPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, chatId, createTimeMs, updateTimeMs, status,
                attachmentType, description, rowCount, truncated, errorMessage, docRef, docPath);
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
               ", truncated=" + truncated +
               ", docRef=" + docRef +
               '}';
    }
}
