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

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

import java.util.function.Consumer;

/// A streaming version of {@link StubChatModel}, for testing without a real model: it works out the stub's
/// response, then sends its reasoning and text a few characters at a time, so streaming can be seen in the UI.
///
/// Activated, like the stub, by setting `modelId = "__stub__"` on an OpenAIModel document.
class StubStreamingChatModel implements StreamingChatModel {

    private static final int CHUNK_CHARS = 12;
    private static final int CHUNK_DELAY_MS = 60;

    private final ChatModel stub;

    StubStreamingChatModel(final ChatModel stub) {
        this.stub = stub;
    }

    @Override
    public void doChat(final ChatRequest chatRequest, final StreamingChatResponseHandler handler) {
        try {
            final ChatResponse response = stub.chat(chatRequest);
            final String thinking = response.aiMessage().thinking();
            if (thinking != null) {
                sendInChunks(thinking, chunk -> handler.onPartialThinking(new PartialThinking(chunk)));
            }
            final String text = response.aiMessage().text();
            if (text != null) {
                sendInChunks(text, handler::onPartialResponse);
            }
            handler.onCompleteResponse(response);
        } catch (final RuntimeException e) {
            handler.onError(e);
        }
    }

    private static void sendInChunks(final String text, final Consumer<String> consumer) {
        for (int start = 0; start < text.length(); start += CHUNK_CHARS) {
            consumer.accept(text.substring(start, Math.min(text.length(), start + CHUNK_CHARS)));
            try {
                Thread.sleep(CHUNK_DELAY_MS);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
