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
import org.mockito.Mockito;

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
                Tools you used to answer this:
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

    @Test
    void testEstimateUsage() {
        final stroom.ai.api.AiService aiService = Mockito.mock(stroom.ai.api.AiService.class);
        final AiDao aiDao = Mockito.mock(AiDao.class);
        final stroom.ai.impl.AiWorkspaceDao workspaceDao = Mockito.mock(stroom.ai.impl.AiWorkspaceDao.class);
        final ContextUsageTracker tracker = Mockito.mock(ContextUsageTracker.class);
        final ContextAssembler assembler = new ContextAssembler(
                () -> null, AiConfig::new, null, aiService, aiDao, workspaceDao, tracker, null);
        Mockito.when(aiService.getMessages(1)).thenReturn(List.of(
                new AiChatMessage(1, 1, 0L, AiMessageType.ATTACHMENT, 50, "table", 1, null),
                new AiChatMessage(2, 1, 0L, AiMessageType.USER_MESSAGE, null, "q", 1, 100),
                new AiChatMessage(3, 1, 0L, AiMessageType.AI_RESPONSE, null, "a", 1, 200),
                // Not sent to the model.
                new AiChatMessage(4, 1, 0L, AiMessageType.ERROR, null, "x".repeat(4_000), 1, null),
                new AiChatMessage(5, 1, 0L, AiMessageType.USER_MESSAGE, null, "q2", 2, 50)));
        Mockito.when(workspaceDao.getSize(50)).thenReturn(4_000L);
        Mockito.when(tracker.getToolDefinitionTokens(1)).thenReturn(300);
        Mockito.when(tracker.getCorrection(1)).thenReturn(java.util.Optional.empty());
        Mockito.when(aiDao.getUsage(1)).thenReturn(new AiDao.ChatUsage("m", 10_000, null, 0, 0));

        final stroom.ai.shared.AiContextUsage usage = assembler.estimateUsage(1);

        assertThat(usage.getParts())
                .containsEntry(ContextAssembler.PART_CONVERSATION, 350L)
                .containsEntry(ContextAssembler.PART_ATTACHMENTS, 1_000L)
                .containsEntry(ContextAssembler.PART_TOOL_DEFINITIONS, 300L)
                .containsEntry(ContextAssembler.PART_TOOL_RESULTS, 0L)
                .containsEntry(ContextAssembler.PART_EARLIER_TURNS, 0L);
        assertThat(usage.isEstimated()).isTrue();
        assertThat(usage.getContextWindowTokens()).isEqualTo(10_000);
        final long sum = usage.getParts().values().stream().mapToLong(Long::longValue).sum();
        assertThat(usage.getUsedTokens()).isEqualTo(sum);
        assertThat(usage.getPercentUsed()).isEqualTo((int) (sum * 100 / 10_000));

        // Once a call has been measured, the estimate is corrected.
        Mockito.when(tracker.getCorrection(1)).thenReturn(java.util.Optional.of(2.0));
        final stroom.ai.shared.AiContextUsage corrected = assembler.estimateUsage(1);
        assertThat(corrected.isEstimated()).isFalse();
        assertThat(corrected.getUsedTokens()).isEqualTo(sum * 2);
    }

    private static AiChatMessage message(final int id, final AiMessageType type, final Object record) {
        return new AiChatMessage(id, 1, 0L, type, null, JsonUtil.writeValueAsString(record, false), 1, null);
    }
}
