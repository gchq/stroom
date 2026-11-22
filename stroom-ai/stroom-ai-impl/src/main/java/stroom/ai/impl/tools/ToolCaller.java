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

/// Who a tool call is made for: Stroom AI's own chat, or (later) an external client connected over MCP.
///
/// @param kind The kind of caller.
/// @param name The caller's name, e.g. the MCP client's name.
public record ToolCaller(Kind kind, String name) {

    /// Stroom AI's own chat agent.
    public static final ToolCaller STROOM_AI_CHAT = new ToolCaller(Kind.STROOM_AI_CHAT, "Stroom AI chat");
    /// What a result too large for Stroom AI's chat to give its model whole is outlined for: the stub the model is
    /// given in its place ([LargeResults#chatOutline]).
    public static final ToolCaller CHAT_STUB = new ToolCaller(Kind.CHAT_STUB, "Stroom AI chat's stub");

    /// @return True if the caller is Stroom AI's own chat agent.
    public boolean isStroomAiChat() {
        return kind == Kind.STROOM_AI_CHAT;
    }

    /// @return True if a result too large for the caller is given to it in outline ([LargeResults]): a client
    /// outside Stroom, or the stub of Stroom AI's chat. Stroom AI's chat itself gets every result whole, and keeps
    /// a large one in its workspace.
    public boolean takesOutlines() {
        return kind == Kind.EXTERNAL_CLIENT || kind == Kind.CHAT_STUB;
    }

    /// The kinds of caller.
    public enum Kind {
        /// Stroom AI's own chat agent.
        STROOM_AI_CHAT,
        /// A client outside Stroom, e.g. an agent connected over MCP.
        EXTERNAL_CLIENT,
        /// The stub Stroom AI's chat gives its model in place of a large result: an outline of it, made on a copy.
        CHAT_STUB
    }
}
