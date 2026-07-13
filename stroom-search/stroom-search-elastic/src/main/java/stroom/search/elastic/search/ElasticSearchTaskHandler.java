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

package stroom.search.elastic.search;

import stroom.ai.api.AiService;
import stroom.ai.api.OpenAIModelStore;
import stroom.docref.DocRef;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.query.api.datasource.FieldType;
import stroom.query.common.v2.Coprocessors;
import stroom.query.common.v2.ResultStore;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValBoolean;
import stroom.query.language.functions.ValDouble;
import stroom.query.language.functions.ValInteger;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValString;
import stroom.query.language.functions.ValuesConsumer;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.search.elastic.ElasticClientCache;
import stroom.search.elastic.ElasticClusterStore;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticConnectionConfig;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.elastic.shared.ElasticIndexField;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ElasticSearchTaskHandler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ElasticSearchTaskHandler.class);
    private static final String RERANK_SCORE_FIELD_NAME = "Rerank Score";
    private static final Pattern RERANK_VECTOR_FIELD_NAME_PATTERN = Pattern.compile("^(.+)\\.[^.]+$");
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
            final DocRef rerankModelRef = elasticIndex.getRerankModelRef();
            final OpenAIModelDoc rerankModel = rerankModelRef != null
                    ?
                    openAIModelStore.readDocument(rerankModelRef)
                    : null;
            final ElasticConnectionConfig connectionConfig = elasticCluster.getConnection();

            final CompletableFuture<Void> searchFuture = executeSearch(
                    taskContext,
                    elasticIndex,
                    rerankModel,
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

    private CompletableFuture<Void> executeSearch(final TaskContext parentContext,
                                                  final ElasticIndexDoc elasticIndex,
                                                  final OpenAIModelDoc rerankModel,
                                                  final ElasticQueryParams queryParams,
                                                  final Highlight highlightBuilder,
                                                  final Coprocessors coprocessors,
                                                  final ResultStore resultStore,
                                                  final ValuesConsumer valuesConsumer,
                                                  final ErrorConsumer errorConsumer,
                                                  final AtomicLong hitCount,
                                                  final ElasticConnectionConfig connectionConfig) {

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
                                    rerankModel,
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

    private void searchSlice(final ElasticIndexDoc elasticIndex,
                             final OpenAIModelDoc rerankModel,
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

            if (elasticSearchConfigProvider.get().getHighlight()) {
                searchRequestBuilder.highlight(highlightBuilder);
            }

            // Limit the returned fields to what the values consumers require
            final FieldIndex fieldIndex = coprocessors.getFieldIndex();
            final String[] fieldNames = coprocessors.getFieldIndex().getFields();
            searchRequestBuilder.fields(Arrays.stream(fieldNames)
                    .map(fieldName -> FieldAndFormat.of(f -> f.field(fieldName)))
                    .toList()
            );

            // Number of slices needs to be > 1 else an exception is raised
            if (elasticIndex.getSearchSlices() > 1) {
                searchRequestBuilder.slice(SlicedScroll.of(s -> s
                        .id(String.valueOf(slice))
                        .max(elasticIndex.getSearchSlices())
                ));
            }

            // Insert a field representing the rerank score, if enabled
            if (rerankModel != null) {
                fieldIndex.create(RERANK_SCORE_FIELD_NAME);
            }

            final SearchResponse<ObjectNode> searchResponse = elasticClient.search(
                    searchRequestBuilder.build(),
                    ObjectNode.class);
            final String scrollId = searchResponse.scrollId();

            // Retrieve the initial result batch
            List<Hit<ObjectNode>> searchHits = searchResponse.hits().hits();
            long totalHitCount = 0L;

            // Continue requesting results until we have all results
            while (!taskContext.isTerminated() && !searchHits.isEmpty()) {
                totalHitCount += searchHits.size();
                processResultBatch(fieldIndex, elasticIndex, queryParams, rerankModel, resultStore, valuesConsumer,
                        errorConsumer, hitCount, searchHits);

                final long totalHits = totalHitCount;
                taskContext.info(() -> LogUtil.message("Processed {} hits", totalHits));

                final ScrollResponse<ObjectNode> scrollResponse = elasticClient.scroll(s -> s
                                .scrollId(scrollId)
                                .scroll(scrollTime),
                        ObjectNode.class
                );

                searchHits = scrollResponse.hits().hits();
            }

            LOGGER.info("Search completed for index doc {}, {} hits returned", elasticIndex.getName(), totalHitCount);

            // Close the scroll context as we're done streaming results
            elasticClient.clearScroll(s -> s.scrollId(scrollId));
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

    /**
     * Receive a batch of search hits and send each one to the values consumer
     */
    private void processResultBatch(final FieldIndex fieldIndex,
                                    final ElasticIndexDoc elasticIndex,
                                    final ElasticQueryParams queryParams,
                                    final OpenAIModelDoc rerankModel,
                                    final ResultStore resultStore,
                                    final ValuesConsumer valuesConsumer,
                                    final ErrorConsumer errorConsumer,
                                    final AtomicLong hitCount,
                                    final List<Hit<ObjectNode>> searchHits) {
        try {
            final Map<String, Content> rankedContentMap = new HashMap<>();
            final boolean performRerank = rerankModel != null && !queryParams.getKnnFieldQueries().isEmpty();
            if (performRerank) {
                rerankSearchHits(elasticIndex, queryParams, rerankModel, searchHits, rankedContentMap);
            }

            for (final Hit<ObjectNode> searchHit : searchHits) {
                if (performRerank && !rankedContentMap.containsKey(searchHit.id())) {
                    // Search hit excluded due to rerank minimum score cutoff
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
                    if (performRerank && rankedContentMap.containsKey(searchHit.id()) &&
                        RERANK_SCORE_FIELD_NAME.equals(fieldName)) {
                        fieldValue = rankedContentMap.get(searchHit.id()).metadata().get(
                                ContentMetadata.RERANKED_SCORE);
                    } else {
                        fieldValue = getFieldValue(mapSearchHit, fieldName);
                    }

                    if (fieldValue != null) {
                        if (values == null) {
                            values = new Val[fieldIndex.size()];
                        }

                        if (fieldValue instanceof Long) {
                            // TODO Need to handle date fields as ValDate, assuming they come in as longs,
                            //  but we need the field type from somewhere
                            values[insertAt] = ValLong.create((Long) fieldValue);
                        } else if (fieldValue instanceof Integer) {
                            values[insertAt] = ValInteger.create((Integer) fieldValue);
                        } else if (fieldValue instanceof Double) {
                            values[insertAt] = ValDouble.create((Double) fieldValue);
                        } else if (fieldValue instanceof Float) {
                            values[insertAt] = ValDouble.create((Float) fieldValue);
                        } else if (fieldValue instanceof Boolean) {
                            values[insertAt] = ValBoolean.create((Boolean) fieldValue);
                        } else if (fieldValue instanceof final Collection<?> collectionValue) {
                            // Multi-valued fields (including values gathered from across nested objects) are
                            // flattened to a single, comma-delimited string.
                            values[insertAt] = ValString.create(collectionValue.stream()
                                    .map(Object::toString)
                                    .collect(Collectors.joining(", ")));
                        } else {
                            values[insertAt] = ValString.create(fieldValue.toString());
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

    private void rerankSearchHits(final ElasticIndexDoc elasticIndex,
                                  final ElasticQueryParams queryParams,
                                  final OpenAIModelDoc rerankModel,
                                  final List<Hit<ObjectNode>> searchHits,
                                  final Map<String, Content> rankedContentMap) {
        final Map<String, String> denseVectorToTextFieldMapping = new HashMap<>();
        for (final ElasticIndexField field : elasticIndex.getFields()) {
            if (FieldType.DENSE_VECTOR.equals(field.getFldType())) {
                final String fieldName = field.getFldName();
                final Matcher fieldNameMatcher = RERANK_VECTOR_FIELD_NAME_PATTERN.matcher(fieldName);
                if (fieldNameMatcher.matches()) {
                    final String textFieldName = fieldNameMatcher.group(1) + elasticIndex.getRerankTextFieldSuffix();
                    final boolean textFieldExists = elasticIndex.getFields().stream()
                            .anyMatch(f -> f.getFldName().equals(textFieldName));
                    if (textFieldExists) {
                        denseVectorToTextFieldMapping.put(fieldName, textFieldName);
                    } else {
                        throw new IllegalArgumentException("Text field `" + textFieldName + "` for rerank scoring " +
                                                           "was not found");
                    }
                }
            }
        }

        final ScoringModel scoringModel = aiServiceProvider.get().getJinaScoringModel(rerankModel);
        final int maxContextWindowTokens = rerankModel.getMaxContextWindowTokens();
        final ReRankingContentAggregator rerankAggregator = ReRankingContentAggregator.builder()
                .scoringModel(scoringModel)
                .minScore(elasticIndex.getRerankScoreMinimum().doubleValue())
                .build();

        final Map<Query, Collection<List<Content>>> queryContentMap = new HashMap<>();
        final Map<String, List<String>> fieldValueToDocIdMap = new HashMap<>();
        for (final String denseVectorField : denseVectorToTextFieldMapping.keySet()) {
            final String denseVectorTextField = denseVectorToTextFieldMapping.get(denseVectorField);
            final String knnQueryTerm = queryParams.getKnnFieldQueries().get(denseVectorField);
            final List<Content> fieldValues = new ArrayList<>();

            if (knnQueryTerm != null) {
                if (!queryContentMap.isEmpty()) {
                    throw new UnsupportedOperationException("Rerank using multiple dense_vector fields is not " +
                                                            "supported");
                }
                for (final Hit<ObjectNode> searchHit : searchHits) {
                    final Map<String, JsonData> mapSearchHit = searchHit.fields();
                    // The reranker source text may itself live inside a nested object, so resolve it using the
                    // same nested-aware extraction used for result values rather than a flat map lookup.
                    final Object rawFieldValue = getFieldValue(mapSearchHit, denseVectorTextField);
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
                            fieldValueToDocIdMap.computeIfAbsent(fieldValue,
                                    k -> new ArrayList<>()).add(searchHit.id());
                            fieldValues.add(Content.from(TextSegment.from(fieldValue)));
                        } catch (final Exception e) {
                            throw new RuntimeException("Failed to parse value '" + rawFieldValue + "' for field " +
                                                       denseVectorTextField, e);
                        }
                    }
                }

                queryContentMap.put(Query.from(knnQueryTerm), List.of(fieldValues));
            }
        }

        final List<Content> rankedContent = rerankAggregator.aggregate(queryContentMap);
        for (final Content content : rankedContent) {
            final List<String> docIds = fieldValueToDocIdMap.get(content.textSegment().text());
            if (docIds != null) {
                for (final String docId : docIds) {
                    rankedContentMap.put(docId, content);
                }
            }
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
     */
    private Object getFieldValue(final Map<String, JsonData> searchHitMap, final String fieldName) {
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
    private void collectFieldValues(final Map<String, JsonValue> fields,
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
    private int findNestedSplit(final Map<String, JsonValue> fields, final String fieldPath) {
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
    private boolean isArrayOfObjects(final JsonValue value) {
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
    private void addLeafValues(final JsonValue value, final List<Object> out) {
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
    private Object toFieldResult(final List<Object> values) {
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
    private String firstStringValue(final Object fieldValue) {
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

    private Object jsonValueToNative(final JsonValue jsonValue) {
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
                    return jsonNumber.longValue();
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

    private void error(final ErrorConsumer errorConsumer, final Throwable t) {
        if (!(t instanceof UncheckedInterruptedException)) {
            if (errorConsumer == null) {
                LOGGER.error(t::getMessage, t);
            } else {
                errorConsumer.add(t);
            }
        }
    }
}
