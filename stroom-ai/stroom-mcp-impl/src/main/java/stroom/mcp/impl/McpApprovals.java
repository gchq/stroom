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

package stroom.mcp.impl;

import stroom.ai.impl.AiConfig;
import stroom.ai.impl.McpEndpointConfig;
import stroom.util.shared.NullSafe;

import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/// The user's approval of a tool call, made over MCP, that changes something in Stroom. Every such call asks: a
/// client's model is not Stroom's, and Stroom can't tell what it was asked to do.
///
/// A client that can show forms (MCP elicitation) shows its user a yes or no form. Any other client is given an
/// approval id for the call: it shows its user what the call will do, and, if they agree, calls [#APPROVE_TOOL]
/// with the id, which makes the call as it was first made. An id can be used once, only in its own session, and
/// only for a while.
@Singleton
class McpApprovals {

    /// The tool a client without forms calls, with an approval id, to make a call its user has approved.
    static final String APPROVE_TOOL = "approve_call";
    /// Its argument.
    static final String APPROVAL_ID_ARG = "approval_id";
    /// The most characters of a call's arguments a form or approval request shows.
    static final int MAX_SHOWN_ARGUMENTS = 1_500;

    private final McpForms forms;
    private final Provider<AiConfig> aiConfigProvider;

    @Inject
    McpApprovals(final McpForms forms, final Provider<AiConfig> aiConfigProvider) {
        this.forms = forms;
        this.aiConfigProvider = aiConfigProvider;
    }

    /// Asks the user to approve a call that changes something.
    ///
    /// @param session   The session the call is made in.
    /// @param exchange  The request.
    /// @param toolName  The tool.
    /// @param arguments The call's arguments, as JSON.
    /// @return Empty if the user approved it, so it may be made; otherwise what the client is told instead.
    Optional<String> approve(final McpSession session,
                             final McpAsyncServerExchange exchange,
                             final String toolName,
                             final String arguments) {
        if (session.canShowForms()) {
            final ElicitResult result = forms.ask(session, exchange, formMessage(session, toolName, arguments),
                    McpForms.NO_FIELDS);
            return result.action() == ElicitResult.Action.ACCEPT
                    ? Optional.empty()
                    : Optional.of(result.action() == ElicitResult.Action.DECLINE
                            ? "Not run: the user did not allow this call. Don't try it again the same way: ask them "
                              + "what they want instead."
                            : "Not run: the user was asked to allow this call, as it changes something in Stroom, "
                              + "but did not answer.");
        }
        final String approvalId = UUID.randomUUID().toString();
        final Duration timeout = config().getApprovalTimeout().getDuration();
        session.pendingCalls().values().removeIf(pending -> pending.expiresMs() < System.currentTimeMillis());
        session.pendingCalls().put(approvalId, new PendingCall(approvalId, toolName, arguments,
                System.currentTimeMillis() + timeout.toMillis()));
        return Optional.of("Not run yet: this call changes something in Stroom, so the user must approve it first. "
                           + "Show the user what it will do: " + toolName + " with " + shown(arguments) + ". "
                           + "Only if they agree, call " + APPROVE_TOOL + " with " + APPROVAL_ID_ARG + "='"
                           + approvalId + "', which makes this call as it was made. Never approve it yourself. "
                           + "The approval id can be used once, within " + minutes(timeout) + ".");
    }

    /// @param session    The session.
    /// @param approvalId An approval id given in the session.
    /// @return The call it is for, if it is still waiting: it is then no longer waiting.
    Optional<PendingCall> take(final McpSession session, final String approvalId) {
        if (approvalId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(session.pendingCalls().remove(approvalId.strip()))
                .filter(pending -> pending.expiresMs() >= System.currentTimeMillis());
    }

    private static String formMessage(final McpSession session, final String toolName, final String arguments) {
        return session.clientName() + " wants to use Stroom's " + toolName + " tool, which changes something in "
               + "Stroom, as you, with " + shown(arguments) + ". Allow it?";
    }

    private static String shown(final String arguments) {
        final String text = NullSafe.nonBlankStringElse(arguments, "no arguments").strip();
        return text.length() > MAX_SHOWN_ARGUMENTS
                ? text.substring(0, MAX_SHOWN_ARGUMENTS) + "…"
                : text;
    }

    private static String minutes(final Duration duration) {
        final long minutes = Math.max(1, duration.toMinutes());
        return minutes + (minutes == 1
                ? " minute"
                : " minutes");
    }

    private McpEndpointConfig config() {
        return NullSafe.getOrElse(aiConfigProvider.get(), AiConfig::getMcpEndpoint, new McpEndpointConfig());
    }


    // --------------------------------------------------------------------------------


    /// A call waiting for the user's approval.
    ///
    /// @param approvalId The id the client passes back once the user agrees.
    /// @param toolName   The tool.
    /// @param arguments  The call's arguments, as JSON.
    /// @param expiresMs  When the id can no longer be used.
    record PendingCall(String approvalId, String toolName, String arguments, long expiresMs) {

    }
}
