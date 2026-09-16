/*
 * Copyright 2026 Crown Copyright
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

package stroom.proxy.app.pipeline.stage;

import stroom.proxy.app.pipeline.queue.FileGroupQueue;
import stroom.proxy.app.pipeline.queue.FileGroupQueueItem;
import stroom.proxy.app.pipeline.queue.FileGroupQueueItemProcessor;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessage;
import stroom.proxy.app.pipeline.store.FileGroupNotFoundException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import org.slf4j.MDC;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Worker for processing one claimed {@link FileGroupQueueItem} at a time
 * ({@code designs/infrastructure/queues.md} §5.4).
 * <p>
 * This class centralises the queue processing contract used by queue-consuming
 * stages:
 * </p>
 * <ol>
 *     <li>poll the queue for the next item,</li>
 *     <li>run stage-specific processing,</li>
 *     <li>acknowledge the item when processing succeeds, or when its input has already gone (R12),</li>
 *     <li>fail the item when processing throws, and</li>
 *     <li>close the item in all cases, which releases a claim that was neither acknowledged nor failed.</li>
 * </ol>
 * <p>
 * Stage-specific code should normally implement {@link FileGroupQueueItemProcessor}
 * and should not call {@link FileGroupQueueItem#acknowledge()} or
 * {@link FileGroupQueueItem#fail(Throwable)} directly. Keeping ack/fail behaviour
 * here gives all stages the same at-least-once processing semantics.
 * </p>
 */
