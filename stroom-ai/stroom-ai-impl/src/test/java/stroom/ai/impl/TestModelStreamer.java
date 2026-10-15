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
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiMessageType;
import stroom.task.api.TaskTerminatedException;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestModelStreamer {

    private final AiService aiService = Mockito.mock(AiService.class);
    private final ModelStreamer modelStreamer = new ModelStreamer(aiService);
    private final AtomicInteger nextId = new AtomicInteger(100);
    private final List<String> written = new ArrayList<>();
    private final ChatRequest request = ChatRequest.builder()
            .messages(List.of(UserMessage.from("hello")))
            .build();

    {
        Mockito.when(aiService.storeMessage(Mockito.anyInt(), Mockito.any(), Mockito.anyString()))
                .thenAnswer(invocation -> {
                    written.add(invocation.getArgument(1) + ":" + invocation.getArgument(2));
                    return new AiChatMessage(nextId.getAndIncrement(), invocation.getArgument(0), 0L,
                            invocation.getArgument(1), null, invocation.getArgument(2), 1, null);
                });
    }

    @Test
    void theAnswerAndReasoningAreWrittenAsTheyArriveThenRemoved() {
        final String thinking = "Thinking about it. ".repeat(4);
        final String answer = "The answer is 42.";
        final StreamingChatModel model = slowModel(thinking, answer);

        final ModelStreamer.StreamedResponse streamed = modelStreamer.chat(model, request, 5, true, () -> false);

        assertThat(streamed.response().aiMessage().text()).isEqualTo(answer);
        assertThat(streamed.thinkingMs()).isPositive();
        // Each row holds what had arrived when it was written, which (on a busy machine) may be just a
        // character or two, so check it is the start of the text rather than some particular amount of it.
        assertThat(written).anySatisfy(row -> assertStartOf(row, "STREAMING_THINKING:", thinking));
        assertThat(written).anySatisfy(row -> assertStartOf(row, "STREAMING_ANSWER:", answer));
        // Both rows are removed once the call ends, as the caller stores the finished answer and reasoning.
        Mockito.verify(aiService, Mockito.times(written.size())).deleteMessage(Mockito.anyInt());
    }

    private static void assertStartOf(final String row, final String prefix, final String text) {
        assertThat(row).startsWith(prefix);
        final String written = row.substring(prefix.length());
        assertThat(written).isNotEmpty();
        assertThat(text).startsWith(written);
    }

    @Test
    void reasoningIsNotWrittenWhenNotShown() {
        final StreamingChatModel model = slowModel("Thinking about it. ".repeat(4), "The answer is 42.");

        modelStreamer.chat(model, request, 5, false, () -> false);

        assertThat(written).noneSatisfy(row -> assertThat(row).startsWith("STREAMING_THINKING"));
    }

    @Test
    void stoppingTheRunStopsTheCall() {
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        final StreamingChatModel model = slowModel("x".repeat(200), "never finished");

        new Thread(() -> {
            sleep(ModelStreamer.FLUSH_INTERVAL.toMillis() * 2);
            cancelled.set(true);
        }).start();

        assertThatThrownBy(() -> modelStreamer.chat(model, request, 5, true, cancelled::get))
                .isInstanceOf(TaskTerminatedException.class);
        Mockito.verify(aiService, Mockito.atLeastOnce()).deleteMessage(Mockito.anyInt());
    }

    @Test
    void anErrorFromTheModelIsThrown() {
        final StreamingChatModel model = new StreamingChatModel() {
            @Override
            public void doChat(final ChatRequest chatRequest, final StreamingChatResponseHandler handler) {
                handler.onError(new IllegalStateException("context_length_exceeded"));
            }
        };

        assertThatThrownBy(() -> modelStreamer.chat(model, request, 5, true, () -> false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("context_length_exceeded");
        Mockito.verify(aiService, Mockito.never()).storeMessage(Mockito.anyInt(), Mockito.any(),
                Mockito.anyString());
    }

    @Test
    void theStubStreamsItsThinking() {
        final ModelStreamer.StreamedResponse streamed = modelStreamer.chat(
                new StubStreamingChatModel(new StubChatModel()),
                ChatRequest.builder().messages(List.of(UserMessage.from("hello [[think]]"))).build(),
                5, true, () -> false);

        assertThat(streamed.response().aiMessage().thinking()).contains("Planning the answer");
        assertThat(streamed.response().aiMessage().text()).contains("You asked: hello");
    }

    /// A model that sends its reasoning, then its answer, a character at a time, slowly enough for several
    /// writes to be made while it does.
    private static StreamingChatModel slowModel(final String thinking, final String answer) {
        return new StreamingChatModel() {
            @Override
            public void doChat(final ChatRequest chatRequest, final StreamingChatResponseHandler handler) {
                for (final char c : thinking.toCharArray()) {
                    handler.onPartialThinking(new PartialThinking(String.valueOf(c)));
                    sleep(10);
                }
                for (final char c : answer.toCharArray()) {
                    handler.onPartialResponse(String.valueOf(c));
                    sleep(30);
                }
                handler.onCompleteResponse(ChatResponse.builder()
                        .aiMessage(AiMessage.builder().text(answer).thinking(thinking).build())
                        .build());
            }
        };
    }

    private static void sleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
