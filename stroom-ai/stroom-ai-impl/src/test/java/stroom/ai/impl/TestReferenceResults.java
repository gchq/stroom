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

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestReferenceResults {

    @Test
    void resultsReadForReferenceAreShortenedOnlyOnceOtherResultsHaveBeen() {
        // Seen: an event type's elements, read with read_document, shortened a few steps later, so the agent
        // mapped fields to a type it remembered better.
        final List<ChatMessage> conversation = new ArrayList<>(List.of(UserMessage.from("q")));
        for (int n = 1; n <= 4; n++) {
            final String tool = n == 1
                    ? "read_document"
                    : "search";
            conversation.add(AiMessage.from("Step " + n, List.of(ToolExecutionRequest.builder()
                    .id(String.valueOf(n)).name(tool).arguments("{\"n\":" + n + "}").build())));
            conversation.add(ToolExecutionResultMessage.from(String.valueOf(n), tool, "r" + n + "x".repeat(1_000)));
        }

        // Other results first, keeping the latest two.
        assertThat(McpAgentChat.shrink(conversation)).isEqualTo(1);
        assertThat(resultTexts(conversation)).containsExactly("read_document", "shortened", "kept", "kept");
        // Then the latest but one; the schema read still kept.
        assertThat(McpAgentChat.shrink(conversation)).isEqualTo(1);
        assertThat(resultTexts(conversation)).containsExactly("read_document", "shortened", "shortened", "kept");
        // Then results read for reference.
        assertThat(McpAgentChat.shrink(conversation)).isEqualTo(1);
        assertThat(resultTexts(conversation)).containsExactly("shortened", "shortened", "shortened", "kept");

        // A large result isn't kept for reference, nor is one from another tool.
        assertThat(ReferenceResults.isReference(ToolExecutionResultMessage.from("1", "read_document",
                "x".repeat(ReferenceResults.MAX_CHARS + 1)))).isFalse();
        assertThat(ReferenceResults.isReference(ToolExecutionResultMessage.from("1", "search", "x"))).isFalse();
        assertThat(ReferenceResults.isReference(ToolExecutionResultMessage.from("1", "read_document",
                McpAgentChat.SHORTENED_RESULT))).isFalse();
    }

    @Test
    void aSummaryOfEarlierStepsKeepsWhatWasReadForReferenceAsItWas() {
        final List<ChatMessage> conversation = new ArrayList<>(List.of(UserMessage.from("q")));
        conversation.add(AiMessage.from("Step 1", List.of(call("1", "read_document"))));
        conversation.add(ToolExecutionResultMessage.from("1", "read_document", "Network: Permit, Deny, Payload"));
        for (int n = 2; n <= 6; n++) {
            conversation.add(AiMessage.from("Step " + n, List.of(call(String.valueOf(n), "search"))));
            conversation.add(ToolExecutionResultMessage.from(String.valueOf(n), "search", "result " + n));
        }
        final int keptFrom = indexOfStep(conversation, "Step 5");

        McpAgentChat.withTurnSummary(conversation, keptFrom, "First summary.");

        final String note = ((UserMessage) conversation.get(1)).singleText();
        assertThat(note)
                .startsWith(McpAgentChat.TURN_SUMMARY_NOTE + "\nFirst summary.\n\n" + ReferenceResults.KEPT_INTRO)
                .contains("- read_document({\"element\":\"EventDetail/Network\"}):\nNetwork: Permit, Deny, Payload")
                .doesNotContain("result 2");

        // A second summary carries it on, and the model isn't given it to summarise.
        for (int n = 7; n <= 9; n++) {
            conversation.add(AiMessage.from("Step " + n, List.of(call(String.valueOf(n), "search"))));
            conversation.add(ToolExecutionResultMessage.from(String.valueOf(n), "search", "result " + n));
        }
        final int keptFrom2 = indexOfStep(conversation, "Step 8");
        assertThat(McpAgentChat.turnSummaryInput(conversation, keptFrom2))
                .startsWith("Summary so far:\nFirst summary.\n\nSteps since:")
                .doesNotContain("Permit");
        McpAgentChat.withTurnSummary(conversation, keptFrom2, "Second summary.");
        assertThat(((UserMessage) conversation.get(1)).singleText())
                .startsWith(McpAgentChat.TURN_SUMMARY_NOTE + "\nSecond summary.")
                .containsOnlyOnce("Network: Permit, Deny, Payload");
    }

    // Each tool result as "read_document" (kept for reference), "kept" or "shortened".
    private static List<String> resultTexts(final List<ChatMessage> conversation) {
        return conversation.stream()
                .filter(ToolExecutionResultMessage.class::isInstance)
                .map(m -> (ToolExecutionResultMessage) m)
                .map(r -> McpAgentChat.SHORTENED_RESULT.equals(r.text())
                        ? "shortened"
                        : r.toolName().equals("read_document")
                                ? "read_document"
                                : "kept")
                .toList();
    }

    private static int indexOfStep(final List<ChatMessage> conversation, final String text) {
        for (int i = 0; i < conversation.size(); i++) {
            if (conversation.get(i) instanceof final AiMessage ai && text.equals(ai.text())) {
                return i;
            }
        }
        throw new IllegalArgumentException(text);
    }

    private static ToolExecutionRequest call(final String id, final String name) {
        return ToolExecutionRequest.builder()
                .id(id)
                .name(name)
                .arguments("read_document".equals(name)
                        ? "{\"element\":\"EventDetail/Network\"}"
                        : "{}")
                .build();
    }
}
