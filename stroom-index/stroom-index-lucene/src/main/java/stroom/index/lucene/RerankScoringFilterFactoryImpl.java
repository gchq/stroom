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

import stroom.docref.DocRef;
import stroom.langchain.api.OpenAIModelStore;
import stroom.langchain.api.OpenAIService;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionUtil;
import stroom.query.api.datasource.DenseVectorFieldConfig;
import stroom.query.api.datasource.DenseVectorFieldConfig.RerankModelType;
import stroom.query.api.datasource.IndexField;
import stroom.query.common.v2.IndexFieldCache;
import stroom.query.common.v2.RerankScoringFilter;
import stroom.query.common.v2.RerankScoringFilterFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValuesConsumer;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.content.aggregator.ReRankingContentAggregator;
import dev.langchain4j.rag.query.Query;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class RerankScoringFilterFactoryImpl implements RerankScoringFilterFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RerankScoringFilterFactoryImpl.class);

    private static final String SCORE_FIELD_SUFFIX = "__rerank_score";
    private static final String VALUE_FIELD_SUFFIX = "__rerank_value";

    private final IndexFieldCache indexFieldCache;
    private final OpenAIModelStore openAIModelStore;
    private final OpenAIService openAIService;

    @Inject
    public RerankScoringFilterFactoryImpl(final IndexFieldCache indexFieldCache,
                                          final OpenAIModelStore openAIModelStore,
                                          final OpenAIService openAIService) {
        this.indexFieldCache = indexFieldCache;
        this.openAIModelStore = openAIModelStore;
        this.openAIService = openAIService;
    }

    @Override
    public Optional<RerankScoringFilter> create(final DocRef indexDocRef,
                                                final ExpressionOperator expression,
                                                final FieldIndex fieldIndex,
                                                final ValuesConsumer valuesConsumer,
                                                final ErrorConsumer errorConsumer) {
        try {
            // Get score and value field sets.
            final String[] fields = fieldIndex.getFields();
            final Map<String, FieldRef> fieldRefMap = new HashMap<>();
            for (int i = 0; i < fields.length; i++) {
                final String field = fields[i];
                final int scoreFieldIndex = field.indexOf(SCORE_FIELD_SUFFIX);

                if (scoreFieldIndex != -1) {
                    final String denseVectorFieldName = field.substring(0, scoreFieldIndex);
                    final FieldRef fieldRef = fieldRefMap.get(denseVectorFieldName);
                    final NameAndIndex scoreField = new NameAndIndex(field, i);
                    if (fieldRef == null) {
                        fieldRefMap.put(denseVectorFieldName, new FieldRef(scoreField, null));
                    } else {
                        fieldRefMap.put(denseVectorFieldName, new FieldRef(scoreField, fieldRef.valueField));
                    }

                } else {
                    final int valueFieldIndex = field.indexOf(VALUE_FIELD_SUFFIX);
                    if (valueFieldIndex != -1) {
                        final String denseVectorFieldName = field.substring(0, valueFieldIndex);
                        final FieldRef fieldRef = fieldRefMap.get(denseVectorFieldName);
                        final NameAndIndex valueField = new NameAndIndex(field, i);
                        if (fieldRef == null) {
                            fieldRefMap.put(denseVectorFieldName, new FieldRef(null, valueField));
                        } else {
                            fieldRefMap.put(denseVectorFieldName, new FieldRef(fieldRef.scoreField, valueField));
                        }
                    }
                }
            }

            if (!fieldRefMap.isEmpty()) {
                // Just use the first field ref for now.
                final Entry<String, FieldRef> entry = fieldRefMap.entrySet().iterator().next();
                final String denseVectorFieldName = entry.getKey();
                final FieldRef fieldRef = entry.getValue();

                if (fieldRef.scoreField.index != -1 && fieldRef.valueField.index != -1) {
                    final IndexField denseVectorField = indexFieldCache.get(indexDocRef, denseVectorFieldName);
                    if (denseVectorField == null) {
                        throw new UnsupportedOperationException(
                                "Unable to find dense vector field '" +
                                denseVectorFieldName +
                                "'");
                    }

                    // Get term value.
                    final AtomicReference<String> termValue = new AtomicReference<>();
                    ExpressionUtil.walkExpressionTree(ExpressionUtil.simplify(expression), item -> {
                        if (item instanceof final ExpressionTerm expressionTerm) {
                            if (denseVectorFieldName.equals(expressionTerm.getField())) {
                                termValue.set(expressionTerm.getValue());
                                return false;
                            }
                        }
                        return true;
                    });
                    if (termValue.get() == null) {
                        throw new UnsupportedOperationException(
                                "Unable to get vector query term value for field '" +
                                denseVectorFieldName +
                                "' needed to calculate score");
                    }

                    final DenseVectorFieldConfig denseVectorFieldConfig =
                            denseVectorField.getDenseVectorFieldConfig();
                    if (denseVectorFieldConfig.getRerankModelRef() == null) {
                        throw new UnsupportedOperationException(
                                "Rerank model ref is null for vector query on field '" +
                                denseVectorFieldName +
                                "'");
                    }

                    final DocRef rerankModelRef = denseVectorFieldConfig.getRerankModelRef();
                    final OpenAIModelDoc rerankModel = openAIModelStore.readDocument(
                            rerankModelRef);

                    if (rerankModel == null) {
                        throw new UnsupportedOperationException(
                                "Unable to load rerank model for vector query on field '" +
                                denseVectorFieldName +
                                "'");
                    }

                    final ContentAggregator rerankAggregator = createContentAggregator(denseVectorFieldConfig);

                    // Create reranking query.
                    final Query rerankQuery = Query.from(termValue.get());

                    return Optional.of(new RerankScoringFilterImpl(
                            fieldRef,
                            rerankQuery,
                            rerankAggregator,
                            valuesConsumer,
                            denseVectorFieldConfig.getRerankBatchSize()));
                }
            }
        } catch (final Exception e) {
            LOGGER.debug(e::getMessage, e);
            errorConsumer.add(e);
        }

        return Optional.empty();
    }

    private ContentAggregator createContentAggregator(final DenseVectorFieldConfig denseVectorFieldConfig) {
        final DocRef rerankModelRef = denseVectorFieldConfig.getRerankModelRef();
        final OpenAIModelDoc rerankModel = openAIModelStore.readDocument(
                rerankModelRef);

        if (rerankModel == null) {
            throw new UnsupportedOperationException(
                    "Unable to load rerank model for vector query");
        }

        final RerankModelType rerankModelType = denseVectorFieldConfig.getRerankModelType();
        return switch (rerankModelType) {
            case JINA -> {
                final ScoringModel scoringModel = openAIService.getJinaScoringModel(
                        rerankModel);
                yield ReRankingContentAggregator
                        .builder()
                        .scoringModel(scoringModel)
                        .minScore(denseVectorFieldConfig.getRerankScoreMinimum().doubleValue())
                        .build();
            }
            case COHERE -> {
                final ScoringModel scoringModel = openAIService.getCohereScoringModel(
                        rerankModel);
                yield ReRankingContentAggregator
                        .builder()
                        .scoringModel(scoringModel)
                        .minScore(denseVectorFieldConfig.getRerankScoreMinimum().doubleValue())
                        .build();
            }
            case OPEN_AI -> {
                final ChatModel chatModel = openAIService.getChatModel(rerankModel);
                yield new OpenAIAdvancedReranker(chatModel);
            }
        };
    }

    static class RerankScoringFilterImpl implements RerankScoringFilter {

        private final FieldRef fieldRef;
        private final dev.langchain4j.rag.query.Query rerankQuery;
        private final ContentAggregator rerankAggregator;
        private final ValuesConsumer valuesConsumer;
        private final BatchedBuffer<Val[]> batchedBuffer;

        public RerankScoringFilterImpl(final FieldRef fieldRef,
                                       final Query rerankQuery,
                                       final ContentAggregator rerankAggregator,
                                       final ValuesConsumer valuesConsumer,
                                       final int maxBatchSize) {
            this.fieldRef = fieldRef;
            this.rerankQuery = rerankQuery;
            this.rerankAggregator = rerankAggregator;
            this.valuesConsumer = valuesConsumer;
            this.batchedBuffer = new BatchedBuffer<>(maxBatchSize, this::rerank);
        }

        @Override
        public void accept(final Val[] values) {
            batchedBuffer.receive(values);
        }

        @Override
        public void close() {
            batchedBuffer.close();
        }

        private void rerank(final List<Val[]> values) {
            final List<List<Content>> contentList = new ArrayList<>(values.size());

            // Extract content to pass to the ranking model.
            final Map<String, List<Val[]>> fieldValueToValuesMap = new HashMap<>();
            for (final Val[] val : values) {
                final String fieldValue = val[fieldRef.valueField.index].toString();
                if (fieldValue == null) {
                    throw new UnsupportedOperationException(
                            "Unable to find dense vector stored value field: " +
                            fieldRef.valueField.name);
                }

                final List<Content> fieldValues = new ArrayList<>();
                try {
                    fieldValueToValuesMap.computeIfAbsent(fieldValue, k -> new ArrayList<>()).add(val);
                    fieldValues.add(Content.from(TextSegment.from(fieldValue)));
                } catch (final Exception e) {
                    throw new RuntimeException("Failed to parse value '" +
                                               fieldValue +
                                               "' for field " +
                                               fieldRef.valueField.name, e);
                }
                contentList.add(fieldValues);
            }

            // Get scores.
            final Map<dev.langchain4j.rag.query.Query, Collection<List<Content>>> queryContentMap = new HashMap<>();
            queryContentMap.put(rerankQuery, contentList);
            final List<Content> rankedContent = rerankAggregator.aggregate(queryContentMap);

            // Set the scores.
            for (final Content content : rankedContent) {
                final Object score = content.metadata().get(ContentMetadata.RERANKED_SCORE);
                final Val scoreVal = Val.create(score);

                final List<Val[]> vals = fieldValueToValuesMap.get(content.textSegment().text());
                for (final Val[] val : vals) {
                    val[fieldRef.scoreField.index] = scoreVal;
                }
            }

            // Pass the values on.
            for (final Val[] vals : values) {
                // Ensure null if not scored.
                if (vals[fieldRef.scoreField.index] == null) {
                    vals[fieldRef.scoreField.index] = ValNull.INSTANCE;
                }
                valuesConsumer.accept(vals);
            }
        }
    }

    record FieldRef(NameAndIndex scoreField,
                    NameAndIndex valueField) {

    }

    record NameAndIndex(String name,
                        int index) {

    }
}
