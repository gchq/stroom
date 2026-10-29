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

class TestTurnSummaries {

    private static final String NOTE = McpAgentChat.TURN_SUMMARY_NOTE + "\nDid steps 1 to 3.";

    private final TurnSummaries turnSummaries = new TurnSummaries();

    @Test
    void aSummaryIsPutBackInPlaceOfTheStepsItCovers() {
        rememberStepsOneToThree(1, conversation("a", 6));

        final List<ChatMessage> rebuilt = conversation("a", 6);
        assertThat(turnSummaries.putBack(1, rebuilt)).isEqualTo(3);

        assertThat(rebuilt).hasSize(1 + 1 + 3 * 2);
        assertThat(((UserMessage) rebuilt.get(1)).singleText()).isEqualTo(NOTE);
        assertThat(((AiMessage) rebuilt.get(2)).text()).isEqualTo("Step 4");
    }

    @Test
    void stepsTakenSinceTheSummaryFollowIt() {
        rememberStepsOneToThree(1, conversation("a", 6));

        final List<ChatMessage> rebuilt = conversation("a", 9);
        assertThat(turnSummaries.putBack(1, rebuilt)).isEqualTo(3);

        assertThat(rebuilt.stream().filter(AiMessage.class::isInstance).map(m -> ((AiMessage) m).text()).toList())
                .containsExactly("Step 4", "Step 5", "Step 6", "Step 7", "Step 8", "Step 9");
    }

    @Test
    void nothingIsPutBackInAChatWithNoSummary() {
        final List<ChatMessage> rebuilt = conversation("a", 6);

        assertThat(turnSummaries.putBack(1, rebuilt)).isZero();
        assertThat(rebuilt).isEqualTo(conversation("a", 6));
    }

    @Test
    void aSummaryIsKeptForItsOwnChat() {
        rememberStepsOneToThree(1, conversation("a", 6));

        final List<ChatMessage> rebuilt = conversation("a", 6);
        assertThat(turnSummaries.putBack(2, rebuilt)).isZero();
        assertThat(rebuilt).isEqualTo(conversation("a", 6));
    }

    @Test
    void aSummaryOfStepsTheConversationDoesNotHaveIsDropped() {
        // E.g. the chat has gone on to another question, or the question was edited.
        rememberStepsOneToThree(1, conversation("a", 6));

        final List<ChatMessage> other = conversation("b", 6);
        assertThat(turnSummaries.putBack(1, other)).isZero();
        assertThat(other).isEqualTo(conversation("b", 6));

        assertThat(turnSummaries.putBack(1, conversation("a", 6))).isZero();
    }

    @Test
    void aSummaryIsNotPutBackWhenNoStepFollowsTheStepsItCovers() {
        rememberStepsOneToThree(1, conversation("a", 6));

        final List<ChatMessage> rebuilt = conversation("a", 3);
        assertThat(turnSummaries.putBack(1, rebuilt)).isZero();
        assertThat(rebuilt).isEqualTo(conversation("a", 3));
    }

    @Test
    void theLatestSummaryIsPutBack() {
        rememberStepsOneToThree(1, conversation("a", 6));
        final List<ChatMessage> later = conversation("a", 9);
        final String laterNote = McpAgentChat.TURN_SUMMARY_NOTE + "\nDid steps 1 to 5.";
        turnSummaries.remember(1, later, stepIndex(later, 6), laterNote);

        final List<ChatMessage> rebuilt = conversation("a", 9);
        assertThat(turnSummaries.putBack(1, rebuilt)).isEqualTo(5);
        assertThat(((UserMessage) rebuilt.get(1)).singleText()).isEqualTo(laterNote);
        assertThat(((AiMessage) rebuilt.get(2)).text()).isEqualTo("Step 6");
    }

    @Test
    void aStepWithSeveralCallsIsFoundByItsLastCall() {
        final List<ChatMessage> conversation = new ArrayList<>(List.of(UserMessage.from("q"),
                AiMessage.from("Step 1", List.of(call("x"), call("y"))),
                ToolExecutionResultMessage.from("x", "t", "r"),
                ToolExecutionResultMessage.from("y", "t", "r"),
                AiMessage.from("Step 2", List.of(call("z"))),
                ToolExecutionResultMessage.from("z", "t", "r")));
        turnSummaries.remember(1, new ArrayList<>(conversation), 4, NOTE);

        assertThat(turnSummaries.putBack(1, conversation)).isEqualTo(1);
        assertThat(((AiMessage) conversation.get(2)).text()).isEqualTo("Step 2");
    }

    @Test
    void aSummaryWithNoStepsBeforeItIsNotKept() {
        rememberStepsOneToThree(1, conversation("a", 6));
        turnSummaries.remember(1, conversation("a", 6), 1, NOTE);

        assertThat(turnSummaries.putBack(1, conversation("a", 6))).isZero();
    }

    @Test
    void theSummariesOfTheLeastRecentlyUsedChatsAreDropped() {
        for (int chatId = 1; chatId <= TurnSummaries.MAX_CHATS + 1; chatId++) {
            rememberStepsOneToThree(chatId, conversation("a", 6));
        }

        assertThat(turnSummaries.putBack(1, conversation("a", 6))).isZero();
        assertThat(turnSummaries.putBack(2, conversation("a", 6))).isEqualTo(3);
    }

    // Keeps a summary of the first three steps.
    private void rememberStepsOneToThree(final int chatId, final List<ChatMessage> conversation) {
        turnSummaries.remember(chatId, conversation, stepIndex(conversation, 4), NOTE);
    }

    // A question and the given number of steps, each one call (with ids that start with the prefix) and its result.
    private static List<ChatMessage> conversation(final String idPrefix, final int steps) {
        final List<ChatMessage> conversation = new ArrayList<>(List.of(UserMessage.from("q")));
        for (int n = 1; n <= steps; n++) {
            conversation.add(AiMessage.from("Step " + n, List.of(call(idPrefix + n))));
            conversation.add(ToolExecutionResultMessage.from(idPrefix + n, "t", "result " + n));
        }
        return conversation;
    }

    private static int stepIndex(final List<ChatMessage> conversation, final int step) {
        return conversation.indexOf(conversation.stream()
                .filter(m -> m instanceof final AiMessage ai && ("Step " + step).equals(ai.text()))
                .findFirst()
                .orElseThrow());
    }

    private static ToolExecutionRequest call(final String id) {
        return ToolExecutionRequest.builder().id(id).name("t").arguments("{}").build();
    }
}
