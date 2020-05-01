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

import stroom.annotation.api.AnnotationDataSource;
import stroom.cluster.task.api.ClusterResult;
import stroom.cluster.task.api.ClusterTaskHandler;
import stroom.cluster.task.api.ClusterTaskRef;
import stroom.cluster.task.api.ClusterWorker;
import stroom.pipeline.errorhandler.MessageUtil;
import stroom.query.api.v2.ExpressionOperator;
import stroom.search.coprocessor.CompletionState;
import stroom.search.coprocessor.Coprocessors;
import stroom.search.coprocessor.CoprocessorsFactory;
import stroom.search.coprocessor.Error;
import stroom.search.coprocessor.NewCoprocessor;
import stroom.search.coprocessor.Receiver;
import stroom.search.coprocessor.ReceiverImpl;
import stroom.search.extraction.ExpressionFilter;
import stroom.search.extraction.ExtractionDecoratorFactory;
import stroom.search.impl.shard.IndexShardSearchFactory;
import stroom.search.resultsender.NodeResult;
import stroom.search.resultsender.ResultSender;
import stroom.search.resultsender.ResultSenderFactory;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskTerminatedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

class ClusterSearchTaskHandler implements ClusterTaskHandler<ClusterSearchTask, NodeResult>, Consumer<Error> {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ClusterSearchTaskHandler.class);

    private final ClusterWorker clusterWorker;
    private final CoprocessorsFactory coprocessorsFactory;
    private final IndexShardSearchFactory indexShardSearchFactory;
    private final ExtractionDecoratorFactory extractionDecoratorFactory;
    private final ResultSenderFactory resultSenderFactory;
    private final SecurityContext securityContext;
    private final LinkedBlockingQueue<String> errors = new LinkedBlockingQueue<>();
    private final CompletionState searchCompletionState = new CompletionState();

    private ClusterSearchTask task;

    @Inject
    ClusterSearchTaskHandler(final ClusterWorker clusterWorker,
                             final CoprocessorsFactory coprocessorsFactory,
                             final IndexShardSearchFactory indexShardSearchFactory,
                             final ExtractionDecoratorFactory extractionDecoratorFactory,
                             final ResultSenderFactory resultSenderFactory,
                             final SecurityContext securityContext) {
        this.clusterWorker = clusterWorker;
        this.coprocessorsFactory = coprocessorsFactory;
        this.indexShardSearchFactory = indexShardSearchFactory;
        this.extractionDecoratorFactory = extractionDecoratorFactory;
        this.resultSenderFactory = resultSenderFactory;
        this.securityContext = securityContext;
    }

    @Override
    public void exec(final TaskContext taskContext, final ClusterSearchTask task, final ClusterTaskRef<NodeResult> clusterTaskRef) {
        securityContext.useAsRead(() -> {
            final Consumer<NodeResult> resultConsumer = result ->
                    clusterWorker.sendResult(ClusterResult.success(clusterTaskRef, result));

            CompletionState sendingDataCompletionState = new CompletionState();
            sendingDataCompletionState.complete();

            if (!Thread.currentThread().isInterrupted()) {
                taskContext.info(() -> "Initialising...");

                this.task = task;
                final stroom.query.api.v2.Query query = task.getQuery();

                try {
                    final long frequency = task.getResultSendFrequency();

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
                    final Coprocessors coprocessors = coprocessorsFactory.create(task.getCoprocessorMap(), storedFields, query.getParams(), this);

                    if (coprocessors.size() > 0) {
                        // Start forwarding data to target node.
                        final ResultSender resultSender = resultSenderFactory.create(taskContext);
                        sendingDataCompletionState = resultSender.sendData(coprocessors, resultConsumer, frequency, searchCompletionState, errors);

                        // Start searching.
                        search(taskContext, task, query, coprocessors);
                    }
                } catch (final RuntimeException e) {
                    try {
                        // Send failure.
                        clusterWorker.sendResult(ClusterResult.failure(clusterTaskRef, e));

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

                // Now we must wait for results to be sent to the requesting node.
                try {
                    taskContext.info(() -> "Sending final results");
                    while (!Thread.currentThread().isInterrupted() && !sendingDataCompletionState.isComplete()) {
                        sendingDataCompletionState.await(1, TimeUnit.SECONDS);
                    }
                } catch (final InterruptedException e) {
                    //Don't want to reset interrupt status as this thread will go back into
                    //the executor's pool. Throwing an exception will terminate the task
                    throw new RuntimeException("Thread interrupted");
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
                final AtomicLong allDocumentCount = new AtomicLong();
                final Receiver rootReceiver = new ReceiverImpl(null, this, allDocumentCount::addAndGet, null);
                final Receiver extractionReceiver = extractionDecoratorFactory.create(taskContext, rootReceiver, task.getStoredFields(), coprocessors, query);

                // Search all index shards.
                final ExpressionFilter expressionFilter = new ExpressionFilter.Builder()
                        .addPrefixExcludeFilter(AnnotationDataSource.ANNOTATION_FIELD_PREFIX)
                        .build();
                final ExpressionOperator expression = expressionFilter.copy(task.getQuery().getExpression());
                indexShardSearchFactory.search(taskContext, task, expression, extractionReceiver);

                // Wait for index search completion.
                long extractionCount = getMinExtractions(coprocessors.getSet());
                long documentCount = allDocumentCount.get();
                while (!Thread.currentThread().isInterrupted() && extractionCount < documentCount) {
                    log(taskContext, documentCount, extractionCount);

                    Thread.sleep(1000);

                    extractionCount = getMinExtractions(coprocessors.getSet());
                    documentCount = allDocumentCount.get();
                }

                LOGGER.debug(() -> "Complete");
                Thread.currentThread().interrupt();
            }
        } catch (final RuntimeException pEx) {
            throw SearchException.wrap(pEx);
        } catch (final InterruptedException pEx) {
            // Continue to interrupt.
            Thread.currentThread().interrupt();
            throw SearchException.wrap(pEx);
        }
    }

    private void log(final TaskContext taskContext, final long documentCount, final long extractionCount) {
        taskContext.info(() ->
                "Searching... " +
                        "found " + documentCount + " documents" +
                        " performed " + extractionCount + " extractions");
    }

    private long getMinExtractions(final Set<NewCoprocessor> coprocessorConsumers) {
        return coprocessorConsumers.stream().mapToLong(NewCoprocessor::getCompletionCount).min().orElse(0);
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
