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

package stroom.ai.impl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

/// Tool calls waiting for the user's approval, as stored (as JSON) in an APPROVAL_REQUEST message. Only the agent
/// loop makes these, never the model. All the calls of one model reply that need approval are on one request.
///
/// @param kind                Always {@link #KIND}, so requests can be told apart.
/// @param status              Where it has got to.
/// @param reason              Why the calls need approval.
/// @param calls               The calls.
/// @param allowForChat        True if the calls may be allowed for the rest of the chat.
/// @param allowServerReadOnly True if the server's read-only tools may be allowed for the rest of the chat.
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public record ToolApprovalRequest(@JsonProperty("kind") String kind,
                                  @JsonProperty("status") Status status,
                                  @JsonProperty("reason") String reason,
                                  @JsonProperty("calls") List<PendingCall> calls,
                                  @JsonProperty("allowForChat") boolean allowForChat,
                                  @JsonProperty("allowServerReadOnly") boolean allowServerReadOnly) {

    public static final String KIND = "TOOL";

    @JsonCreator
    public ToolApprovalRequest {
        calls = calls == null
                ? List.of()
                : List.copyOf(calls);
    }

    /// @return A copy with the status and calls changed.
    public ToolApprovalRequest with(final Status status, final List<PendingCall> calls) {
        return new ToolApprovalRequest(kind, status, reason, calls, allowForChat, allowServerReadOnly);
    }

    /// @return True once every call has been decided.
    public boolean allDecided() {
        return calls.stream().allMatch(call -> call.decision() != null);
    }

    /// Where a request has got to.
    public enum Status {
        /// Waiting for the user.
        PENDING,
        /// Every call has been decided.
        ANSWERED,
        /// Not answered in time; no call was made.
        EXPIRED
    }

    /// What was decided about a call.
    public enum Decision {
        ALLOWED,
        DENIED,
        EXPIRED
    }

    /// A tool call waiting for approval.
    ///
    /// @param callId     The model's id for the call.
    /// @param tool       The tool's name, as the model saw it.
    /// @param serverKey  The tool's server, or null for a built-in tool.
    /// @param serverTool The tool's own name on its server, or null.
    /// @param arguments  The arguments, in full.
    /// @param risks      Why the call might matter, e.g. "untrusted server", "destructive".
    /// @param toolHash   The tool's hash (see [McpToolInfo]), or null.
    /// @param serverHash The server's hash (see [McpToolInfo]), or null.
    /// @param decision   What was decided, or null if not yet.
    /// @param note       Why it was denied, given by the user, or null.
    @JsonInclude(Include.NON_NULL)
    @JsonPropertyOrder(alphabetic = true)
    public record PendingCall(@JsonProperty("callId") String callId,
                              @JsonProperty("tool") String tool,
                              @JsonProperty("serverKey") String serverKey,
                              @JsonProperty("serverTool") String serverTool,
                              @JsonProperty("arguments") String arguments,
                              @JsonProperty("risks") List<String> risks,
                              @JsonProperty("toolHash") String toolHash,
                              @JsonProperty("serverHash") String serverHash,
                              @JsonProperty("decision") Decision decision,
                              @JsonProperty("note") String note) {

        @JsonCreator
        public PendingCall {
            risks = risks == null
                    ? List.of()
                    : List.copyOf(risks);
        }

        /// @return A copy with the decision made.
        public PendingCall decided(final Decision decision, final String note) {
            return new PendingCall(callId, tool, serverKey, serverTool, arguments, risks, toolHash, serverHash,
                    decision, note);
        }
    }
}
