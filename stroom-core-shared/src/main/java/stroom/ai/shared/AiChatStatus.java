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
 * Where one of a user's 'Ask Stroom AI' chats has got to, for the chat switcher and badges.
 */
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class AiChatStatus {

    @JsonProperty
    private final int chatId;
    @JsonProperty
    private final String title;
    @JsonProperty
    private final long updateTimeMs;
    @JsonProperty
    private final AiChatRunStatus runStatus;
    @JsonProperty
    private final boolean unread;
    @JsonProperty
    private final boolean approveEverything;
    @JsonProperty
    private final long lastOpenedMs;
    @JsonProperty
    private final AiAgentMode agentMode;

    /**
     * @param chatId            The chat.
     * @param title             Its title.
     * @param updateTimeMs      When it last changed.
     * @param runStatus         The status of its latest run, or null if it has never had one.
     * @param unread            True if it has messages the user has not seen.
     * @param approveEverything True if 'approve everything' is on for it.
     * @param lastOpenedMs      When the user last looked at it, or 0.
     */
    @JsonCreator
    public AiChatStatus(@JsonProperty("chatId") final Integer chatId,
                        @JsonProperty("title") final String title,
                        @JsonProperty("updateTimeMs") final Long updateTimeMs,
                        @JsonProperty("runStatus") final AiChatRunStatus runStatus,
                        @JsonProperty("unread") final Boolean unread,
                        @JsonProperty("approveEverything") final Boolean approveEverything,
                        @JsonProperty("lastOpenedMs") final Long lastOpenedMs,
                        @JsonProperty("agentMode") final AiAgentMode agentMode) {
        this.chatId = Objects.requireNonNullElse(chatId, 0);
        this.title = title;
        this.updateTimeMs = Objects.requireNonNullElse(updateTimeMs, 0L);
        this.runStatus = runStatus;
        this.unread = Objects.requireNonNullElse(unread, false);
        this.approveEverything = Objects.requireNonNullElse(approveEverything, false);
        this.lastOpenedMs = Objects.requireNonNullElse(lastOpenedMs, 0L);
        this.agentMode = Objects.requireNonNullElse(agentMode, AiAgentMode.AGENT);
    }

    public int getChatId() {
        return chatId;
    }

    public String getTitle() {
        return title;
    }

    public long getUpdateTimeMs() {
        return updateTimeMs;
    }

    public AiChatRunStatus getRunStatus() {
        return runStatus;
    }

    public boolean isUnread() {
        return unread;
    }

    public boolean isApproveEverything() {
        return approveEverything;
    }

    /**
     * @return When the user last looked at the chat, or 0 if never.
     */
    public long getLastOpenedMs() {
        return lastOpenedMs;
    }

    /**
     * @return How the agent works in the chat.
     */
    public AiAgentMode getAgentMode() {
        return agentMode;
    }

    /**
     * @return True if the chat is waiting for the user, e.g. to approve something.
     */
    @JsonIgnore
    public boolean isNeedsYou() {
        return runStatus == AiChatRunStatus.WAITING_FOR_USER;
    }

    /**
     * @return True if a run is working on, or queued for, the chat.
     */
    @JsonIgnore
    public boolean isBusy() {
        return runStatus == AiChatRunStatus.RUNNING
               || runStatus == AiChatRunStatus.QUEUED
               || runStatus == AiChatRunStatus.STOPPING;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AiChatStatus that = (AiChatStatus) o;
        return chatId == that.chatId
               && updateTimeMs == that.updateTimeMs
               && unread == that.unread
               && approveEverything == that.approveEverything
               && lastOpenedMs == that.lastOpenedMs
               && agentMode == that.agentMode
               && Objects.equals(title, that.title)
               && runStatus == that.runStatus;
    }

    @Override
    public int hashCode() {
        return Objects.hash(chatId, title, updateTimeMs, runStatus, unread, approveEverything, lastOpenedMs,
                agentMode);
    }
}
