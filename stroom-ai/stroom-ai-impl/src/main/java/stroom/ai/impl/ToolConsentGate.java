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

import stroom.ai.impl.memory.MemoryChange;
import stroom.ai.impl.tools.BuiltInTools;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.UserQuestions;

import java.util.Optional;

/// The one place the agent loop and the tools ask whether a tool call or a change may go ahead, and ask the user
/// to approve it when it needs approval. Each kind of session has its own way of asking:
///
/// * a Stroom AI chat ([ChatConsentGate]) decides with [ToolConsent], asks on an approval card in the chat (an
///   APPROVAL_REQUEST message, answered through [ToolApprovalService] or `AiMemoryService`), stops the run until
///   the user answers, asks questions as a form in the chat ([McpElicitations]), and holds to the stricter rules of
///   a run the user is away from;
/// * an MCP session (later) can ask with a short form where its client supports elicitation, and otherwise give
///   the client an approval id that the user confirms and the client passes back, as the Stroom MCP server does.
///
/// The rules for what needs approval stay with each tool and policy ([ToolConsent], `AiMemoryService`): the gate
/// only says how a session applies them and asks.
public interface ToolConsentGate {

    /// @param session A session.
    /// @return True if the user is away from the session (e.g. a run they are not watching), so that only what is
    /// safe without them may go ahead, and they can't be asked anything now.
    boolean isUserAway(ToolSession session);

    /// Decides whether a tool call the model or client asks for may go ahead.
    ///
    /// @param session      The session the call is made in.
    /// @param toolName     The tool, as the model or client named it.
    /// @param builtInTools Stroom's own tools.
    /// @param toolset      The MCP tools offered in the session.
    /// @param userAway     True if the user is away (see [#isUserAway(ToolSession)]), as found once for the call.
    /// @return Whether to run it, ask the user first, or not run it, and why.
    ToolConsent.Verdict checkCall(ToolSession session,
                                  String toolName,
                                  BuiltInTools builtInTools,
                                  McpToolset toolset,
                                  boolean userAway);

    /// Asks the user to approve the tool calls of one step that need it ([#checkCall] said ASK), all at once.
    ///
    /// @param session The session the calls are made in.
    /// @param request The calls, why they need approval, and how widely the user may allow them.
    /// @return What became of asking: a chat asks on a card, and the answer comes later
    /// ([ApprovalOutcome#isPending()]).
    ApprovalOutcome requestCallApproval(ToolSession session, ToolApprovalRequest request);

    /// Decides whether a change that outlives the session (e.g. to the user's own memory, which every later chat
    /// loads) needs the user's approval.
    ///
    /// @param session The session the change is made in.
    /// @return Why the change needs the user's approval, or empty if it may be made straight away.
    Optional<String> lastingChangeApprovalReason(ToolSession session);

    /// Asks the user to approve a change to their memory.
    ///
    /// @param session The session the change is made in.
    /// @param change  The change, waiting for approval, with why it needs it.
    /// @return What became of asking: a chat asks on a card, and the answer comes later
    /// ([ApprovalOutcome#isPending()]).
    ApprovalOutcome requestChangeApproval(ToolSession session, MemoryChange change);

    /// @param session A session.
    /// @return How to ask the user of the session questions (a form), and whether they can be asked now.
    UserQuestions userQuestions(ToolSession session);
}