public class FileGroupQueueWorker {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FileGroupQueueWorker.class);

    /**
     * How long a consumer waits for work before reporting an empty poll. Only a backstop - a
     * publish wakes the waiter immediately - so it governs idle wake-ups, not latency.
     */
    private static final Duration POLL_WAIT = Duration.ofSeconds(1);

    private final FileGroupQueue queue;
    private final FileGroupQueueItemProcessor processor;
    private final FileGroupQueueWorkerCounters counters;

    public FileGroupQueueWorker(final FileGroupQueue queue,
                                final FileGroupQueueItemProcessor processor) {
        this(queue, processor, new FileGroupQueueWorkerCounters());
    }

    public FileGroupQueueWorker(final FileGroupQueue queue,
                                final FileGroupQueueItemProcessor processor,
                                final FileGroupQueueWorkerCounters counters) {
        this.queue = Objects.requireNonNull(queue, "queue");
        this.processor = Objects.requireNonNull(processor, "processor");
        this.counters = Objects.requireNonNull(counters, "counters");
    }

    public FileGroupQueueWorkerCounters getCounters() {
        return counters;
    }

    /**
     * Poll and process a single queue item if one is available.
     *
     * @return The outcome of this processing attempt.
     * @throws IOException If the queue cannot be polled, an item cannot be
     * acknowledged after successful processing, or a failed item cannot be
     * returned to the queue/failure mechanism.
     */
    public FileGroupQueueWorkerResult processNext() throws IOException {
        // nanoTime, not the wall clock: this is an interval, and a clock stepped backwards during a
        // poll must not turn a processed and acknowledged item into an exception from the result's
        // constructor, which refuses a negative duration.
        final long startNanos = System.nanoTime();
        counters.incrementPollCount();

        // Wait for work rather than returning empty and letting the runner sleep on a timer.
        final Optional<FileGroupQueueItem> optionalItem = queue.next(POLL_WAIT);
        if (optionalItem.isEmpty()) {
            counters.incrementEmptyPollCount();
            return FileGroupQueueWorkerResult.noItem(
                    queue.getName(),
                    durationSince(startNanos));
        }

        counters.incrementItemReceivedCount();

        final FileGroupQueueItem item = optionalItem.get();
        try {
            return processItem(item, startNanos);
        } finally {
            closeItem(item);
        }
    }

    private FileGroupQueueWorkerResult processItem(final FileGroupQueueItem item,
                                                   final long startNanos) throws IOException {
        final String itemId = safeGetItemId(item);
        final FileGroupQueueMessage message = safeGetMessage(item);

        try {
            LOGGER.debug(() -> LogUtil.message(
                    "Processing queue item {}, queue {}, messageId {}",
                    itemId,
                    queue.getName(),
                    message != null
                            ? message.messageId()
                            : null));

            // Set MDC context for structured logging — allows log correlation.
            if (message != null) {
                if (message.traceId() != null) {
                    MDC.put("traceId", message.traceId());
                }
                MDC.put("messageId", message.messageId());
            }
            MDC.put("stageName", queue.getName());

            processor.process(item);

        } catch (final FileGroupNotFoundException e) {
            // R12 (contracts.md §2.6): absence of data means the work was done, so this is acknowledged
            // rather than failed - unconditionally, and without consulting the delivery-attempt count.
            // §2.1's ordering is what makes that sound: the output is made durable and its onward
            // message published before the input is deleted, and the acknowledgement comes last, so
            // the input being gone is itself proof that everything before it happened. In shared mode
            // this needs no crash: a slow acknowledgement, a visibility lapse or a rebalance is enough
            // for a redelivery to arrive after another node consumed the input. Failing it would
            // quarantine finished work on a local queue and, on Kafka, never commit past it.
            counters.incrementItemProcessedCount();
            LOGGER.info(() -> LogUtil.message(
                    "Queue item {} on queue {} (messageId {}) resolves to no data, so "
                    + "its work was already done - acknowledging it. {}",
                    itemId,
                    queue.getName(),
                    message != null ? message.messageId() : null,
                    e.getMessage()));
            return acknowledgeItem(item, itemId, message, startNanos);

        } catch (final IOException | RuntimeException e) {
            counters.incrementProcessorErrorCount();
            failItem(item, itemId, message, e);
            return FileGroupQueueWorkerResult.failed(
                    queue.getName(),
                    itemId,
                    message,
                    e,
                    durationSince(startNanos));

        } catch (final Exception e) {
            counters.incrementProcessorErrorCount();
            failItem(item, itemId, message, e);
            return FileGroupQueueWorkerResult.failed(
                    queue.getName(),
                    itemId,
                    message,
                    e,
                    durationSince(startNanos));
        } finally {
            MDC.remove("traceId");
            MDC.remove("messageId");
            MDC.remove("stageName");
        }

        counters.incrementItemProcessedCount();
        return acknowledgeItem(item, itemId, message, startNanos);
    }

    /**
     * Acknowledge a completed item. Shared by the normal path and by R12's absence path, which
     * reaches the same conclusion by a different route: either the processor did the work, or the
     * work was already done and the absent input is the proof of it.
     */
    private FileGroupQueueWorkerResult acknowledgeItem(final FileGroupQueueItem item,
                                                       final String itemId,
                                                       final FileGroupQueueMessage message,
                                                       final long startNanos) throws IOException {
        try {
            item.acknowledge();
            counters.incrementItemAcknowledgedCount();
        } catch (final IOException | RuntimeException e) {
            counters.incrementAcknowledgeErrorCount();
            LOGGER.error(() -> LogUtil.message(
                    "Failed to acknowledge queue item {}, queue {}, messageId {}",
                    itemId,
                    queue.getName(),
                    message != null
                            ? message.messageId()
                            : null), e);
            throw e;
        }

        LOGGER.debug(() -> LogUtil.message(
                "Processed and acknowledged queue item {}, queue {}, messageId {}",
                itemId,
                queue.getName(),
                message != null
                        ? message.messageId()
                        : null));

        return FileGroupQueueWorkerResult.processed(
                queue.getName(),
                itemId,
                message,
                durationSince(startNanos));
    }

    private void failItem(final FileGroupQueueItem item,
                          final String itemId,
                          final FileGroupQueueMessage message,
                          final Throwable error) throws IOException {
        try {
            LOGGER.warn(() -> LogUtil.message(
                    "Failing queue item {}, queue {}, messageId {} due to {}",
                    itemId,
                    queue.getName(),
                    message != null
                            ? message.messageId()
                            : null,
                    error.getMessage()), error);

            item.fail(error);
            counters.incrementItemFailedCount();

        } catch (final IOException | RuntimeException e) {
            counters.incrementFailErrorCount();
            LOGGER.error(() -> LogUtil.message(
                    "Failed to mark queue item {}, queue {}, messageId {} as failed",
                    itemId,
                    queue.getName(),
                    message != null
                            ? message.messageId()
                            : null), e);
            throw e;
        }
    }

    private void closeItem(final FileGroupQueueItem item) throws IOException {
        try {
            item.close();
        } catch (final IOException | RuntimeException e) {
            counters.incrementCloseErrorCount();
            LOGGER.error(() -> LogUtil.message(
                    "Failed to close queue item {}, queue {}",
                    safeGetItemId(item),
                    queue.getName()), e);
            throw e;
        }
    }

    private static String safeGetItemId(final FileGroupQueueItem item) {
        try {
            return item.getId();
        } catch (final RuntimeException e) {
            return null;
        }
    }

    private static FileGroupQueueMessage safeGetMessage(final FileGroupQueueItem item) {
        try {
            return item.getMessage();
        } catch (final RuntimeException e) {
            return null;
        }
    }

    private static Duration durationSince(final long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos);
    }
}
