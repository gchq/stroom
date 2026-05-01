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

import stroom.test.common.util.test.StroomUnitTest;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestReceiveStagePublisher extends StroomUnitTest {

    @Test
    void testPublishCopiesFilesToStoreAndPublishesMessage() throws IOException {
        final LocalFileStore fileStore = createFileStore("receive-store");
        final LocalFileGroupQueue outputQueue = createQueue("output-queue");

        final ReceiveStagePublisher publisher = new ReceiveStagePublisher(
                fileStore, outputQueue, null, "test-node");

        // Simulate a received file group in a temp directory.
        final Path receivedDir = createReceivedFileGroup("received-1");

        publisher.accept(receivedDir);

        // Temp dir should be deleted.
        assertThat(receivedDir).doesNotExist();

        // Queue should have one message.
        final FileGroupQueueItem item = outputQueue.next().orElse(null);
        assertThat(item).isNotNull();

        final FileGroupQueueMessage message = item.getMessage();
        assertThat(message.producingStage()).isEqualTo("receive");
        assertThat(message.producerId()).isEqualTo("test-node");
        assertThat(message.queueName()).isEqualTo("output-queue");
        assertThat(message.fileStoreLocation().storeName()).isEqualTo("receive-store");

        // File store should contain the file group.
        final Path stablePath = fileStore.resolve(message.fileStoreLocation());
        assertThat(stablePath).exists().isDirectory();
        assertThat(stablePath.resolve("proxy.meta")).hasContent("meta-content");
        assertThat(stablePath.resolve("proxy.zip")).hasContent("zip-content");
        assertThat(stablePath.resolve("proxy.entries")).hasContent("entries-content");

        item.acknowledge();
    }

    @Test
    void testPublishMultipleReceives() throws IOException {
        final LocalFileStore fileStore = createFileStore("receive-store-multi");
        final LocalFileGroupQueue outputQueue = createQueue("output-queue-multi");

        final ReceiveStagePublisher publisher = new ReceiveStagePublisher(
                fileStore, outputQueue, null, "test-node");

        // Publish 3 received file groups.
        for (int i = 1; i <= 3; i++) {
            final Path receivedDir = createReceivedFileGroup("received-" + i);
            publisher.accept(receivedDir);
            assertThat(receivedDir).doesNotExist();
        }

        // Queue should have 3 messages with distinct file-group IDs.
        final java.util.Set<String> fileGroupIds = new java.util.HashSet<>();
        for (int i = 0; i < 3; i++) {
            final FileGroupQueueItem item = outputQueue.next().orElse(null);
            assertThat(item).isNotNull();
            fileGroupIds.add(item.getMessage().fileGroupId());
            item.acknowledge();
        }
        assertThat(fileGroupIds).hasSize(3);

        // No more messages.
        assertThat(outputQueue.next()).isEmpty();
    }

    @Test
    void testPublishWithEmptyReceivedDirPublishesEmptyFileGroup() throws IOException {
        final LocalFileStore fileStore = createFileStore("receive-store-empty");
        final LocalFileGroupQueue outputQueue = createQueue("output-queue-empty");

        final ReceiveStagePublisher publisher = new ReceiveStagePublisher(
                fileStore, outputQueue, null, "test-node");

        // Create an empty temp dir.
        final Path emptyDir = getCurrentTestDir().resolve("empty-received");
        Files.createDirectories(emptyDir);

        publisher.accept(emptyDir);

        assertThat(emptyDir).doesNotExist();

        // Queue should have one message.
        final FileGroupQueueItem item = outputQueue.next().orElse(null);
        assertThat(item).isNotNull();

        // File store location should exist (empty dir).
        final Path stablePath = fileStore.resolve(item.getMessage().fileStoreLocation());
        assertThat(stablePath).exists().isDirectory();

        item.acknowledge();
    }

    @Test
    void testPublishSetsCorrectMessageFields() throws IOException {
        final LocalFileStore fileStore = createFileStore("receive-store-fields");
        final LocalFileGroupQueue outputQueue = createQueue("output-queue-fields");

        final ReceiveStagePublisher publisher = new ReceiveStagePublisher(
                fileStore, outputQueue, null, "my-proxy-node");

        final Path receivedDir = createReceivedFileGroup("received-fields");
        publisher.accept(receivedDir);

        final FileGroupQueueItem item = outputQueue.next().orElse(null);
        assertThat(item).isNotNull();

        final FileGroupQueueMessage message = item.getMessage();
        assertThat(message.messageId()).isNotBlank();
        assertThat(message.queueName()).isEqualTo("output-queue-fields");
        assertThat(message.fileGroupId()).isNotBlank();
        assertThat(message.producingStage()).isEqualTo("receive");
        assertThat(message.producerId()).isEqualTo("my-proxy-node");
        assertThat(message.createdTime()).isNotNull();
        assertThat(message.fileStoreLocation()).isNotNull();
        assertThat(message.fileStoreLocation().locationType())
                .isEqualTo(FileStoreLocation.LocationType.LOCAL_FILESYSTEM);

        item.acknowledge();
    }

    @Test
    void testRejectsNullReceivedDir() {
        final ReceiveStagePublisher publisher = new ReceiveStagePublisher(
                new LocalFileStore("s", getCurrentTestDir().resolve("s"), "w"),
                createQueueUnchecked("q"),
                null,
                "node");

        assertThatThrownBy(() -> publisher.accept(null))
                .isInstanceOf(NullPointerException.class);
    }

    // --- helpers ---

    private LocalFileStore createFileStore(final String name) {
        return new LocalFileStore(
                name,
                getCurrentTestDir().resolve(name),
                "writer-1");
    }

    private LocalFileGroupQueue createQueue(final String name) throws IOException {
        final Path queueDir = getCurrentTestDir().resolve("queues").resolve(name);
        Files.createDirectories(queueDir);
        return new LocalFileGroupQueue(name, queueDir);
    }

    private LocalFileGroupQueue createQueueUnchecked(final String name) {
        try {
            return createQueue(name);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Path createReceivedFileGroup(final String name) throws IOException {
        final Path dir = getCurrentTestDir().resolve("receiving").resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("proxy.meta"), "meta-content");
        Files.writeString(dir.resolve("proxy.zip"), "zip-content");
        Files.writeString(dir.resolve("proxy.entries"), "entries-content");
        return dir;
    }
}
