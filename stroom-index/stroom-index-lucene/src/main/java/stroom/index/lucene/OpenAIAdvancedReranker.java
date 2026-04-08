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

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.query.Query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class OpenAIAdvancedReranker implements ContentAggregator {

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
        final String prompt = "Score these docs (0.0 to 1.0) for query: " + primaryQuery.text() + "\n" +
                              formatForScoring(allCandidates);

        final String response = scoringModel.chat(prompt);
        final List<Double> scores = parseScores(response, allCandidates.size());

        final List<Content> result = new ArrayList<>();
        for (int i = 0; i < allCandidates.size() && i < scores.size(); i++) {
            final Content doc = allCandidates.get(i);
            final Double score = scores.get(i);
            result.add(Content.from(doc.textSegment(), Map.of(ContentMetadata.RERANKED_SCORE, score)));
        }

        return result;
    }

    private String formatForScoring(final List<Content> docs) {
        return docs.stream()
                .map(d -> "- " + d.textSegment().text())
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private List<Double> parseScores(final String res, final int size) {
        try {
            return Arrays.stream(res.split(",")).map(s -> Double.parseDouble(s.trim())).toList();
        } catch (final Exception e) {
            return Collections.nCopies(size, 0.5);
        }
    }
}