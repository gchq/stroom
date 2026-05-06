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

package stroom.proxy.app.pipeline.queue.local;


import stroom.proxy.app.pipeline.queue.AbstractFileGroupQueueContractTest;
import stroom.proxy.app.pipeline.queue.FileGroupQueue;
import stroom.proxy.app.pipeline.queue.FileGroupQueueItem;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessage;
import stroom.proxy.app.pipeline.queue.QueueType;
import stroom.proxy.app.pipeline.store.FileStoreLocation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestLocalFileGroupQueue extends AbstractFileGroupQueueContractTest {

    @Override
    protected FileGroupQueue createQueue(final String name) throws IOException {
        final Path queueRoot = getCurrentTestDir().resolve("contract-queue-" + name);
        return new LocalFileGroupQueue(name, queueRoot);
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
            assertThat(item.getMetadata())
                    .containsEntry("queueName", "preAggregateInput")
                    .containsEntry("queueType", QueueType.LOCAL_FILESYSTEM.name())
                    .containsEntry("itemId", "00000000000000000001")
                    .containsEntry("state", "in-flight");
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

            assertThat(item.getMetadata())
                    .containsEntry("state", "completed");
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
            assertThat(retryItem.getId()).isEqualTo("00000000000000000001");
            assertThat(retryItem.getMessage()).isEqualTo(message);

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
            assertThat(recoveredItem.getId()).isEqualTo("00000000000000000001");
            assertThat(recoveredItem.getMessage()).isEqualTo(message);

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
