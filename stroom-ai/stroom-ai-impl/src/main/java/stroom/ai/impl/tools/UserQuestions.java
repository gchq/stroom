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

import io.modelcontextprotocol.spec.McpSchema.ElicitResult;

import java.util.Map;

/// How a tool asks the user questions in its session and waits for the answers: a form in the chat for Stroom
/// AI's chat (see `McpElicitations`), and an MCP elicitation later.
public interface UserQuestions {

    /// For a session in which the user can't be asked anything.
    UserQuestions NONE = new UserQuestions() {
        @Override
        public boolean canAsk() {
            return false;
        }

        @Override
        public ElicitResult ask(final String toolName,
                                final String callId,
                                final String intro,
                                final Map<String, Object> form) {
            return new ElicitResult(ElicitResult.Action.DECLINE, null);
        }
    };

    /// What separates a tool call's id from a form's key in the id of one of several forms the call asks.
    String FORM_SEPARATOR = "#form:";

    /// @return False if the user can't be asked now, e.g. as they aren't watching the chat (an unattended run).
    boolean canAsk();

    /// Asks the user questions, and gives their answer. In a chat, the run may stop while the questions wait (see
    /// [ChatUserQuestions]), and carry on by making the same call again, when it is given the answer.
    ///
    /// @param toolName The tool asking, which says how the questions are shown.
    /// @param callId   The id of the tool call asking, or null if it has none.
    /// @param intro    What the questions are for.
    /// @param form     The questions, as an MCP form's schema.
    /// @return The user's answer, or a decline or cancel if the form can't be shown, the user didn't answer (e.g.
    /// sent a message instead), or the run is stopped.
    /// @throws stroom.ai.impl.AiChatRunManager.WaitingForUserException if the run is to stop until the user
    ///                                                                 answers.
    ElicitResult ask(String toolName, String callId, String intro, Map<String, Object> form);

    /// The id to ask one of several forms of a tool call with. In a chat, the call is made again once a form is
    /// answered, and each form it asks is told apart by its id: a call asking a confirmation and then a choice
    /// gets the confirmation's answer again, then asks the choice.
    ///
    /// @param callId The tool call's id, or null if it has none.
    /// @param key    The form's key, the same each time the call asks it, e.g. `confirmation-1a2b3c`.
    /// @return The form's id, or null if the call has none.
    static String formCallId(final String callId, final String key) {
        return callId == null
                ? null
                : callId + FORM_SEPARATOR + key;
    }

    /// @param formCallId A form's id, from [#formCallId], or a tool call's own id.
    /// @return The id of the tool call that asked it.
    static String toolCallId(final String formCallId) {
        final int at = formCallId.indexOf(FORM_SEPARATOR);
        return at < 0
                ? formCallId
                : formCallId.substring(0, at);
    }
}
