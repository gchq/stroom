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

class TestTurnDigests {

    @Test
    void testBuild() {
        final List<AiChatMessage> turn = List.of(
                message(10, AiMessageType.USER_MESSAGE, "How many errors were there yesterday?"),
                message(11, AiMessageType.STEER_MESSAGE, "only on fw1"),
                json(12, AiMessageType.TOOL_CALL, new ToolCallRecord("c1", "run_query", "{\"q\":\"errors\"}", null)),
                json(13, AiMessageType.TOOL_RESULT,
                        new ToolResultRecord("c1", "run_query", false, "| host | n |\n| fw1 | 42 |", null,
                                20, 5, null)),
                json(14, AiMessageType.TOOL_CALL, new ToolCallRecord("c2", "big", "{}", null)),
                json(15, AiMessageType.TOOL_RESULT,
                        new ToolResultRecord("c2", "big", false, "stub", "/outputs/turn-10/call-14.txt",
                                90_000, 5, null)),
                json(16, AiMessageType.TOOL_CALL, new ToolCallRecord("c3", "broken", "{}", null)),
                json(17, AiMessageType.TOOL_RESULT,
                        new ToolResultRecord("c3", "broken", true, "Error: no such feed", null, 19, 5, null)),
                message(18, AiMessageType.AI_RESPONSE, "There were 42 errors on fw1.\n\nThe details follow..."),
                message(19, AiMessageType.THINKING, "not in the digest"));

        final String digest = TurnDigests.build(10, turn, 20);

        assertThat(digest)
                .startsWith("Turn 10:")
                .contains("- Question: How many errors were there yesterday?")
                .contains("- The user added while you worked: only on fw1")
                // Small results are copied exactly.
                .contains("- Tool run_query({\"q\":\"errors\"}) -> result:\n    | host | n |\n    | fw1 | 42 |")
                .contains("- Tool big({}) -> full result (90000 characters) saved to /outputs/turn-10/call-14.txt")
                .contains("- Tool broken({}) (failed) -> result:\n    Error: no such feed")
                .contains("- Workspace files created: /outputs/turn-10/call-14.txt")
                .contains("- Answer (start): There were 42 errors on fw1.")
                .doesNotContain("The details follow")
                .doesNotContain("not in the digest");
    }

    @Test
    void testBuild_largeResultIsNotCopied() {
        final String result = "row\n".repeat(30);
        final List<AiChatMessage> turn = List.of(
                message(10, AiMessageType.USER_MESSAGE, "q"),
                json(11, AiMessageType.TOOL_CALL, new ToolCallRecord("c1", "t", "{}", null)),
                json(12, AiMessageType.TOOL_RESULT, new ToolResultRecord("c1", "t", false, result, null,
                        120, 1, null)));

        assertThat(TurnDigests.build(10, turn, 20))
                .contains("(30 lines; use read_turn 10 for all of it)")
                .contains("- No answer was given (the run did not finish).");
    }

    @Test
    void testDigests_usesStoredDigestsAndBuildsMissingOnes() {
        final List<AiChatMessage> history = List.of(
                message(10, AiMessageType.USER_MESSAGE, "q1"),
                message(11, AiMessageType.AI_RESPONSE, "a1"),
                new AiChatMessage(12, 1, 0L, AiMessageType.TURN_DIGEST, null, "stored digest", 10, null),
                new AiChatMessage(20, 1, 0L, AiMessageType.USER_MESSAGE, null, "q2", 20, null),
                new AiChatMessage(21, 1, 0L, AiMessageType.AI_RESPONSE, null, "a2", 20, null));

        assertThat(TurnDigests.digests(history, 20))
                .containsEntry(10, "stored digest")
                .hasEntrySatisfying(20, digest -> assertThat(digest).startsWith("Turn 20:").contains("a2"));
    }

    @Test
    void testOneLine() {
        assertThat(TurnDigests.oneLine(" a\n  b\tc ", 100)).isEqualTo("a b c");
        assertThat(TurnDigests.oneLine("abcdef", 4)).isEqualTo("abc…");
        assertThat(TurnDigests.oneLine(null, 4)).isEmpty();
    }

    private static AiChatMessage message(final int id, final AiMessageType type, final String text) {
        return new AiChatMessage(id, 1, 0L, type, null, text, 10, null);
    }

    private static AiChatMessage json(final int id, final AiMessageType type, final Object record) {
        return message(id, type, JsonUtil.writeValueAsString(record, false));
    }
}
