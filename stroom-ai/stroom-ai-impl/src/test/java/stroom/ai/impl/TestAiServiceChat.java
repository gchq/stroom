/*
 * Copyright 2016-2026 Crown Copyright
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

import stroom.cache.impl.CacheManagerImpl;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentActionHandler;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.docstore.shared.AbstractDoc;
import stroom.openai.shared.OpenAIModelDoc;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers what {@link AiServiceImpl#chat} asks the model and what it serves from the cache. A model call
 * costs money and minutes, so a question already asked must not be asked again, and a question that failed
 * must not be answered from the failure.
 */
class TestAiServiceChat {

    private static final DocRef MODEL_REF = OpenAIModelDoc
            .buildDocRef()
            .uuid(UUID.randomUUID().toString())
            .name("My Model")
            .build();

    private final List<String> asked = new ArrayList<>();

    @Test
    void theSameQuestionTwice_onlyReachesTheModelOnce() {
        final AiServiceImpl aiService = aiService(question -> "answer to " + question);

        assertThat(aiService.chat(MODEL_REF, null, "What is this?")).isEqualTo("answer to What is this?");
        assertThat(aiService.chat(MODEL_REF, null, "What is this?")).isEqualTo("answer to What is this?");

        assertThat(asked).hasSize(1);
    }

    @Test
    void differentQuestionOrSystemPrompt_isADifferentQuestion() {
        final AiServiceImpl aiService = aiService(question -> "an answer");

        aiService.chat(MODEL_REF, null, "What is this?");
        aiService.chat(MODEL_REF, null, "What is that?");
        aiService.chat(MODEL_REF, "You are a log parser", "What is this?");

        assertThat(asked).hasSize(3);
    }

    /**
     * A failure that was cached would be served for as long as the entry lived, so an outage lasting
     * seconds would suppress answers for the life of the cache entry.
     */
    @Test
    void failedQuestion_isNotCached() {
        final AiServiceImpl aiService = aiService(question -> {
            throw new RuntimeException("model said no");
        });

        assertThatThrownBy(() -> aiService.chat(MODEL_REF, null, "What is this?"))
                .hasMessageContaining("model said no");
        assertThatThrownBy(() -> aiService.chat(MODEL_REF, null, "What is this?"))
                .hasMessageContaining("model said no");

        assertThat(asked).hasSize(2);
    }

    @Test
    void theSystemPrompt_isOnlySentWhenThereIsOne() {
        final List<Integer> messageCounts = new ArrayList<>();
        final AiServiceImpl aiService = new TestableAiService(chatModel(question -> "an answer", messageCounts));

        aiService.chat(MODEL_REF, null, "What is this?");
        aiService.chat(MODEL_REF, "  ", "What is this?");
        aiService.chat(MODEL_REF, "You are a log parser", "What is this?");

        // A null or blank system prompt is no system prompt, rather than an empty one.
        assertThat(messageCounts).containsExactly(1, 1, 2);
    }

    // -----------------------------------------------------------------------------------------------

    private AiServiceImpl aiService(final Function<String, String> answer) {
        return new TestableAiService(chatModel(answer, new ArrayList<>()));
    }

    private ChatModel chatModel(final Function<String, String> answer, final List<Integer> messageCounts) {
        return new ChatModel() {
            @Override
            public ChatResponse doChat(final ChatRequest chatRequest) {
                final UserMessage lastMessage = (UserMessage) chatRequest.messages().getLast();
                final String question = lastMessage.singleText();
                asked.add(question);
                messageCounts.add(chatRequest.messages().size());
                return ChatResponse.builder()
                        .aiMessage(new AiMessage(answer.apply(question)))
                        .build();
            }
        };
    }


    // --------------------------------------------------------------------------------


    /**
     * The real service with the one thing a test cannot have - a model - replaced. Everything the chat path
     * touches other than the model is exercised as it is in production.
     */
    private static class TestableAiService extends AiServiceImpl {

        private final ChatModel chatModel;

        TestableAiService(final ChatModel chatModel) {
            super(() -> null,
                    () -> modelDocReader(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    new CacheManagerImpl(),
                    AiConfig::new);
            this.chatModel = chatModel;
        }

        @Override
        public ChatModel getChatModel(final OpenAIModelDoc modelDoc) {
            return chatModel;
        }

        private static DocumentResourceHelper modelDocReader() {
            final OpenAIModelDoc modelDoc = OpenAIModelDoc
                    .builder()
                    .uuid(MODEL_REF.getUuid())
                    .name(MODEL_REF.getName())
                    .modelId("test-model")
                    .build();
            return new DocumentResourceHelper() {
                @SuppressWarnings("unchecked")
                @Override
                public <D extends AbstractDoc> D read(final DocumentActionHandler<D> handler,
                                                      final DocRef docRef) {
                    return (D) modelDoc;
                }

                @Override
                public <D extends AbstractDoc> D update(final DocumentActionHandler<D> handler, final D doc) {
                    throw new UnsupportedOperationException();
                }
            };
        }
    }
}
