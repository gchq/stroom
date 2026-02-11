/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.langchain.impl;

import stroom.docref.DocRef;
import stroom.langchain.api.OpenAIService;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.test.common.StroomCoreServerTestFileUtil;

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
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Singleton
public class MockOpenAiService implements OpenAIService {

    private static final Path EMBEDDING_CACHE = StroomCoreServerTestFileUtil
            .getTestResourcesDir()
            .resolve("embedding_cache.txt");

    private final OpenAIServiceImpl openAIService;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    @Inject
    MockOpenAiService(final OpenAIServiceImpl openAIService) {
        this.openAIService = openAIService;

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
        return openAIService.getOpenAIModelDoc(docRef);
    }

    @Override
    public String getModel(final OpenAIModelDoc modelDoc) {
        return openAIService.getModel(modelDoc);
    }

    @Override
    public dev.langchain4j.model.chat.ChatModel getChatModel(final OpenAIModelDoc modelDoc) {
        return openAIService.getChatModel(modelDoc);
    }

    @Override
    public synchronized EmbeddingModel getEmbeddingModel(final OpenAIModelDoc modelDoc) {
        final EmbeddingModel innerProxy = openAIService.getEmbeddingModel(modelDoc);
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
        return openAIService.getCohereScoringModel(modelDoc);
    }

    @Override
    public dev.langchain4j.model.scoring.ScoringModel getJinaScoringModel(final OpenAIModelDoc modelDoc) {
        return openAIService.getJinaScoringModel(modelDoc);
    }

}
