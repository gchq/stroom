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

import java.util.Objects;

/// The limits of the tool calls in a Stroom AI chat: the workspace limits in Stroom's AI configuration (see
/// [ChatWorkspace#limits()]), the same for every chat, and looked up each time they are read, as the chat's tools
/// always have.
public final class ChatSessionLimits implements ToolSessionLimits {

    private final ChatWorkspace workspace;

    /// @param workspace The workspaces of the chats, which hold their limits.
    public ChatSessionLimits(final ChatWorkspace workspace) {
        this.workspace = Objects.requireNonNull(workspace, "workspace");
    }

    /// @param session A session.
    /// @return The limits of its tool calls: the chats' workspace limits. Every session given here is a chat's.
    @Override
    public SessionLimits forSession(final ToolSession session) {
        Objects.requireNonNull(session, "session");
        return SessionLimits.lookedUp(workspace::limits);
    }
}
