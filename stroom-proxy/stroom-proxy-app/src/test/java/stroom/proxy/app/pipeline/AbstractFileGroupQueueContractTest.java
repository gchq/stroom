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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Shared contract test suite that every {@link FileGroupQueue} implementation
 * must satisfy.
 * <p>
 * Subclasses provide the concrete queue via {@link #createQueue(String)} and
 * optionally prepare records for consumption via
 * {@link #prepareForConsumption(FileGroupQueue, FileGroupQueueMessage)}.
 * </p>
 * <p>
 * These tests validate the contract defined in the implementation plan
 * (§Required contract tests, lines 1611–1636):
 * </p>
 * <ol>
 *     <li>Producer: publish makes item available; does not mutate source path;
 *         includes expected location; has a stable ID.</li>
 *     <li>Consumer: next blocks/waits; next returns available item;
 *         acknowledge prevents redelivery; fail causes redelivery;
 *         close releases resources.</li>
 * </ol>
 */
abstract class AbstractFileGroupQueueContractTest extends StroomUnitTest {

    private static final String QUEUE_NAME = "contractTestQueue";

    private FileGroupQueue queue;

    /**
     * Create a fresh queue for the given logical name.
     */
    protected abstract FileGroupQueue createQueue(String name) throws IOException;

    /**
     * If the queue implementation requires external setup for a published
     * message to become consumable (e.g. Kafka mock consumer needs records
     * injected), perform that setup here. The default implementation does
     * nothing — suitable for queues where publish automatically makes items
     * consumable (e.g. local filesystem, SQS).
     */
    protected void prepareForConsumption(final FileGroupQueue queue,
                                          final FileGroupQueueMessage message) throws IOException {
        // Default: no-op.
    }

    @AfterEach
    void tearDown() throws IOException {
        if (queue != null) {
            queue.close();
            queue = null;
        }
    }

    // ------------------------------------------------------------------
    // Producer contract tests
    // ------------------------------------------------------------------

    @Test
    void contractPublishMakesItemAvailableToConsumer() throws IOException {
        queue = createQueue(QUEUE_NAME);
        final FileGroupQueueMessage message = createMessage("fg-avail-1");
        queue.publish(message);
        prepareForConsumption(queue, message);

        final Optional<FileGroupQueueItem> item = queue.next();
        assertThat(item).isPresent();
        assertThat(item.get().getMessage().fileGroupId()).isEqualTo("fg-avail-1");
    }

    @Test
    void contractPublishDoesNotMutateReferencedSourcePath() throws IOException {
        queue = createQueue(QUEUE_NAME);
        final FileStoreLocation location = testLocation();
        final String uriBefore = location.uri();
        final FileGroupQueueMessage message = createMessage("fg-nomut-1", location);

        queue.publish(message);

        // The location object and URI must not have been changed by publish.
        assertThat(location.uri()).isEqualTo(uriBefore);
        assertThat(location.storeName()).isEqualTo("testStore");
    }

    @Test
    void contractQueueItemIncludesExpectedFileStoreLocation() throws IOException {
        queue = createQueue(QUEUE_NAME);
        final FileStoreLocation location = testLocation();
        final FileGroupQueueMessage message = createMessage("fg-loc-1", location);
        queue.publish(message);
        prepareForConsumption(queue, message);

        final FileGroupQueueItem item = queue.next().orElseThrow();
        assertThat(item.getMessage().fileStoreLocation().uri()).isEqualTo(location.uri());
        assertThat(item.getMessage().fileStoreLocation().storeName()).isEqualTo(location.storeName());
    }

    @Test
    void contractQueueItemHasStableId() throws IOException {
        queue = createQueue(QUEUE_NAME);
        final FileGroupQueueMessage message = createMessage("fg-id-1");
        queue.publish(message);
        prepareForConsumption(queue, message);

        final FileGroupQueueItem item = queue.next().orElseThrow();
        final String id = item.getId();
        assertThat(id).isNotBlank();
        // Calling getId() again must return the same value.
        assertThat(item.getId()).isEqualTo(id);
    }

    @Test
    void contractPublishRejectsWrongQueueName() throws IOException {
        queue = createQueue(QUEUE_NAME);
        final FileGroupQueueMessage wrongNameMessage = FileGroupQueueMessage.create(
                "wrongQueueName",
                "fg-wrong-1",
                testLocation(),
                "receive",
                "node-1",
                null,
                Map.of());

        assertThatThrownBy(() -> queue.publish(wrongNameMessage))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------
    // Consumer contract tests
    // ------------------------------------------------------------------

    @Test
    void contractNextReturnsEmptyWhenQueueIsEmpty() throws IOException {
        queue = createQueue(QUEUE_NAME);
        final Optional<FileGroupQueueItem> item = queue.next();
        assertThat(item).isEmpty();
    }

    @Test
    void contractAcknowledgePreventsRedelivery() throws IOException {
        queue = createQueue(QUEUE_NAME);
        final FileGroupQueueMessage message = createMessage("fg-ack-1");
        queue.publish(message);
        prepareForConsumption(queue, message);

        try (final FileGroupQueueItem item = queue.next().orElseThrow()) {
            item.acknowledge();
        }

        // After acknowledgement, the queue should not redeliver this item.
        // (For queues that don't support this check naturally, the next()
        // call should return empty.)
        final Optional<FileGroupQueueItem> retry = queue.next();
        assertThat(retry).isEmpty();
    }

    @Test
    void contractAcknowledgeIsIdempotent() throws IOException {
        queue = createQueue(QUEUE_NAME);
        final FileGroupQueueMessage message = createMessage("fg-ack-idem-1");
        queue.publish(message);
        prepareForConsumption(queue, message);

        try (final FileGroupQueueItem item = queue.next().orElseThrow()) {
            item.acknowledge();
            // Must not throw.
            item.acknowledge();
        }
    }

    @Test
    void contractCloseReleasesResources() throws IOException {
        queue = createQueue(QUEUE_NAME);
        // Close must not throw.
        queue.close();
        queue = null;
    }

    @Test
    void contractItemMetadataContainsQueueName() throws IOException {
        queue = createQueue(QUEUE_NAME);
        final FileGroupQueueMessage message = createMessage("fg-meta-1");
        queue.publish(message);
        prepareForConsumption(queue, message);

        try (final FileGroupQueueItem item = queue.next().orElseThrow()) {
            assertThat(item.getMetadata())
                    .containsEntry("queueName", QUEUE_NAME);
        }
    }

    @Test
    void contractNameAndType() throws IOException {
        queue = createQueue(QUEUE_NAME);
        assertThat(queue.getName()).isEqualTo(QUEUE_NAME);
        assertThat(queue.getType()).isNotNull();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    protected FileGroupQueueMessage createMessage(final String fileGroupId) {
        return createMessage(fileGroupId, testLocation());
    }

    protected FileGroupQueueMessage createMessage(final String fileGroupId,
                                                   final FileStoreLocation location) {
        return FileGroupQueueMessage.create(
                QUEUE_NAME,
                fileGroupId,
                location,
                "receive",
                "test-node",
                null,
                Map.of());
    }

    protected static FileStoreLocation testLocation() {
        return FileStoreLocation.localFileSystem(
                "testStore",
                Path.of("/tmp/test/store/0000000001"));
    }
}
