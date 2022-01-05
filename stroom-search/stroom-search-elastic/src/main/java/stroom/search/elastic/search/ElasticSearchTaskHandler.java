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

import stroom.dashboard.expression.v1.FieldIndex;
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
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.search.Scroll;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.slice.SliceBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.inject.Inject;

public class ElasticSearchTaskHandler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ElasticSearchTaskHandler.class);

    /**
     * Number of minutes to allow a scroll request to continue before being aborted
     */
    private static final long SCROLL_DURATION = 1L;

    private static final ThreadPool THREAD_POOL = new ThreadPoolImpl("Search Elasticsearch Index");

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
        taskContextFactory.childContext(parentContext, "Index Searcher", taskContext ->
                        LOGGER.logDurationIfDebugEnabled(
                                () -> {
                                    try {
                                        if (Thread.interrupted()) {
                                            Thread.currentThread().interrupt();
                                            throw new RuntimeException("Interrupted");
                                        }

                                        taskContext.info(() -> "Searching Elasticsearch index");

                                        // Start searching.
                                        searchIndex(task);

                                    } catch (final RuntimeException e) {
                                        LOGGER.debug(e::getMessage, e);
                                        error(task, e.getMessage(), e);
                                    }
                                },
                                "exec()"))
                .run();
    }

    private void searchIndex(final ElasticSearchTask task) {
        final ElasticIndexDoc elasticIndex = task.getElasticIndex();
        final ElasticClusterDoc elasticCluster = elasticClusterStore.readDocument(elasticIndex.getClusterRef());
        final ElasticConnectionConfig connectionConfig = elasticCluster.getConnection();

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

                SearchRequest searchRequest = new SearchRequest(elasticIndex.getIndexName())
                        .scroll(scroll);

                IntStream.range(0, elasticIndex.getSearchSlices()).parallel().forEach(slice -> {
                    try {
                        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
                                .query(task.getQuery())
                                .size(elasticIndex.getSearchScrollSize())
                                .slice(new SliceBuilder(slice, elasticIndex.getSearchSlices()));

                        searchRequest.source(searchSourceBuilder);
                        SearchResponse searchResponse = elasticClient.search(searchRequest, RequestOptions.DEFAULT);
                        String scrollId = searchResponse.getScrollId();

                        SearchHit[] searchHits = searchResponse.getHits().getHits();
                        processBatch(task, searchHits);

                        // Continue requesting results until we have all results
                        final int maxResultSize = task.getResultCollector().getMaxResultSizes().size(0);
                        while (searchHits != null && searchHits.length > 0 &&
                                task.getTracker().getHitCount() < maxResultSize) {
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
                    } catch (final IOException e) {
                        error(task, e.getMessage(), e);
                    }
                });

                LOGGER.debug(() -> "Total hits: " + task.getTracker().getHitCount());
            } catch (final RuntimeException e) {
                error(task, e.getMessage(), e);
            }
        });
    }

    private void processBatch(final ElasticSearchTask task, final SearchHit[] searchHits) {
        final Tracker tracker = task.getTracker();
        final FieldIndex fieldIndex = task.getFieldIndex();
        final Consumer<Val[]> valuesConsumer = task.getReceiver().getValuesConsumer();
        final Consumer<Throwable> errorConsumer = task.getReceiver().getErrorConsumer();

        try {
            for (final SearchHit searchHit : searchHits) {
                tracker.incrementHitCount();

                final Map<String, Object> mapSearchHit = searchHit.getSourceAsMap();
                Val[] values = null;

                for (final String fieldName : fieldIndex.getFieldNames()) {
                    final Integer insertAt = fieldIndex.getPos(fieldName);
                    Object fieldValue = null;

                    if (mapSearchHit.containsKey(fieldName)) {
                        // Property found by its key
                        fieldValue = mapSearchHit.get(fieldName);
                    } else if (fieldName.contains(".")) {
                        // Property is an object or array, so use the first part of the key to access the
                        // child properties or array items
                        final String[] fieldNameParts = fieldName.split("\\.");
                        final Object property = mapSearchHit.get(fieldNameParts[0]);
                        final String childPropertyName = fieldNameParts[1];

                        if (property instanceof ArrayList) {
                            final ArrayList<?> propertyArray = (ArrayList<?>) property;

                            if (propertyArray.size() > 0) {
                                if (propertyArray.get(0) instanceof HashMap) {
                                    @SuppressWarnings("unchecked")
                                    ArrayList<HashMap<String, Object>> propertyArrayMap =
                                            (ArrayList<HashMap<String, Object>>) propertyArray;

                                    // Create an array of all child properties matching the field path
                                    fieldValue = propertyArrayMap.stream()
                                            .map(prop -> prop.get(childPropertyName))
                                            .collect(Collectors.toList());
                                }
                            }
                        } else if (property instanceof HashMap) {
                            @SuppressWarnings("unchecked") final HashMap<String, Object> propertyMap =
                                    (HashMap<String, Object>) property;

                            // Get the child property matching the field path
                            fieldValue = propertyMap.get(childPropertyName);
                        }
                    }

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
