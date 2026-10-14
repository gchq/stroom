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

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
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
    void testRecord_noUsageReported() {
        tracker.record(1, null, List.of(UserMessage.from("hi")), null,
                ChatResponse.builder().aiMessage(AiMessage.from("hello")).build());

        Mockito.verifyNoInteractions(aiDao, runManager);
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
