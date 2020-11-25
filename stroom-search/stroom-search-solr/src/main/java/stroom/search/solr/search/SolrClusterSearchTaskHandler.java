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
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.Query;
import stroom.query.common.v2.CompletionState;
import stroom.query.common.v2.Coprocessor;
import stroom.query.common.v2.Coprocessors;
import stroom.query.common.v2.Receiver;
import stroom.search.extraction.ExpressionFilter;
import stroom.search.extraction.ExtractionDecoratorFactory;
import stroom.search.solr.CachedSolrIndex;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

class SolrClusterSearchTaskHandler {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SolrClusterSearchTaskHandler.class);

    private final SolrSearchFactory solrSearchFactory;
    private final ExtractionDecoratorFactory extractionDecoratorFactory;
    private final SecurityContext securityContext;
    private final CompletionState searchCompletionState = new CompletionState();

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
                     final String[] storedFields,
                     final long now,
                     final String dateTimeLocale,
                     final Coprocessors coprocessors) {
        securityContext.useAsRead(() -> {
            if (!Thread.currentThread().isInterrupted()) {
                taskContext.info(() -> "Initialising...");

                try {
                    // Make sure we have been given a query.
                    if (query.getExpression() == null) {
                        throw new SearchException("Search expression has not been set");
                    }

                    // Get the stored fields that search is hoping to use.
                    if (storedFields == null || storedFields.length == 0) {
                        throw new SearchException("No stored fields have been requested");
                    }

                    if (coprocessors.size() > 0) {
                        // Start searching.
                        search(taskContext, cachedSolrIndex, query, storedFields, now, dateTimeLocale, coprocessors);
                    }
                } catch (final RuntimeException e) {
                    try {
                        coprocessors.getErrorConsumer().accept(e);
                    } catch (final RuntimeException e2) {
                        // If we failed to send the result or the source node rejected the result because the source task has been terminated then terminate the task.
                        LOGGER.info(() -> "Terminating search because we were unable to send result");
                        Thread.currentThread().interrupt();
                    }
                } finally {
                    LOGGER.trace(() -> "Search is complete, setting searchComplete to true and " +
                            "counting down searchCompleteLatch");
                    // Tell the client that the search has completed.
                    searchCompletionState.complete();
                }
            }
        });
    }

    private void search(final TaskContext taskContext,
                        final CachedSolrIndex cachedSolrIndex,
                        final Query query,
                        final String[] storedFields,
                        final long now,
                        final String dateTimeLocale,
                        final Coprocessors coprocessors) {
        taskContext.info(() -> "Searching...");
        LOGGER.debug(() -> "Incoming search request:\n" + query.getExpression().toString());

        try {
            final Receiver extractionReceiver = extractionDecoratorFactory.create(taskContext, storedFields, coprocessors, query);

            // Search all index shards.
            final ExpressionFilter expressionFilter = new ExpressionFilter.Builder()
                    .addPrefixExcludeFilter(AnnotationFields.ANNOTATION_FIELD_PREFIX)
                    .build();
            final ExpressionOperator expression = expressionFilter.copy(query.getExpression());
            final AtomicLong hitCount = new AtomicLong();
            solrSearchFactory.search(cachedSolrIndex, storedFields, now, expression, extractionReceiver, taskContext, hitCount, dateTimeLocale);

            // Wait for search completion.
            boolean allComplete = false;
            while (!allComplete) {
                allComplete = true;
                for (final Coprocessor coprocessor : coprocessors) {
                    if (!Thread.currentThread().isInterrupted()) {
                        taskContext.info(() -> "" +
                                "Searching... " +
                                "found " +
                                hitCount.get() +
                                " documents" +
                                " performed "
                                + coprocessor.getValuesCount().get() +
                                " extractions");

                        final boolean complete = coprocessor.awaitCompletion(1, TimeUnit.SECONDS);
                        if (!complete) {
                            allComplete = false;
                        }
                    }
                }
            }

            LOGGER.debug(() -> "Complete");
        } catch (final InterruptedException e) {
            // Continue to interrupt.
            Thread.currentThread().interrupt();
            LOGGER.debug(e::getMessage, e);
        } catch (final RuntimeException e) {
            throw SearchException.wrap(e);
        }
    }
}
