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

import stroom.ai.shared.AiToolApprovalDecision;

import java.util.List;
import java.util.Optional;

/// Stores the decisions about tools that last for a chat ('allow in this chat', and 'allow this server's
/// read-only tools in this chat', whose tool name is [#ALL_READ_ONLY_TOOLS]).
public interface AiToolApprovalDao {

    /// The tool name of an approval covering all of a server's read-only tools.
    String ALL_READ_ONLY_TOOLS = "*";

    /// @return The decision about a tool (or [#ALL_READ_ONLY_TOOLS]) of a server in a chat, if one is held.
    Optional<Approval> find(int chatId, String serverKey, String toolName);

    /// Records a decision, replacing any held for the tool.
    void save(String userUuid,
              int chatId,
              String serverKey,
              String toolName,
              String toolHash,
              AiToolApprovalDecision decision,
              String updateUser);

    /// Deletes a decision.
    void delete(int id);

    /// @return All the decisions held for a chat.
    List<Approval> list(int chatId);

    /// A decision about a tool that lasts for a chat.
    ///
    /// @param id        The id.
    /// @param serverKey The server.
    /// @param toolName  The tool, or [#ALL_READ_ONLY_TOOLS].
    /// @param toolHash  A hash of the tool's name and description when the decision was made, so a changed tool
    ///                  is asked about again.
    /// @param decision  The decision.
    record Approval(int id, String serverKey, String toolName, String toolHash, AiToolApprovalDecision decision) {

    }
}
