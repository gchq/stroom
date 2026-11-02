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
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * An "Ask Stroom AI" agent run: the work of answering one question in a chat, which carries on in the
 * background while the user is away.
 */
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class AiChatRun {

    @JsonProperty
    private final int id;
    @JsonProperty
    private final int chatId;
    @JsonProperty
    private final AiChatRunStatus status;
    @JsonProperty
    private final AiChatRunStopReason stopReason;
    @JsonProperty
    private final Long startTimeMs;
    @JsonProperty
    private final Long endTimeMs;
    @JsonProperty
    private final boolean unattended;
    @JsonProperty
    private final String errorMessage;

    @JsonCreator
    public AiChatRun(@JsonProperty("id") final Integer id,
                     @JsonProperty("chatId") final Integer chatId,
                     @JsonProperty("status") final AiChatRunStatus status,
                     @JsonProperty("stopReason") final AiChatRunStopReason stopReason,
                     @JsonProperty("startTimeMs") final Long startTimeMs,
                     @JsonProperty("endTimeMs") final Long endTimeMs,
                     @JsonProperty("unattended") final Boolean unattended,
                     @JsonProperty("errorMessage") final String errorMessage) {
        this.id = Objects.requireNonNullElse(id, 0);
        this.chatId = Objects.requireNonNullElse(chatId, 0);
        this.status = status;
        this.stopReason = stopReason;
        this.startTimeMs = startTimeMs;
        this.endTimeMs = endTimeMs;
        this.unattended = Objects.requireNonNullElse(unattended, false);
        this.errorMessage = errorMessage;
    }

    public int getId() {
        return id;
    }

    public int getChatId() {
        return chatId;
    }

    public AiChatRunStatus getStatus() {
        return status;
    }

    /**
     * @return Why the run stopped or paused, or null if it has not.
     */
    public AiChatRunStopReason getStopReason() {
        return stopReason;
    }

    public Long getStartTimeMs() {
        return startTimeMs;
    }

    public Long getEndTimeMs() {
        return endTimeMs;
    }

    /**
     * @return True if the user has gone (no browser is polling), so the run may only use read-only tools.
     */
    public boolean isUnattended() {
        return unattended;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * @return True if the run has not ended.
     */
    @JsonIgnore
    public boolean isActive() {
        return status != null && status.isActive();
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AiChatRun that = (AiChatRun) o;
        return id == that.id
               && chatId == that.chatId
               && unattended == that.unattended
               && status == that.status
               && stopReason == that.stopReason
               && Objects.equals(startTimeMs, that.startTimeMs)
               && Objects.equals(endTimeMs, that.endTimeMs)
               && Objects.equals(errorMessage, that.errorMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, chatId, status, stopReason, startTimeMs, endTimeMs, unattended, errorMessage);
    }

    @Override
    public String toString() {
        return "AiChatRun{" +
               "id=" + id +
               ", chatId=" + chatId +
               ", status=" + status +
               ", stopReason=" + stopReason +
               ", unattended=" + unattended +
               '}';
    }
}
