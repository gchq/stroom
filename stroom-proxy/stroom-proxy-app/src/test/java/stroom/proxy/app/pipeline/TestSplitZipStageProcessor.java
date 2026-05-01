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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestSplitZipStageProcessor extends StroomUnitTest {

    private static final String INPUT_STORE = "receiveStore";
    private static final String OUTPUT_STORE = "splitStore";

    @Test
    void testSplitsInputIntoMultipleOutputs() throws Exception {
        final LocalFileStore inputStore = createFileStore(INPUT_STORE, "input-store");
        final LocalFileStore outputStore = createFileStore(OUTPUT_STORE, "output-store");
        final LocalFileGroupQueue outputQueue = createQueue("output-queue");
        final FileStoreRegistry registry = new FileStoreRegistry(List.of(inputStore));

        // Create a source file group in the input store.
        final FileStoreLocation inputLocation = writeFileGroup(inputStore, "input-data");

        // Stub split function: creates 2 per-feed output directories.
        final SplitZipStageProcessor.SplitFunction stubSplit = (sourceDir, outputParentDir) -> {
            createFeedSplit(outputParentDir, "feed-a");
            createFeedSplit(outputParentDir, "feed-b");
        };

        final SplitZipStageProcessor processor = new SplitZipStageProcessor(
                registry, outputStore, outputQueue, "test-node", stubSplit);

        final FileGroupQueueItem item = createItem(outputQueue.getName(), inputLocation);
        processor.process(item);

        // Input should be deleted.
        assertThat(inputStore.resolve(inputLocation)).doesNotExist();

        // Output queue should have 2 messages.
        final List<FileGroupQueueMessage> messages = drainQueue(outputQueue);
        assertThat(messages).hasSize(2);

        for (final FileGroupQueueMessage message : messages) {
            assertThat(message.producingStage()).isEqualTo("splitZip");
            assertThat(message.producerId()).isEqualTo("test-node");
            assertThat(message.fileStoreLocation().storeName()).isEqualTo(OUTPUT_STORE);

            // Verify the file store has the split content.
            final Path splitPath = outputStore.resolve(message.fileStoreLocation());
            assertThat(splitPath).exists().isDirectory();
            assertThat(splitPath.resolve("proxy.meta")).exists();
            assertThat(splitPath.resolve("proxy.zip")).exists();
        }
    }

    @Test
    void testSingleSplitOutput() throws Exception {
        final LocalFileStore inputStore = createFileStore(INPUT_STORE, "input-store-single");
        final LocalFileStore outputStore = createFileStore(OUTPUT_STORE, "output-store-single");
        final LocalFileGroupQueue outputQueue = createQueue("output-queue-single");
        final FileStoreRegistry registry = new FileStoreRegistry(List.of(inputStore));

        final FileStoreLocation inputLocation = writeFileGroup(inputStore, "single-input");

        // Split that produces a single output.
        final SplitZipStageProcessor.SplitFunction stubSplit = (sourceDir, outputParentDir) -> {
            createFeedSplit(outputParentDir, "only-feed");
        };

        final SplitZipStageProcessor processor = new SplitZipStageProcessor(
                registry, outputStore, outputQueue, "test-node", stubSplit);

        final FileGroupQueueItem item = createItem(outputQueue.getName(), inputLocation);
        processor.process(item);

        assertThat(inputStore.resolve(inputLocation)).doesNotExist();

        final List<FileGroupQueueMessage> messages = drainQueue(outputQueue);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).producingStage()).isEqualTo("splitZip");
    }

    @Test
    void testTraceIdPropagated() throws Exception {
        final LocalFileStore inputStore = createFileStore(INPUT_STORE, "input-store-trace");
        final LocalFileStore outputStore = createFileStore(OUTPUT_STORE, "output-store-trace");
        final LocalFileGroupQueue outputQueue = createQueue("output-queue-trace");
        final FileStoreRegistry registry = new FileStoreRegistry(List.of(inputStore));

        final FileStoreLocation inputLocation = writeFileGroup(inputStore, "trace-input");

        final SplitZipStageProcessor.SplitFunction stubSplit = (sourceDir, outputParentDir) -> {
            createFeedSplit(outputParentDir, "feed-x");
        };

        final SplitZipStageProcessor processor = new SplitZipStageProcessor(
                registry, outputStore, outputQueue, "test-node", stubSplit);

        // Create an item with a traceId.
        final FileGroupQueueMessage inputMessage = FileGroupQueueMessage.create(
                "input-msg-id",
                outputQueue.getName(),
                "fg-trace",
                inputLocation,
                "receive",
                "test-node",
                java.time.Instant.now(),
                "trace-abc-123",
                Map.of());

        final FileGroupQueueItem item = new SimpleQueueItem(inputMessage);
        processor.process(item);

        final List<FileGroupQueueMessage> messages = drainQueue(outputQueue);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).traceId()).isEqualTo("trace-abc-123");
    }

    @Test
    void testRejectsNonDirectorySource() throws Exception {
        final LocalFileStore inputStore = createFileStore(INPUT_STORE, "input-store-nodir");
        final LocalFileStore outputStore = createFileStore(OUTPUT_STORE, "output-store-nodir");
        final LocalFileGroupQueue outputQueue = createQueue("output-queue-nodir");
        final FileStoreRegistry registry = new FileStoreRegistry(List.of(inputStore));

        // Create a location that doesn't exist.
        final FileStoreLocation fakeLocation = FileStoreLocation.localFileSystem(
                INPUT_STORE,
                getCurrentTestDir().resolve("nonexistent"));

        final SplitZipStageProcessor processor = new SplitZipStageProcessor(
                registry, outputStore, outputQueue, "test-node", (s, o) -> {});

        final FileGroupQueueItem item = createItem(outputQueue.getName(), fakeLocation);

        assertThatThrownBy(() -> processor.process(item))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("outside store root");
    }

    @Test
    void testTempDirCleanedUpAfterFailure() throws Exception {
        final LocalFileStore inputStore = createFileStore(INPUT_STORE, "input-store-fail");
        final LocalFileStore outputStore = createFileStore(OUTPUT_STORE, "output-store-fail");
        final LocalFileGroupQueue outputQueue = createQueue("output-queue-fail");
        final FileStoreRegistry registry = new FileStoreRegistry(List.of(inputStore));

        final FileStoreLocation inputLocation = writeFileGroup(inputStore, "fail-input");

        // Split function that throws.
        final SplitZipStageProcessor.SplitFunction failingSplit = (sourceDir, outputParentDir) -> {
            throw new IOException("Simulated split failure");
        };

        final SplitZipStageProcessor processor = new SplitZipStageProcessor(
                registry, outputStore, outputQueue, "test-node", failingSplit);

        final FileGroupQueueItem item = createItem(outputQueue.getName(), inputLocation);

        assertThatThrownBy(() -> processor.process(item))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Simulated split failure");

        // Input should NOT be deleted (failure occurred before deletion step).
        assertThat(inputStore.resolve(inputLocation)).exists();

        // Queue should be empty.
        assertThat(outputQueue.next()).isEmpty();
    }

    // --- helpers ---

    private LocalFileStore createFileStore(final String name, final String dirName) {
        return new LocalFileStore(
                name,
                getCurrentTestDir().resolve(dirName),
                "writer-1");
    }

    private LocalFileGroupQueue createQueue(final String name) throws IOException {
        final Path queueDir = getCurrentTestDir().resolve("queues").resolve(name);
        Files.createDirectories(queueDir);
        return new LocalFileGroupQueue(name, queueDir);
    }

    private FileStoreLocation writeFileGroup(final LocalFileStore store,
                                              final String content) throws IOException {
        try (final FileStoreWrite write = store.newWrite()) {
            Files.writeString(write.getPath().resolve("proxy.meta"), "meta-" + content);
            Files.writeString(write.getPath().resolve("proxy.zip"), "zip-" + content);
            Files.writeString(write.getPath().resolve("proxy.entries"), "entries-" + content);
            return write.commit();
        }
    }

    private void createFeedSplit(final Path parentDir, final String feedName) throws IOException {
        final Path feedDir = parentDir.resolve(feedName);
        Files.createDirectories(feedDir);
        Files.writeString(feedDir.resolve("proxy.meta"), "meta-" + feedName);
        Files.writeString(feedDir.resolve("proxy.zip"), "zip-" + feedName);
        Files.writeString(feedDir.resolve("proxy.entries"), "entries-" + feedName);
    }

    private FileGroupQueueItem createItem(final String queueName,
                                           final FileStoreLocation location) {
        final FileGroupQueueMessage message = FileGroupQueueMessage.create(
                queueName,
                "fg-" + java.util.UUID.randomUUID(),
                location,
                "receive",
                "test-node",
                null,
                Map.of());
        return new SimpleQueueItem(message);
    }

    private List<FileGroupQueueMessage> drainQueue(final LocalFileGroupQueue queue) throws IOException {
        final List<FileGroupQueueMessage> messages = new ArrayList<>();
        while (true) {
            final var item = queue.next();
            if (item.isEmpty()) {
                break;
            }
            messages.add(item.get().getMessage());
            item.get().acknowledge();
        }
        return messages;
    }

    /**
     * Simple queue item implementation for tests that don't need real
     * queue lease management.
     */
    private static final class SimpleQueueItem implements FileGroupQueueItem {

        private final FileGroupQueueMessage message;

        private SimpleQueueItem(final FileGroupQueueMessage message) {
            this.message = message;
        }

        @Override
        public String getId() {
            return message.messageId();
        }

        @Override
        public FileGroupQueueMessage getMessage() {
            return message;
        }

        @Override
        public Map<String, String> getMetadata() {
            return Map.of();
        }

        @Override
        public void acknowledge() {
        }

        @Override
        public void fail(final Throwable error) {
        }

        @Override
        public void close() {
        }
    }
}
