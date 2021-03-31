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

import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValBoolean;
import stroom.dashboard.expression.v1.ValDouble;
import stroom.dashboard.expression.v1.ValInteger;
import stroom.dashboard.expression.v1.ValLong;
import stroom.dashboard.expression.v1.ValString;
import stroom.search.elastic.ElasticClientCache;
import stroom.search.elastic.ElasticClusterStore;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticConnectionConfig;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.ThreadPoolImpl;
import stroom.task.shared.ThreadPool;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.search.Scroll;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import javax.inject.Inject;

public class ElasticSearchTaskHandler {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ElasticSearchTaskHandler.class);

    /**
     * Number of minutes to allow a scroll request to continue before being aborted
     */
    private static final long SCROLL_DURATION = 1L;

    /**
     * Number of documents to return in a single search scroll request
     */
    private static final int SCROLL_SIZE = 1000;

    private static final ThreadPool THREAD_POOL = new ThreadPoolImpl(
        "Search Elasticsearch Index",
        5,
        0,
        Integer.MAX_VALUE);

    private final ElasticClientCache elasticClientCache;
    private final ElasticClusterStore elasticClusterStore;
    private final Executor executor;
    private final TaskContextFactory taskContextFactory;
    private final CountDownLatch completionLatch = new CountDownLatch(1);

    @Inject
    ElasticSearchTaskHandler(final ElasticClientCache elasticClientCache,
                             final ElasticClusterStore elasticClusterStore,
                             final ExecutorProvider executorProvider,
                             final TaskContextFactory taskContextFactory) {
        this.elasticClientCache = elasticClientCache;
        this.elasticClusterStore = elasticClusterStore;
        this.executor = executorProvider.get(THREAD_POOL);
        this.taskContextFactory = taskContextFactory;
    }

    public void exec(final TaskContext parentContext, final ElasticSearchTask task) {
        taskContextFactory.context(parentContext, "Index Searcher", taskContext ->
                LOGGER.logDurationIfDebugEnabled(
                        () -> {
                            try {
                                if (Thread.interrupted()) {
                                    Thread.currentThread().interrupt();
                                    throw new RuntimeException("Interrupted");
                                }

                                taskContext.info(() -> "Searching Elasticsearch index");

                                // Start searching.
                                searchIndex(task, taskContext);

                            } catch (final RuntimeException e) {
                                LOGGER.debug(e::getMessage, e);
                                error(task, e.getMessage(), e);
                            }
                        },
                        "exec()"))
                .run();
    }

    private void searchIndex(final ElasticSearchTask task, final TaskContext taskContext) {
        final ElasticIndexDoc elasticIndex = task.getElasticIndex();
        final ElasticClusterDoc elasticCluster = elasticClusterStore.readDocument(elasticIndex.getClusterRef());
        final ElasticConnectionConfig connectionConfig = elasticCluster.getConnectionConfig();

        // If there is an error building the query then it will be null here.
        try {
            final Runnable runnable = () ->
                LOGGER.logDurationIfDebugEnabled(
                    () -> {
                        try {
                            streamingSearch(task, elasticIndex, connectionConfig);
                        } catch (final RuntimeException e) {
                            error(task, e.getMessage(), e);
                        } finally {
                            task.getTracker().complete();
                            completionLatch.countDown();
                        }
                    },
                    () -> "searcher.search()");
            CompletableFuture.runAsync(runnable, executor);
        } catch (final RuntimeException e) {
            error(task, e.getMessage(), e);
        }
    }

    private void streamingSearch(final ElasticSearchTask task,
                                 final ElasticIndexDoc elasticIndex,
                                 final ElasticConnectionConfig connectionConfig
    ) {
        elasticClientCache.context(connectionConfig, elasticClient -> {
            try {
                final Scroll scroll = new Scroll(TimeValue.timeValueMinutes(SCROLL_DURATION));
                SearchRequest searchRequest = new SearchRequest(elasticIndex.getIndexName());
                searchRequest.scroll(scroll);

                SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
                    .query(task.getQuery())
                    .size(SCROLL_SIZE);
                searchRequest.source(searchSourceBuilder);

                SearchResponse searchResponse = elasticClient.search(searchRequest, RequestOptions.DEFAULT);
                String scrollId = searchResponse.getScrollId();

                SearchHit[] searchHits = searchResponse.getHits().getHits();
                processBatch(task, searchHits);

                // Continue requesting results until we have all results
                while (searchHits != null && searchHits.length > 0) {
                    if (task.getAsyncSearchTask().getResultCollector().isComplete()) {
                        break;
                    }

                    SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId);
                    scrollRequest.scroll(scroll);
                    searchResponse = elasticClient.scroll(scrollRequest, RequestOptions.DEFAULT);
                    searchHits = searchResponse.getHits().getHits();

                    processBatch(task, searchHits);
                }

                ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
                clearScrollRequest.addScrollId(scrollId);
                elasticClient.clearScroll(clearScrollRequest, RequestOptions.DEFAULT);

                LOGGER.debug(() -> "Total hits: " + task.getTracker().getHitCount());
            } catch (final IOException | RuntimeException e) {
                error(task, e.getMessage(), e);
            }
        });
    }

    private void processBatch(final ElasticSearchTask task, final SearchHit[] searchHits) {
        final Tracker tracker = task.getTracker();
        final String[] fieldNames = task.getFieldNames();
        final Consumer<Val[]> valuesConsumer = task.getReceiver().getValuesConsumer();
        final Consumer<Throwable> errorConsumer = task.getReceiver().getErrorConsumer();

        try {
            for (final SearchHit searchHit : searchHits) {
                tracker.incrementHitCount();

                Val[] values = null;
                for (int i = 0; i < fieldNames.length; i++) {
                    final String fieldName = fieldNames[i];
                    final Object value = searchHit.getSourceAsMap().get(fieldName);

                    if (value != null) {
                        if (values == null) {
                            values = new Val[fieldNames.length];
                        }

                        if (value instanceof Long) {
                            values[i] = ValLong.create((Long) value);
                        } else if (value instanceof Integer) {
                            values[i] = ValInteger.create((Integer) value);
                        } else if (value instanceof Double) {
                            values[i] = ValDouble.create((Double) value);
                        } else if (value instanceof Float) {
                            values[i] = ValDouble.create((Float) value);
                        } else if (value instanceof Boolean) {
                            values[i] = ValBoolean.create((Boolean) value);
                        } else {
                            values[i] = ValString.create(value.toString());
                        }
                    }
                }

                if (values != null) {
                    valuesConsumer.accept(values);
                }
            }
        } catch (final RuntimeException e) {
            if (errorConsumer == null) {
                LOGGER.error(e::getMessage, e);
            } else {
                errorConsumer.accept(new Error(e.getMessage(), e));
            }
        }
    }

    private void error(final ElasticSearchTask task, final String message, final Throwable t) {
        if (task == null) {
            LOGGER.error(() -> message, t);
        } else {
            task.getReceiver().getErrorConsumer().accept(new Error(message, t));
        }
    }
}
