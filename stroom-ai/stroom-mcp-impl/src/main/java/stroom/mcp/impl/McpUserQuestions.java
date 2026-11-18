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

package stroom.mcp.impl;

import stroom.ai.impl.tools.UserQuestions;
import stroom.ai.impl.workspace.ToolDeadline;

import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;

import java.util.Map;
import java.util.Objects;

/// Asks an MCP client's user a tool's questions (e.g. ask_user's) as a form (MCP elicitation), and waits for the
/// answer, holding the call: unlike a chat's run, an MCP call has nothing to stop and carry on later.
///
/// While the user is asked, the call gives back its run slot (if it holds one) and its time limit is paused, so a
/// form waiting up to the form timeout neither keeps the user's other work from starting nor uses up the call's
/// time.
final class McpUserQuestions implements UserQuestions {

    private final McpSession session;
    private final McpAsyncServerExchange exchange;
    private final McpForms forms;
    private final McpCallSlot slot;

    /// @param session  The session asking.
    /// @param exchange The request the questions are asked in.
    /// @param forms    Shows the form and waits for the answer.
    /// @param slot     The call's run slot, given back while the user is asked.
    McpUserQuestions(final McpSession session,
                     final McpAsyncServerExchange exchange,
                     final McpForms forms,
                     final McpCallSlot slot) {
        this.session = Objects.requireNonNull(session, "session");
        this.exchange = Objects.requireNonNull(exchange, "exchange");
        this.forms = Objects.requireNonNull(forms, "forms");
        this.slot = Objects.requireNonNull(slot, "slot");
    }

    /// @return True if the client can show forms.
    @Override
    public boolean canAsk() {
        return session.canShowForms();
    }

    /// Asks the questions, and waits for the answer (up to the form timeout).
    ///
    /// @param toolName The tool asking.
    /// @param callId   The call's id; not used, as the call waits for the answer.
    /// @param intro    What the questions are for.
    /// @param form     The questions, as an MCP form's schema.
    /// @return The user's answer, or a cancel if the client can't show forms or the user doesn't answer.
    @Override
    public ElicitResult ask(final String toolName,
                            final String callId,
                            final String intro,
                            final Map<String, Object> form) {
        return slot.releasedWhile(() -> ToolDeadline.paused(() -> forms.ask(session, exchange, intro, form)));
    }
}
