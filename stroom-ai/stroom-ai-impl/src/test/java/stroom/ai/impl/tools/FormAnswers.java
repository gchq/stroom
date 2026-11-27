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

import stroom.ai.impl.McpElicitations;

import io.modelcontextprotocol.spec.McpSchema.ElicitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/// A user answering a tool's forms in a test: each form asked is kept ([#asked()]), and answered the same way.
public final class FormAnswers implements UserQuestions {

    private final ElicitResult answer;
    private final List<String> asked = new ArrayList<>();

    private FormAnswers(final ElicitResult answer) {
        this.answer = answer;
    }

    /// @param content The answer to every form, by field.
    /// @return A user accepting each form with it.
    public static FormAnswers answering(final Map<String, Object> content) {
        return new FormAnswers(new ElicitResult(ElicitResult.Action.ACCEPT, content));
    }

    /// @param message What the user sent in the chat.
    /// @return A user who sends a message instead of answering, putting each form aside, as a chat does.
    public static FormAnswers sendingInstead(final String message) {
        return new FormAnswers(new ElicitResult(ElicitResult.Action.CANCEL, null,
                Map.of(McpElicitations.SENT_INSTEAD, message)));
    }

    /// @return The intro of each form asked, in order.
    public List<String> asked() {
        return asked;
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
        asked.add(intro);
        return answer;
    }
}
