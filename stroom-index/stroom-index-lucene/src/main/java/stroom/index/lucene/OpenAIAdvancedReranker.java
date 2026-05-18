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

package stroom.index.lucene;

import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.query.Query;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class OpenAIAdvancedReranker implements ContentAggregator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(OpenAIAdvancedReranker.class);
    private static final JsonMapper MAPPER = JsonUtil.getNoIndentMapper();

    private final ChatModel scoringModel;

    public OpenAIAdvancedReranker(final ChatModel scoringModel) {
        // Use a cheaper, faster model for the scoring task
        this.scoringModel = scoringModel;
    }

    @Override
    public List<Content> aggregate(final Map<Query, Collection<List<Content>>> queryToContents) {
        // 1. Flatten all retrieved contents into a unique list
        // queryToContents maps each Query to a Collection of result lists from different retrievers
        final List<Content> allCandidates = queryToContents.values().stream()
                .flatMap(Collection::stream)
                .flatMap(List::stream)
                .distinct()
                .toList();

        if (allCandidates.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. Use the primary query for scoring (assuming one main query in simple RAG)
        final Query primaryQuery = queryToContents.keySet().iterator().next();

        // 3. Ask the ChatModel to score the candidates
        final String prompt = writeJsonPrompt(primaryQuery, allCandidates);
        LOGGER.debug("rerank prompt = {}", prompt);
        final String response = scoringModel.chat(prompt);
        LOGGER.debug("rerank response = {}", response);

        // 4. Parse response and get scores
        final List<Content> result = new ArrayList<>();
        final List<ScoreResult> results = readJsonResponse(response);
        for (int i = 0; i < allCandidates.size() && i < results.size(); i++) {
            final Content doc = allCandidates.get(i);
            final ScoreResult score = results.get(i);
            result.add(Content.from(doc.textSegment(), Map.of(ContentMetadata.RERANKED_SCORE, score.score)));
        }
        return result;
    }

    private String writeJsonPrompt(final Query primaryQuery, final List<Content> allCandidates) {
        try {
            final ObjectNode root = MAPPER.createObjectNode();
            root.put("task", "relevance_scoring");
            root.put("instructions", "Score each document for relevance to the query. " +
                                     "Use the full 0.0–1.0 range — do not cluster scores. " +
                                     "Respond ONLY with a JSON array, no preamble or markdown fences.");

            final ArrayNode outputFormat = root.putArray("output_format");
            final ObjectNode example = outputFormat.addObject();
            example.put("id", 1);
            example.put("score", 0.92);
            example.put("reason", "one sentence justification");

            root.put("query", primaryQuery.text());

            final ArrayNode documents = root.putArray("documents");
            for (int i = 0; i < allCandidates.size(); i++) {
                final ObjectNode doc = documents.addObject();
                doc.put("id", i + 1);
                doc.put("text", allCandidates.get(i).textSegment().text());
            }

            return MAPPER.writeValueAsString(root);
        } catch (final JacksonException e) {
            LOGGER.error(e::getMessage, e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private List<ScoreResult> readJsonResponse(final String response) {
        try {
            return MAPPER.readValue(response,
                    MAPPER.getTypeFactory().constructCollectionType(List.class, ScoreResult.class));
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        } catch (final Exception e) {
            LOGGER.error(e::getMessage, e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ScoreResult(int id, double score, String reason) {

    }
}
