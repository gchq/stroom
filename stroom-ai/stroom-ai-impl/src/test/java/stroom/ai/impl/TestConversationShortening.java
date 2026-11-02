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

import stroom.util.json.JsonUtil;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestConversationShortening {

    @Test
    void shortenToolResultsKeepsTheLatestAndShortOnes() {
        final List<ChatMessage> conversation = new ArrayList<>(List.of(
                UserMessage.from("q"),
                ToolExecutionResultMessage.from("1", "t", "x".repeat(1_000)),
                ToolExecutionResultMessage.from("2", "t", "short"),
                ToolExecutionResultMessage.from("3", "t", "y".repeat(1_000)),
                ToolExecutionResultMessage.from("4", "t", "z".repeat(1_000))));

        assertThat(ConversationShortening.shortenToolResults(conversation, 2, true)).isEqualTo(1);
        assertThat(((ToolExecutionResultMessage) conversation.get(1)).text())
                .isEqualTo(ConversationShortening.SHORTENED_RESULT);
        assertThat(((ToolExecutionResultMessage) conversation.get(1)).id())
                .isEqualTo("1");
        assertThat(((ToolExecutionResultMessage) conversation.get(2)).text())
                .isEqualTo("short");
        assertThat(((ToolExecutionResultMessage) conversation.get(3)).text())
                .startsWith("y");

        assertThat(ConversationShortening.shortenToolResults(conversation, 0, true)).isEqualTo(2);
        assertThat(ConversationShortening.shortenToolResults(conversation, 0, true)).isZero();
    }

    @Test
    void whatIsGivenToBeSummarisedIsLimitedToTheLatestStepsThatFit() {
        final List<ChatMessage> conversation = new ArrayList<>(List.of(UserMessage.from("q")));
        for (int n = 1; n <= 100; n++) {
            conversation.add(AiMessage.from("Step " + n, List.of(request(String.valueOf(n), "{}", "search"))));
            conversation.add(ToolExecutionResultMessage.from(String.valueOf(n), "search", "r".repeat(2_000)));
        }

        final String input = ConversationShortening.turnSummaryInput(conversation, conversation.size());

        assertThat(input.length()).isLessThanOrEqualTo(ConversationShortening.SUMMARY_INPUT_MAX_CHARS + 200);
        assertThat(input)
                .contains("earliest part(s) are left out")
                .contains("You said: Step 100")
                .doesNotContain("You said: Step 1\n");
    }

    @Test
    void aSecondSummaryFoldsInTheFirstAndKeepsWhatTheUserSent() {
        final List<ChatMessage> conversation = new ArrayList<>(List.of(UserMessage.from("q")));
        conversation.add(UserMessage.from(ConversationShortening.TURN_SUMMARY_NOTE + "\nFirst summary."));
        for (int n = 1; n <= 8; n++) {
            conversation.add(AiMessage.from("Step " + n, List.of(request(String.valueOf(n), "{\"n\":" + n + "}"))));
            conversation.add(ToolExecutionResultMessage.from(String.valueOf(n), "t", "result " + n));
            if (n == 2) {
                conversation.add(UserMessage.from(McpAgentChat.STEER_PREFIX + "also fw2"));
            }
        }
        final int keptFrom = conversation.indexOf(conversation.stream()
                .filter(m -> m instanceof final AiMessage ai && "Step 5".equals(ai.text()))
                .findFirst()
                .orElseThrow());

        final String input = ConversationShortening.turnSummaryInput(conversation, keptFrom);
        assertThat(input)
                .startsWith("Summary so far:\nFirst summary.\n\nSteps since:")
                .contains("You said: Step 4")
                .doesNotContain("Step 5")
                .contains("Called t with {\"n\":1}")
                .contains("Result of t: result 4");

        assertThat(ConversationShortening.withTurnSummary(conversation, keptFrom, "Second summary.")).isEqualTo(4);
        assertThat(conversation).hasSize(1 + 1 + 1 + 8);
        assertThat(((UserMessage) conversation.get(1)).singleText())
                .isEqualTo(ConversationShortening.TURN_SUMMARY_NOTE + "\nSecond summary.");
        // What the user sent while the run went on stays, as it was.
        assertThat(((UserMessage) conversation.get(2)).singleText()).isEqualTo(McpAgentChat.STEER_PREFIX + "also fw2");
        assertThat(((AiMessage) conversation.get(3)).text()).isEqualTo("Step 5");
    }

    @Test
    void aSummaryLeavesWhatToDoNextToTheStepsKeptAfterIt() {
        // Seen: a summary of the steps before the latest few said the next step was to reprocess a stream, which a
        // step kept after it had done, and the model did it again.
        final List<ChatMessage> conversation = new ArrayList<>(List.of(UserMessage.from("q")));
        for (int n = 1; n <= 6; n++) {
            conversation.add(AiMessage.from("Step " + n, List.of(request(String.valueOf(n), "{\"n\":" + n + "}"))));
            conversation.add(ToolExecutionResultMessage.from(String.valueOf(n), "t", "result " + n));
        }
        final int keptFrom = conversation.indexOf(conversation.stream()
                .filter(m -> m instanceof final AiMessage ai && "Step 3".equals(ai.text()))
                .findFirst()
                .orElseThrow());

        ConversationShortening.withTurnSummary(conversation, keptFrom, "Next: reprocess the stream.");

        // The note says the kept steps came later and win over the summary, and they follow it.
        assertThat(((UserMessage) conversation.get(1)).singleText())
                .startsWith(ConversationShortening.TURN_SUMMARY_NOTE)
                .contains("The steps after this note came later")
                .contains("what they did is done even if the summary says it is still to do")
                .contains("Carry on from the latest of them");
        assertThat(((AiMessage) conversation.get(2)).text()).isEqualTo("Step 3");
        // The summary is asked for what was left when its steps ended, not for the next step.
        assertThat(McpAgentChat.TURN_SUMMARY_PROMPT)
                .contains("what was left to do when these steps ended")
                .contains("don't say what to do next")
                .doesNotContain("and the next step");
    }

    @Test
    void shrinkTakesTheLeastLossyStepFirstAndKeepsTheLatestStep() {
        final List<ChatMessage> conversation = new ArrayList<>(List.of(
                UserMessage.from("q"),
                AiMessage.from("Looking", List.of(request("1", "{\"code\":\"" + "c".repeat(1_000) + "\"}"))),
                ToolExecutionResultMessage.from("1", "t", "x".repeat(1_000)),
                AiMessage.from(List.of(request("2", "{}"))),
                ToolExecutionResultMessage.from("2", "t", "y".repeat(1_000)),
                UserMessage.from(McpAgentChat.STEER_PREFIX + "also fw2"),
                AiMessage.from(List.of(request("3", "{\"code\":\"" + "d".repeat(1_000) + "\"}"))),
                ToolExecutionResultMessage.from("3", "t", "z".repeat(1_000))));

        // 1: results but the latest two.
        assertThat(ConversationShortening.shrink(conversation)).isEqualTo(1);
        // 2: the arguments of all but the latest step, still as JSON.
        assertThat(ConversationShortening.shrink(conversation)).isEqualTo(1);
        final String arguments = ((AiMessage) conversation.get(1)).toolExecutionRequests().getFirst().arguments();
        assertThat(JsonUtil.readValue(arguments, Map.class))
                .containsKey(ConversationShortening.SHORTENED_ARGUMENTS_KEY);
        assertThat(((AiMessage) conversation.get(6)).toolExecutionRequests().getFirst().arguments())
                .hasSizeGreaterThan(1_000);
        // 3: results but the latest.
        assertThat(ConversationShortening.shrink(conversation)).isEqualTo(1);
        // 4: the earlier steps, next to each other, put in one note, before what the user said.
        assertThat(ConversationShortening.shrink(conversation)).isEqualTo(2);
        assertThat(conversation).hasSize(5);
        assertThat(((UserMessage) conversation.get(1)).singleText())
                .startsWith(ConversationShortening.COLLAPSED_STEPS_NOTE)
                .contains("- You said: Looking")
                .contains("- t(");
        assertThat(((UserMessage) conversation.get(2)).singleText()).startsWith(McpAgentChat.STEER_PREFIX);
        assertThat(conversation.get(3)).isInstanceOf(AiMessage.class);
        assertThat(((ToolExecutionResultMessage) conversation.get(4)).text()).isEqualTo("z".repeat(1_000));
        // Nothing more to shorten.
        assertThat(ConversationShortening.shrink(conversation)).isZero();
    }

    private static ToolExecutionRequest request(final String id, final String arguments) {
        return request(id, arguments, "t");
    }

    private static ToolExecutionRequest request(final String id, final String arguments, final String name) {
        return ToolExecutionRequest.builder().id(id).name(name).arguments(arguments).build();
    }
}
