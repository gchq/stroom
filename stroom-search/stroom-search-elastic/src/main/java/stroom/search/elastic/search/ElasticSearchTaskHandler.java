/*
 * Copyright 2016-2024 Crown Copyright
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
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.ScrollResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Highlight;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.SourceConfig;
import co.elastic.clients.json.JsonData;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class ElasticSearchTaskHandler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ElasticSearchTaskHandler.class);
    public static final ThreadPool SCROLL_REQUEST_THREAD_POOL =
            new ThreadPoolImpl("Elasticsearch Scroll Request");

    private final Provider<ElasticSearchConfig> elasticSearchConfigProvider;
    private final ElasticClientCache elasticClientCache;
    private final ElasticClusterStore elasticClusterStore;
    private final ExecutorProvider executorProvider;
    private final TaskContextFactory taskContextFactory;

    @Inject
    ElasticSearchTaskHandler(final Provider<ElasticSearchConfig> elasticSearchConfigProvider,
                             final ElasticClientCache elasticClientCache,
                             final ElasticClusterStore elasticClusterStore,
                             final ExecutorProvider executorProvider,
                             final TaskContextFactory taskContextFactory) {
        this.elasticSearchConfigProvider = elasticSearchConfigProvider;
        this.elasticClientCache = elasticClientCache;
        this.elasticClusterStore = elasticClusterStore;
        this.executorProvider = executorProvider;
        this.taskContextFactory = taskContextFactory;
    }

    public void search(final TaskContext taskContext,
                       final ElasticIndexDoc elasticIndex,
                       final Query queryBuilder,
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
            final ElasticConnectionConfig connectionConfig = elasticCluster.getConnection();

            final CompletableFuture<Void> searchFuture = executeSearch(
                    taskContext,
                    elasticIndex,
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
                                                  final Query queryBuilder,
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
                                    queryBuilder,
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
                             final Query queryBuilder,
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
                    .query(queryBuilder)
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
            final List<String> fieldNames = coprocessors.getFieldIndex().getFields();
            searchRequestBuilder.fields(fieldNames.stream()
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

            SearchResponse<ObjectNode> searchResponse = elasticClient.search(searchRequestBuilder.build(),
                    ObjectNode.class);
            String scrollId = searchResponse.scrollId();

            // Retrieve the initial result batch
            List<Hit<ObjectNode>> searchHits = searchResponse.hits().hits();
            long totalHitCount = 0L;

            // Continue requesting results until we have all results
            while (!taskContext.isTerminated() && !searchHits.isEmpty()) {
                totalHitCount += searchHits.size();
                processResultBatch(fieldIndex, resultStore, valuesConsumer, errorConsumer, hitCount, searchHits);

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
                                    final ResultStore resultStore,
                                    final ValuesConsumer valuesConsumer,
                                    final ErrorConsumer errorConsumer,
                                    final AtomicLong hitCount,
                                    final List<Hit<ObjectNode>> searchHits) {
        try {
            for (final Hit<ObjectNode> searchHit : searchHits) {
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
                    Object fieldValue = getFieldValue(mapSearchHit, fieldName);

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
                        } else if (fieldValue instanceof ArrayList) {
                            values[insertAt] = ValString.create(((ArrayList<?>) fieldValue).stream()
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

    /**
     * Locate the value of the doc field by its full path
     */
    private Object getFieldValue(final Map<String, JsonData> searchHitMap, final String fieldName) {
        if (fieldName == null || !searchHitMap.containsKey(fieldName)) {
            return null;
        }

        JsonArray docField = searchHitMap.get(fieldName).toJson().asJsonArray();
        if (docField.size() > 1) {
            return docField.stream()
                    .map(this::jsonValueToNative)
                    .collect(Collectors.toList());
        } else {
            return jsonValueToNative(docField.get(0));
        }
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
