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

package stroom.search.solr.search;

import stroom.annotation.shared.AnnotationDecorationFields;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.Query;
import stroom.query.api.QueryKey;
import stroom.query.common.v2.Coprocessors;
import stroom.query.common.v2.SearchProgressLog;
import stroom.query.common.v2.SearchProgressLog.SearchPhase;
import stroom.search.extraction.ExpressionFilter;
import stroom.search.extraction.ExtractionDecorator;
import stroom.search.extraction.ExtractionDecoratorFactory;
import stroom.search.extraction.StoredDataQueue;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

class SolrClusterSearchTaskHandler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SolrClusterSearchTaskHandler.class);

    private final SolrSearchFactory solrSearchFactory;
    private final ExtractionDecoratorFactory extractionDecoratorFactory;
    private final SecurityContext securityContext;
    private final ExecutorProvider executorProvider;

    private final LongAdder hitCount = new LongAdder();
    private final LongAdder extractionCount = new LongAdder();

    private TaskContext taskContext;
    private QueryKey queryKey;

    @Inject
    SolrClusterSearchTaskHandler(final SolrSearchFactory solrSearchFactory,
                                 final ExtractionDecoratorFactory extractionDecoratorFactory,
                                 final SecurityContext securityContext,
                                 final ExecutorProvider executorProvider) {
        this.solrSearchFactory = solrSearchFactory;
        this.extractionDecoratorFactory = extractionDecoratorFactory;
        this.securityContext = securityContext;
        this.executorProvider = executorProvider;
    }

    public void search(final TaskContext taskContext,
                       final QueryKey queryKey,
                       final Query query,
                       final DateTimeSettings dateTimeSettings,
                       final Coprocessors coprocessors) {
        SearchProgressLog.increment(queryKey, SearchPhase.CLUSTER_SEARCH_TASK_HANDLER_EXEC);
        this.taskContext = taskContext;
        this.queryKey = queryKey;
        securityContext.useAsRead(() -> {
            if (!Thread.currentThread().isInterrupted()) {
                taskContext.info(() -> "Initialising...");

                // Start searching.
                doSearch(taskContext, dateTimeSettings, queryKey, query, coprocessors);
            }
        });
    }

    private void doSearch(final TaskContext taskContext,
                          final DateTimeSettings dateTimeSettings,
                          final QueryKey queryKey,
                          final Query query,
                          final Coprocessors coprocessors) {
        taskContext.info(() -> "Searching...");
        LOGGER.debug(() -> "Incoming search request:\n" + query.getExpression().toString());

        // Start searching.
        SearchProgressLog.increment(queryKey, SearchPhase.CLUSTER_SEARCH_TASK_HANDLER_SEARCH);

        try {
            final ExtractionDecorator extractionDecorator = extractionDecoratorFactory.create(queryKey);
            final StoredDataQueue storedDataQueue = extractionDecorator.createStoredDataQueue(
                    coprocessors,
                    query);

            // Search all index shards.
            final ExpressionFilter expressionFilter = ExpressionFilter.builder()
                    .addPrefixExcludeFilter(AnnotationDecorationFields.ANNOTATION_FIELD_PREFIX)
                    .build();
            final ExpressionOperator expression = expressionFilter.copy(query.getExpression());
            final CompletableFuture<Void> indexShardSearchFuture = solrSearchFactory.search(
                    queryKey,
                    query,
                    dateTimeSettings,
                    expression,
                    coprocessors.getFieldIndex(),
                    taskContext,
                    hitCount,
                    storedDataQueue,
                    coprocessors.getErrorConsumer());

            // Start mapping streams.
            final CompletableFuture<Void> streamMappingFuture = extractionDecorator
                    .startMapping(taskContext, coprocessors);

            // Start extracting data.
            final CompletableFuture<Void> extractionFuture = extractionDecorator
                    .startExtraction(taskContext, extractionCount, coprocessors.getErrorConsumer());

            // Create a countdown latch to keep updating status until we complete.
            final CountDownLatch complete = new CountDownLatch(1);

            // Wait for all to complete.
            final CompletableFuture<Void> all = CompletableFuture
                    .allOf(indexShardSearchFuture, streamMappingFuture, extractionFuture);
            all.whenCompleteAsync((r, t) -> complete.countDown(), executorProvider.get());

            // Update status until we complete.
            while (!complete.await(1, TimeUnit.SECONDS)) {
                updateInfo();
            }

            LOGGER.debug(() -> "Complete");
        } catch (final InterruptedException e) {
            LOGGER.trace(e::getMessage, e);
            // Keep interrupting this thread.
            Thread.currentThread().interrupt();
        } catch (final RuntimeException e) {
            throw SearchException.wrap(e);
        }
    }

    public void updateInfo() {
        taskContext.info(() -> "" +
                "Searching... " +
                "found "
                + hitCount.sum() +
                " documents" +
                " performed " +
                extractionCount.sum() +
                " extractions");
    }
}
