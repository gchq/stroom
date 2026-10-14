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

import java.util.List;
import java.util.Objects;

/**
 * The user's answer to something the agent asked their approval for (an APPROVAL_REQUEST message).
 */
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class AnswerAiApprovalRequest {

    @JsonProperty
    private final AiChat aiChat;
    @JsonProperty
    private final int messageId;
    @JsonProperty
    private final boolean approved;
    @JsonProperty
    private final AiApprovalScope scope;
    @JsonProperty
    private final String reason;
    @JsonProperty
    private final List<String> callIds;

    /**
     * @param aiChat    The chat.
     * @param messageId The id of the APPROVAL_REQUEST message being answered.
     * @param approved  True to approve, false to reject.
     * @param scope     For tool calls, how far an approval reaches (default just this call).
     * @param reason    For tool calls, why they are rejected, which the model is told. May be null.
     * @param callIds   For tool calls, which of the request's calls are answered, or null for all of them.
     */
    @JsonCreator
    public AnswerAiApprovalRequest(@JsonProperty("aiChat") final AiChat aiChat,
                                   @JsonProperty("messageId") final Integer messageId,
                                   @JsonProperty("approved") final Boolean approved,
                                   @JsonProperty("scope") final AiApprovalScope scope,
                                   @JsonProperty("reason") final String reason,
                                   @JsonProperty("callIds") final List<String> callIds) {
        this.aiChat = aiChat;
        this.messageId = Objects.requireNonNullElse(messageId, 0);
        this.approved = Objects.requireNonNullElse(approved, false);
        this.scope = Objects.requireNonNullElse(scope, AiApprovalScope.ONCE);
        this.reason = reason;
        this.callIds = callIds;
    }

    /**
     * @param aiChat    The chat.
     * @param messageId The id of the APPROVAL_REQUEST message being answered.
     * @param approved  True to approve all of it, false to reject all of it.
     */
    public AnswerAiApprovalRequest(final AiChat aiChat, final Integer messageId, final Boolean approved) {
        this(aiChat, messageId, approved, AiApprovalScope.ONCE, null, null);
    }

    public AiChat getAiChat() {
        return aiChat;
    }

    public int getMessageId() {
        return messageId;
    }

    public boolean isApproved() {
        return approved;
    }

    public AiApprovalScope getScope() {
        return scope;
    }

    public String getReason() {
        return reason;
    }

    public List<String> getCallIds() {
        return callIds;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AnswerAiApprovalRequest that = (AnswerAiApprovalRequest) o;
        return messageId == that.messageId
               && approved == that.approved
               && Objects.equals(aiChat, that.aiChat)
               && scope == that.scope
               && Objects.equals(reason, that.reason)
               && Objects.equals(callIds, that.callIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(aiChat, messageId, approved, scope, reason, callIds);
    }
}
