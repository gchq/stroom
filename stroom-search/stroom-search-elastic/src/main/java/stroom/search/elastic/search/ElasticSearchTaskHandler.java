/*
 * Copyright 2021 Crown Copyright
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

package stroom.search.elastic.search;

import stroom.ai.api.AiService;
import stroom.ai.api.OpenAIModelStore;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.query.api.datasource.DenseVectorFieldConfig;
import stroom.query.common.v2.Coprocessors;
import stroom.query.common.v2.ResultStore;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValString;
import stroom.query.language.functions.ValuesConsumer;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.search.elastic.ElasticClientCache;
import stroom.search.elastic.ElasticClusterStore;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticConnectionConfig;
import stroom.search.elastic.shared.ElasticDenseVectorSettings;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.elastic.shared.ElasticIndexField;
import stroom.search.elastic.shared.ElasticIndexFieldSettings;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TerminateHandlerFactory;
import stroom.task.api.ThreadPoolImpl;
import stroom.task.shared.ThreadPool;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Severity;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SlicedScroll;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch._types.query_dsl.FieldAndFormat;
import co.elastic.clients.elasticsearch.core.ScrollResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Highlight;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.SourceConfig;
import co.elastic.clients.json.JsonData;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.aggregator.ReRankingContentAggregator;
import dev.langchain4j.rag.query.Query;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class ElasticSearchTaskHandler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ElasticSearchTaskHandler.class);
    private static final char FIELD_PATH_SEPARATOR = '.';
    public static final ThreadPool SCROLL_REQUEST_THREAD_POOL =
            new ThreadPoolImpl("Elasticsearch Scroll Request");

    private final Provider<ElasticSearchConfig> elasticSearchConfigProvider;
    private final Provider<AiService> aiServiceProvider;
    private final ElasticClientCache elasticClientCache;
    private final ElasticClusterStore elasticClusterStore;
    private final OpenAIModelStore openAIModelStore;
    private final ExecutorProvider executorProvider;
    private final TaskContextFactory taskContextFactory;

    @Inject
    ElasticSearchTaskHandler(final Provider<ElasticSearchConfig> elasticSearchConfigProvider,
                             final Provider<AiService> aiServiceProvider,
                             final ElasticClientCache elasticClientCache,
                             final ElasticClusterStore elasticClusterStore,
                             final OpenAIModelStore openAIModelStore,
                             final ExecutorProvider executorProvider,
                             final TaskContextFactory taskContextFactory) {
        this.elasticSearchConfigProvider = elasticSearchConfigProvider;
        this.aiServiceProvider = aiServiceProvider;
        this.elasticClientCache = elasticClientCache;
        this.elasticClusterStore = elasticClusterStore;
        this.openAIModelStore = openAIModelStore;
        this.executorProvider = executorProvider;
        this.taskContextFactory = taskContextFactory;
    }

    public void search(final TaskContext taskContext,
                       final ElasticIndexDoc elasticIndex,
                       final ElasticQueryParams queryBuilder,
                       final Highlight highlightBuilder,
                       final Coprocessors coprocessors,
                       final ResultStore resultStore,
                       final ValuesConsumer valuesConsumer,
                       final ErrorConsumer errorConsumer,
                       final AtomicLong hitCount) {
        if (!taskContext.isTerminated()) {
            taskContext.reset();
            taskContext.info(() -> LogUtil.message("Searching Elasticsearch index {}", elasticIndex.getName()));

            final ElasticClusterDoc elasticCluster = elasticClusterStore.readDocument(elasticIndex.getClusterRef());
            final List<RerankTarget> rerankTargets;
            try {
                rerankTargets = createRerankTargets(elasticIndex, queryBuilder);
            } catch (final RuntimeException e) {
                error(errorConsumer, e);
                return;
            }
            final ElasticConnectionConfig connectionConfig = elasticCluster.getConnection();

            final CompletableFuture<Void> searchFuture = executeSearch(
                    taskContext,
                    elasticIndex,
                    rerankTargets,
                    queryBuilder,
                    highlightBuilder,
                    coprocessors,
                    resultStore,
                    valuesConsumer,
                    errorConsumer,
                    hitCount,
                    connectionConfig);

            try {
                searchFuture.get();
            } catch (final RuntimeException | ExecutionException e) {
                error(errorConsumer, e);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                searchFuture.join();
            }
        }
    }

    /// Works out which kNN-queried `dense_vector` fields have their hits reranked, from each field's settings.
    /// Package-private for testing.
    ///
    /// @param elasticIndex the index doc, holding the field settings and the fields in the index mapping
    /// @param queryParams  the query, holding the `dense_vector` fields it queries
    /// @return a rerank target for each queried field with a rerank model
    /// @throws IllegalArgumentException if a field's rerank settings are incomplete or refer to a missing field
    List<RerankTarget> createRerankTargets(final ElasticIndexDoc elasticIndex,
                                           final ElasticQueryParams queryParams) {
        final List<RerankTarget> targets = new ArrayList<>();
        for (final String vectorFieldName : queryParams.getKnnFieldQueries().keySet()) {
            final ElasticDenseVectorSettings denseVector = NullSafe.get(
                    elasticIndex.findFieldSettings(vectorFieldName),
                    ElasticIndexFieldSettings::getDenseVector);
            final DenseVectorFieldConfig config = NullSafe.get(denseVector, ElasticDenseVectorSettings::getConfig);
            if (config == null || config.getRerankModelRef() == null) {
                continue;
            }
            final String textFieldName = denseVector.getRerankTextField();
            final String scoreFieldName = denseVector.getRerankScoreField();
            if (NullSafe.isBlankString(textFieldName)) {
                throw new IllegalArgumentException("Rerank text field is not set for dense_vector field '" +
                                                   vectorFieldName + "'");
            }
            if (NullSafe.isBlankString(scoreFieldName)) {
                throw new IllegalArgumentException("Rerank score field name is not set for dense_vector field '" +
                                                   vectorFieldName + "'");
            }
            final boolean textFieldExists = NullSafe.list(elasticIndex.getFields())
                    .stream()
                    .anyMatch(field -> field.getFldName().equals(textFieldName));
            if (!textFieldExists) {
                throw new IllegalArgumentException("Text field `" + textFieldName + "` for rerank scoring " +
                                                   "was not found");
            }
            final OpenAIModelDoc rerankModel = openAIModelStore.readDocument(config.getRerankModelRef());
            if (rerankModel == null) {
                throw new IllegalArgumentException("Rerank model for dense_vector field '" + vectorFieldName +
                                                   "' was not found");
            }
            targets.add(new RerankTarget(
                    vectorFieldName,
                    textFieldName,
                    scoreFieldName,
                    rerankModel,
                    Objects.requireNonNullElse(config.getRerankScoreMinimum(), 0F),
                    config.getRerankBatchSize()));
        }
        return targets;
    }

    private CompletableFuture<Void> executeSearch(final TaskContext parentContext,
                                                  final ElasticIndexDoc elasticIndex,
                                                  final List<RerankTarget> rerankTargets,
                                                  final ElasticQueryParams queryParams,
                                                  final Highlight highlightBuilder,
                                                  final Coprocessors coprocessors,
                                                  final ResultStore resultStore,
                                                  final ValuesConsumer valuesConsumer,
                                                  final ErrorConsumer errorConsumer,
                                                  final AtomicLong hitCount,
                                                  final ElasticConnectionConfig connectionConfig) {

        @SuppressWarnings("unchecked")
        final CompletableFuture<Void>[] futures = new CompletableFuture[elasticIndex.getSearchSlices()];
        final Executor executor = executorProvider.get(SCROLL_REQUEST_THREAD_POOL);

        try {
            elasticClientCache.context(connectionConfig, elasticClient -> {
                for (int i = 0; i < elasticIndex.getSearchSlices(); i++) {
                    final int slice = i;
                    final Runnable runnable = taskContextFactory.childContext(
                            parentContext,
                            "Elasticsearch Search Scroll Slice",
                            TerminateHandlerFactory.NOOP_FACTORY,
                            taskContext -> searchSlice(
                                    elasticIndex,
                                    rerankTargets,
                                    queryParams,
                                    highlightBuilder,
                                    coprocessors,
                                    resultStore,
                                    valuesConsumer,
                                    errorConsumer,
                                    hitCount,
                                    elasticClient,
                                    slice,
                                    taskContext
                            ));

                    futures[i] = CompletableFuture.runAsync(runnable, executor);
                }
            });
        } catch (final UncheckedInterruptedException e) {
            throw e;
        } catch (final RuntimeException e) {
            error(errorConsumer, e);
        }

        return CompletableFuture.allOf(futures);
    }

    /// Runs a scrolling search for a single slice of the index, passing each batch of hits to the values
    /// consumer until the results are exhausted or the task is terminated. Package-private for testing.
    ///
    /// Errors are passed to `errorConsumer` rather than thrown.
    void searchSlice(final ElasticIndexDoc elasticIndex,
                     final List<RerankTarget> rerankTargets,
                     final ElasticQueryParams queryParams,
                     final Highlight highlightBuilder,
                     final Coprocessors coprocessors,
                     final ResultStore resultStore,
                     final ValuesConsumer valuesConsumer,
                     final ErrorConsumer errorConsumer,
                     final AtomicLong hitCount,
                     final ElasticsearchClient elasticClient,
                     final int slice,
                     final TaskContext taskContext) {
        try {
            final long scrollSeconds = elasticSearchConfigProvider.get().getScrollDuration().getDuration().getSeconds();
            final Time scrollTime = Time.of(t -> t.time(String.format("%ds", scrollSeconds)));
            final SearchRequest.Builder searchRequestBuilder = new SearchRequest.Builder()
                    .index(elasticIndex.getIndexName())
                    .query(queryParams.getQuery())
                    .size(elasticIndex.getSearchScrollSize())
                    .scroll(scrollTime)
                    .source(SourceConfig.of(sc -> sc
                            .fetch(false)
                    ));

            // Number of slices needs to be > 1 else an exception is raised
            if (elasticIndex.getSearchSlices() > 1) {
                searchRequestBuilder.slice(SlicedScroll.of(s -> s
                        .id(String.valueOf(slice))
                        .max(elasticIndex.getSearchSlices())
                ));
            }

            // Add highlights
            if (elasticSearchConfigProvider.get().getHighlight()) {
                searchRequestBuilder.highlight(highlightBuilder);
            }

            final FieldIndex fieldIndex = coprocessors.getFieldIndex();
            final Set<String> fieldNames = new HashSet<>(Arrays.stream(fieldIndex.getFields()).toList());

            // Disabled and virtual fields have no values to return, so leave them out of the request
            final Set<String> unavailableFieldNames = getUnavailableFieldNames(elasticIndex, fieldNames);
            if (!unavailableFieldNames.isEmpty()) {
                fieldNames.removeAll(unavailableFieldNames);
                if (slice == 0 && errorConsumer != null) {
                    errorConsumer.add(Severity.WARNING, () -> LogUtil.message(
                            "No values are returned for these fields, as they are disabled or virtual: {}",
                            String.join(", ", new TreeSet<>(unavailableFieldNames))));
                }
            }

            // Insert fields representing the rerank score, if enabled.
            // Limit the returned fields to what the values consumers require, plus any knn query text fields.
            // These need to be added, as the original text field values are needed for computing relevance scores.
            final Set<String> rerankScoreFieldNames = new HashSet<>();
            for (final RerankTarget target : rerankTargets) {
                if (target.scoreMinimum() > 0F && !fieldNames.contains(target.scoreField())) {
                    throw new UnsupportedOperationException(
                            "Missing rerank score field '" + target.scoreField() + "' in values consumer. " +
                            "Either add the field or remove the rerank score minimum in the settings of " +
                            "field '" + target.vectorField() + "'.");
                }
                fieldNames.add(target.textField());
                fieldIndex.create(target.scoreField());
                rerankScoreFieldNames.add(target.scoreField());
            }

            searchRequestBuilder.fields(fieldNames.stream().toList().stream()
                    .map(fieldName -> FieldAndFormat.of(f -> f.field(fieldName)))
                    .toList()
            );

            final SearchResponse<ObjectNode> searchResponse = elasticClient.search(
                    searchRequestBuilder.build(),
                    ObjectNode.class);
            final String scrollId = searchResponse.scrollId();

            try {
                // Retrieve the initial result batch
                List<Hit<ObjectNode>> searchHits = searchResponse.hits().hits();
                long totalHitCount = 0L;

                // Continue requesting results until we have all results
                while (!taskContext.isTerminated() && !searchHits.isEmpty()) {
                    totalHitCount += searchHits.size();
                    processResultBatch(fieldIndex, rerankTargets, queryParams,
                            resultStore, valuesConsumer, errorConsumer, hitCount, searchHits);

                    final long totalHits = totalHitCount;
                    taskContext.info(() -> LogUtil.message("Processed {} hits", totalHits));

                    final ScrollResponse<ObjectNode> scrollResponse = elasticClient.scroll(s -> s
                                    .scrollId(scrollId)
                                    .scroll(scrollTime),
                            ObjectNode.class
                    );

                    searchHits = scrollResponse.hits().hits();
                }

                LOGGER.info("Search completed for index doc {}, {} hits returned",
                        elasticIndex.getName(), totalHitCount);
            } finally {
                // Always close the scroll context, whether we finished streaming results, were terminated or
                // failed part way through, so it does not hold resources on the cluster until it expires.
                clearScroll(elasticClient, scrollId);
            }
        } catch (final UncheckedInterruptedException e) {
            throw e;
        } catch (final IOException | RuntimeException e) {
            error(errorConsumer, e);

            // Record any inner exceptions from Elasticsearch
            for (final Throwable ex : e.getSuppressed()) {
                error(errorConsumer, ex);
            }
        }
    }

    /// Finds the requested fields that have no values to return: mapped fields that are disabled, and virtual fields,
    /// which can only be queried. Package-private for testing.
    ///
    /// @param elasticIndex the index doc, holding the field settings and the fields in the index mapping
    /// @param fieldNames   the names of the fields whose values are requested
    /// @return the names of the requested fields that have no values to return
    static Set<String> getUnavailableFieldNames(final ElasticIndexDoc elasticIndex, final Set<String> fieldNames) {
        final Map<String, ElasticIndexField> mappedFields = new HashMap<>();
        NullSafe.list(elasticIndex.getFields()).forEach(field -> mappedFields.put(field.getFldName(), field));

        final Set<String> unavailable = new HashSet<>();
        for (final String fieldName : fieldNames) {
            final ElasticIndexField mappedField = mappedFields.get(fieldName);
            if (mappedField != null) {
                if (!ElasticSearchProvider.isFieldEnabled(elasticIndex, mappedField)) {
                    unavailable.add(fieldName);
                }
            } else {
                final ElasticIndexFieldSettings settings = elasticIndex.findFieldSettings(fieldName);
                if (settings != null && (settings.isVirtual() || !settings.isEnabled())) {
                    unavailable.add(fieldName);
                }
            }
        }
        return unavailable;
    }

    /// Clears the scroll context identified by `scrollId`, if there is one.
    ///
    /// A failure to clear the scroll is logged rather than thrown. It must not mask any error from the search
    /// itself, nor be reported as a search failure when the search succeeded. The cluster will release the
    /// scroll context anyway once its keep-alive expires.
    private void clearScroll(final ElasticsearchClient elasticClient, final String scrollId) {
        if (scrollId == null) {
            return;
        }
        try {
            elasticClient.clearScroll(s -> s.scrollId(scrollId));
        } catch (final IOException | RuntimeException e) {
            LOGGER.warn(() -> LogUtil.message("Failed to clear Elasticsearch scroll context: {}",
                    e.getMessage()), e);
        }
    }

    /**
     * Receive a batch of search hits and send each one to the values consumer
     */
    void processResultBatch(final FieldIndex fieldIndex,
                            final List<RerankTarget> rerankTargets,
                            final ElasticQueryParams queryParams,
                            final ResultStore resultStore,
                            final ValuesConsumer valuesConsumer,
                            final ErrorConsumer errorConsumer,
                            final AtomicLong hitCount,
                            final List<Hit<ObjectNode>> searchHits) {
        try {
            // Map rerank fields to document IDs to relevance scores, to determine whether each reranked hit
            // meets the relevance score threshold.
            final Map<String, Map<String, Double>> fieldToDocIdScores = new HashMap<>();
            final boolean performRerank = !rerankTargets.isEmpty() && !queryParams.getKnnFieldQueries().isEmpty();
            if (performRerank) {
                for (final RerankTarget target : rerankTargets) {
                    final Map<String, Double> docIdToScoreMap = fieldToDocIdScores
                            .computeIfAbsent(target.scoreField(), k -> new HashMap<>());
                    rerankForField(target, queryParams, searchHits, docIdToScoreMap);
                }
            }

            for (final Hit<ObjectNode> searchHit : searchHits) {
                // Determine whether to include the search hit based on dense vector relevance score cutoff.
                // If at least one field meets the cutoff, include the search hit.
                if (performRerank && fieldToDocIdScores.values().stream()
                        .noneMatch(content -> content.containsKey(searchHit.id()))) {
                    continue;
                }

                hitCount.incrementAndGet();

                // Add highlights
                if (elasticSearchConfigProvider.get().getHighlight()) {
                    for (final List<String> highlightField : searchHit.highlight().values()) {
                        resultStore.addHighlights(Set.copyOf(highlightField));
                    }
                }

                final Map<String, JsonData> mapSearchHit = searchHit.fields();
                Val[] values = null;
                for (final String fieldName : fieldIndex.getFields()) {
                    final Integer insertAt = fieldIndex.getPos(fieldName);

                    final Object fieldValue;
                    if (fieldToDocIdScores.containsKey(fieldName)) {
                        // If this is a rerank score field, obtain the score from the rerank content map and insert
                        // into the values returned to the consumer.
                        fieldValue = fieldToDocIdScores.get(fieldName).get(searchHit.id());
                    } else {
                        fieldValue = getFieldValue(mapSearchHit, fieldName);
                    }

                    if (fieldValue != null) {
                        if (values == null) {
                            values = new Val[fieldIndex.size()];
                        }

                        if (fieldValue instanceof final Collection<?> collectionValue) {
                            // Multivalued fields (including values gathered from across nested objects) are
                            // flattened to a single, comma-delimited string.
                            values[insertAt] = ValString.create(collectionValue.stream()
                                    .map(Object::toString)
                                    .collect(Collectors.joining(", ")));
                        } else {
                            values[insertAt] = Val.create(fieldValue);
                        }
                    }
                }

                if (values != null) {
                    valuesConsumer.accept(Val.of(values));
                }
            }
        } catch (final UncheckedInterruptedException e) {
            throw e;
        } catch (final RuntimeException e) {
            error(errorConsumer, e);
        }
    }

    /**
     * Uses the configured LLM to generate relevance scores for the specified dense_vector field, against the user's
     * query.
     */
    private void rerankForField(final RerankTarget target,
                                final ElasticQueryParams queryParams,
                                final List<Hit<ObjectNode>> searchHits,
                                final Map<String, Double> docIdToScoreMap) {
        final String knnQueryTerm = queryParams.getKnnFieldQueries().get(target.vectorField());
        if (knnQueryTerm == null) {
            return;
        }
        // Send the hits to the rerank model in batches of at most the configured size
        final int batchSize = target.batchSize() > 0
                ? target.batchSize()
                : searchHits.size();
        for (int from = 0; from < searchHits.size(); from += batchSize) {
            final List<Hit<ObjectNode>> batch = searchHits.subList(from, Math.min(from + batchSize, searchHits.size()));
            rerankBatch(target, knnQueryTerm, batch, docIdToScoreMap);
        }
    }

    private void rerankBatch(final RerankTarget target,
                             final String knnQueryTerm,
                             final List<Hit<ObjectNode>> searchHits,
                             final Map<String, Double> docIdToScoreMap) {
        final OpenAIModelDoc rerankModel = target.model();
        final String textFieldName = target.textField();
        final ScoringModel scoringModel = aiServiceProvider.get().getJinaScoringModel(rerankModel);
        final int maxContextWindowTokens = rerankModel.getMaxContextWindowTokens();
        final ReRankingContentAggregator rerankAggregator = ReRankingContentAggregator.builder()
                .scoringModel(scoringModel)
                .minScore((double) target.scoreMinimum())
                .build();

        final Map<Query, Collection<List<Content>>> queryContentMap = new HashMap<>();
        final List<Content> fieldValues = new ArrayList<>();

        for (final Hit<ObjectNode> searchHit : searchHits) {
            // The reranker source text may itself live inside a nested object, so resolve it using the
            // same nested-aware extraction used for result values rather than a flat map lookup.
            final Object rawFieldValue = getFieldValue(searchHit.fields(), textFieldName);
            if (rawFieldValue != null && searchHit.id() != null) {
                try {
                    String fieldValue = firstStringValue(rawFieldValue);
                    if (fieldValue == null) {
                        continue;
                    }
                    if (maxContextWindowTokens > 0) {
                        // Model context window limit is specified, so truncate the field value to fit
                        final int charLimit = Math.min(fieldValue.length(), maxContextWindowTokens);
                        fieldValue = fieldValue.substring(0, charLimit);
                    }
                    fieldValues.add(Content.from(
                            TextSegment.from(fieldValue, Metadata.from("id", searchHit.id()))));
                } catch (final Exception e) {
                    throw new RuntimeException("Failed to parse value '" + rawFieldValue + "' for field " +
                                               textFieldName, e);
                }
            }
        }

        queryContentMap.put(Query.from(knnQueryTerm), List.of(fieldValues));

        final List<Content> rankedContent = rerankAggregator.aggregate(queryContentMap);
        for (final Content content : rankedContent) {
            final Double score = (Double) content.metadata().get(ContentMetadata.RERANKED_SCORE);
            docIdToScoreMap.put(content.textSegment().metadata().getString("id"), score);
        }
    }

    /**
     * Resolves the value of a field by its full (dot-delimited) path from a single search hit's
     * {@code fields} response.
     * <p>
     * Elasticsearch returns non-nested fields flat, keyed by their full path (e.g.
     * {@code {"user.name": ["Alice"]}}). Fields that live inside a {@code nested} object are instead
     * grouped by the nested object they belong to, with the sub-field keys expressed relative to the
     * nesting path. For a doubly-nested field {@code a.b.c} this looks like:
     * <pre>
     *   {
     *     "a": [
     *       { "b": [ { "c": ["value"] } ] }
     *     ]
     *   }
     * </pre>
     * This method handles both shapes, descending through an arbitrary number of nesting levels and
     * flattening the values collected across sibling nested objects.
     *
     * @return {@code null} if the field is absent, a single native value, or a {@link List} of native
     * values when the field (or the nested objects it spans) yields more than one value.
     * @throws IllegalArgumentException if the field has a whole number value beyond the range of a long,
     *                                  e.g. an `unsigned_long` value above {@link Long#MAX_VALUE}.
     */
    static Object getFieldValue(final Map<String, JsonData> searchHitMap, final String fieldName) {
        try {
            return resolveFieldValue(searchHitMap, fieldName);
        } catch (final ArithmeticException e) {
            throw new IllegalArgumentException(LogUtil.message(
                    "Unable to read the value of field '{}'. {}", fieldName, e.getMessage()), e);
        }
    }

    private static Object resolveFieldValue(final Map<String, JsonData> searchHitMap, final String fieldName) {
        if (fieldName == null || searchHitMap == null || searchHitMap.isEmpty()) {
            return null;
        }

        // Fast path: the field was returned flat (not nested), matching the original behaviour and
        // avoiding any conversion for the common, non-nested case.
        final JsonData directField = searchHitMap.get(fieldName);
        if (directField != null) {
            final List<Object> values = new ArrayList<>();
            addLeafValues(directField.toJson(), values);
            return toFieldResult(values);
        }

        // Nested field: values are grouped under the enclosing nested object(s). Build a JSON view of
        // the top-level fields and descend, splitting the path at each nested boundary.
        final Map<String, JsonValue> fields = new HashMap<>(searchHitMap.size());
        searchHitMap.forEach((key, data) -> fields.put(key, data.toJson()));

        final List<Object> values = new ArrayList<>();
        collectFieldValues(fields, fieldName, values);
        return toFieldResult(values);
    }

    /**
     * Recursively collects the native values for {@code fieldPath} from a map of fields that is either
     * the top-level hit fields or the contents of a single nested object.
     */
    static void collectFieldValues(final Map<String, JsonValue> fields,
                                   final String fieldPath,
                                   final List<Object> out) {
        if (fields == null || fieldPath == null) {
            return;
        }

        // The field may be present directly at this level, either because it is a plain (non-nested)
        // sub-field or because we have reached the leaf of a nested path.
        final JsonValue direct = fields.get(fieldPath);
        if (direct != null) {
            addLeafValues(direct, out);
            return;
        }

        // Otherwise the field is grouped inside a nested object. Find the longest ancestor path that is
        // present as an array of objects, then recurse into each object with the remaining relative path.
        final int splitAt = findNestedSplit(fields, fieldPath);
        if (splitAt < 0) {
            return;
        }

        final String nestedPath = fieldPath.substring(0, splitAt);
        final String remainder = fieldPath.substring(splitAt + 1);
        final JsonValue container = fields.get(nestedPath);
        for (final JsonValue element : container.asJsonArray()) {
            if (element.getValueType() == JsonValue.ValueType.OBJECT) {
                // A JsonObject is a Map<String, JsonValue>, so it can be descended into directly.
                final JsonObject nestedObject = element.asJsonObject();
                collectFieldValues(nestedObject, remainder, out);
            }
        }
    }

    /**
     * Finds the index of the {@code '.'} at which {@code fieldPath} should be split into a nested
     * container path and a relative remainder. Prefixes are tested longest-first so that nested
     * containers whose relative name spans plain-object segments (e.g. {@code b.c}) are preferred over
     * shorter matches.
     *
     * @return the split index, or {@code -1} if no nested container prefix is present
     */
    static int findNestedSplit(final Map<String, JsonValue> fields, final String fieldPath) {
        int idx = fieldPath.lastIndexOf(FIELD_PATH_SEPARATOR);
        while (idx > 0) {
            final String prefix = fieldPath.substring(0, idx);
            if (isArrayOfObjects(fields.get(prefix))) {
                return idx;
            }
            idx = fieldPath.lastIndexOf(FIELD_PATH_SEPARATOR, idx - 1);
        }
        return -1;
    }

    /**
     * @return true if the value is a non-empty array containing at least one object (the shape used by
     * Elasticsearch to group the values of a {@code nested} field).
     */
    private static boolean isArrayOfObjects(final JsonValue value) {
        if (value == null || value.getValueType() != JsonValue.ValueType.ARRAY) {
            return false;
        }
        return value.asJsonArray().stream()
                .anyMatch(element -> element.getValueType() == JsonValue.ValueType.OBJECT);
    }

    /**
     * Adds the native representation of a leaf field's value(s) to {@code out}. Elasticsearch always
     * returns field values as an array, but this also tolerates a bare scalar defensively.
     */
    private static void addLeafValues(final JsonValue value, final List<Object> out) {
        if (value == null) {
            return;
        }

        if (value.getValueType() == JsonValue.ValueType.ARRAY) {
            for (final JsonValue element : value.asJsonArray()) {
                final Object nativeValue = jsonValueToNative(element);
                if (nativeValue != null) {
                    out.add(nativeValue);
                }
            }
        } else {
            final Object nativeValue = jsonValueToNative(value);
            if (nativeValue != null) {
                out.add(nativeValue);
            }
        }
    }

    /**
     * Collapses the collected values into the shape expected by the caller: {@code null} for no values,
     * the single value, or the list itself for multiple values.
     */
    private static Object toFieldResult(final List<Object> values) {
        if (values.isEmpty()) {
            return null;
        } else if (values.size() == 1) {
            return values.getFirst();
        } else {
            return values;
        }
    }

    /**
     * Returns the first value of a resolved field as a String, whether the field yielded a single value
     * or a list of values.
     */
    static String firstStringValue(final Object fieldValue) {
        if (fieldValue == null) {
            return null;
        }
        if (fieldValue instanceof final Collection<?> collection) {
            return collection.isEmpty()
                    ? null
                    : String.valueOf(collection.iterator().next());
        }
        return String.valueOf(fieldValue);
    }

    /// Package-private for testing.
    ///
    /// @param jsonValue The JSON value to convert.
    /// @return The native Java representation of `jsonValue`, i.e. a `Boolean`, `Long`, `Double` or
    /// `String`, or `null` for a JSON null.
    /// @throws ArithmeticException if `jsonValue` is a whole number beyond the range of a long.
    static Object jsonValueToNative(final JsonValue jsonValue) {
        if (jsonValue == null) {
            return null;
        }

        switch (jsonValue.getValueType()) {
            case NULL:
                return null;
            case FALSE:
                return false;
            case TRUE:
                return true;
            case NUMBER:
                final JsonNumber jsonNumber = (JsonNumber) jsonValue;
                if (jsonNumber.isIntegral()) {
                    return toLongExact(jsonNumber);
                } else {
                    return jsonNumber.doubleValue();
                }
            case STRING:
                return ((JsonString) jsonValue).getString();
            case OBJECT:
                return jsonValue.asJsonObject().toString();
            default:
                return jsonValue.toString();
        }
    }

    /// An `unsigned_long` field may hold values beyond the range of a long, which no `Val` type can hold, so
    /// reject them rather than silently truncate them as `longValue()` would.
    private static long toLongExact(final JsonNumber jsonNumber) {
        try {
            return jsonNumber.longValueExact();
        } catch (final ArithmeticException e) {
            throw new ArithmeticException(LogUtil.message(
                    "Value {} is outside the range of a long ({} to {}). Elasticsearch unsigned_long values " +
                    "above {} are not supported.",
                    jsonNumber, Long.MIN_VALUE, Long.MAX_VALUE, Long.MAX_VALUE));
        }
    }

    private void error(final ErrorConsumer errorConsumer, final Throwable t) {
        if (!(t instanceof UncheckedInterruptedException)) {
            if (errorConsumer == null) {
                LOGGER.error(t::getMessage, t);
            } else {
                errorConsumer.add(t);
            }
        }
    }

    /// A `dense_vector` field queried by kNN whose hits are reranked.
    ///
    /// @param vectorField  the full name of the `dense_vector` field
    /// @param textField    the text field whose values are sent to the rerank model
    /// @param scoreField   the name of the output field holding each hit's rerank score
    /// @param model        the rerank model
    /// @param scoreMinimum hits scoring below this are dropped
    /// @param batchSize    the most hits sent to the rerank model in one call; 0 or less sends them all at once
    record RerankTarget(String vectorField,
                        String textField,
                        String scoreField,
                        OpenAIModelDoc model,
                        float scoreMinimum,
                        int batchSize) {

    }
}
