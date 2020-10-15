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

package stroom.search.impl;

import stroom.annotation.api.AnnotationFields;
import stroom.pipeline.errorhandler.MessageUtil;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.common.v2.CompletionState;
import stroom.search.coprocessor.Coprocessors;
import stroom.search.coprocessor.CoprocessorsFactory;
import stroom.search.coprocessor.Error;
import stroom.search.coprocessor.NewCoprocessor;
import stroom.search.coprocessor.Receiver;
import stroom.search.extraction.ExpressionFilter;
import stroom.search.extraction.ExtractionDecoratorFactory;
import stroom.search.impl.shard.IndexShardSearchFactory;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskTerminatedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

class ClusterSearchTaskHandler implements Consumer<Error> {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ClusterSearchTaskHandler.class);

    private final CoprocessorsFactory coprocessorsFactory;
    private final IndexShardSearchFactory indexShardSearchFactory;
    private final ExtractionDecoratorFactory extractionDecoratorFactory;
    private final SecurityContext securityContext;
    private final LinkedBlockingQueue<String> errors = new LinkedBlockingQueue<>();
    private ClusterSearchTask task;

    @Inject
    ClusterSearchTaskHandler(final CoprocessorsFactory coprocessorsFactory,
                             final IndexShardSearchFactory indexShardSearchFactory,
                             final ExtractionDecoratorFactory extractionDecoratorFactory,
                             final SecurityContext securityContext) {
        this.coprocessorsFactory = coprocessorsFactory;
        this.indexShardSearchFactory = indexShardSearchFactory;
        this.extractionDecoratorFactory = extractionDecoratorFactory;
        this.securityContext = securityContext;
    }

    public void exec(final TaskContext taskContext, final ClusterSearchTask task, final RemoteSearchResultFactory remoteSearchResultFactory) {
        securityContext.useAsRead(() -> {
            CompletionState sendingDataCompletionState = new CompletionState();
            sendingDataCompletionState.complete();

            if (!Thread.currentThread().isInterrupted()) {
                taskContext.info(() -> "Initialising...");

                this.task = task;
                final stroom.query.api.v2.Query query = task.getQuery();

                try {
                    // Make sure we have been given a query.
                    if (query.getExpression() == null) {
                        throw new SearchException("Search expression has not been set");
                    }

                    // Get the stored fields that search is hoping to use.
                    final String[] storedFields = task.getStoredFields();
                    if (storedFields == null || storedFields.length == 0) {
                        throw new SearchException("No stored fields have been requested");
                    }

                    // Create coprocessors.
                    final Coprocessors coprocessors = coprocessorsFactory.create(
                            task.getCoprocessorMap(),
                            storedFields,
                            query.getParams(),
                            this);
                    // Start forwarding data to target node.
                    remoteSearchResultFactory.setCoprocessors(coprocessors);
                    remoteSearchResultFactory.setErrors(errors);
                    remoteSearchResultFactory.setTaskId(taskContext.getTaskId());
                    remoteSearchResultFactory.setStarted(true);

                    if (coprocessors.size() > 0) {
                        // Start searching.
                        search(taskContext, task, query, coprocessors);
                    }
                } catch (final RuntimeException e) {
                    errors.add(e.getMessage());
                } finally {
                    LOGGER.trace(() -> "Search is complete, setting searchComplete to true and " +
                            "counting down searchCompleteLatch");
                    // Tell the client that the search has completed.
                    remoteSearchResultFactory.complete();
                }
            }
        });
    }

    private void search(final TaskContext taskContext,
                        final ClusterSearchTask task,
                        final stroom.query.api.v2.Query query,
                        final Coprocessors coprocessors) {
        taskContext.info(() -> "Searching...");
        LOGGER.debug(() -> "Incoming search request:\n" + query.getExpression().toString());

        try {
            if (task.getShards().size() > 0) {
                final Receiver extractionReceiver = extractionDecoratorFactory.create(
                        taskContext,
                        this,
                        task.getStoredFields(),
                        coprocessors,
                        query);

                // Search all index shards.
                final ExpressionFilter expressionFilter = new ExpressionFilter.Builder()
                        .addPrefixExcludeFilter(AnnotationFields.ANNOTATION_FIELD_PREFIX)
                        .build();
                final ExpressionOperator expression = expressionFilter.copy(task.getQuery().getExpression());
                final AtomicLong hitCount = new AtomicLong();
                indexShardSearchFactory.search(task, expression, extractionReceiver, taskContext, hitCount);

                // Wait for search completion.
                boolean allComplete = false;
                while (!allComplete) {
                    allComplete = true;
                    for (final NewCoprocessor coprocessor : coprocessors.getSet()) {
                        if (!Thread.currentThread().isInterrupted()) {
                            taskContext.info(() -> "" +
                                    "Searching... " +
                                    "found "
                                    + hitCount.get() +
                                    " documents" +
                                    " performed " +
                                    coprocessor.getValuesCount().get() +
                                    " extractions");

                            final boolean complete = coprocessor.awaitCompletion(1, TimeUnit.SECONDS);
                            if (!complete) {
                                allComplete = false;
                            }
                        }
                    }
                }
            }

            LOGGER.debug(() -> "Complete");
        } catch (final RuntimeException pEx) {
            throw SearchException.wrap(pEx);
        } catch (final InterruptedException pEx) {
            // Continue to interrupt.
            Thread.currentThread().interrupt();
            throw SearchException.wrap(pEx);
        }
    }

    @Override
    public void accept(final Error error) {
        if (error != null) {
            LOGGER.debug(error::getMessage, error.getThrowable());
            if (!(error.getThrowable() instanceof TaskTerminatedException)) {
                final String msg = MessageUtil.getMessage(error.getMessage(), error.getThrowable());
                errors.offer(msg);
            }
        }
    }

    public ClusterSearchTask getTask() {
        return task;
    }
}
