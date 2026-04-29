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

package stroom.proxy.app.pipeline;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Worker for processing one leased {@link FileGroupQueueItem} at a time.
 * <p>
 * This class centralises the queue processing contract used by queue-consuming
 * stages:
 * </p>
 * <ol>
 *     <li>poll the queue for the next item,</li>
 *     <li>run stage-specific processing,</li>
 *     <li>acknowledge the item when processing succeeds,</li>
 *     <li>fail the item when processing throws, and</li>
 *     <li>close the leased item in all cases.</li>
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

    public String getQueueName() {
        return queue.getName();
    }

    public QueueType getQueueType() {
        return queue.getType();
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
        final Instant startTime = Instant.now();
        counters.incrementPollCount();

        final Optional<FileGroupQueueItem> optionalItem = queue.next();
        if (optionalItem.isEmpty()) {
            counters.incrementEmptyPollCount();
            return FileGroupQueueWorkerResult.noItem(
                    queue.getName(),
                    durationSince(startTime));
        }

        counters.incrementItemReceivedCount();

        final FileGroupQueueItem item = optionalItem.get();
        try {
            return processItem(item, startTime);
        } finally {
            closeItem(item);
        }
    }

    private FileGroupQueueWorkerResult processItem(final FileGroupQueueItem item,
                                                   final Instant startTime) throws IOException {
        final String itemId = safeGetItemId(item);
        final FileGroupQueueMessage message = safeGetMessage(item);

        try {
            LOGGER.debug(() -> LogUtil.message(
                    "Processing queue item {}, queue {}, messageId {}, fileGroupId {}",
                    itemId,
                    queue.getName(),
                    message != null
                            ? message.messageId()
                            : null,
                    message != null
                            ? message.fileGroupId()
                            : null));

            processor.process(item);

        } catch (final IOException | RuntimeException e) {
            counters.incrementProcessorErrorCount();
            failItem(item, itemId, message, e);
            return FileGroupQueueWorkerResult.failed(
                    queue.getName(),
                    itemId,
                    message,
                    e,
                    durationSince(startTime));

        } catch (final Exception e) {
            counters.incrementProcessorErrorCount();
            failItem(item, itemId, message, e);
            return FileGroupQueueWorkerResult.failed(
                    queue.getName(),
                    itemId,
                    message,
                    e,
                    durationSince(startTime));
        }

        counters.incrementItemProcessedCount();

        try {
            item.acknowledge();
            counters.incrementItemAcknowledgedCount();
        } catch (final IOException | RuntimeException e) {
            counters.incrementAcknowledgeErrorCount();
            LOGGER.error(() -> LogUtil.message(
                    "Failed to acknowledge queue item {}, queue {}, messageId {}, fileGroupId {}",
                    itemId,
                    queue.getName(),
                    message != null
                            ? message.messageId()
                            : null,
                    message != null
                            ? message.fileGroupId()
                            : null), e);
            throw e;
        }

        LOGGER.debug(() -> LogUtil.message(
                "Processed and acknowledged queue item {}, queue {}, messageId {}, fileGroupId {}",
                itemId,
                queue.getName(),
                message != null
                        ? message.messageId()
                        : null,
                message != null
                        ? message.fileGroupId()
                        : null));

        return FileGroupQueueWorkerResult.processed(
                queue.getName(),
                itemId,
                message,
                durationSince(startTime));
    }

    private void failItem(final FileGroupQueueItem item,
                          final String itemId,
                          final FileGroupQueueMessage message,
                          final Throwable error) throws IOException {
        try {
            LOGGER.warn(() -> LogUtil.message(
                    "Failing queue item {}, queue {}, messageId {}, fileGroupId {} due to {}",
                    itemId,
                    queue.getName(),
                    message != null
                            ? message.messageId()
                            : null,
                    message != null
                            ? message.fileGroupId()
                            : null,
                    error.getMessage()), error);

            item.fail(error);
            counters.incrementItemFailedCount();

        } catch (final IOException | RuntimeException e) {
            counters.incrementFailErrorCount();
            LOGGER.error(() -> LogUtil.message(
                    "Failed to mark queue item {}, queue {}, messageId {}, fileGroupId {} as failed",
                    itemId,
                    queue.getName(),
                    message != null
                            ? message.messageId()
                            : null,
                    message != null
                            ? message.fileGroupId()
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

    private static Duration durationSince(final Instant startTime) {
        return Duration.between(startTime, Instant.now());
    }
}
