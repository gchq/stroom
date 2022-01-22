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
import stroom.dashboard.expression.v1.ValuesConsumer;
import stroom.query.common.v2.ErrorConsumer;
import stroom.search.elastic.ElasticClientCache;
import stroom.search.elastic.ElasticClusterStore;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticConnectionConfig;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.task.api.TaskContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.document.DocumentField;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.Scroll;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.slice.SliceBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.inject.Inject;

public class ElasticSearchTaskHandler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ElasticSearchTaskHandler.class);

    /**
     * Number of minutes to allow a scroll request to continue before being aborted
     */
    private static final long SCROLL_DURATION = 1L;

    private final ElasticClientCache elasticClientCache;
    private final ElasticClusterStore elasticClusterStore;
    private final CountDownLatch completionLatch = new CountDownLatch(1);

    @Inject
    ElasticSearchTaskHandler(final ElasticClientCache elasticClientCache,
                             final ElasticClusterStore elasticClusterStore) {
        this.elasticClientCache = elasticClientCache;
        this.elasticClusterStore = elasticClusterStore;
    }

    public void search(final TaskContext taskContext,
                       final ElasticIndexDoc elasticIndex,
                       final QueryBuilder query,
                       final FieldIndex fieldIndex,
                       final ValuesConsumer valuesConsumer,
                       final ErrorConsumer errorConsumer,
                       final AtomicLong hitCount) {
        if (!Thread.currentThread().isInterrupted()) {
            taskContext.reset();
            taskContext.info(() -> "Searching Elasticsearch index");

            // Start searching.
            searchIndex(
                    elasticIndex,
                    query,
                    fieldIndex,
                    valuesConsumer,
                    errorConsumer,
                    hitCount);
        }
    }

    private void searchIndex(final ElasticIndexDoc elasticIndex,
                             final QueryBuilder query,
                             final FieldIndex fieldIndex,
                             final ValuesConsumer valuesConsumer,
                             final ErrorConsumer errorConsumer,
                             final AtomicLong hitCount) {
        final ElasticClusterDoc elasticCluster = elasticClusterStore.readDocument(elasticIndex.getClusterRef());
        final ElasticConnectionConfig connectionConfig = elasticCluster.getConnection();

        // If there is an error building the query then it will be null here.
        try {
            LOGGER.logDurationIfDebugEnabled(
                    () -> {
                        try {
                            streamingSearch(
                                    elasticIndex,
                                    query,
                                    fieldIndex,
                                    valuesConsumer,
                                    errorConsumer,
                                    hitCount,
                                    connectionConfig);
                        } catch (final RuntimeException e) {
                            error(errorConsumer, e);
                        } finally {
                            completionLatch.countDown();
                        }
                    },
                    () -> "searcher.search()");
        } catch (final RuntimeException e) {
            error(errorConsumer, e);
        }
    }

    private void streamingSearch(final ElasticIndexDoc elasticIndex,
                                 final QueryBuilder query,
                                 final FieldIndex fieldIndex,
                                 final ValuesConsumer valuesConsumer,
                                 final ErrorConsumer errorConsumer,
                                 final AtomicLong hitCount,
                                 final ElasticConnectionConfig connectionConfig) {
        elasticClientCache.context(connectionConfig, elasticClient -> {
            try {
                IntStream.range(0, elasticIndex.getSearchSlices()).parallel().forEach(slice -> {
                    try {
                        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
                                .query(query)
                                .fetchSource(false)
                                .size(elasticIndex.getSearchScrollSize());

                        // Limit the returned fields to what the values consumers require
                        for (var field : fieldIndex.getFieldNames()) {
                            searchSourceBuilder.fetchField(field);
                        }

                        // Number of slices needs to be > 1 else an exception is raised
                        if (elasticIndex.getSearchSlices() > 1) {
                            searchSourceBuilder.slice(new SliceBuilder(slice, elasticIndex.getSearchSlices()));
                        }

                        final Scroll scroll = new Scroll(TimeValue.timeValueMinutes(SCROLL_DURATION));
                        SearchRequest searchRequest = new SearchRequest(elasticIndex.getIndexName())
                                .source(searchSourceBuilder)
                                .scroll(scroll);

                        SearchResponse searchResponse = elasticClient.search(searchRequest, RequestOptions.DEFAULT);
                        String scrollId = searchResponse.getScrollId();

                        SearchHit[] searchHits = searchResponse.getHits().getHits();
                        processBatch(
                                fieldIndex,
                                valuesConsumer,
                                errorConsumer,
                                hitCount,
                                searchHits);

                        // Continue requesting results until we have all results
                        while (searchHits != null && searchHits.length > 0) {
                            SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId);
                            scrollRequest.scroll(scroll);
                            searchResponse = elasticClient.scroll(scrollRequest, RequestOptions.DEFAULT);
                            searchHits = searchResponse.getHits().getHits();

                            processBatch(
                                    fieldIndex,
                                    valuesConsumer,
                                    errorConsumer,
                                    hitCount,
                                    searchHits);
                        }

                        ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
                        clearScrollRequest.addScrollId(scrollId);
                        elasticClient.clearScroll(clearScrollRequest, RequestOptions.DEFAULT);
                    } catch (final IOException | RuntimeException e) {
                        error(errorConsumer, e);
                    }
                });

                LOGGER.debug(() -> "Total hits: " + hitCount.get());
            } catch (final RuntimeException e) {
                error(errorConsumer, e);
            }
        });
    }

    private void processBatch(final FieldIndex fieldIndex,
                              final ValuesConsumer valuesConsumer,
                              final ErrorConsumer errorConsumer,
                              final AtomicLong hitCount,
                              final SearchHit[] searchHits) {
        try {
            for (final SearchHit searchHit : searchHits) {
                hitCount.incrementAndGet();

                final Map<String, DocumentField> mapSearchHit = searchHit.getFields();
                Val[] values = null;

                for (final String fieldName : fieldIndex.getFieldNames()) {
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
                    valuesConsumer.add(values);
                }
            }
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
        if (errorConsumer == null) {
            LOGGER.error(t::getMessage, t);
        } else {
            errorConsumer.add(t);
        }
    }
}
