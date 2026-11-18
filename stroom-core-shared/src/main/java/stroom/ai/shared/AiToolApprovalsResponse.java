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
 * The decisions the user has made about tools in a chat, and whether 'approve everything' is on for it.
 */
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class AiToolApprovalsResponse {

    @JsonProperty
    private final List<AiToolApproval> approvals;
    @JsonProperty
    private final boolean approveEverything;
    @JsonProperty
    private final boolean approveEverythingAllowed;

    /**
     * @param approvals                The decisions, by server then tool.
     * @param approveEverything        True if 'approve everything' is on for the chat.
     * @param approveEverythingAllowed True if an administrator allows 'approve everything'.
     */
    @JsonCreator
    public AiToolApprovalsResponse(@JsonProperty("approvals") final List<AiToolApproval> approvals,
                                   @JsonProperty("approveEverything") final Boolean approveEverything,
                                   @JsonProperty("approveEverythingAllowed") final Boolean approveEverythingAllowed) {
        this.approvals = approvals;
        this.approveEverything = Objects.requireNonNullElse(approveEverything, false);
        this.approveEverythingAllowed = Objects.requireNonNullElse(approveEverythingAllowed, false);
    }

    /**
     * @return The decisions, by server then tool.
     */
    public List<AiToolApproval> getApprovals() {
        return approvals;
    }

    /**
     * @return True if 'approve everything' is on for the chat.
     */
    public boolean isApproveEverything() {
        return approveEverything;
    }

    /**
     * @return True if an administrator allows 'approve everything'.
     */
    public boolean isApproveEverythingAllowed() {
        return approveEverythingAllowed;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AiToolApprovalsResponse that = (AiToolApprovalsResponse) o;
        return Objects.equals(approvals, that.approvals)
               && approveEverything == that.approveEverything
               && approveEverythingAllowed == that.approveEverythingAllowed;
    }

    @Override
    public int hashCode() {
        return Objects.hash(approvals, approveEverything, approveEverythingAllowed);
    }
}
