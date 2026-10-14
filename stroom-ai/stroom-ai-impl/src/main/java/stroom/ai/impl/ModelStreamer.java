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
import stroom.ai.shared.AiMessageType;
import stroom.task.api.TaskTerminatedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialThinkingContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

/// Calls a model that streams its answer, writing the answer and reasoning so far into
/// [AiMessageType#STREAMING_ANSWER] and [AiMessageType#STREAMING_THINKING] messages as they arrive, so the user
/// can watch them being written. The messages are removed when the call ends, as the caller stores the finished
/// answer and reasoning.
///
/// The messages are written from the calling thread, at most every [#FLUSH_INTERVAL], rather than for every
/// chunk the model sends.
class ModelStreamer {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ModelStreamer.class);

    static final Duration FLUSH_INTERVAL = Duration.ofMillis(300);
    private static final Duration MAX_WAIT = Duration.ofMinutes(30);

    private final AiService aiService;

    @Inject
    ModelStreamer(final AiService aiService) {
        this.aiService = aiService;
    }

    /// Calls the model, streaming its answer and reasoning into the chat as they arrive.
    ///
    /// @param model         The model.
    /// @param request       The request.
    /// @param chatId        The chat to stream into.
    /// @param showReasoning True if the model's reasoning is to be shown as it arrives.
    /// @param cancelled     Says if the user has stopped the run.
    /// @return The model's response, with how long it spent thinking.
    /// @throws TaskTerminatedException If the run was stopped.
    StreamedResponse chat(final StreamingChatModel model,
                          final ChatRequest request,
                          final int chatId,
                          final boolean showReasoning,
                          final BooleanSupplier cancelled) {
        final Collector collector = new Collector();
        final long startMs = System.currentTimeMillis();
        // Some models stream on the calling thread, so the call is made on its own thread to leave this one free
        // to write what has arrived.
        Thread.ofVirtual()
                .name("ai-model-stream-" + chatId)
                .start(() -> {
                    try {
                        model.chat(request, collector);
                    } catch (final RuntimeException e) {
                        collector.onError(e);
                    }
                });
        final StreamedRow answerRow = new StreamedRow(chatId, AiMessageType.STREAMING_ANSWER);
        final StreamedRow thinkingRow = new StreamedRow(chatId, AiMessageType.STREAMING_THINKING);
        try {
            while (true) {
                try {
                    final ChatResponse response = collector.future.get(FLUSH_INTERVAL.toMillis(),
                            TimeUnit.MILLISECONDS);
                    return new StreamedResponse(response, collector.thinkingMs(System.currentTimeMillis()));
                } catch (final TimeoutException e) {
                    if (cancelled.getAsBoolean()) {
                        collector.cancel();
                        throw new TaskTerminatedException();
                    }
                    if (System.currentTimeMillis() - startMs > MAX_WAIT.toMillis()) {
                        collector.cancel();
                        throw new RuntimeException("The model did not finish answering within "
                                                   + MAX_WAIT.toMinutes() + " minutes");
                    }
                    if (showReasoning) {
                        thinkingRow.write(collector.thinking());
                    }
                    answerRow.write(collector.answer());
                }
            }
        } catch (final ExecutionException e) {
            if (e.getCause() instanceof final RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(e.getCause());
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            collector.cancel();
            throw new TaskTerminatedException();
        } finally {
            thinkingRow.delete();
            answerRow.delete();
        }
    }


    // --------------------------------------------------------------------------------


    /// The model's response, and how long it spent thinking (0 if it didn't, or that is not known).
    record StreamedResponse(ChatResponse response, long thinkingMs) {

    }


    // --------------------------------------------------------------------------------


    /// A message holding what has been streamed so far, written only when it changes.
    private final class StreamedRow {

        private final int chatId;
        private final AiMessageType type;
        private Integer messageId;
        private String written = "";

        private StreamedRow(final int chatId, final AiMessageType type) {
            this.chatId = chatId;
            this.type = type;
        }

        private void write(final String text) {
            if (text.isEmpty() || text.equals(written)) {
                return;
            }
            try {
                if (messageId == null) {
                    messageId = aiService.storeMessage(chatId, type, text).getId();
                } else {
                    aiService.updateMessageText(messageId, text);
                }
                written = text;
            } catch (final RuntimeException e) {
                // Streaming is only to show progress, so the answer carries on without it.
                LOGGER.debug(() -> "Unable to write the " + type + " message for chatId=" + chatId, e);
            }
        }

        private void delete() {
            if (messageId != null) {
                try {
                    aiService.deleteMessage(messageId);
                } catch (final RuntimeException e) {
                    LOGGER.debug(() -> "Unable to delete the " + type + " message for chatId=" + chatId, e);
                }
            }
        }
    }


    // --------------------------------------------------------------------------------


    /// Collects what the model sends, on whichever thread it sends it.
    private static final class Collector implements StreamingChatResponseHandler {

        private final CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        private final StringBuilder answer = new StringBuilder();
        private final StringBuilder thinking = new StringBuilder();
        private long thinkingStartMs;
        private long thinkingEndMs;
        private volatile StreamingHandle handle;

        @Override
        public void onPartialResponse(final PartialResponse partialResponse, final PartialResponseContext context) {
            handle = context.streamingHandle();
            onPartialResponse(partialResponse.text());
        }

        @Override
        public synchronized void onPartialResponse(final String partialResponse) {
            if (thinkingStartMs > 0 && thinkingEndMs == 0) {
                thinkingEndMs = System.currentTimeMillis();
            }
            if (partialResponse != null) {
                answer.append(partialResponse);
            }
        }

        @Override
        public void onPartialThinking(final PartialThinking partialThinking, final PartialThinkingContext context) {
            handle = context.streamingHandle();
            onPartialThinking(partialThinking);
        }

        @Override
        public synchronized void onPartialThinking(final PartialThinking partialThinking) {
            if (thinkingStartMs == 0) {
                thinkingStartMs = System.currentTimeMillis();
            }
            if (partialThinking != null && partialThinking.text() != null) {
                thinking.append(partialThinking.text());
            }
        }

        @Override
        public void onCompleteResponse(final ChatResponse completeResponse) {
            future.complete(completeResponse);
        }

        @Override
        public void onError(final Throwable error) {
            future.completeExceptionally(error);
        }

        private synchronized String answer() {
            return answer.toString();
        }

        private synchronized String thinking() {
            return thinking.toString();
        }

        private synchronized long thinkingMs(final long nowMs) {
            if (thinkingStartMs == 0) {
                return 0;
            }
            return (thinkingEndMs > 0
                    ? thinkingEndMs
                    : nowMs) - thinkingStartMs;
        }

        private void cancel() {
            final StreamingHandle streamingHandle = handle;
            if (streamingHandle != null && !streamingHandle.isCancelled()) {
                try {
                    streamingHandle.cancel();
                } catch (final RuntimeException e) {
                    LOGGER.debug("Unable to cancel the model's stream", e);
                }
            }
        }
    }
}
