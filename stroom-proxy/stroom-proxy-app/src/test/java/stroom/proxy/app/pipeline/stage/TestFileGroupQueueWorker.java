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
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessage;
import stroom.proxy.app.pipeline.queue.QueueType;
import stroom.proxy.app.pipeline.store.FileGroupNotFoundException;
import stroom.proxy.app.pipeline.store.FileStoreLocation;
import stroom.test.common.util.test.StroomUnitTest;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestFileGroupQueueWorker extends StroomUnitTest {

    @Test
    void testProcessNextWhenNoItemAvailable() throws IOException {
        final FakeQueue queue = new FakeQueue("preAggregateInput", null);
        final FileGroupQueueWorkerCounters counters = new FileGroupQueueWorkerCounters();
        final FileGroupQueueWorker worker = new FileGroupQueueWorker(
                queue,
                item -> {
                    throw new AssertionError("Processor should not be called when no item is available");
                },
                counters);

        final FileGroupQueueWorkerResult result = worker.processNext();

        assertThat(result.isNoItem()).isTrue();
        assertThat(result.hasItem()).isFalse();
        assertThat(result.itemId()).isNull();
        assertThat(result.message()).isNull();
        assertThat(result.error()).isNull();

        final FileGroupQueueWorkerCounters.Snapshot snapshot = counters.snapshot();
        assertThat(snapshot.pollCount()).isEqualTo(1);
        assertThat(snapshot.emptyPollCount()).isEqualTo(1);
        assertThat(snapshot.itemReceivedCount()).isZero();
        assertThat(snapshot.itemProcessedCount()).isZero();
        assertThat(snapshot.itemAcknowledgedCount()).isZero();
        assertThat(snapshot.itemFailedCount()).isZero();
        assertThat(snapshot.hasErrors()).isFalse();
    }

    @Test
    void testProcessNextProcessesAndAcknowledgesItem() throws IOException {
        final FileGroupQueueMessage message = createMessage("preAggregateInput", "file-group-1");
        final FakeItem item = new FakeItem("item-1", message);
        final FakeQueue queue = new FakeQueue("preAggregateInput", item);
        final FileGroupQueueWorkerCounters counters = new FileGroupQueueWorkerCounters();
        final boolean[] processorCalled = new boolean[1];

        final FileGroupQueueWorker worker = new FileGroupQueueWorker(
                queue,
                queueItem -> {
                    processorCalled[0] = true;
                    assertThat(queueItem).isSameAs(item);
                    assertThat(queueItem.getMessage()).isEqualTo(message);
                },
                counters);

        final FileGroupQueueWorkerResult result = worker.processNext();

        assertThat(processorCalled[0]).isTrue();
        assertThat(result.isProcessed()).isTrue();
        assertThat(result.hasItem()).isTrue();
        assertThat(result.itemId()).isEqualTo("item-1");
        assertThat(result.message()).isEqualTo(message);
        assertThat(result.error()).isNull();

        assertThat(item.acknowledged).isTrue();
        assertThat(item.failed).isFalse();
        assertThat(item.closed).isTrue();
        assertThat(item.failure).isNull();

        final FileGroupQueueWorkerCounters.Snapshot snapshot = counters.snapshot();
        assertThat(snapshot.pollCount()).isEqualTo(1);
        assertThat(snapshot.emptyPollCount()).isZero();
        assertThat(snapshot.itemReceivedCount()).isEqualTo(1);
        assertThat(snapshot.itemProcessedCount()).isEqualTo(1);
        assertThat(snapshot.itemAcknowledgedCount()).isEqualTo(1);
        assertThat(snapshot.itemFailedCount()).isZero();
        assertThat(snapshot.processorErrorCount()).isZero();
        assertThat(snapshot.acknowledgeErrorCount()).isZero();
        assertThat(snapshot.failErrorCount()).isZero();
        assertThat(snapshot.closeErrorCount()).isZero();
        assertThat(snapshot.hasErrors()).isFalse();
    }

    /**
     * R12 (contracts.md §2.6). A queue item that resolves to nothing is <strong>acknowledged,
     * not failed</strong> - unconditionally, and without consulting the delivery-attempt count.
     * §2.1's ordering is what makes that sound: output is durable and its onward message published
     * before the input is deleted, so an absent input is proof the work completed.
     * <p>
     * Failing it was the defect, and it was worst on the backends that need it most: local
     * quarantined work that had actually completed, SQS redelivered forever, and Kafka never
     * committed the offset so <em>the partition stopped</em>. Under R1a none of that needs a crash -
     * a slow ack or a GC pause is enough.
     * </p>
     */
    @Test
    void testAnItemWhoseInputIsAlreadyGoneIsAcknowledgedRatherThanFailed() throws IOException {
        final FileGroupQueueMessage message = createMessage("preAggregateInput", "file-group-1");
        final FakeItem item = new FakeItem("item-1", message);
        final FakeQueue queue = new FakeQueue("preAggregateInput", item);
        final FileGroupQueueWorkerCounters counters = new FileGroupQueueWorkerCounters();

        final FileGroupQueueWorker worker = new FileGroupQueueWorker(
                queue,
                queueItem -> {
                    throw new FileGroupNotFoundException(
                            message.fileStoreLocation(),
                            "No file group at '/store/0000000001'. It was never written, or it has "
                            + "already been consumed and deleted.");
                },
                counters);

        final FileGroupQueueWorkerResult result = worker.processNext();

        assertThat(result.isFailed())
                .as("absence is not a failure - the work was done")
                .isFalse();
        assertThat(item.acknowledged).isTrue();
        assertThat(item.failed)
                .as("failing it is what quarantined completed work and stalled Kafka partitions")
                .isFalse();
        assertThat(item.closed).isTrue();

        final FileGroupQueueWorkerCounters.Snapshot snapshot = counters.snapshot();
        assertThat(snapshot.itemAcknowledgedCount()).isEqualTo(1);
        assertThat(snapshot.itemFailedCount()).isZero();
        assertThat(snapshot.processorErrorCount())
                .as("an absent input is an expected state under R1a, not a processor error")
                .isZero();
    }

    @Test
    void testProcessNextFailsItemWhenProcessorThrows() throws IOException {
        final FileGroupQueueMessage message = createMessage("preAggregateInput", "file-group-1");
        final FakeItem item = new FakeItem("item-1", message);
        final FakeQueue queue = new FakeQueue("preAggregateInput", item);
        final FileGroupQueueWorkerCounters counters = new FileGroupQueueWorkerCounters();
        final RuntimeException processorError = new RuntimeException("Deliberate processor failure");

        final FileGroupQueueWorker worker = new FileGroupQueueWorker(
                queue,
                queueItem -> {
                    throw processorError;
                },
                counters);

        final FileGroupQueueWorkerResult result = worker.processNext();

        assertThat(result.isFailed()).isTrue();
        assertThat(result.hasItem()).isTrue();
        assertThat(result.itemId()).isEqualTo("item-1");
        assertThat(result.message()).isEqualTo(message);
        assertThat(result.error()).isSameAs(processorError);

        assertThat(item.acknowledged).isFalse();
        assertThat(item.failed).isTrue();
        assertThat(item.closed).isTrue();
        assertThat(item.failure).isSameAs(processorError);

        final FileGroupQueueWorkerCounters.Snapshot snapshot = counters.snapshot();
        assertThat(snapshot.pollCount()).isEqualTo(1);
        assertThat(snapshot.emptyPollCount()).isZero();
        assertThat(snapshot.itemReceivedCount()).isEqualTo(1);
        assertThat(snapshot.itemProcessedCount()).isZero();
        assertThat(snapshot.itemAcknowledgedCount()).isZero();
        assertThat(snapshot.itemFailedCount()).isEqualTo(1);
        assertThat(snapshot.processorErrorCount()).isEqualTo(1);
        assertThat(snapshot.acknowledgeErrorCount()).isZero();
        assertThat(snapshot.failErrorCount()).isZero();
        assertThat(snapshot.closeErrorCount()).isZero();
        assertThat(snapshot.hasErrors()).isTrue();
    }

    @Test
    void testProcessNextFailsItemWhenProcessorThrowsCheckedException() throws IOException {
        final FileGroupQueueMessage message = createMessage("preAggregateInput", "file-group-1");
        final FakeItem item = new FakeItem("item-1", message);
        final FakeQueue queue = new FakeQueue("preAggregateInput", item);
        final FileGroupQueueWorkerCounters counters = new FileGroupQueueWorkerCounters();
        final Exception processorError = new Exception("Deliberate checked processor failure");

        final FileGroupQueueWorker worker = new FileGroupQueueWorker(
                queue,
                queueItem -> {
                    throw processorError;
                },
                counters);

        final FileGroupQueueWorkerResult result = worker.processNext();

        assertThat(result.isFailed()).isTrue();
        assertThat(result.error()).isSameAs(processorError);
        assertThat(item.failed).isTrue();
        assertThat(item.failure).isSameAs(processorError);
        assertThat(item.acknowledged).isFalse();
        assertThat(item.closed).isTrue();

        final FileGroupQueueWorkerCounters.Snapshot snapshot = counters.snapshot();
        assertThat(snapshot.processorErrorCount()).isEqualTo(1);
        assertThat(snapshot.itemFailedCount()).isEqualTo(1);
        assertThat(snapshot.itemAcknowledgedCount()).isZero();
    }

    @Test
    void testProcessNextDoesNotFailItemWhenAcknowledgeThrows() {
        final FileGroupQueueMessage message = createMessage("preAggregateInput", "file-group-1");
        final FakeItem item = new FakeItem("item-1", message);
        item.acknowledgeException = new IOException("Deliberate ack failure");

        final FakeQueue queue = new FakeQueue("preAggregateInput", item);
        final FileGroupQueueWorkerCounters counters = new FileGroupQueueWorkerCounters();
        final FileGroupQueueWorker worker = new FileGroupQueueWorker(queue, queueItem -> {
            // Successful processing. Ack should be attempted afterwards by the worker.
        }, counters);

        assertThatThrownBy(worker::processNext)
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Deliberate ack failure");

        assertThat(item.acknowledged).isFalse();
        assertThat(item.failed).isFalse();
        assertThat(item.closed).isTrue();

        final FileGroupQueueWorkerCounters.Snapshot snapshot = counters.snapshot();
        assertThat(snapshot.pollCount()).isEqualTo(1);
        assertThat(snapshot.itemReceivedCount()).isEqualTo(1);
        assertThat(snapshot.itemProcessedCount()).isEqualTo(1);
        assertThat(snapshot.itemAcknowledgedCount()).isZero();
        assertThat(snapshot.itemFailedCount()).isZero();
        assertThat(snapshot.processorErrorCount()).isZero();
        assertThat(snapshot.acknowledgeErrorCount()).isEqualTo(1);
        assertThat(snapshot.failErrorCount()).isZero();
        assertThat(snapshot.closeErrorCount()).isZero();
        assertThat(snapshot.hasErrors()).isTrue();
    }

    @Test
    void testProcessNextThrowsWhenFailThrows() {
        final FileGroupQueueMessage message = createMessage("preAggregateInput", "file-group-1");
        final FakeItem item = new FakeItem("item-1", message);
        item.failException = new IOException("Deliberate fail failure");

        final FakeQueue queue = new FakeQueue("preAggregateInput", item);
        final FileGroupQueueWorkerCounters counters = new FileGroupQueueWorkerCounters();
        final RuntimeException processorError = new RuntimeException("Deliberate processor failure");
        final FileGroupQueueWorker worker = new FileGroupQueueWorker(
                queue,
                queueItem -> {
                    throw processorError;
                },
                counters);

        assertThatThrownBy(worker::processNext)
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Deliberate fail failure");

        assertThat(item.acknowledged).isFalse();
        assertThat(item.failed).isFalse();
        assertThat(item.failure).isSameAs(processorError);
        assertThat(item.closed).isTrue();

        final FileGroupQueueWorkerCounters.Snapshot snapshot = counters.snapshot();
        assertThat(snapshot.pollCount()).isEqualTo(1);
        assertThat(snapshot.itemReceivedCount()).isEqualTo(1);
        assertThat(snapshot.itemProcessedCount()).isZero();
        assertThat(snapshot.itemAcknowledgedCount()).isZero();
        assertThat(snapshot.itemFailedCount()).isZero();
        assertThat(snapshot.processorErrorCount()).isEqualTo(1);
        assertThat(snapshot.failErrorCount()).isEqualTo(1);
        assertThat(snapshot.closeErrorCount()).isZero();
        assertThat(snapshot.hasErrors()).isTrue();
    }

    @Test
    void testProcessNextThrowsWhenCloseThrowsAfterSuccessfulAck() {
        final FileGroupQueueMessage message = createMessage("preAggregateInput", "file-group-1");
        final FakeItem item = new FakeItem("item-1", message);
        item.closeException = new IOException("Deliberate close failure");

        final FakeQueue queue = new FakeQueue("preAggregateInput", item);
        final FileGroupQueueWorkerCounters counters = new FileGroupQueueWorkerCounters();
        final FileGroupQueueWorker worker = new FileGroupQueueWorker(queue, queueItem -> {
            // Successful processing.
        }, counters);

        assertThatThrownBy(worker::processNext)
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Deliberate close failure");

        assertThat(item.acknowledged).isTrue();
        assertThat(item.failed).isFalse();
        assertThat(item.closed).isFalse();

        final FileGroupQueueWorkerCounters.Snapshot snapshot = counters.snapshot();
        assertThat(snapshot.pollCount()).isEqualTo(1);
        assertThat(snapshot.itemReceivedCount()).isEqualTo(1);
        assertThat(snapshot.itemProcessedCount()).isEqualTo(1);
        assertThat(snapshot.itemAcknowledgedCount()).isEqualTo(1);
        assertThat(snapshot.itemFailedCount()).isZero();
        assertThat(snapshot.processorErrorCount()).isZero();
        assertThat(snapshot.acknowledgeErrorCount()).isZero();
        assertThat(snapshot.failErrorCount()).isZero();
        assertThat(snapshot.closeErrorCount()).isEqualTo(1);
        assertThat(snapshot.hasErrors()).isTrue();
    }

    private FileGroupQueueMessage createMessage(final String queueName,
                                                final String fileGroupId) {
        return new FileGroupQueueMessage(
                FileGroupQueueMessage.CURRENT_SCHEMA_VERSION,
                "message-" + fileGroupId,
                FileStoreLocation.filesystem(
                        "receiveStore",
                        getCurrentTestDir().resolve("store").resolve(fileGroupId)),
                null,
                null,
                "receive",
                "proxy-node-1",
                Instant.parse("2025-01-02T03:04:05Z"),
                "trace-" + fileGroupId,
                Map.of("feed", "TEST_FEED"));
    }

    private static final class FakeQueue implements FileGroupQueue {

        private final String name;
        private final FileGroupQueueItem item;
        private boolean itemReturned;

        private FakeQueue(final String name,
                          final FileGroupQueueItem item) {
            this.name = name;
            this.item = item;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public QueueType getType() {
            return QueueType.LOCAL_FILESYSTEM;
        }

        @Override
        public void publish(final FileGroupQueueMessage message) {
            throw new UnsupportedOperationException("publish is not used by these tests");
        }

        @Override
        public Optional<FileGroupQueueItem> next(final Duration maxWait) {
            if (item == null || itemReturned) {
                return Optional.empty();
            }

            itemReturned = true;
            return Optional.of(item);
        }

        @Override
        public void close() {
            // No resources.
        }
    }

    private static final class FakeItem implements FileGroupQueueItem {

        private final String id;
        private final FileGroupQueueMessage message;

        private boolean acknowledged;
        private boolean failed;
        private boolean closed;
        private Throwable failure;
        private IOException acknowledgeException;
        private IOException failException;
        private IOException closeException;

        private FakeItem(final String id,
                         final FileGroupQueueMessage message) {
            this.id = id;
            this.message = message;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public FileGroupQueueMessage getMessage() {
            return message;
        }

        @Override
        public int getDeliveryAttempt() {
            return 1;
        }

        @Override
        public void acknowledge() throws IOException {
            if (acknowledgeException != null) {
                throw acknowledgeException;
            }
            acknowledged = true;
        }

        @Override
        public void fail(final Throwable error) throws IOException {
            failure = error;
            if (failException != null) {
                throw failException;
            }
            failed = true;
        }

        @Override
        public void close() throws IOException {
            if (closeException != null) {
                throw closeException;
            }
            closed = true;
        }
    }
}
