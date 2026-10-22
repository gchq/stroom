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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolCaller;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.UserQuestions;
import stroom.util.json.JsonUtil;

import io.modelcontextprotocol.spec.McpSchema.ElicitResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Calls of the ported tools for their tests, as an MCP client makes them.
final class PortedToolCalls {

    private PortedToolCalls() {
        // Static methods only.
    }

    /// @return A call of the tool with the arguments, as JSON.
    static ToolCall call(final String name, final String arguments) {
        return new ToolCall("1", name, arguments);
    }

    /// @return The context of a call from an MCP client that can't show forms, with no workspace: a call that needs
    /// the user's agreement is refused.
    static ToolCallContext context() {
        return ToolCallContext.builder(new ToolSession(ToolSession.Kind.MCP, "mcp-session-1",
                        new ToolCaller(ToolCaller.Kind.EXTERNAL_CLIENT, "A test client")))
                .userQuestions(UserQuestions.NONE)
                .build();
    }

    /// @param answer What the user answers every form with (accepting it).
    /// @return The context of a call from an MCP client that shows forms, with no workspace, and the user's
    /// answers.
    static ToolCallContext answering(final Map<String, Object> answer) {
        return user(answer).context();
    }

    /// @param answer What the user answers every form with (accepting it).
    /// @return A user of an MCP client that shows forms, who answers every form so, and keeps what they were shown.
    static FormUser user(final Map<String, Object> answer) {
        return new FormUser(new ElicitResult(ElicitResult.Action.ACCEPT, answer));
    }

    /// @return A user of an MCP client that shows forms, who declines every form.
    static FormUser declining() {
        return new FormUser(new ElicitResult(ElicitResult.Action.DECLINE, null));
    }

    /// @return A result's JSON text, read.
    @SuppressWarnings("unchecked")
    static Map<String, Object> json(final String text) {
        return JsonUtil.readValue(text, Map.class);
    }


    // --------------------------------------------------------------------------------


    /// A user of an MCP client that shows forms: answers every form the same, and keeps the forms' messages.
    static final class FormUser implements UserQuestions {

        private final ElicitResult answer;
        private final List<String> shown = new ArrayList<>();
        private final List<String> callIds = new ArrayList<>();

        private FormUser(final ElicitResult answer) {
            this.answer = answer;
        }

        /// @return The context of a call from an MCP client, with no workspace, whose forms this user answers.
        ToolCallContext context() {
            return ToolCallContext.builder(new ToolSession(ToolSession.Kind.MCP, "mcp-session-1",
                            new ToolCaller(ToolCaller.Kind.EXTERNAL_CLIENT, "A test client")))
                    .userQuestions(this)
                    .build();
        }

        /// @return The message of each form shown, in order.
        List<String> shown() {
            return shown;
        }

        /// Each form shown, as the round the Stroom MCP server returned instead of a form: its `status`
        /// (`needs_confirmation` or `needs_approval`, as the form's id says), its `summary` and its `details` (as
        /// the form shows them, each cut short: see [Consent#formMessage]).
        ///
        /// @return The forms shown, in order.
        List<Map<String, Object>> forms() {
            final List<Map<String, Object>> forms = new ArrayList<>();
            for (int i = 0; i < shown.size(); i++) {
                final String message = shown.get(i);
                final String key = callIds.get(i).substring(callIds.get(i).indexOf(UserQuestions.FORM_SEPARATOR)
                                                            + UserQuestions.FORM_SEPARATOR.length());
                final int split = message.indexOf("\n\n");
                final Map<String, Object> details = new LinkedHashMap<>();
                if (split >= 0) {
                    message.substring(split + 2).lines()
                            .filter(line -> line.startsWith("- ") && line.contains(": "))
                            .forEach(line -> details.put(line.substring(2, line.indexOf(": ")),
                                    line.substring(line.indexOf(": ") + 2)));
                }
                final Map<String, Object> form = new LinkedHashMap<>();
                form.put("status", "needs_" + key.substring(0, key.indexOf('-')));
                form.put("summary", split < 0
                        ? message
                        : message.substring(0, split));
                form.put("details", details);
                forms.add(form);
            }
            return forms;
        }

        @Override
        public boolean canAsk() {
            return true;
        }

        @Override
        public ElicitResult ask(final String toolName,
                                final String callId,
                                final String intro,
                                final Map<String, Object> form) {
            shown.add(intro);
            callIds.add(callId);
            return answer;
        }
    }
}
