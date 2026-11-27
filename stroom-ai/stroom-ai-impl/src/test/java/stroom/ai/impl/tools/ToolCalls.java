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

import stroom.ai.impl.workspace.ChatWorkspace;

import org.mockito.Mockito;

import java.util.function.Supplier;

/// Calls and call contexts for tests of the built-in tools: a call in a chat, made as the chat makes it (see
/// [ChatToolCallContexts]), or in a session that isn't a chat.
public final class ToolCalls {

    private ToolCalls() {
    }

    /// @param name      The tool's name.
    /// @param arguments The call's arguments, as JSON.
    /// @return A call of the tool.
    public static ToolCall call(final String name, final String arguments) {
        return new ToolCall("1", name, arguments);
    }

    /// @param chatId    The chat.
    /// @param workspace The chats' workspaces.
    /// @return The context of a call in the chat, as the chat makes it, part of the agent run this thread is
    /// working on, if any. Asking the user fails the test.
    public static ToolCallContext inChat(final int chatId, final ChatWorkspace workspace) {
        return chatContexts(workspace).forChat(chatId);
    }

    /// @param workspace The chats' workspaces.
    /// @return What makes the contexts of calls in chats, as a chat makes them. Asking the user fails the test.
    public static ChatToolCallContexts chatContexts(final ChatWorkspace workspace) {
        final Supplier<RuntimeException> noForms = () -> new IllegalStateException("No forms in this test");
        return new ChatToolCallContexts(workspace,
                () -> {
                    throw noForms.get();
                },
                () -> {
                    throw noForms.get();
                });
    }

    /// @return What makes the contexts of calls in chats, with a mocked workspace.
    public static ChatToolCallContexts chatContexts() {
        return chatContexts(Mockito.mock(ChatWorkspace.class));
    }

    /// @param chatId The chat.
    /// @return The context of a call in the chat, with a mocked workspace.
    public static ToolCallContext inChat(final int chatId) {
        return inChat(chatId, Mockito.mock(ChatWorkspace.class));
    }

    /// @return The context of a call in a session that isn't a chat (an MCP client's), with no workspace, in which
    /// the user can't be asked anything.
    public static ToolCallContext outsideChat() {
        return ToolCallContext.builder(new ToolSession(ToolSession.Kind.MCP,
                        "mcp-session-1",
                        new ToolCaller(ToolCaller.Kind.EXTERNAL_CLIENT, "A test client")))
                .userQuestions(UserQuestions.NONE)
                .build();
    }
}
