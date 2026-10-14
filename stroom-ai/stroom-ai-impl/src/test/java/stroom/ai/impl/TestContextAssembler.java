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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestContextAssembler {

    @Test
    void testToolLog() {
        final List<AiChatMessage> toolMessages = List.of(
                message(1, AiMessageType.TOOL_CALL,
                        new ToolCallRecord("c1", "stroom__find_documents", "{\"name\":\"fw\"}", "looking")),
                message(2, AiMessageType.TOOL_RESULT,
                        new ToolResultRecord("c1", "stroom__find_documents", false, "Found 2 feeds", null, 13, 5)),
                message(3, AiMessageType.TOOL_CALL,
                        new ToolCallRecord("c2", "run_stroomql", "{\"query\":\"from x\"}", null)),
                message(4, AiMessageType.TOOL_RESULT,
                        new ToolResultRecord("c2", "run_stroomql", false, "stub", "/outputs/turn-1/call-3.txt",
                                90_000, 50)));

        assertThat(ContextAssembler.toolLog(toolMessages)).isEqualTo("""
                Tools used for the next answer:
                - stroom__find_documents({"name":"fw"}) -> Found 2 feeds
                - run_stroomql({"query":"from x"}) -> result saved to /outputs/turn-1/call-3.txt""");
    }

    @Test
    void testToolLog_none() {
        assertThat(ContextAssembler.toolLog(null)).isNull();
        assertThat(ContextAssembler.toolLog(List.of())).isNull();
    }

    @Test
    void testToolLog_longArgumentsAndResultsAreShortened() {
        final String longText = "y".repeat(500);
        final List<AiChatMessage> toolMessages = List.of(
                message(1, AiMessageType.TOOL_CALL, new ToolCallRecord("c1", "t", longText, null)),
                message(2, AiMessageType.TOOL_RESULT, new ToolResultRecord("c1", "t", false, longText, null, 500, 1)));

        assertThat(ContextAssembler.toolLog(toolMessages))
                .contains("y".repeat(ContextAssembler.TOOL_LOG_ARGS_LIMIT - 1) + "…")
                .hasSizeLessThan(500);
    }

    private static AiChatMessage message(final int id, final AiMessageType type, final Object record) {
        return new AiChatMessage(id, 1, 0L, type, null, JsonUtil.writeValueAsString(record, false), 1, null);
    }
}
