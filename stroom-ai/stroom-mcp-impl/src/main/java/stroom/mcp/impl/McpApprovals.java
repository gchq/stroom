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
import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;

import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/// The user's approval of a tool call, made over MCP, that changes something in Stroom. Every such call asks: a
/// client's model is not Stroom's, and Stroom can't tell what it was asked to do.
///
/// A client that can show forms (MCP elicitation) shows its user a yes or no form. Any other client is given an
/// approval id for the call: it shows its user what the call will do, and, if they agree, calls [#APPROVE_TOOL]
/// with the id, which makes the call as it was first made. An id can be used once, only in its own session, and
/// only for a while.
///
/// What is shown leads with the arguments that say what the call acts on (a target's uuid or name, the build, the
/// tool), then the arguments, cut at [#MAX_SHOWN_ARGUMENTS] characters, saying so when they are. A call whose
/// arguments are cut gets no approval id: the user couldn't see all of what they would approve.
@Singleton
class McpApprovals {

    /// The tool a client without forms calls, with an approval id, to make a call its user has approved.
    static final String APPROVE_TOOL = "approve_call";
    /// Its argument.
    static final String APPROVAL_ID_ARG = "approval_id";
    /// The most characters of a call's arguments a form or approval request shows.
    static final int MAX_SHOWN_ARGUMENTS = 1_500;
    /// The most characters of one argument shown among the arguments that say what the call acts on.
    static final int MAX_SHOWN_KEY_VALUE = 200;
    // The arguments that say what a call acts on, shown first: those named so, or ending so (e.g. pipeline_uuid).
    private static final Set<String> KEY_NAMES = Set.of("build", "tool", "target", "feed", "pipeline", "index");
    private static final List<String> KEY_ENDINGS = List.of("uuid", "name");

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
        if (isCut(arguments)) {
            return Optional.of("Not run: this call's arguments are " + NullSafe.trim(arguments).length()
                               + " characters long, more than the " + MAX_SHOWN_ARGUMENTS + " an approval request "
                               + "shows, so the user couldn't see all of what they would approve. Make the change "
                               + "in smaller calls, or use an MCP client that can show forms.");
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

    /// @param arguments A call's arguments, as JSON.
    /// @return How they are shown to the user: the arguments that say what the call acts on, then all of them,
    /// cut at [#MAX_SHOWN_ARGUMENTS] characters, saying so if they are.
    static String shown(final String arguments) {
        final String text = NullSafe.nonBlankStringElse(arguments, "no arguments").strip();
        final String keys = keyArguments(text);
        final String prefix = keys.isEmpty()
                ? ""
                : keys + "; all arguments: ";
        return isCut(text)
                ? prefix + text.substring(0, MAX_SHOWN_ARGUMENTS) + "… (cut: only the first " + MAX_SHOWN_ARGUMENTS
                  + " of their " + text.length() + " characters are shown)"
                : prefix + text;
    }

    private static boolean isCut(final String arguments) {
        return NullSafe.trim(arguments).length() > MAX_SHOWN_ARGUMENTS;
    }

    // The arguments that say what the call acts on, e.g. "pipeline_uuid=..., build=...", or "" if none.
    private static String keyArguments(final String arguments) {
        final Map<?, ?> parsed;
        try {
            parsed = JsonUtil.readValue(arguments, Map.class);
        } catch (final RuntimeException e) {
            return "";
        }
        final List<String> keys = new ArrayList<>();
        NullSafe.map(parsed).forEach((name, value) -> {
            if (isKeyName(String.valueOf(name)) && isScalar(value)) {
                final String shownValue = String.valueOf(value);
                keys.add(name + "=" + (shownValue.length() > MAX_SHOWN_KEY_VALUE
                        ? shownValue.substring(0, MAX_SHOWN_KEY_VALUE) + "…"
                        : shownValue));
            }
        });
        return String.join(", ", keys);
    }

    private static boolean isKeyName(final String name) {
        final String lower = name.toLowerCase(Locale.ROOT);
        return KEY_NAMES.contains(lower) || KEY_ENDINGS.stream().anyMatch(lower::endsWith);
    }

    private static boolean isScalar(final Object value) {
        return value instanceof String || value instanceof Number || value instanceof Boolean;
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
