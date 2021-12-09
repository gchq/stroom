/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.search.solr.search;

import stroom.annotation.api.AnnotationFields;
import stroom.query.api.v2.DateTimeSettings;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.Query;
import stroom.query.common.v2.Coprocessors;
import stroom.search.extraction.ExpressionFilter;
import stroom.search.extraction.ExtractionDecoratorFactory;
import stroom.search.extraction.StoredDataQueue;
import stroom.search.extraction.StreamMapCreator;
import stroom.search.solr.CachedSolrIndex;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;

class SolrClusterSearchTaskHandler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SolrClusterSearchTaskHandler.class);

    private final SolrSearchFactory solrSearchFactory;
    private final ExtractionDecoratorFactory extractionDecoratorFactory;
    private final SecurityContext securityContext;

    private final AtomicLong hitCount = new AtomicLong();
    private final AtomicLong extractionCount = new AtomicLong();

    private TaskContext taskContext;

    @Inject
    SolrClusterSearchTaskHandler(final SolrSearchFactory solrSearchFactory,
                                 final ExtractionDecoratorFactory extractionDecoratorFactory,
                                 final SecurityContext securityContext) {
        this.solrSearchFactory = solrSearchFactory;
        this.extractionDecoratorFactory = extractionDecoratorFactory;
        this.securityContext = securityContext;
    }

    public void exec(final TaskContext taskContext,
                     final CachedSolrIndex cachedSolrIndex,
                     final Query query,
                     final long now,
                     final DateTimeSettings dateTimeSettings,
                     final Coprocessors coprocessors) {
        this.taskContext = taskContext;
        securityContext.useAsRead(() -> {
            if (!Thread.currentThread().isInterrupted()) {
                taskContext.info(() -> "Initialising...");

                try {
                    // Make sure we have been given a query.
                    if (query.getExpression() == null) {
                        throw new SearchException("Search expression has not been set");
                    }

                    if (coprocessors.size() > 0) {
                        // Start searching.
                        search(taskContext, cachedSolrIndex, query, now, dateTimeSettings, coprocessors);
                    }

                } catch (final RuntimeException e) {
                    try {
                        coprocessors.getErrorConsumer().add(e);
                    } catch (final RuntimeException e2) {
                        // If we failed to send the result or the source node rejected the result because the
                        // source task has been terminated then terminate the task.
                        LOGGER.info(() -> "Terminating search because we were unable to send result");
                        Thread.currentThread().interrupt();
                    }
                } finally {
                    LOGGER.trace(() -> "Search is complete, setting searchComplete to true and " +
                            "counting down searchCompleteLatch");
                    // Tell the client that the search has completed.
                    coprocessors.getCompletionState().signalComplete();
                }
            }
        });
    }

    private void search(final TaskContext taskContext,
                        final CachedSolrIndex cachedSolrIndex,
                        final Query query,
                        final long now,
                        final DateTimeSettings dateTimeSettings,
                        final Coprocessors coprocessors) {
        taskContext.info(() -> "Searching...");
        LOGGER.debug(() -> "Incoming search request:\n" + query.getExpression().toString());

        try {
            final StoredDataQueue storedDataQueue = extractionDecoratorFactory.createStoredDataQueue(
                    coprocessors,
                    query);

            // Search all index shards.
            final ExpressionFilter expressionFilter = ExpressionFilter.builder()
                    .addPrefixExcludeFilter(AnnotationFields.ANNOTATION_FIELD_PREFIX)
                    .build();
            final ExpressionOperator expression = expressionFilter.copy(query.getExpression());
            final CompletableFuture<Void> indexShardSearchFuture = solrSearchFactory.search(cachedSolrIndex,
                    coprocessors.getFieldIndex(),
                    now,
                    expression,
                    storedDataQueue,
                    coprocessors.getErrorConsumer(),
                    taskContext,
                    hitCount,
                    dateTimeSettings);

            // When we complete the index shard search tell teh stored data queue we are complete.
            indexShardSearchFuture.whenCompleteAsync((r, t) -> {
                LOGGER.debug("Complete stored data queue");
                storedDataQueue.complete();
            });

            // Create an object to make event lists from raw index data.
            final StreamMapCreator streamMapCreator = new StreamMapCreator(
                    coprocessors.getFieldIndex());

            // Start mapping streams.
            final CompletableFuture<Void> streamMappingFuture = extractionDecoratorFactory
                    .startMapping(taskContext, streamMapCreator, coprocessors.getErrorConsumer());

            // Start extracting data.
            final CompletableFuture<Void> extractionFuture = extractionDecoratorFactory
                    .startExtraction(taskContext, extractionCount, coprocessors.getErrorConsumer());

            // Create a countdown latch to keep updating status until we complete.
            final CountDownLatch complete = new CountDownLatch(1);

            // Wait for all to complete.
            final CompletableFuture<Void> all = CompletableFuture
                    .allOf(indexShardSearchFuture, streamMappingFuture, extractionFuture);
            all.whenCompleteAsync((r, t) -> complete.countDown());

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
                + hitCount.get() +
                " documents" +
                " performed " +
                extractionCount.get() +
                " extractions");
    }
}
