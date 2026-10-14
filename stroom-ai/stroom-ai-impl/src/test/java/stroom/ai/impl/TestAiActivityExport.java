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

import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiMessageType;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestAiActivityExport {

    @Test
    void thinkingIsWrittenAsItsText() {
        final String json = JsonUtil.writeValueAsString(new ThinkingRecord("Let me think.", 1200), false);

        assertThat(AiActivityExport.body(message(AiMessageType.THINKING, json))).isEqualTo("Let me think.");
        // Older reasoning was stored as plain text.
        assertThat(AiActivityExport.body(message(AiMessageType.THINKING, "Plain thoughts")))
                .isEqualTo("Plain thoughts");
    }

    @Test
    void toolCallsAndResultsShowTheToolAndItsArgumentsOrResult() {
        final String call = JsonUtil.writeValueAsString(
                new ToolCallRecord("1", "grep", "{\"pattern\":\"ERROR\"}", null), false);
        final String result = JsonUtil.writeValueAsString(
                new ToolResultRecord("1", "grep", true, "No such file", "/outputs/x.txt", 12, 30), false);

        assertThat(AiActivityExport.body(message(AiMessageType.TOOL_CALL, call)))
                .startsWith("`grep`")
                .contains("{\"pattern\":\"ERROR\"}");
        assertThat(AiActivityExport.body(message(AiMessageType.TOOL_RESULT, result)))
                .contains("`grep` (failed)")
                .contains("/outputs/x.txt")
                .contains("No such file");
    }

    @Test
    void textCannotEndTheFenceEarly() {
        final String result = JsonUtil.writeValueAsString(
                new ToolResultRecord("1", "read_file", false, "a\n~~~~~~~~\n# Not a heading", null, 30, 1), false);

        final String body = AiActivityExport.body(message(AiMessageType.TOOL_RESULT, result));

        // Only the opening and closing fences remain.
        assertThat(body.split("\n~~~~~~~~\n", -1)).hasSize(2);
        assertThat(body).endsWith("\n~~~~~~~~");
    }

    @Test
    void unreadableJsonIsWrittenAsItIs() {
        assertThat(AiActivityExport.body(message(AiMessageType.TOOL_CALL, "not json"))).contains("not json");
    }

    private static AiChatMessage message(final AiMessageType type, final String text) {
        return new AiChatMessage(1, 1, 0L, type, null, text, 1, null);
    }
}
