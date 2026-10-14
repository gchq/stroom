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

import stroom.ai.api.AiService;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiMessageType;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestHistoryTools {

    private final AiService aiService = Mockito.mock(AiService.class);
    private final ReadTurnTool readTurn = new ReadTurnTool(aiService);
    private final SearchHistoryTool searchHistory = new SearchHistoryTool(aiService);

    {
        Mockito.when(aiService.getMessages(1)).thenReturn(List.of(
                message(10, AiMessageType.USER_MESSAGE, "How many errors on FW1?", 10),
                message(11, AiMessageType.TOOL_CALL,
                        "{\"callId\":\"c1\",\"tool\":\"run_query\",\"arguments\":\"{\\\"q\\\":1}\"}", 10),
                message(12, AiMessageType.TOOL_RESULT,
                        "{\"callId\":\"c1\",\"tool\":\"run_query\",\"error\":false,\"text\":\"42 rows\","
                        + "\"path\":\"/outputs/turn-10/call-11.txt\",\"chars\":9,\"durationMs\":1}", 10),
                message(13, AiMessageType.AI_RESPONSE, "There were 42 errors on fw1.", 10),
                message(14, AiMessageType.DEBUG_DETAIL, "secret debug about fw1", 10),
                message(20, AiMessageType.USER_MESSAGE, "And fw2?", 20)));
        // Another chat's messages must never be reached.
        Mockito.when(aiService.getMessages(2)).thenReturn(List.of(
                message(30, AiMessageType.USER_MESSAGE, "other chat fw1", 30)));
    }

    @Test
    void testReadTurn() {
        final String result = readTurn.execute(request("{\"turnId\":10}"), 1);

        assertThat(result)
                .startsWith("Turn 10:")
                .contains("Question:\nHow many errors on FW1?")
                .contains("Called tool run_query with {\"q\":1}")
                .contains("Result:\n42 rows")
                .contains("(The full result is in the workspace file /outputs/turn-10/call-11.txt)")
                .contains("Answer:\nThere were 42 errors on fw1.")
                .doesNotContain("secret debug");
    }

    @Test
    void testReadTurn_defaultsToTheLatestTurn() {
        assertThat(readTurn.execute(request("{}"), 1)).startsWith("Turn 20:").contains("And fw2?");
    }

    @Test
    void testReadTurn_onlyReadsItsOwnChat() {
        assertThat(readTurn.execute(request("{\"turnId\":30}"), 1)).startsWith("Error: there is no turn 30");
        assertThat(readTurn.execute(request("{\"turnId\":\"x\"}"), 1)).startsWith("Turn 20:");
        assertThat(readTurn.execute(request("{}"))).startsWith("Error:");
    }

    @Test
    void testSearchHistory() {
        final String result = searchHistory.execute(request("{\"text\":\"fw1\"}"), 1);

        assertThat(result)
                .startsWith("Found \"fw1\" in 2 place(s):")
                .contains("- turn 10, question: How many errors on FW1?")
                .contains("- turn 10, answer: There were 42 errors on fw1.")
                .doesNotContain("secret")
                .doesNotContain("other chat");
    }

    @Test
    void testSearchHistory_textIsLiteralNotAPattern() {
        assertThat(searchHistory.execute(request("{\"text\":\"(a+)+$\"}"), 1))
                .isEqualTo("No part of this conversation mentions \"(a+)+$\".");
        assertThat(searchHistory.execute(request("{\"text\":\"42 rows\"}"), 1))
                .contains("tool result: 42 rows");
    }

    @Test
    void testSearchHistory_limitsAndBadInput() {
        assertThat(searchHistory.execute(request("{\"text\":\"fw\",\"maxResults\":1}"), 1))
                .contains("in 3 place(s)")
                .contains("(2 more not listed.)");
        assertThat(searchHistory.execute(request("{}"), 1)).startsWith("Error:");
        assertThat(searchHistory.execute(request("{\"text\":\"" + "x".repeat(300) + "\"}"), 1))
                .startsWith("Error:");
    }

    @Test
    void testSnippet() {
        final String text = "a".repeat(200) + "NEEDLE" + "b".repeat(200);
        assertThat(SearchHistoryTool.snippet(text, 200, 6))
                .startsWith("…")
                .endsWith("…")
                .contains("NEEDLE")
                .hasSize(2 * SearchHistoryTool.SNIPPET_CONTEXT + 6 + 2);
    }

    @Test
    void testRegistered() {
        final BuiltInTools tools = new BuiltInTools(Set.of(readTurn, searchHistory));

        assertThat(tools.isReadOnly(ReadTurnTool.NAME)).isTrue();
        assertThat(tools.isReadOnly(SearchHistoryTool.NAME)).isTrue();
        // The chat is passed through as the memory id.
        assertThat(tools.toolMap().entrySet().stream()
                .filter(e -> e.getKey().name().equals(ReadTurnTool.NAME))
                .findFirst()
                .orElseThrow()
                .getValue()
                .execute(request("{}"), 1)).startsWith("Turn 20:");
    }

    private static ToolExecutionRequest request(final String arguments) {
        return ToolExecutionRequest.builder().id("x").name("t").arguments(arguments).build();
    }

    private static AiChatMessage message(final int id, final AiMessageType type, final String text, final int turn) {
        return new AiChatMessage(id, 1, 0L, type, null, text, turn, null);
    }
}
