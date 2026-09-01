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

package stroom.proxy.app.pipeline.queue.local;

import stroom.proxy.app.pipeline.queue.AbstractFileGroupQueueContractTest;
import stroom.proxy.app.pipeline.queue.FileGroupQueue;
import stroom.proxy.app.pipeline.queue.FileGroupQueueItem;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessage;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessageCodec;
import stroom.proxy.app.pipeline.queue.QueueType;
import stroom.proxy.app.pipeline.store.FileStoreLocation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestLocalFileGroupQueue extends AbstractFileGroupQueueContractTest {

    @Override
    protected FileGroupQueue createQueue(final String name) throws IOException {
        final Path queueRoot = getCurrentTestDir().resolve("contract-queue-" + name);
        return new LocalFileGroupQueue(name, queueRoot);
    }

    /**
     * H9. Without the increment on recovery, a message that kills its consumer on every attempt is
     * handed back at the head of the queue forever and {@code maxDeliveryAttempts} can never break
     * the loop.
     */
    @Test
    void testARepeatedCrashEventuallyQuarantinesTheMessage() throws IOException {
        final Path queueRoot = getCurrentTestDir().resolve("queue");
        final FileStoreLocation location = FileStoreLocation.localFileSystem(
                "receiveStore",
                createReferencedFileGroup("store/receive/0000000001"));
        final FileGroupQueueMessage message = createMessage("preAggregateInput", "file-group-1", location);

        final LocalFileGroupQueue queue = newQueue(queueRoot, 2);
        queue.publish(message);
        queue.close();

        // Each round is a process that takes the message and is killed before acknowledging it.
        for (int attempt = 1; attempt < 3; attempt++) {
            final LocalFileGroupQueue crashed = newQueue(queueRoot, 2);
            final FileGroupQueueItem item = crashed.next().orElseThrow();
            assertThat(LocalFileGroupQueue.deliveryAttempts(item.getMessage()))
                    .as("attempt %d should carry the count of the crashes before it", attempt)
                    .isEqualTo(attempt - 1);
            crashed.close();
        }

        // The third recovery takes it to the bound, so it is quarantined rather than handed out again.
        final LocalFileGroupQueue afterBound = newQueue(queueRoot, 2);
        assertThat(afterBound.next())
                .as("a message at maxDeliveryAttempts must not be delivered again")
                .isEmpty();
        assertThat(afterBound.getApproximatePendingCount()).isZero();
        assertThat(Files.list(queueRoot.resolve("failed")).count())
                .as("it is quarantined for an operator, not deleted")
                .isPositive();
    }

    /**
     * The increment on recovery goes through writePending, which allocates an id. With a non-empty
     * queue that allocation collides unless the sequence is seeded first, and the collision is
     * swallowed as a failed requeue - so the attempt silently went uncounted in exactly the case a
     * real restart presents. The queue below is deliberately not empty.
     */
    @Test
    void testRecoveryCountsTheAttemptEvenWhenTheQueueIsNotEmpty() throws IOException {
        final Path queueRoot = getCurrentTestDir().resolve("queue");
        final FileStoreLocation location = FileStoreLocation.localFileSystem(
                "receiveStore",
                createReferencedFileGroup("store/receive/0000000001"));

        final LocalFileGroupQueue queue = newQueue(queueRoot, 100);
        queue.publish(createMessage("preAggregateInput", "file-group-1", location));
        queue.publish(createMessage("preAggregateInput", "file-group-2", location));

        // Take the first and be killed, leaving the second still queued behind it.
        final FileGroupQueueItem taken = queue.next().orElseThrow();
        assertThat(taken.getMessage().fileGroupId()).isEqualTo("file-group-1");
        queue.close();

        final LocalFileGroupQueue restarted = newQueue(queueRoot, 100);
        assertThat(restarted.getApproximatePendingCount()).isEqualTo(2);

        // The recovered message is behind the one that was already queued, and carries the attempt.
        try (final FileGroupQueueItem first = restarted.next().orElseThrow()) {
            assertThat(first.getMessage().fileGroupId()).isEqualTo("file-group-2");
            assertThat(LocalFileGroupQueue.deliveryAttempts(first.getMessage())).isZero();
            first.acknowledge();
        }
        try (final FileGroupQueueItem recovered = restarted.next().orElseThrow()) {
            assertThat(recovered.getMessage().fileGroupId()).isEqualTo("file-group-1");
            assertThat(LocalFileGroupQueue.deliveryAttempts(recovered.getMessage()))
                    .as("the crashed delivery must be counted")
                    .isEqualTo(1);
            recovered.acknowledge();
        }
    }

    /**
     * M1. Only a vanished pending file means another consumer won the race. Anything else - here a
     * missing in-flight directory - used to send this loop round again onto the same file, forever.
     */
    @Test
    void testAMoveFailureThatIsNotALostRaceIsReportedRatherThanRetriedForever() throws Exception {
        final Path queueRoot = getCurrentTestDir().resolve("queue");
        final FileStoreLocation location = FileStoreLocation.localFileSystem(
                "receiveStore",
                createReferencedFileGroup("store/receive/0000000001"));
        final FileGroupQueueMessage message = createMessage("preAggregateInput", "file-group-1", location);

        final LocalFileGroupQueue queue = newQueue(queueRoot, 100);
        queue.publish(message);

        Files.delete(queueRoot.resolve("in-flight"));

        // Before the fix this looped forever, and neither @Timeout nor a thread interrupt can stop a
        // tight loop of uninterruptible file operations - it would hang the build rather than fail it.
        // So the call runs on a daemon thread we can walk away from, and the assertion is on the wait.
        final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            final Thread thread = new Thread(runnable, "m1-probe");
            thread.setDaemon(true);
            return thread;
        });
        try {
            final Callable<Optional<FileGroupQueueItem>> take = queue::next;
            final Future<Optional<FileGroupQueueItem>> future = executor.submit(take);
            assertThatThrownBy(() -> future.get(15, TimeUnit.SECONDS))
                    .as("next() must report the failure rather than spin on the same file")
                    .isNotInstanceOf(TimeoutException.class)
                    .hasRootCauseInstanceOf(java.nio.file.NoSuchFileException.class);
        } finally {
            executor.shutdownNow();
        }

        assertThat(queue.getActiveLeaseCount())
                .as("a claim taken for a move that failed must not be kept")
                .isZero();
    }

    private static LocalFileGroupQueue newQueue(final Path queueRoot,
                                                final int maxDeliveryAttempts) throws IOException {
        return new LocalFileGroupQueue(
                "preAggregateInput",
                queueRoot,
                new FileGroupQueueMessageCodec(),
                Duration.ofHours(1),
                maxDeliveryAttempts);
    }

    @Test
    void testPublishStoresMessageFileAndDoesNotMoveReferencedData() throws IOException {
        final Path queueRoot = getCurrentTestDir().resolve("queue");
        final Path fileGroupPath = createReferencedFileGroup("store/receive/0000000001");
        final FileStoreLocation location = FileStoreLocation.localFileSystem("receiveStore", fileGroupPath);
        final FileGroupQueueMessage message = createMessage("preAggregateInput", "file-group-1", location);

        final LocalFileGroupQueue queue = new LocalFileGroupQueue("preAggregateInput", queueRoot);

        queue.publish(message);

        assertThat(fileGroupPath).exists().isDirectory();
        assertThat(fileGroupPath.resolve("proxy.meta")).hasContent("meta");
        assertThat(fileGroupPath.resolve("proxy.zip")).hasContent("zip");
        assertThat(fileGroupPath.resolve("proxy.entries")).hasContent("entries");

        assertThat(queue.getApproximatePendingCount()).isEqualTo(1);
        assertThat(queue.getApproximateInFlightCount()).isZero();
        assertThat(queue.getApproximateFailedCount()).isZero();

        assertThat(queue.getPendingDir())
                .isDirectoryContaining(path -> path.getFileName().toString().endsWith(".json"));
        assertThat(queue.getOldestPendingItemTime()).isPresent();
    }

    @Test
    void testConsumerGetsOriginalFileStoreLocation() throws IOException {
        final Path queueRoot = getCurrentTestDir().resolve("queue");
        final Path fileGroupPath = createReferencedFileGroup("store/receive/0000000001");
        final FileStoreLocation location = FileStoreLocation.localFileSystem("receiveStore", fileGroupPath);
        final FileGroupQueueMessage message = createMessage("preAggregateInput", "file-group-1", location);

        final LocalFileGroupQueue queue = new LocalFileGroupQueue("preAggregateInput", queueRoot);
        queue.publish(message);

        final Optional<FileGroupQueueItem> optionalItem = queue.next();

        assertThat(optionalItem).isPresent();

        try (final FileGroupQueueItem item = optionalItem.orElseThrow()) {
            assertThat(item.getId()).isEqualTo("00000000000000000001");
            assertThat(item.getMessage()).isEqualTo(message);
            assertThat(item.getMessage().fileStoreLocation()).isEqualTo(location);
            assertThat(item.getMessage().fileStoreLocation().uri()).isEqualTo(location.uri());
        }

        assertThat(fileGroupPath).exists().isDirectory();
        assertThat(queue.getApproximatePendingCount()).isZero();
        assertThat(queue.getApproximateInFlightCount()).isEqualTo(1);
    }

    @Test
    void testAcknowledgeRemovesQueueRecord() throws IOException {
        final Path queueRoot = getCurrentTestDir().resolve("queue");
        final FileStoreLocation location = FileStoreLocation.localFileSystem(
                "receiveStore",
                createReferencedFileGroup("store/receive/0000000001"));
        final FileGroupQueueMessage message = createMessage("preAggregateInput", "file-group-1", location);

        final LocalFileGroupQueue queue = new LocalFileGroupQueue("preAggregateInput", queueRoot);
        queue.publish(message);

        try (final FileGroupQueueItem item = queue.next().orElseThrow()) {
            item.acknowledge();

        }

        assertThat(queue.getApproximatePendingCount()).isZero();
        assertThat(queue.getApproximateInFlightCount()).isZero();
        assertThat(queue.getApproximateFailedCount()).isZero();
        assertThat(queue.next()).isEmpty();
    }

    @Test
    void testFailMakesMessageVisibleAgainForRetry() throws IOException {
        final Path queueRoot = getCurrentTestDir().resolve("queue");
        final FileStoreLocation location = FileStoreLocation.localFileSystem(
                "receiveStore",
                createReferencedFileGroup("store/receive/0000000001"));
        final FileGroupQueueMessage message = createMessage("preAggregateInput", "file-group-1", location);

        final LocalFileGroupQueue queue = new LocalFileGroupQueue("preAggregateInput", queueRoot);
        queue.publish(message);

        try (final FileGroupQueueItem item = queue.next().orElseThrow()) {
            assertThat(item.getId()).isEqualTo("00000000000000000001");

            item.fail(new RuntimeException("Deliberate failure"));
        }

        assertThat(queue.getApproximatePendingCount()).isEqualTo(1);
        assertThat(queue.getApproximateInFlightCount()).isZero();
        assertThat(queue.getApproximateFailedCount()).isZero();

        try (final FileGroupQueueItem retryItem = queue.next().orElseThrow()) {
            // A retry is re-queued under a new id so it goes to the back rather than
            // straight back to the head, where it would block everything behind it.
            assertThat(retryItem.getId()).isNotEqualTo("00000000000000000001");

            // It is still the same message, plus the delivery-attempt count that
            // bounds how long it can keep circulating.
            assertThat(retryItem.getMessage().messageId()).isEqualTo(message.messageId());
            assertThat(retryItem.getMessage().fileGroupId()).isEqualTo(message.fileGroupId());
            assertThat(retryItem.getMessage().fileStoreLocation()).isEqualTo(message.fileStoreLocation());
            assertThat(LocalFileGroupQueue.deliveryAttempts(retryItem.getMessage())).isEqualTo(1);

            retryItem.acknowledge();
        }

        assertThat(queue.getApproximatePendingCount()).isZero();
        assertThat(queue.getApproximateInFlightCount()).isZero();
    }

    @Test
    void testRestartRecoveryMovesInFlightMessageBackToPending() throws IOException {
        final Path queueRoot = getCurrentTestDir().resolve("queue");
        final FileStoreLocation location = FileStoreLocation.localFileSystem(
                "receiveStore",
                createReferencedFileGroup("store/receive/0000000001"));
        final FileGroupQueueMessage message = createMessage("preAggregateInput", "file-group-1", location);

        final LocalFileGroupQueue queue = new LocalFileGroupQueue("preAggregateInput", queueRoot);
        queue.publish(message);

        final FileGroupQueueItem item = queue.next().orElseThrow();

        assertThat(item.getId()).isEqualTo("00000000000000000001");
        assertThat(queue.getApproximatePendingCount()).isZero();
        assertThat(queue.getApproximateInFlightCount()).isEqualTo(1);

        item.close();
        queue.close();

        final LocalFileGroupQueue restartedQueue = new LocalFileGroupQueue("preAggregateInput", queueRoot);

        assertThat(restartedQueue.getApproximatePendingCount()).isEqualTo(1);
        assertThat(restartedQueue.getApproximateInFlightCount()).isZero();

        try (final FileGroupQueueItem recoveredItem = restartedQueue.next().orElseThrow()) {
            // Recovery re-queues through writePending, so the message comes back under a NEW id at the
            // back of the queue rather than under its old id at the head - the same reasoning as
            // fail(): a message that keeps killing its consumer must not block everything behind it.
            assertThat(recoveredItem.getId()).isEqualTo("00000000000000000002");
            // The recovered message is no longer equal to the published one, and must not be: recovery
            // is a redelivery, so it counts as an attempt. Asserting equality here is what let a crash
            // loop run forever without ever reaching maxDeliveryAttempts (H9).
            assertThat(LocalFileGroupQueue.deliveryAttempts(recoveredItem.getMessage()))
                    .isEqualTo(1);
            assertThat(recoveredItem.getMessage().fileGroupId())
                    .isEqualTo(message.fileGroupId());
            assertThat(recoveredItem.getMessage().fileStoreLocation())
                    .isEqualTo(message.fileStoreLocation());

            recoveredItem.acknowledge();
        }

        assertThat(restartedQueue.getApproximatePendingCount()).isZero();
        assertThat(restartedQueue.getApproximateInFlightCount()).isZero();
    }

    @Test
    void testMessagesAreConsumedInSequenceOrder() throws IOException {
        final Path queueRoot = getCurrentTestDir().resolve("queue");
        final LocalFileGroupQueue queue = new LocalFileGroupQueue("preAggregateInput", queueRoot);

        final FileGroupQueueMessage firstMessage = createMessage(
                "preAggregateInput",
                "file-group-1",
                FileStoreLocation.localFileSystem(
                        "receiveStore",
                        createReferencedFileGroup("store/receive/0000000001")));
        final FileGroupQueueMessage secondMessage = createMessage(
                "preAggregateInput",
                "file-group-2",
                FileStoreLocation.localFileSystem(
                        "receiveStore",
                        createReferencedFileGroup("store/receive/0000000002")));

        queue.publish(firstMessage);
        queue.publish(secondMessage);

        try (final FileGroupQueueItem firstItem = queue.next().orElseThrow();
             final FileGroupQueueItem secondItem = queue.next().orElseThrow()) {
            assertThat(firstItem.getId()).isEqualTo("00000000000000000001");
            assertThat(firstItem.getMessage()).isEqualTo(firstMessage);

            assertThat(secondItem.getId()).isEqualTo("00000000000000000002");
            assertThat(secondItem.getMessage()).isEqualTo(secondMessage);

            firstItem.acknowledge();
            secondItem.acknowledge();
        }

        assertThat(queue.next()).isEmpty();
    }

    @Test
    void testPublishRejectsMessageForDifferentQueueName() throws IOException {
        final LocalFileGroupQueue queue = new LocalFileGroupQueue(
                "preAggregateInput",
                getCurrentTestDir().resolve("queue"));
        final FileStoreLocation location = FileStoreLocation.localFileSystem(
                "receiveStore",
                createReferencedFileGroup("store/receive/0000000001"));
        final FileGroupQueueMessage message = createMessage("aggregateInput", "file-group-1", location);

        assertThatThrownBy(() -> queue.publish(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aggregateInput")
                .hasMessageContaining("preAggregateInput");

        assertThat(queue.getApproximatePendingCount()).isZero();
    }

    @Test
    void testQueuePersistsMessagesAcrossRestart() throws IOException {
        final Path queueRoot = getCurrentTestDir().resolve("queue");
        final FileStoreLocation location = FileStoreLocation.localFileSystem(
                "receiveStore",
                createReferencedFileGroup("store/receive/0000000001"));
        final FileGroupQueueMessage message = createMessage("preAggregateInput", "file-group-1", location);

        final LocalFileGroupQueue originalQueue = new LocalFileGroupQueue("preAggregateInput", queueRoot);
        originalQueue.publish(message);
        originalQueue.close();

        final LocalFileGroupQueue restartedQueue = new LocalFileGroupQueue("preAggregateInput", queueRoot);

        assertThat(restartedQueue.getApproximatePendingCount()).isEqualTo(1);

        try (final FileGroupQueueItem item = restartedQueue.next().orElseThrow()) {
            assertThat(item.getId()).isEqualTo("00000000000000000001");
            assertThat(item.getMessage()).isEqualTo(message);

            item.acknowledge();
        }

        assertThat(restartedQueue.getApproximatePendingCount()).isZero();
    }

    private Path createReferencedFileGroup(final String relativePath) throws IOException {
        final Path fileGroupPath = getCurrentTestDir().resolve(relativePath);
        Files.createDirectories(fileGroupPath);
        Files.writeString(fileGroupPath.resolve("proxy.meta"), "meta");
        Files.writeString(fileGroupPath.resolve("proxy.zip"), "zip");
        Files.writeString(fileGroupPath.resolve("proxy.entries"), "entries");
        return fileGroupPath;
    }

    private static FileGroupQueueMessage createMessage(final String queueName,
                                                       final String fileGroupId,
                                                       final FileStoreLocation location) {
        return FileGroupQueueMessage.create(
                "message-" + fileGroupId,
                queueName,
                fileGroupId,
                location,
                "receive",
                "proxy-node-1",
                Instant.parse("2025-01-02T03:04:05Z"),
                "trace-" + fileGroupId,
                Map.of(
                        "feed", "TEST_FEED",
                        "type", "Raw Events"));
    }
}
