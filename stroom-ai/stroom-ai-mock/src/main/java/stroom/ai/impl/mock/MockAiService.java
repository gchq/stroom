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

package stroom.ai.impl.mock;

import stroom.ai.api.AiService;
import stroom.ai.impl.AiServiceImpl;
import stroom.ai.shared.AiAttachmentStatus;
import stroom.ai.shared.AiAttachmentType;
import stroom.ai.shared.AiChat;
import stroom.ai.shared.AiChatAttachment;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiMessageType;
import stroom.ai.shared.FindAiChatHistoryCriteria;
import stroom.docref.DocRef;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.test.common.StroomCoreServerTestFileUtil;
import stroom.util.shared.ResultPage;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Singleton
public class MockAiService implements AiService {

    private static final Path EMBEDDING_CACHE = StroomCoreServerTestFileUtil
            .getTestResourcesDir()
            .resolve("embedding_cache.txt");

    private final AiServiceImpl aiService;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    @Inject
    public MockAiService(final AiServiceImpl aiService) {
        this.aiService = aiService;

        try {
            if (Files.exists(EMBEDDING_CACHE)) {
                final String string = Files.readString(EMBEDDING_CACHE);
                final String[] parts = string.split("\n");
                for (int i = 0; i < parts.length; i += 2) {
                    final String text = parts[i];
                    final String embeddings = parts[i + 1];
                    cache.put(text, embeddings);
                }
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private TextSegment readTextSegment(final String string) {
        return new TextSegment(string, Metadata.metadata("index", "0"));
    }

    private String writeTextSegment(final TextSegment textSegment) {
        return textSegment.text();
    }

    private Embedding readEmbedding(final String string) {
        final String[] vectors = string.split(" ");
        final List<Float> floats = Arrays.stream(vectors).map(Float::parseFloat).toList();
        return Embedding.from(floats);
    }

    private String writeEmbedding(final Embedding embedding) {
        return embedding.vectorAsList().stream().map(Object::toString).collect(Collectors.joining(" "));
    }

    @Override
    public OpenAIModelDoc getOpenAIModelDoc(final DocRef docRef) {
        return aiService.getOpenAIModelDoc(docRef);
    }

    @Override
    public String getModel(final OpenAIModelDoc modelDoc) {
        return aiService.getModel(modelDoc);
    }

    @Override
    public dev.langchain4j.model.chat.ChatModel getChatModel(final OpenAIModelDoc modelDoc) {
        return aiService.getChatModel(modelDoc);
    }

    @Override
    public synchronized EmbeddingModel getEmbeddingModel(final OpenAIModelDoc modelDoc) {
        final EmbeddingModel innerProxy = aiService.getEmbeddingModel(modelDoc);
        return textSegments -> {
            final TextSegment textSegment = textSegments.getFirst();
            final String text = writeTextSegment(textSegment);
            String embeddings = cache.get(text);
            if (embeddings == null) {
                final Embedding embedding = innerProxy.embedAll(textSegments).content().getFirst();
                embeddings = writeEmbedding(embedding);
                cache.put(text, embeddings);

                try {
                    // Dump it all.
                    final StringBuilder sb = new StringBuilder();
                    cache.entrySet()
                            .stream()
                            .sorted(Map.Entry.comparingByKey())
                            .forEach(entry -> {
                                sb.append(entry.getKey());
                                sb.append("\n");
                                sb.append(entry.getValue());
                                sb.append("\n");
                            });

                    Files.writeString(EMBEDDING_CACHE, sb.toString());
                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            return new Response<>(List.of(readEmbedding(embeddings)));
        };
    }

    @Override
    public dev.langchain4j.model.scoring.ScoringModel getCohereScoringModel(final OpenAIModelDoc modelDoc) {
        return aiService.getCohereScoringModel(modelDoc);
    }

    @Override
    public dev.langchain4j.model.scoring.ScoringModel getJinaScoringModel(final OpenAIModelDoc modelDoc) {
        return aiService.getJinaScoringModel(modelDoc);
    }

    // ---------------------------------------------------------------------
    // Chat persistence operations (delegate to wrapped AiServiceImpl)
    // ---------------------------------------------------------------------


    @Override
    public AiChat createChat() {
        return aiService.createChat();
    }

    @Override
    public ResultPage<AiChat> listChats(final FindAiChatHistoryCriteria criteria) {
        return aiService.listChats(criteria);
    }

    @Override
    public AiChat getChat(final int chatId) {
        return aiService.getChat(chatId);
    }

    @Override
    public void updateChatTitle(final int chatId, final String title) {
        aiService.updateChatTitle(chatId, title);
    }

    @Override
    public void deleteChat(final int chatId) {
        aiService.deleteChat(chatId);
    }

    @Override
    public AiChatMessage storeMessage(final int chatId,
                                      final AiMessageType messageType,
                                      final String message) {
        return aiService.storeMessage(chatId, messageType, message);
    }

    @Override
    public List<AiChatMessage> getMessages(final int chatId) {
        return aiService.getMessages(chatId);
    }

    @Override
    public List<AiChatMessage> getMessagesSince(final int chatId, final int lastSeenMessageId) {
        return aiService.getMessagesSince(chatId, lastSeenMessageId);
    }

    @Override
    public void verifyOwnership(final int chatId) {

    }

    @Override
    public void verifyOwnership(final AiChat chat) {

    }

    @Override
    public AiChatMessage storeMessage(final int chatId,
                                      final AiMessageType messageType,
                                      final Integer attachmentId,
                                      final String message) {
        return null;
    }

    @Override
    public AiChatAttachment createAttachment(final int chatId, final AiAttachmentType type, final String contextJson) {
        return null;
    }

    @Override
    public void updateAttachmentStatus(final int attachmentId,
                                       final AiAttachmentStatus status,
                                       final Integer rowCount,
                                       final String description,
                                       final String errorMessage,
                                       final boolean truncated) {

    }

    @Override
    public Optional<AiChatAttachment> getAttachment(final int attachmentId) {
        return Optional.empty();
    }

    @Override
    public List<AiChatAttachment> getAttachmentsByChatId(final int chatId) {
        return List.of();
    }

    @Override
    public void updateMessageText(final int messageId, final String message) {

    }

    @Override
    public void deleteMessage(final int messageId) {

    }

    @Override
    public void deleteAttachment(final int attachmentId) {

    }

    @Override
    public void deleteAllChatMessagesAndAttachments(final int chatId) {

    }
}
