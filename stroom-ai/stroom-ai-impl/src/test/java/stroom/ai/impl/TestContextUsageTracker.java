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

import stroom.openai.shared.OpenAIModelDoc;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestContextUsageTracker {

    private final AiDao aiDao = Mockito.mock(AiDao.class);
    private final AiChatRunManager runManager = Mockito.mock(AiChatRunManager.class);
    private final ContextUsageTracker tracker = new ContextUsageTracker(aiDao, runManager);

    @Test
    void testRecord_storesUsageAndCorrection() {
        final List<ChatMessage> sent = List.of(UserMessage.from("x".repeat(400)));
        final OpenAIModelDoc modelDoc = OpenAIModelDoc.builder().uuid("m1").maxContextWindowTokens(8_000).build();

        tracker.record(1, modelDoc, sent, null, response(150, 20));

        Mockito.verify(aiDao).recordUsage(1, "m1", 8_000, 150, 20);
        Mockito.verify(runManager).addUsage(1, 150, 20, 0);
        final int estimate = new stroom.ai.api.SimpleTokenCountEstimator().estimateTokenCountInMessages(sent);
        assertThat(tracker.getCorrection(1)).contains(150 / (double) estimate);
        assertThat(tracker.getCorrection(2)).isEmpty();
    }

    @Test
    void testRecordOverflow_correctsEstimatesUpwardsFromTheReportedSize() {
        final List<ChatMessage> sent = List.of(UserMessage.from("x".repeat(400)));
        final long estimate = tracker.estimate(1, sent, null);

        assertThat(tracker.recordOverflow(1, sent, null, new RuntimeException("wrapped", new RuntimeException(
                "you requested 999 tokens (" + estimate * 3 + " in the messages, 0 in the completion)")), 8_000))
                .hasValue(estimate * 3);
        assertThat(tracker.estimate(1, sent, null)).isEqualTo(estimate * 3);
        // A smaller size never lowers the correction.
        tracker.recordOverflow(1, sent, null, new RuntimeException("your request has " + estimate
                                                                   + " input tokens"), 8_000);
        assertThat(tracker.estimate(1, sent, null)).isEqualTo(estimate * 3);
    }

    @Test
    void testRecordOverflow_withoutASizeTakesTheRequestAsLargerThanTheWindow() {
        final List<ChatMessage> sent = List.of(UserMessage.from("x".repeat(400)));

        assertThat(tracker.recordOverflow(1, sent, null, new RuntimeException("context_length_exceeded"), 1_000))
                .isEmpty();
        assertThat(tracker.estimate(1, sent, null))
                .isEqualTo(Math.round(1_000 * ContextUsageTracker.UNKNOWN_OVERFLOW_FACTOR));
    }

    @Test
    void testOverflowSize() {
        assertThat(ContextUsageTracker.overflowSize(new RuntimeException("prompt is too long: 140000 tokens > "
                                                                         + "131072 maximum"))).hasValue(140_000);
        assertThat(ContextUsageTracker.overflowSize(new RuntimeException("requested 5000 tokens"))).hasValue(5_000);
        assertThat(ContextUsageTracker.overflowSize(new RuntimeException("too large"))).isEmpty();
        assertThat(ContextUsageTracker.overflowSize(new RuntimeException((String) null))).isEmpty();
    }

    @Test
    void testEstimateCautiously_errsLargeUntilACallIsMeasured() {
        final List<ChatMessage> sent = List.of(UserMessage.from("x".repeat(400)));
        final long estimate = tracker.estimate(1, sent, null);

        assertThat(tracker.estimateCautiously(1, sent, null))
                .isEqualTo(Math.round(estimate * ContextUsageTracker.UNMEASURED_SAFETY_FACTOR));
        tracker.record(1, null, sent, null, ChatResponse.builder()
                .aiMessage(AiMessage.from("ok"))
                .tokenUsage(new TokenUsage((int) estimate, 1))
                .build());
        assertThat(tracker.estimateCautiously(1, sent, null)).isEqualTo(estimate);
    }

    @Test
    void testLastCallAndShortenedPartsAreKeptForTheMeter() {
        final List<ChatMessage> sent = List.of(UserMessage.from("x".repeat(400)));
        tracker.record(1, null, sent, null, ChatResponse.builder()
                .aiMessage(AiMessage.from("ok"))
                .tokenUsage(new TokenUsage(123, 1))
                .build());

        assertThat(tracker.getLastCall(1)).hasValueSatisfying(call -> assertThat(call.inputTokens()).isEqualTo(123));
        tracker.recordShortened(1, 2);
        tracker.recordShortened(1, 0);
        tracker.recordShortened(1, 3);
        assertThat(tracker.getShortenedParts(1)).isEqualTo(5);
        tracker.resetShortened(1);
        assertThat(tracker.getShortenedParts(1)).isZero();
        tracker.recordShortened(1, 1);
        tracker.forget(1);
        assertThat(tracker.getLastCall(1)).isEmpty();
        assertThat(tracker.getShortenedParts(1)).isZero();
    }

    @Test
    void testRecord_keepsTheLargestToolsAndTheStepsAsSent() {
        final List<ToolSpecification> tools = List.of(
                ToolSpecification.builder().name("small").description("s").build(),
                ToolSpecification.builder().name("large").description("l".repeat(4_000)).build(),
                ToolSpecification.builder().name("middle").description("m".repeat(400)).build());
        final ToolExecutionRequest call = ToolExecutionRequest.builder().id("c1").name("small").arguments("{}")
                .build();
        final List<ChatMessage> sent = List.of(
                UserMessage.from("q".repeat(4_000)),
                AiMessage.from("looking", List.of(call)),
                ToolExecutionResultMessage.from(call, "r".repeat(400)),
                UserMessage.from(McpAgentChat.COLLAPSED_STEPS_NOTE + "\n- small({}) -> r"));

        tracker.record(1, null, sent, tools, ChatResponse.builder()
                .aiMessage(AiMessage.from("ok"))
                .tokenUsage(new TokenUsage(2_000, 10))
                .build());

        assertThat(tracker.getLargestTools(1).keySet()).containsExactly("large", "middle", "small");
        assertThat(tracker.getLargestTools(1).values().stream().mapToLong(Long::longValue).sum())
                .isEqualTo(tracker.getToolDefinitionTokens(1));
        // The question isn't one of its steps; the call, its result and the note of earlier steps are.
        assertThat(tracker.getSentStepTokens(1)).hasValueSatisfying(tokens -> assertThat(tokens)
                .isGreaterThan(100L)
                .isLessThan(1_000L));
        assertThat(ContextUsageTracker.largest(java.util.Map.of("a", 1L, "b", 3L, "c", 2L), 2).keySet())
                .containsExactly("b", "c");

        tracker.forget(1);
        assertThat(tracker.getLargestTools(1)).isEmpty();
        assertThat(tracker.getSentStepTokens(1)).isEmpty();
    }

    @Test
    void testRecord_noUsageReported() {
        tracker.record(1, null, List.of(UserMessage.from("hi")), null,
                ChatResponse.builder().aiMessage(AiMessage.from("hello")).build());

        Mockito.verifyNoInteractions(aiDao, runManager);
        assertThat(tracker.getCorrection(1)).isEmpty();
    }

    @Test
    void testRecord_noUsageReportedStillRecordsTheModelsWindow() {
        final OpenAIModelDoc modelDoc = OpenAIModelDoc.builder().uuid("m1").maxContextWindowTokens(8_000).build();

        tracker.record(1, modelDoc, List.of(UserMessage.from("hi")), null,
                ChatResponse.builder().aiMessage(AiMessage.from("hello")).build());

        Mockito.verify(aiDao).recordModel(1, "m1", 8_000);
        Mockito.verify(aiDao, Mockito.never()).recordUsage(Mockito.anyInt(), Mockito.any(), Mockito.anyInt(),
                Mockito.anyLong(), Mockito.anyLong());
        assertThat(tracker.getCorrection(1)).isEmpty();
    }

    @Test
    void testToolDefinitionsAreCounted() {
        final List<ToolSpecification> tools = List.of(
                ToolSpecification.builder().name("search").description("Searches for things".repeat(10)).build());

        tracker.record(3, null, List.of(UserMessage.from("hi")), tools, response(500, 1));

        assertThat(tracker.getToolDefinitionTokens(3))
                .isPositive()
                .isEqualTo(ContextUsageTracker.estimateTools(tools));
        assertThat(ContextUsageTracker.estimateTools(null)).isZero();

        tracker.forget(3);
        assertThat(tracker.getToolDefinitionTokens(3)).isZero();
        assertThat(tracker.getCorrection(3)).isEmpty();
    }

    private static ChatResponse response(final int input, final int output) {
        return ChatResponse.builder()
                .aiMessage(AiMessage.from("answer"))
                .tokenUsage(new TokenUsage(input, output))
                .build();
    }
}
