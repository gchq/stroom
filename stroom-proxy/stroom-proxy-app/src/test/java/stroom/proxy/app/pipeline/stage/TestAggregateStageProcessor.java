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

package stroom.proxy.app.pipeline.stage;

import stroom.proxy.app.pipeline.queue.FileGroupQueueItem;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessage;
import stroom.proxy.app.pipeline.queue.local.LocalFileGroupQueue;
import stroom.proxy.app.pipeline.runtime.FileStoreRegistry;
import stroom.proxy.app.pipeline.stage.aggregate.AggregateStageProcessor;
import stroom.proxy.app.pipeline.stage.aggregate.AggregateClosePublisher;
import stroom.proxy.app.pipeline.runtime.PipelineStageName;
import stroom.proxy.app.pipeline.store.FileStoreLocation;
import stroom.proxy.app.pipeline.store.FileStoreWrite;
import stroom.proxy.app.pipeline.store.local.LocalFileStore;
import stroom.test.common.util.test.StroomUnitTest;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestAggregateStageProcessor extends StroomUnitTest {

    private static final String INPUT_STORE = "preAggStore";

    @Test
    void testDelegatesToAggregateFunctionAndDeletesInput() throws Exception {
        final LocalFileStore inputStore = createFileStore(INPUT_STORE, "input-store");
        final FileStoreRegistry registry = new FileStoreRegistry(List.of(inputStore));

        final FileStoreLocation inputLocation = writeAggregateDir(inputStore);
        final Path resolvedPath = inputStore.resolve(inputLocation);

        // Verify the input exists before processing.
        assertThat(resolvedPath).exists().isDirectory();

        // Capture the state as seen by the aggregate function during execution.
        final java.util.ArrayList<Boolean> dirExistedDuringCall = new java.util.ArrayList<>();
        final AggregateStageProcessor.AggregateFunction stubFunction = dir -> {
            dirExistedDuringCall.add(Files.isDirectory(dir));
        };

        final AggregateStageProcessor processor = new AggregateStageProcessor(
                registry, stubFunction);

        final FileGroupQueueItem item = createItem("agg-queue", inputLocation);
        processor.process(item);

        // The function should have been called with a valid directory.
        assertThat(dirExistedDuringCall).containsExactly(true);

        // After processing, the input should be deleted (ownership transfer).
        assertThat(resolvedPath).doesNotExist();
    }

    @Test
    void testRejectsNonDirectorySource() throws Exception {
        final LocalFileStore inputStore = createFileStore(INPUT_STORE, "input-store-nodir");
        final FileStoreRegistry registry = new FileStoreRegistry(List.of(inputStore));

        final FileStoreLocation fakeLocation = FileStoreLocation.localFileSystem(
                INPUT_STORE,
                getCurrentTestDir().resolve("nonexistent"));

        final AggregateStageProcessor processor = new AggregateStageProcessor(
                registry, dir -> {});

        final FileGroupQueueItem item = createItem("queue", fakeLocation);

        assertThatThrownBy(() -> processor.process(item))
                .isInstanceOf(IOException.class);
    }

    @Test
    void testFunctionExceptionPropagates() throws Exception {
        final LocalFileStore inputStore = createFileStore(INPUT_STORE, "input-store-err");
        final FileStoreRegistry registry = new FileStoreRegistry(List.of(inputStore));

        final FileStoreLocation inputLocation = writeAggregateDir(inputStore);

        final AggregateStageProcessor.AggregateFunction failingFunction = dir -> {
            throw new RuntimeException("Simulated aggregation failure");
        };

        final AggregateStageProcessor processor = new AggregateStageProcessor(
                registry, failingFunction);

        final FileGroupQueueItem item = createItem("queue", inputLocation);

        assertThatThrownBy(() -> processor.process(item))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Simulated aggregation failure");
    }

    @Test
    void testIntegrationWithAggregateClosePublisher() throws Exception {
        // End-to-end: processor resolves → stub function simulates merge →
        // AggregateClosePublisher publishes result to output queue.
        final LocalFileStore inputStore = createFileStore(INPUT_STORE, "input-store-int");
        final LocalFileStore outputStore = createFileStore("fwdStore", "output-store-int");
        final LocalFileGroupQueue outputQueue = createQueue("fwd-queue");
        final FileStoreRegistry registry = new FileStoreRegistry(List.of(inputStore));

        final FileStoreLocation inputLocation = writeAggregateDir(inputStore);

        // Create a close publisher for the aggregate stage.
        final AggregateClosePublisher closePublisher = new AggregateClosePublisher(
                outputStore, outputQueue, PipelineStageName.AGGREGATE, "agg-node");

        // Stub function that simulates merge by writing a single merged output
        // and passing it to the close publisher.
        final AggregateStageProcessor.AggregateFunction stubFunction = sourceDir -> {
            try {
                final Path mergedDir = getCurrentTestDir().resolve("merged-output");
                Files.createDirectories(mergedDir);
                Files.writeString(mergedDir.resolve("proxy.meta"), "merged-meta");
                Files.writeString(mergedDir.resolve("proxy.zip"), "merged-zip");
                closePublisher.accept(mergedDir);
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
        };

        final AggregateStageProcessor processor = new AggregateStageProcessor(
                registry, stubFunction);

        final FileGroupQueueItem item = createItem("agg-queue", inputLocation);
        processor.process(item);

        // Output queue should have one message from the close publisher.
        final var outItem = outputQueue.next();
        assertThat(outItem).isPresent();

        final FileGroupQueueMessage message = outItem.get().getMessage();
        assertThat(message.producingStage()).isEqualTo("aggregate");
        assertThat(message.producerId()).isEqualTo("agg-node");
        assertThat(message.fileStoreLocation().storeName()).isEqualTo("fwdStore");

        // Verify the output store has the merged content.
        final Path stablePath = outputStore.resolve(message.fileStoreLocation());
        assertThat(stablePath.resolve("proxy.meta")).hasContent("merged-meta");
        assertThat(stablePath.resolve("proxy.zip")).hasContent("merged-zip");

        outItem.get().acknowledge();
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

    /**
     * Write a simulated pre-aggregate output directory — contains numbered
     * file-group subdirectories like the PreAggregator produces.
     */
    private FileStoreLocation writeAggregateDir(final LocalFileStore store) throws IOException {
        try (final FileStoreWrite write = store.newWrite()) {
            final Path sub1 = write.getPath().resolve("0000000001");
            Files.createDirectories(sub1);
            Files.writeString(sub1.resolve("proxy.meta"), "meta-1");
            Files.writeString(sub1.resolve("proxy.zip"), "zip-1");

            final Path sub2 = write.getPath().resolve("0000000002");
            Files.createDirectories(sub2);
            Files.writeString(sub2.resolve("proxy.meta"), "meta-2");
            Files.writeString(sub2.resolve("proxy.zip"), "zip-2");

            return write.commit();
        }
    }

    private FileGroupQueueItem createItem(final String queueName,
                                           final FileStoreLocation location) {
        final FileGroupQueueMessage message = FileGroupQueueMessage.create(
                queueName,
                "fg-" + java.util.UUID.randomUUID(),
                location,
                "preAggregate",
                "test-node",
                null,
                Map.of());
        return new SimpleQueueItem(message);
    }

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
