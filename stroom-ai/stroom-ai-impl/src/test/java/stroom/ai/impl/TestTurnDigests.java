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
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

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

    @Test
    void aDigestNeverCutsAnIdentifierInACallsArguments() {
        // Seen in chat 194: the digest cut index_uuid at "81b", and after compaction the model called
        // create_indexing_pipeline and find_pipeline_templates with the cut UUIDs.
        final String arguments = "{\"build\": \"endpoint-logs-v1.0\", \"name\": \"Endpoint-Logs-V1.0-Indexing\", "
                                 + "\"template_uuid\": \"1c70ba40-29be-44f1-891b-183acb84b7e4\", "
                                 + "\"xslt_uuid\": \"a0e33cb9-c5d3-4592-a4a1-6f1e0d5c0b3e\", "
                                 + "\"index_uuid\": \"81b0cd88-2481-4291-a8cc-7d3a93e65c4b\", "
                                 + "\"events_stream_ids\": [\"73356\",\"73357\"], "
                                 + "\"source_pipeline_uuid\": \"c05a150d-532e-4b6d-8b0a-1f0e0ec55ddf\"}";
        final List<AiChatMessage> turn = List.of(
                message(10, AiMessageType.USER_MESSAGE, "go on"),
                json(11, AiMessageType.TOOL_CALL, new ToolCallRecord("c1", "create_indexing_pipeline", arguments,
                        null)),
                json(12, AiMessageType.TOOL_RESULT, new ToolResultRecord("c1", "create_indexing_pipeline", false,
                        "ok", null, 2, 5, null)));

        final String digest = TurnDigests.build(10, turn, 20);

        assertThat(arguments).hasSizeGreaterThan(TurnDigests.MAX_ARGUMENTS_CHARS);
        assertThat(digest)
                // The UUID the cut fell in, whole; the argument cut said, and the UUID after it given whole.
                .contains("\"index_uuid\": \"81b0cd88-2481-4291-a8cc-7d3a93e65c4b… (cut in index_uuid; ids "
                          + "further on, whole: source_pipeline_uuid=c05a150d-532e-4b6d-8b0a-1f0e0ec55ddf)")
                .contains("\"template_uuid\": \"1c70ba40-29be-44f1-891b-183acb84b7e4\"");
        // Every UUID-like run in the digest is a whole UUID.
        assertThat(cutUuids(digest)).isEmpty();
    }

    @Test
    void aDigestNeverCutsAnIdentifierInAResultsPreview() {
        // Seen in chat 194: folder_contents' preview cut a folder's docUuid.
        final String result = "Folder 'endpoint-logs-v1.0' (/MCP Workspace/endpoint-logs-v1.0, uuid "
                              + "d96be8d9-f7fa-41a0-b012-c7d229d09e60, [endpoint-logs-v1.0](?action=open-doc&docType="
                              + "Folder&docUuid=d96be8d9-f7fa-41a0-b012-c7d229d09e60))\n"
                              + "row\n".repeat(30);
        final List<AiChatMessage> turn = List.of(
                message(10, AiMessageType.USER_MESSAGE, "q"),
                json(11, AiMessageType.TOOL_CALL, new ToolCallRecord("c1", "folder_contents", "{}", null)),
                json(12, AiMessageType.TOOL_RESULT, new ToolResultRecord("c1", "folder_contents", false, result,
                        null, result.length(), 5, null)));

        final String digest = TurnDigests.build(10, turn, 20);

        assertThat(digest).contains("docUuid=d96be8d9-f7fa-41a0-b012-c7d229d09e60…");
        assertThat(cutUuids(digest)).isEmpty();
    }

    @Test
    void anIdentifierTooFarPastTheCutIsLeftOutWhole() {
        // A long id the cut falls early in: left out, never cut, and the text stays within its limit.
        final String id = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        final String shortened = TurnDigests.oneLine("read " + id, 10);

        assertThat(shortened).isEqualTo("read …");
        // Ordinary words are still cut where they fall.
        assertThat(TurnDigests.oneLine("abcdefghij klmnop", 8)).isEqualTo("abcdefg…");
    }

    @Test
    void shortenedArgumentsStayBounded() {
        // Five UUIDs past the cut: four named whole, the rest counted.
        final StringBuilder sb = new StringBuilder("{\"text\": \"").append("x".repeat(300)).append('"');
        for (int i = 0; i < 5; i++) {
            sb.append(", \"u").append(i).append("\": \"").append(i).append("1b0cd88-2481-4291-a8cc-7d3a93e65c4b\"");
        }
        final String shortened = TurnDigests.arguments(sb.append('}').toString(), TurnDigests.MAX_ARGUMENTS_CHARS);

        assertThat(shortened).endsWith("… (cut in text; ids further on, whole: "
                                       + "u0=01b0cd88-2481-4291-a8cc-7d3a93e65c4b, "
                                       + "u1=11b0cd88-2481-4291-a8cc-7d3a93e65c4b, "
                                       + "u2=21b0cd88-2481-4291-a8cc-7d3a93e65c4b, "
                                       + "u3=31b0cd88-2481-4291-a8cc-7d3a93e65c4b and 1 more)");
        assertThat(shortened.length()).isLessThanOrEqualTo(TurnDigests.MAX_ARGUMENTS_CHARS
                                                           + TurnDigests.MAX_ID_OVERRUN + 250);
        // Short arguments are as they were.
        assertThat(TurnDigests.arguments("{\"a\": 1}", 200)).isEqualTo("{\"a\": 1}");
    }

    // The runs of hex digits and hyphens starting like a UUID that aren't whole UUIDs.
    private static List<String> cutUuids(final String text) {
        return Pattern.compile("[0-9a-f]{8}-[0-9a-f-]*").matcher(text).results()
                .map(MatchResult::group)
                .filter(run -> !run.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
                .toList();
    }

    private static AiChatMessage message(final int id, final AiMessageType type, final String text) {
        return new AiChatMessage(id, 1, 0L, type, null, text, 10, null);
    }

    private static AiChatMessage json(final int id, final AiMessageType type, final Object record) {
        return message(id, type, JsonUtil.writeValueAsString(record, false));
    }
}
