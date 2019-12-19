package stroom.search.resultsender;

import stroom.query.common.v2.CoprocessorSettingsMap.CoprocessorKey;
import stroom.query.common.v2.Payload;
import stroom.search.coprocessor.CompletionState;
import stroom.search.coprocessor.Coprocessors;
import stroom.task.api.TaskContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

class ResultSenderImpl implements ResultSender {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ResultSenderImpl.class);

    private final Executor executor;
    private final TaskContext taskContext;
    private final CompletionState sendingData = new CompletionState();

    ResultSenderImpl(final Executor executor, final TaskContext taskContext) {
        this.executor = executor;
        this.taskContext = taskContext;
    }

    @Override
    public CompletionState sendData(final Coprocessors coprocessors,
                                    final Consumer<NodeResult> consumer,
                                    final long frequency,
                                    final CompletionState searchComplete,
                                    final LinkedBlockingQueue<String> errors) {
        doSend(coprocessors, consumer, frequency, searchComplete, errors);

        return sendingData;
    }

    private void doSend(final Coprocessors coprocessors,
                        final Consumer<NodeResult> consumer,
                        final long frequency,
                        final CompletionState searchComplete,
                        final LinkedBlockingQueue<String> errors) {
        final long now = System.currentTimeMillis();

        LOGGER.trace(() -> "sendData() called");

        final Supplier<Boolean> supplier = () -> {
            // Find out if searching is complete.
            final boolean complete = searchComplete.isComplete();

            if (!isTerminated()) {
                taskContext.setName("Search Result Sender");
                taskContext.info(() -> "Creating search result");

                // Produce payloads for each coprocessor.
                final Map<CoprocessorKey, Payload> payloadMap = coprocessors.createPayloads();

                // Drain all current errors to a list.
                List<String> errorsSnapshot = new ArrayList<>();
                errors.drainTo(errorsSnapshot);
                if (errorsSnapshot.size() == 0) {
                    errorsSnapshot = null;
                }

                // Only send a result if we have something new to send.
                if (payloadMap != null || errorsSnapshot != null || complete) {
                    // Form a result to send back to the requesting node.
                    final NodeResult result = new NodeResult(payloadMap, errorsSnapshot, complete);

                    // Give the result to the callback.
                    taskContext.info(() -> "Sending search result");
                    consumer.accept(result);
                }
            }

            return complete;
        };

        // Run the sending code asynchronously.
        CompletableFuture.supplyAsync(supplier, executor)
                .thenAccept(complete -> {
                    if (complete) {
                        // We have sent the last data we were expected to so tell the parent cluster search that we have finished sending data.
                        sendingData.complete();
                        LOGGER.debug(() -> "sendingData is false");

                    } else {
                        // If we aren't complete then send more using the supplied sending frequency.
                        final long latestSendTimeMs = now + frequency;

                        while (!isTerminated() &&
                                !searchComplete.isComplete() &&
                                System.currentTimeMillis() < latestSendTimeMs) {
                            //wait until the next send frequency time or drop out as soon
                            //as the search completes and the latch is counted down.
                            //Compute the wait time as we may have used up some of the frequency getting to here
                            long waitTime = latestSendTimeMs - System.currentTimeMillis() + 1;
                            LOGGER.trace(() -> "frequency [" + frequency + "], waitTime [" + waitTime + "]");

                            boolean awaitResult = LOGGER.logDurationIfTraceEnabled(
                                    () -> {
                                        try {
                                            return searchComplete.await(waitTime, TimeUnit.MILLISECONDS);
                                        } catch (InterruptedException e) {
                                            //Don't want to reset interrupt status as this thread will go back into
                                            //the executor's pool. Throwing an exception will terminate the task
                                            throw new RuntimeException("Thread interrupted");
                                        }
                                    },
                                    "sendData wait");
                            LOGGER.trace(() -> "await finished with result " + awaitResult);
                        }

                        // Make sure we don't continue to execute this task if it should have terminated.
                        if (!isTerminated()) {
                            // Try to send more data.
                            doSend(coprocessors, consumer, frequency, searchComplete, errors);
                        } else {
                            sendingData.complete();
                        }
                    }
                })
                .exceptionally(t -> {
                    // If we failed to send the result or the source node rejected the result because the source
                    // task has been terminated then terminate the task.
                    LOGGER.info(() -> "Terminating search because we were unable to send result");
                    sendingData.complete();
                    return null;
                });
    }

    private boolean isTerminated() {
        return Thread.currentThread().isInterrupted();
    }
}
