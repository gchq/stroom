/*
 * Copyright 2016 Crown Copyright
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

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.document.DocumentField;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.Scroll;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.slice.SliceBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
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
                       final QueryBuilder queryBuilder,
                       final HighlightBuilder highlightBuilder,
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
                                                  final QueryBuilder queryBuilder,
                                                  final HighlightBuilder highlightBuilder,
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
                             final QueryBuilder queryBuilder,
                             final HighlightBuilder highlightBuilder,
                             final Coprocessors coprocessors,
                             final ResultStore resultStore,
                             final ValuesConsumer valuesConsumer,
                             final ErrorConsumer errorConsumer,
                             final AtomicLong hitCount,
                             final RestHighLevelClient elasticClient,
                             final int slice,
                             final TaskContext taskContext) {
        try {
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
                    .query(queryBuilder)
                    .fetchSource(false)
                    .size(elasticIndex.getSearchScrollSize());

            if (elasticSearchConfigProvider.get().getHighlight()) {
                searchSourceBuilder.highlighter(highlightBuilder);
            }

            // Limit the returned fields to what the values consumers require
            final FieldIndex fieldIndex = coprocessors.getFieldIndex();
            for (String field : fieldIndex.getFields()) {
                searchSourceBuilder.fetchField(field);
            }

            // Number of slices needs to be > 1 else an exception is raised
            if (elasticIndex.getSearchSlices() > 1) {
                searchSourceBuilder.slice(new SliceBuilder(slice, elasticIndex.getSearchSlices()));
            }

            final long scrollSeconds = elasticSearchConfigProvider.get().getScrollDuration().getDuration().getSeconds();
            final Scroll scroll = new Scroll(TimeValue.timeValueSeconds(scrollSeconds));
            final SearchRequest searchRequest = new SearchRequest(elasticIndex.getIndexName())
                    .source(searchSourceBuilder)
                    .scroll(scroll);

            SearchResponse searchResponse = elasticClient.search(searchRequest, RequestOptions.DEFAULT);
            String scrollId = searchResponse.getScrollId();

            // Retrieve the initial result batch
            SearchHit[] searchHits = searchResponse.getHits().getHits();
            processResultBatch(fieldIndex, resultStore, valuesConsumer, errorConsumer, hitCount, searchHits);
            int totalHitCount = searchHits.length;

            // Continue requesting results until we have all results
            while (!taskContext.isTerminated() && searchHits.length > 0) {
                SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId).scroll(scroll);
                searchResponse = elasticClient.scroll(scrollRequest, RequestOptions.DEFAULT);
                searchHits = searchResponse.getHits().getHits();

                processResultBatch(fieldIndex, resultStore, valuesConsumer, errorConsumer, hitCount, searchHits);

                totalHitCount += searchHits.length;
                final Integer finalTotalHitCount = totalHitCount;
                taskContext.info(() -> LogUtil.message("Processed {} hits", finalTotalHitCount));
            }

            // Close the scroll context as we're done streaming results
            ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
            clearScrollRequest.addScrollId(scrollId);
            elasticClient.clearScroll(clearScrollRequest, RequestOptions.DEFAULT);
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
                                    final SearchHit[] searchHits) {
        try {
            for (final SearchHit searchHit : searchHits) {
                hitCount.incrementAndGet();

                // Add highlights
                if (elasticSearchConfigProvider.get().getHighlight()) {
                    for (final HighlightField highlightField : searchHit.getHighlightFields().values()) {
                        resultStore.addHighlights(Arrays.stream(highlightField.getFragments())
                                .map(Text::string)
                                .collect(Collectors.toSet()));
                    }
                }

                final Map<String, DocumentField> mapSearchHit = searchHit.getFields();
                Val[] values = null;

                for (final String fieldName : fieldIndex.getFields()) {
                    final Integer insertAt = fieldIndex.getPos(fieldName);
                    Object fieldValue = getFieldValue(mapSearchHit, fieldName);

                    if (fieldValue != null) {
                        if (values == null) {
                            values = new Val[fieldIndex.size()];
                        }

                        if (fieldValue instanceof Long) {
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
    private Object getFieldValue(final Map<String, DocumentField> searchHitMap, final String fieldName) {
        if (fieldName == null || !searchHitMap.containsKey(fieldName)) {
            return null;
        }

        DocumentField docField = searchHitMap.get(fieldName);
        if (docField.getValues().size() > 1) {
            return docField.getValues();
        } else {
            return docField.getValue();
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
