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
import stroom.search.coprocessor.Error;
import stroom.search.coprocessor.Values;
import stroom.search.elastic.ElasticClientCache;
import stroom.search.elastic.ElasticClusterStore;
import stroom.search.elastic.shared.ElasticCluster;
import stroom.search.elastic.shared.ElasticConnectionConfig;
import stroom.search.elastic.shared.ElasticIndex;
import stroom.task.server.TaskContext;
import stroom.util.concurrent.ExecutorProvider;
import stroom.util.concurrent.ThreadPoolImpl;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ThreadPool;
import stroom.util.shared.VoidResult;
import stroom.util.spring.StroomScope;
import stroom.util.task.TaskWrapper;

import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.search.Scroll;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.inject.Provider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Component
@Scope(StroomScope.TASK)
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
            "Search Elasticsearch Index Shard",
            5,
            0,
            Integer.MAX_VALUE);

    private final ElasticClientCache elasticClientCache;
    private final ElasticClusterStore elasticClusterStore;
    private final Executor executor;
    private final Provider<TaskWrapper> taskWrapperProvider;
    private final TaskContext taskContext;

    @Inject
    ElasticSearchTaskHandler(final ElasticClientCache elasticClientCache,
                             final ElasticClusterStore elasticClusterStore,
                             final ExecutorProvider executorProvider,
                             final Provider<TaskWrapper> taskWrapperProvider,
                             final TaskContext taskContext) {
        this.elasticClientCache = elasticClientCache;
        this.elasticClusterStore = elasticClusterStore;
        this.executor = executorProvider.getExecutor(THREAD_POOL);
        this.taskWrapperProvider = taskWrapperProvider;
        this.taskContext = taskContext;
    }

    public VoidResult exec(final ElasticSearchTask task) {
        LOGGER.logDurationIfDebugEnabled(
            () -> {
                try {
                    taskContext.setName("Search Elasticsearch Index");
                    if (!taskContext.isTerminated()) {
                        taskContext.info("Searching Elasticsearch index");

                        // Start searching.
                        searchIndex(task);
                    }

                } catch (final RuntimeException e) {
                    LOGGER.debug(e::getMessage, e);
                    error(task, e.getMessage(), e);
                }
            },
            () -> LambdaLogger.buildMessage("exec()"));

        return VoidResult.INSTANCE;
    }

    private void searchIndex(final ElasticSearchTask task) {
        final ElasticIndex elasticIndex = task.getElasticIndex();
        final ElasticCluster elasticCluster = elasticClusterStore.readDocument(elasticIndex.getClusterRef());
        final ElasticConnectionConfig connectionConfig = elasticCluster.getConnectionConfig();

        // If there is an error building the query then it will be null here.
        try {
            final Runnable runnable = taskWrapperProvider.get().wrap(() -> {
                taskContext.setName("Index Searcher");
                LOGGER.logDurationIfDebugEnabled(
                    () -> {
                        try {
                            streamingSearch(task, elasticIndex, connectionConfig);
                        } catch (final RuntimeException e) {
                            error(task, e.getMessage(), e);
                        } finally {
                            task.getTracker().complete();
                        }
                    },
                    () -> "searcher.search()");
            });
            CompletableFuture.runAsync(runnable, executor);
        } catch (final RuntimeException e) {
            error(task, e.getMessage(), e);
        }
    }

    private void streamingSearch(final ElasticSearchTask task, final ElasticIndex elasticIndex, final ElasticConnectionConfig connectionConfig) {
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
                final int maxResultSize = task.getResultCollector().getMaxResultSizes().size(0);
                while (searchHits != null && searchHits.length > 0 && task.getTracker().getHitCount() < maxResultSize) {
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
        final Consumer<Values> valuesConsumer = task.getReceiver().getValuesConsumer();
        final Consumer<Error> errorConsumer = task.getReceiver().getErrorConsumer();

        try {
            for (final SearchHit searchHit : searchHits) {
                tracker.incrementHitCount();

                final Map<String, Object> mapSearchHit = searchHit.getSourceAsMap();
                Val[] values = null;

                for (int i = 0; i < fieldNames.length; i++) {
                    final String fieldName = fieldNames[i];

                    Object value = null;

                    if (mapSearchHit.containsKey(fieldName)) {
                        // Property found by its key
                        value = mapSearchHit.get(fieldName);
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
                                    ArrayList<HashMap<String, Object>> propertyArrayMap = (ArrayList<HashMap<String, Object>>) propertyArray;

                                    // Create an array of all child properties matching the field path
                                    value = propertyArrayMap.stream()
                                            .map(prop -> prop.get(childPropertyName))
                                            .collect(Collectors.toList());
                                }
                            }
                        } else if (property instanceof HashMap) {
                            @SuppressWarnings("unchecked")
                            final HashMap<String, Object> propertyMap = (HashMap<String, Object>) property;

                            // Get the child property matching the field path
                            value = propertyMap.get(childPropertyName);
                        }
                    }

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
                    valuesConsumer.accept(new Values(values));
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
