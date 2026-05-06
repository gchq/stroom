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
import stroom.proxy.app.pipeline.runtime.PipelineStageName;
import stroom.proxy.app.pipeline.stage.aggregate.AggregateClosePublisher;
import stroom.proxy.app.pipeline.stage.preaggregate.PreAggregateStageProcessor;
import stroom.proxy.app.pipeline.store.FileStoreLocation;
import stroom.proxy.app.pipeline.store.FileStoreWrite;
import stroom.proxy.app.pipeline.store.local.LocalFileStore;
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

class TestPreAggregateStageProcessor extends StroomUnitTest {

    private static final String INPUT_STORE = "splitStore";

    @Test
    void testDelegatesToPreAggregateFunctionAndDeletesInput() throws Exception {
        final LocalFileStore inputStore = createFileStore(INPUT_STORE, "input-store");
        final FileStoreRegistry registry = new FileStoreRegistry(List.of(inputStore));

        final FileStoreLocation inputLocation = writeFileGroup(inputStore, "test-data");
        final Path resolvedPath = inputStore.resolve(inputLocation);

        // Verify the input exists before processing.
        assertThat(resolvedPath).exists().isDirectory();

        // Capture the state as seen by the pre-aggregate function during execution.
        final List<Boolean> dirExistedDuringCall = new ArrayList<>();
        final List<Boolean> metaExistedDuringCall = new ArrayList<>();
        final PreAggregateStageProcessor.PreAggregateFunction stubFunction = dir -> {
            dirExistedDuringCall.add(Files.isDirectory(dir));
            metaExistedDuringCall.add(Files.isRegularFile(dir.resolve("proxy.meta")));
        };

        final PreAggregateStageProcessor processor = new PreAggregateStageProcessor(
                registry, stubFunction);

        final FileGroupQueueItem item = createItem("queue", inputLocation);
        processor.process(item);

        // The function should have been called with a valid directory.
        assertThat(dirExistedDuringCall).containsExactly(true);
        assertThat(metaExistedDuringCall).containsExactly(true);

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

        final PreAggregateStageProcessor processor = new PreAggregateStageProcessor(
                registry, dir -> {});

        final FileGroupQueueItem item = createItem("queue", fakeLocation);

        assertThatThrownBy(() -> processor.process(item))
                .isInstanceOf(IOException.class);
    }

    @Test
    void testAggregateClosePublisherPublishesToQueue() throws IOException {
        final LocalFileStore outputStore = createFileStore("aggregateStore", "agg-store");
        final LocalFileGroupQueue outputQueue = createQueue("agg-output");

        final AggregateClosePublisher publisher = new AggregateClosePublisher(
                outputStore, outputQueue, PipelineStageName.PRE_AGGREGATE, "test-node");

        // Create a mock aggregate directory with file-group subdirectories.
        final Path aggDir = getCurrentTestDir().resolve("closed-aggregate");
        Files.createDirectories(aggDir);
        // A closed aggregate from PreAggregator contains numbered subdirectories,
        // each being a file group.
        final Path subGroup1 = aggDir.resolve("0000000001");
        Files.createDirectories(subGroup1);
        Files.writeString(subGroup1.resolve("proxy.meta"), "meta-1");
        Files.writeString(subGroup1.resolve("proxy.zip"), "zip-1");

        final Path subGroup2 = aggDir.resolve("0000000002");
        Files.createDirectories(subGroup2);
        Files.writeString(subGroup2.resolve("proxy.meta"), "meta-2");
        Files.writeString(subGroup2.resolve("proxy.zip"), "zip-2");

        publisher.accept(aggDir);

        // Source aggregate dir should be deleted.
        assertThat(aggDir).doesNotExist();

        // Queue should have one message.
        final var item = outputQueue.next();
        assertThat(item).isPresent();

        final FileGroupQueueMessage message = item.get().getMessage();
        assertThat(message.producingStage()).isEqualTo("preAggregate");
        assertThat(message.producerId()).isEqualTo("test-node");
        assertThat(message.fileStoreLocation().storeName()).isEqualTo("aggregateStore");

        // Output store should contain the aggregate with its subdirectories.
        final Path stablePath = outputStore.resolve(message.fileStoreLocation());
        assertThat(stablePath).exists().isDirectory();
        assertThat(stablePath.resolve("0000000001").resolve("proxy.meta")).hasContent("meta-1");
        assertThat(stablePath.resolve("0000000002").resolve("proxy.meta")).hasContent("meta-2");

        item.get().acknowledge();
    }

    @Test
    void testAggregateClosePublisherReusableForAggregateStage() throws IOException {
        final LocalFileStore outputStore = createFileStore("fwdStore", "fwd-store");
        final LocalFileGroupQueue outputQueue = createQueue("fwd-output");

        // Use AGGREGATE stage name instead of PRE_AGGREGATE.
        final AggregateClosePublisher publisher = new AggregateClosePublisher(
                outputStore, outputQueue, PipelineStageName.AGGREGATE, "agg-node");

        final Path aggDir = getCurrentTestDir().resolve("final-aggregate");
        Files.createDirectories(aggDir);
        Files.writeString(aggDir.resolve("proxy.meta"), "final-meta");
        Files.writeString(aggDir.resolve("proxy.zip"), "final-zip");

        publisher.accept(aggDir);

        final var item = outputQueue.next();
        assertThat(item).isPresent();
        assertThat(item.get().getMessage().producingStage()).isEqualTo("aggregate");
        assertThat(item.get().getMessage().producerId()).isEqualTo("agg-node");

        item.get().acknowledge();
    }

    @Test
    void testAggregateClosePublisherRejectsNull() {
        final AggregateClosePublisher publisher = new AggregateClosePublisher(
                createFileStore("s", "s"),
                createQueueUnchecked("q"),
                PipelineStageName.PRE_AGGREGATE,
                "node");

        assertThatThrownBy(() -> publisher.accept(null))
                .isInstanceOf(NullPointerException.class);
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

    private LocalFileGroupQueue createQueueUnchecked(final String name) {
        try {
            return createQueue(name);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
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

    private FileGroupQueueItem createItem(final String queueName,
                                           final FileStoreLocation location) {
        final FileGroupQueueMessage message = FileGroupQueueMessage.create(
                queueName,
                "fg-" + java.util.UUID.randomUUID(),
                location,
                "splitZip",
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
