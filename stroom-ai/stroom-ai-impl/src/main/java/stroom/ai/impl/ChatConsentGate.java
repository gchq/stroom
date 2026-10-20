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

import stroom.ai.api.AiService;
import stroom.ai.impl.memory.MemoryChange;
import stroom.ai.impl.tools.BuiltInTools;
import stroom.ai.impl.tools.ChatUserQuestions;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.ToolSessionException;
import stroom.ai.impl.tools.UserQuestions;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiMessageType;
import stroom.util.json.JsonUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.Objects;
import java.util.Optional;

/// The [ToolConsentGate] of a Stroom AI chat, which asks as the chat always has:
///
/// * a tool call is decided by [ToolConsent] (its policies, the approvals held for the chat, and the rules of a
///   run the user is away from);
/// * calls that need approval are put on one approval card (an APPROVAL_REQUEST message of kind TOOL), which the
///   user answers through [ToolApprovalService], and the run stops until then;
/// * a change to the user's memory needs approval once the run has read anything that could have told it to make
///   the change, or while the user is away, and is put on an approval card (an APPROVAL_REQUEST message of kind
///   MEMORY), which the user answers through `AiMemoryService`;
/// * questions are asked as a form in the chat ([ChatUserQuestions]), only while the user is watching.
public class ChatConsentGate implements ToolConsentGate {

    static final String AWAY_REASON = "You were away when the agent made this change.";
    static final String READ_DATA_REASON = "The agent had read tool output or data in this chat, which could have "
                                           + "told it to make this change.";

    private final Provider<ToolConsent> toolConsentProvider;
    private final Provider<AiService> aiServiceProvider;
    private final Provider<AiChatRunManager> runManagerProvider;
    private final Provider<McpElicitations> elicitationsProvider;

    /// @param toolConsentProvider  Decides whether a tool call runs, asks or is not run.
    /// @param aiServiceProvider    Stores the chat's approval cards.
    /// @param runManagerProvider   Knows whether the user is watching a chat, and what its run has read.
    /// @param elicitationsProvider Shows forms in the chat and waits for the answers.
    @Inject
    public ChatConsentGate(final Provider<ToolConsent> toolConsentProvider,
                           final Provider<AiService> aiServiceProvider,
                           final Provider<AiChatRunManager> runManagerProvider,
                           final Provider<McpElicitations> elicitationsProvider) {
        this.toolConsentProvider = Objects.requireNonNull(toolConsentProvider, "toolConsentProvider");
        this.aiServiceProvider = Objects.requireNonNull(aiServiceProvider, "aiServiceProvider");
        this.runManagerProvider = Objects.requireNonNull(runManagerProvider, "runManagerProvider");
        this.elicitationsProvider = Objects.requireNonNull(elicitationsProvider, "elicitationsProvider");
    }

    /// @param session A chat's session.
    /// @return True while the user is away from the chat (an unattended run).
    @Override
    public boolean isUserAway(final ToolSession session) {
        return runManagerProvider.get().isUnattended(chatId(session));
    }

    /// Decides with [ToolConsent#decide], as the chat always has.
    ///
    /// @param session      A chat's session.
    /// @param toolName     The tool, as the model named it.
    /// @param builtInTools Stroom's own tools.
    /// @param toolset      The MCP tools offered in the chat.
    /// @param userAway     True if the user is away.
    /// @return What to do about the call.
    @Override
    public ToolConsent.Verdict checkCall(final ToolSession session,
                                         final String toolName,
                                         final BuiltInTools builtInTools,
                                         final McpToolset toolset,
                                         final boolean userAway) {
        return toolConsentProvider.get().decide(chatId(session), toolName, builtInTools, toolset, userAway);
    }

    /// Puts the calls on an approval card in the chat (an APPROVAL_REQUEST message), for the user to answer
    /// through [ToolApprovalService].
    ///
    /// @param session A chat's session.
    /// @param request The calls waiting for approval.
    /// @return Pending, with the card's message id.
    @Override
    public ApprovalOutcome requestCallApproval(final ToolSession session, final ToolApprovalRequest request) {
        final AiChatMessage card = aiServiceProvider.get().storeMessage(chatId(session),
                AiMessageType.APPROVAL_REQUEST,
                JsonUtil.writeValueAsString(request, false));
        return pending(card);
    }

    /// A change to the user's memory needs approval once the run has read anything that could have told it to
    /// make the change (tool output, workspace files or Stroom data), or while the user is away.
    ///
    /// @param session A chat's session.
    /// @return Why the change needs approval, or empty if it doesn't.
    @Override
    public Optional<String> lastingChangeApprovalReason(final ToolSession session) {
        final int chatId = chatId(session);
        final AiChatRunManager runManager = runManagerProvider.get();
        if (!runManager.isTainted(chatId) && !runManager.isUnattended(chatId)) {
            return Optional.empty();
        }
        return Optional.of(runManager.isUnattended(chatId)
                ? AWAY_REASON
                : READ_DATA_REASON);
    }

    /// Puts the change on an approval card in the chat (an APPROVAL_REQUEST message), for the user to answer
    /// through `AiMemoryService`.
    ///
    /// @param session A chat's session.
    /// @param change  The change, waiting for approval.
    /// @return Pending, with the card's message id.
    @Override
    public ApprovalOutcome requestChangeApproval(final ToolSession session, final MemoryChange change) {
        final AiChatMessage card = aiServiceProvider.get().storeMessage(chatId(session),
                AiMessageType.APPROVAL_REQUEST,
                JsonUtil.writeValueAsString(change, false));
        return pending(card);
    }

    /// @param session A chat's session.
    /// @return Questions asked as a form in the chat, while the user is watching it.
    @Override
    public UserQuestions userQuestions(final ToolSession session) {
        return new ChatUserQuestions(chatId(session), elicitationsProvider, runManagerProvider);
    }

    private static int chatId(final ToolSession session) {
        return session.chatId()
                .orElseThrow(() -> new ToolSessionException("A chat's consent gate can only be used in a chat."));
    }

    private static ApprovalOutcome pending(final AiChatMessage card) {
        // The id is the card's message id, if the chat service gives the card back.
        return ApprovalOutcome.pending(card == null
                ? null
                : String.valueOf(card.getId()));
    }
}
