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

package stroom.ai.impl.tools;

import stroom.ai.impl.AiChatRunManager;
import stroom.ai.impl.McpElicitations;

import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import jakarta.inject.Provider;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/// Asks the user of a Stroom AI chat questions as a form in the chat ([McpElicitations]), as long as they are
/// watching it: while they are away (an unattended run) nothing is asked.
///
/// One is made for each tool call, so it can tell whether the call asked the run to stop.
public final class ChatUserQuestions implements UserQuestions {

    private final int chatId;
    private final Provider<McpElicitations> elicitationsProvider;
    private final Provider<AiChatRunManager> runManagerProvider;
    private volatile AiChatRunManager.WaitingForUserException waitingForUser;

    /// @param chatId               The chat.
    /// @param elicitationsProvider Shows forms in the chat and waits for the answers.
    /// @param runManagerProvider   Knows whether the user is watching the chat.
    public ChatUserQuestions(final int chatId,
                             final Provider<McpElicitations> elicitationsProvider,
                             final Provider<AiChatRunManager> runManagerProvider) {
        this.chatId = chatId;
        this.elicitationsProvider = Objects.requireNonNull(elicitationsProvider, "elicitationsProvider");
        this.runManagerProvider = Objects.requireNonNull(runManagerProvider, "runManagerProvider");
    }

    /// @return False while the user is away from the chat (an unattended run).
    @Override
    public boolean canAsk() {
        return !runManagerProvider.get().isUnattended(chatId);
    }

    /// Asks the user questions as a form in the chat. Asked in a tool call, the run stops until they answer, however
    /// long they take, and the call is made again then, and given the answer (see [McpElicitations#askUser]).
    ///
    /// @param toolName The tool asking, which says how the questions are shown.
    /// @param callId   The id of the tool call asking, or null if it has none (it then waits for the answer).
    /// @param intro    What the questions are for.
    /// @param form     The questions, as an MCP form's schema.
    /// @return The user's answer, or a decline or cancel if the form can't be shown, the user sent a message
    /// instead, or the run is stopped.
    /// @throws AiChatRunManager.WaitingForUserException if the run is to stop until the user answers.
    @Override
    public ElicitResult ask(final String toolName,
                            final String callId,
                            final String intro,
                            final Map<String, Object> form) {
        try {
            return elicitationsProvider.get().askUser(chatId, toolName, callId, intro, form);
        } catch (final AiChatRunManager.WaitingForUserException e) {
            waitingForUser = e;
            throw e;
        }
    }

    @Override
    public Optional<AiChatRunManager.WaitingForUserException> waitingForUser() {
        return Optional.ofNullable(waitingForUser);
    }
}
