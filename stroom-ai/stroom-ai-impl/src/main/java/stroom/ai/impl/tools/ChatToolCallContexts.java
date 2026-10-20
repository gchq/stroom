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
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.SessionWorkspace;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

/// Makes the [ToolCallContext] of a tool call in a Stroom AI chat: the chat's session, its workspace, questions
/// asked as a form in the chat, the chat's limits ([ChatSessionLimits]), and the agent run the call is part of.
@Singleton
public class ChatToolCallContexts {

    private final ChatWorkspace workspace;
    private final ToolSessionLimits sessionLimits;
    private final Provider<McpElicitations> elicitationsProvider;
    private final Provider<AiChatRunManager> runManagerProvider;

    /// @param workspace            The workspaces of the chats.
    /// @param elicitationsProvider Shows forms in the chat and waits for the answers.
    /// @param runManagerProvider   Knows whether the user is watching a chat.
    @Inject
    public ChatToolCallContexts(final ChatWorkspace workspace,
                                final Provider<McpElicitations> elicitationsProvider,
                                final Provider<AiChatRunManager> runManagerProvider) {
        this.workspace = workspace;
        this.sessionLimits = new ChatSessionLimits(workspace);
        this.elicitationsProvider = elicitationsProvider;
        this.runManagerProvider = runManagerProvider;
    }

    /// @param chatId The chat the call is made in.
    /// @return The context of a call in the chat, part of the agent run this thread is working on, if any (see
    /// [ToolRunContext#current()]).
    public ToolCallContext forChat(final int chatId) {
        final ToolSession session = ToolSession.chat(chatId);
        return ToolCallContext.builder(session)
                .workspace(new SessionWorkspace(workspace, chatId))
                .userQuestions(new ChatUserQuestions(chatId, elicitationsProvider, runManagerProvider))
                // The session's limits, including its time limit.
                .limits(sessionLimits.forSession(session))
                .runContext(ToolRunContext.current().orElse(null))
                .build();
    }

    /// @param memoryId The memory id langchain4j gives a tool executor: the chat's id, in a chat.
    /// @return The context of a call in the chat, or null if the memory id isn't a chat's.
    public ToolCallContext forMemoryId(final Object memoryId) {
        return memoryId instanceof final Integer chatId
                ? forChat(chatId)
                : null;
    }
}
