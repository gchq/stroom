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

/// Finds the limits that apply to a session's tool calls ([SessionLimits]) from the session itself, rather than
/// from a chat's id, so each kind of session can have its own: [ChatSessionLimits] for a chat, and (later) one for
/// an MCP session.
@FunctionalInterface
public interface ToolSessionLimits {

    /// @param session A session.
    /// @return The limits that apply to the session's tool calls.
    SessionLimits forSession(ToolSession session);
}
