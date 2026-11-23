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

import stroom.ai.api.AiService;
import stroom.ai.impl.tools.ModelSideCalls;
import stroom.ai.impl.tools.ToolRunContext;
import stroom.task.api.TaskTerminatedException;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// A chat's side calls ([ChatModelSideCalls]): made to the run's model as step and compaction summaries are
/// ([ModelStreamer#callUnseen]), outside the chat's conversation, their tokens added to the run's usage; none
/// without a model.
class TestChatModelSideCalls {

    private final ModelStreamer modelStreamer = Mockito.mock(ModelStreamer.class);
    private final AiChatRunManager runManager = Mockito.mock(AiChatRunManager.class);
    private final ChatModelSideCalls sideCalls = new ChatModelSideCalls(Mockito.mock(AiService.class),
            modelStreamer, () -> runManager);
    private final ChatModel chatModel = Mockito.mock(ChatModel.class);

    @Test
    void aSideCallGoesToTheRunsModelUnseenAndItsTokensCountTowardsTheRun() {
        Mockito.when(modelStreamer.callUnseen(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyInt(),
                Mockito.any())).thenReturn(ChatResponse.builder()
                .aiMessage(AiMessage.from("  [{\"item\": \"x\"}]  "))
                .tokenUsage(new TokenUsage(120, 30))
                .build());
        final ModelSideCalls calls = sideCalls.forRun(7, new ToolRunContext(chatModel, null, () -> false));

        final ModelSideCalls.Answer answer = calls.ask(new ModelSideCalls.Question("Classify.", "the items", 500));

        assertThat(calls.available()).isTrue();
        assertThat(calls.via()).isEqualTo("the chat's model");
        assertThat(answer.text()).isEqualTo("[{\"item\": \"x\"}]");
        assertThat(answer.inputTokens()).isEqualTo(120);
        final ArgumentCaptor<ChatRequest> request = ArgumentCaptor.forClass(ChatRequest.class);
        Mockito.verify(modelStreamer).callUnseen(Mockito.eq(chatModel), Mockito.isNull(), request.capture(),
                Mockito.eq(7), Mockito.any());
        assertThat(request.getValue().messages()).containsExactly(new SystemMessage("Classify."),
                new UserMessage("the items"));
        assertThat(request.getValue().maxOutputTokens()).isEqualTo(500);
        Mockito.verify(runManager).addUsage(7, 120, 30, 0);
    }

    @Test
    void anEmptyOrFailedAnswerIsASideCallExceptionAndAStopEndsTheRun() {
        final ModelSideCalls calls = sideCalls.forRun(7, new ToolRunContext(chatModel, null, () -> false));
        Mockito.when(modelStreamer.callUnseen(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyInt(),
                Mockito.any())).thenReturn(ChatResponse.builder().aiMessage(AiMessage.from(" ")).build());
        assertThatThrownBy(() -> calls.ask(new ModelSideCalls.Question("s", "u", 10)))
                .isInstanceOf(ModelSideCalls.SideCallException.class)
                .hasMessage("the model gave an empty answer");

        Mockito.when(modelStreamer.callUnseen(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyInt(),
                Mockito.any())).thenThrow(new IllegalStateException("rate limited"));
        assertThatThrownBy(() -> calls.ask(new ModelSideCalls.Question("s", "u", 10)))
                .isInstanceOf(ModelSideCalls.SideCallException.class)
                .hasMessage("rate limited");

        Mockito.reset(modelStreamer);
        Mockito.when(modelStreamer.callUnseen(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyInt(),
                Mockito.any())).thenThrow(new TaskTerminatedException());
        assertThatThrownBy(() -> calls.ask(new ModelSideCalls.Question("s", "u", 10)))
                .isInstanceOf(TaskTerminatedException.class);
    }

    @Test
    void aRunWithNoModelHasNoSideCalls() {
        assertThat(sideCalls.forRun(7, new ToolRunContext(null, null, () -> false))).isSameAs(ModelSideCalls.NONE);
        assertThat(sideCalls.forRun(7, null)).isSameAs(ModelSideCalls.NONE);
        assertThat(ModelSideCalls.NONE.available()).isFalse();
        assertThatThrownBy(() -> ModelSideCalls.NONE.ask(new ModelSideCalls.Question("s", "u", 1)))
                .isInstanceOf(ModelSideCalls.SideCallException.class);
    }
}
