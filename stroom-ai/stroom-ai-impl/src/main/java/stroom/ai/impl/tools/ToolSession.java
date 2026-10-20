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

import java.util.Objects;
import java.util.OptionalInt;

/// The session a tool call is made in: a chat in Stroom AI now, an MCP client's session later. Tools that need the
/// chat itself (e.g. its history or the tabs the user attached) get it from here, and fail clearly in a session
/// that isn't a chat.
///
/// The caller is part of the session, as a session only ever has one: Stroom AI's chat for a chat, the client that
/// connected for an MCP session.
///
/// @param kind   The kind of session.
/// @param id     Its id: a chat's id, or an MCP session's id.
/// @param caller Who the session is for.
public record ToolSession(Kind kind, String id, ToolCaller caller) {

    /// @param kind   The kind of session.
    /// @param id     Its id: a chat's id, or an MCP session's id.
    /// @param caller Who the session is for.
    public ToolSession {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(caller, "caller");
    }

    /// @param chatId A chat's id.
    /// @return The session of a chat in Stroom AI.
    public static ToolSession chat(final int chatId) {
        return new ToolSession(Kind.CHAT, String.valueOf(chatId), ToolCaller.STROOM_AI_CHAT);
    }

    /// @return True if the session is a chat in Stroom AI.
    public boolean isChat() {
        return kind == Kind.CHAT;
    }

    /// @return The chat's id, or empty if the session isn't a chat.
    public OptionalInt chatId() {
        return isChat()
                ? OptionalInt.of(Integer.parseInt(id))
                : OptionalInt.empty();
    }

    /// @param toolName The tool that needs the chat, to say so if it can't have it.
    /// @return The chat's id.
    /// @throws ToolSessionException if the session isn't a chat.
    public int requireChatId(final String toolName) {
        return chatId().orElseThrow(() -> new ToolSessionException(toolName + " can only be used in a chat."));
    }

    /// @return The session's id. Audit records and logs have always shown a chat by its id, and still do.
    @Override
    public String toString() {
        return id;
    }

    /// The kinds of session.
    public enum Kind {
        /// A chat in Stroom AI.
        CHAT,
        /// A session of a client connected over MCP.
        MCP
    }
}
