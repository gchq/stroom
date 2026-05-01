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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestForwardStageProcessor extends StroomUnitTest {

    private static final String STORE_NAME = "aggregateStore";
    private static final String QUEUE_NAME = ProxyPipelineConfig.FORWARDING_INPUT_QUEUE;
    private static final String FILE_GROUP_ID = "file-group-1";
    private static final Instant CREATED_TIME = Instant.parse("2025-01-02T03:04:05Z");

    @Test
    void testFileStoreRegistryResolvesMessageLocation() throws IOException {
        final LocalFileStore fileStore = new LocalFileStore(
                STORE_NAME,
                getCurrentTestDir().resolve("aggregate-store"),
                "writer-1");
        final FileStoreLocation location = writeFileGroup(fileStore);
        final FileGroupQueueMessage message = createMessage(location);

        final FileStoreRegistry registry = new FileStoreRegistry()
                .register(fileStore);

        final Path resolvedPath = registry.resolve(message);

        assertThat(resolvedPath).isEqualTo(fileStore.resolve(location));
        assertThat(resolvedPath).exists().isDirectory();
        assertThat(resolvedPath.resolve("proxy.meta")).hasContent("meta");
        assertThat(resolvedPath.resolve("proxy.zip")).hasContent("zip");
        assertThat(resolvedPath.resolve("proxy.entries")).hasContent("entries");
    }

    @Test
    void testFileStoreRegistryRejectsUnknownStore() throws IOException {
        final LocalFileStore fileStore = new LocalFileStore(
                STORE_NAME,
                getCurrentTestDir().resolve("aggregate-store"),
                "writer-1");
        final FileStoreLocation location = writeFileGroup(fileStore);
        final FileGroupQueueMessage message = createMessage(location);

        final FileStoreRegistry registry = new FileStoreRegistry();

        assertThatThrownBy(() -> registry.resolve(message))
                .isInstanceOf(IOException.class)
                .hasMessageContaining(STORE_NAME);
    }

    @Test
    void testFileStoreRegistryRejectsMismatchedMapKey() {
        final LocalFileStore fileStore = new LocalFileStore(
                STORE_NAME,
                getCurrentTestDir().resolve("aggregate-store"),
                "writer-1");

        assertThatThrownBy(() -> new FileStoreRegistry(Map.of("wrongStore", fileStore)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("wrongStore")
                .hasMessageContaining(STORE_NAME);
    }

    @Test
    void testForwardStageProcessorResolvesFileGroupAndDelegates() throws Exception {
        final LocalFileStore fileStore = new LocalFileStore(
                STORE_NAME,
                getCurrentTestDir().resolve("aggregate-store"),
                "writer-1");
        final FileStoreLocation location = writeFileGroup(fileStore);
        final FileGroupQueueMessage message = createMessage(location);
        final FileStoreRegistry registry = new FileStoreRegistry()
                .register(fileStore);

        final AtomicReference<FileGroupQueueMessage> forwardedMessage = new AtomicReference<>();
        final AtomicReference<Path> forwardedPath = new AtomicReference<>();

        final ForwardStageProcessor processor = new ForwardStageProcessor(
                registry,
                (msg, fileGroupDir) -> {
                    forwardedMessage.set(msg);
                    forwardedPath.set(fileGroupDir);
                });

        processor.process(new TestQueueItem(message));

        assertThat(forwardedMessage).hasValue(message);
        assertThat(forwardedPath).hasValue(fileStore.resolve(location));
    }

    @Test
    void testForwardStageProcessorFailsBeforeForwardingWhenStoreIsUnknown() throws IOException {
        final LocalFileStore fileStore = new LocalFileStore(
                STORE_NAME,
                getCurrentTestDir().resolve("aggregate-store"),
                "writer-1");
        final FileStoreLocation location = writeFileGroup(fileStore);
        final FileGroupQueueMessage message = createMessage(location);
        final AtomicBoolean forwarderCalled = new AtomicBoolean(false);

        final ForwardStageProcessor processor = new ForwardStageProcessor(
                new FileStoreRegistry(),
                (msg, fileGroupDir) -> forwarderCalled.set(true));

        assertThatThrownBy(() -> processor.process(new TestQueueItem(message)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining(STORE_NAME);

        assertThat(forwarderCalled).isFalse();
    }

    @Test
    void testForwardStageProcessorFailsBeforeForwardingWhenFileGroupDirectoryIsMissing() {
        final FileStoreLocation location = FileStoreLocation.localFileSystem(
                STORE_NAME,
                getCurrentTestDir()
                        .resolve("aggregate-store")
                        .resolve("writer-1")
                        .resolve("0000000001"));
        final FileGroupQueueMessage message = createMessage(location);
        final LocalFileStore fileStore = new LocalFileStore(
                STORE_NAME,
                getCurrentTestDir().resolve("aggregate-store"),
                "writer-1");
        final AtomicBoolean forwarderCalled = new AtomicBoolean(false);

        final ForwardStageProcessor processor = new ForwardStageProcessor(
                new FileStoreRegistry().register(fileStore),
                (msg, fileGroupDir) -> forwarderCalled.set(true));

        assertThatThrownBy(() -> processor.process(new TestQueueItem(message)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("not a directory")
                .hasMessageContaining(FILE_GROUP_ID);

        assertThat(forwarderCalled).isFalse();
    }

    @Test
    void testForwardStageProcessorFailsBeforeForwardingWhenMetaFileIsMissing() throws IOException {
        final LocalFileStore fileStore = new LocalFileStore(
                STORE_NAME,
                getCurrentTestDir().resolve("aggregate-store"),
                "writer-1");
        final FileStoreLocation location = writeFileGroup(fileStore, false, true, true);
        final FileGroupQueueMessage message = createMessage(location);
        final AtomicBoolean forwarderCalled = new AtomicBoolean(false);

        final ForwardStageProcessor processor = new ForwardStageProcessor(
                new FileStoreRegistry().register(fileStore),
                (msg, fileGroupDir) -> forwarderCalled.set(true));

        assertThatThrownBy(() -> processor.process(new TestQueueItem(message)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("meta")
                .hasMessageContaining(FILE_GROUP_ID);

        assertThat(forwarderCalled).isFalse();
    }

    @Test
    void testForwardStageProcessorFailsBeforeForwardingWhenZipFileIsMissing() throws IOException {
        final LocalFileStore fileStore = new LocalFileStore(
                STORE_NAME,
                getCurrentTestDir().resolve("aggregate-store"),
                "writer-1");
        final FileStoreLocation location = writeFileGroup(fileStore, true, false, true);
        final FileGroupQueueMessage message = createMessage(location);
        final AtomicBoolean forwarderCalled = new AtomicBoolean(false);

        final ForwardStageProcessor processor = new ForwardStageProcessor(
                new FileStoreRegistry().register(fileStore),
                (msg, fileGroupDir) -> forwarderCalled.set(true));

        assertThatThrownBy(() -> processor.process(new TestQueueItem(message)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("zip")
                .hasMessageContaining(FILE_GROUP_ID);

        assertThat(forwarderCalled).isFalse();
    }

    @Test
    void testForwardStageProcessorFailsBeforeForwardingWhenEntriesFileIsMissing() throws IOException {
        final LocalFileStore fileStore = new LocalFileStore(
                STORE_NAME,
                getCurrentTestDir().resolve("aggregate-store"),
                "writer-1");
        final FileStoreLocation location = writeFileGroup(fileStore, true, true, false);
        final FileGroupQueueMessage message = createMessage(location);
        final AtomicBoolean forwarderCalled = new AtomicBoolean(false);

        final ForwardStageProcessor processor = new ForwardStageProcessor(
                new FileStoreRegistry().register(fileStore),
                (msg, fileGroupDir) -> forwarderCalled.set(true));

        assertThatThrownBy(() -> processor.process(new TestQueueItem(message)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("entries")
                .hasMessageContaining(FILE_GROUP_ID);

        assertThat(forwarderCalled).isFalse();
    }

    @Test
    void testForwardStageProcessorPropagatesForwarderFailure() throws IOException {
        final LocalFileStore fileStore = new LocalFileStore(
                STORE_NAME,
                getCurrentTestDir().resolve("aggregate-store"),
                "writer-1");
        final FileStoreLocation location = writeFileGroup(fileStore);
        final FileGroupQueueMessage message = createMessage(location);
        final RuntimeException forwardFailure = new RuntimeException("forward failed");

        final ForwardStageProcessor processor = new ForwardStageProcessor(
                new FileStoreRegistry().register(fileStore),
                (msg, fileGroupDir) -> {
                    throw forwardFailure;
                });

        assertThatThrownBy(() -> processor.process(new TestQueueItem(message)))
                .isSameAs(forwardFailure);
    }

    @Test
    void testForwardFanOutCopiesSourceToDestinationStoresAndPublishesMessages() throws Exception {
        final LocalFileStore sourceFileStore = new LocalFileStore(
                STORE_NAME,
                getCurrentTestDir().resolve("aggregate-store"),
                "source-writer");
        final FileStoreLocation sourceLocation = writeFileGroup(sourceFileStore);
        final FileGroupQueueMessage sourceMessage = createMessage(sourceLocation);
        final Path sourcePath = sourceFileStore.resolve(sourceLocation);

        final LocalFileStore destinationStoreOne = new LocalFileStore(
                "forwardStoreOne",
                getCurrentTestDir().resolve("forward-store-one"),
                "destination-writer-one");
        final LocalFileStore destinationStoreTwo = new LocalFileStore(
                "forwardStoreTwo",
                getCurrentTestDir().resolve("forward-store-two"),
                "destination-writer-two");
        final LocalFileGroupQueue destinationQueueOne = new LocalFileGroupQueue(
                "forwardQueueOne",
                getCurrentTestDir().resolve("forward-queue-one"));
        final LocalFileGroupQueue destinationQueueTwo = new LocalFileGroupQueue(
                "forwardQueueTwo",
                getCurrentTestDir().resolve("forward-queue-two"));

        final ForwardStageFanOutForwarder fanOutForwarder = new ForwardStageFanOutForwarder(
                List.of(
                        new ForwardStageFanOutForwarder.Destination(
                                "destination-one",
                                destinationStoreOne,
                                destinationQueueOne),
                        new ForwardStageFanOutForwarder.Destination(
                                "destination-two",
                                destinationStoreTwo,
                                destinationQueueTwo)),
                "proxy-node-1");

        fanOutForwarder.forward(sourceMessage, sourcePath);

        assertThat(sourcePath).exists().isDirectory();
        assertThat(sourcePath.resolve("proxy.meta")).hasContent("meta");
        assertThat(sourcePath.resolve("proxy.zip")).hasContent("zip");
        assertThat(sourcePath.resolve("proxy.entries")).hasContent("entries");

        assertThat(destinationQueueOne.getApproximatePendingCount()).isOne();
        assertThat(destinationQueueTwo.getApproximatePendingCount()).isOne();

        try (final FileGroupQueueItem item = destinationQueueOne.next().orElseThrow()) {
            final FileGroupQueueMessage destinationMessage = item.getMessage();
            final Path destinationPath = destinationStoreOne.resolve(destinationMessage.fileStoreLocation());

            assertThat(destinationMessage.queueName()).isEqualTo("forwardQueueOne");
            assertThat(destinationMessage.fileGroupId()).isEqualTo(sourceMessage.fileGroupId());
            assertThat(destinationMessage.traceId()).isEqualTo(sourceMessage.traceId());
            assertThat(destinationMessage.attributes())
                    .containsEntry("feed", "TEST_FEED")
                    .containsEntry(ForwardStageFanOutForwarder.ATTRIBUTE_FORWARD_DESTINATION, "destination-one")
                    .containsEntry(ForwardStageFanOutForwarder.ATTRIBUTE_SOURCE_MESSAGE_ID, sourceMessage.messageId())
                    .containsEntry(ForwardStageFanOutForwarder.ATTRIBUTE_SOURCE_QUEUE_NAME, sourceMessage.queueName());
            assertThat(destinationPath).exists().isDirectory();
            assertThat(destinationPath).isNotEqualTo(sourcePath);
            assertThat(destinationPath.resolve("proxy.meta")).hasContent("meta");
            assertThat(destinationPath.resolve("proxy.zip")).hasContent("zip");
            assertThat(destinationPath.resolve("proxy.entries")).hasContent("entries");
        }

        try (final FileGroupQueueItem item = destinationQueueTwo.next().orElseThrow()) {
            final FileGroupQueueMessage destinationMessage = item.getMessage();
            final Path destinationPath = destinationStoreTwo.resolve(destinationMessage.fileStoreLocation());

            assertThat(destinationMessage.queueName()).isEqualTo("forwardQueueTwo");
            assertThat(destinationMessage.fileGroupId()).isEqualTo(sourceMessage.fileGroupId());
            assertThat(destinationMessage.traceId()).isEqualTo(sourceMessage.traceId());
            assertThat(destinationMessage.attributes())
                    .containsEntry("feed", "TEST_FEED")
                    .containsEntry(ForwardStageFanOutForwarder.ATTRIBUTE_FORWARD_DESTINATION, "destination-two")
                    .containsEntry(ForwardStageFanOutForwarder.ATTRIBUTE_SOURCE_MESSAGE_ID, sourceMessage.messageId())
                    .containsEntry(ForwardStageFanOutForwarder.ATTRIBUTE_SOURCE_QUEUE_NAME, sourceMessage.queueName());
            assertThat(destinationPath).exists().isDirectory();
            assertThat(destinationPath).isNotEqualTo(sourcePath);
            assertThat(destinationPath.resolve("proxy.meta")).hasContent("meta");
            assertThat(destinationPath.resolve("proxy.zip")).hasContent("zip");
            assertThat(destinationPath.resolve("proxy.entries")).hasContent("entries");
        }
    }

    @Test
    void testForwardStageProcessorCanUseFanOutForwarderForHandoff() throws Exception {
        final LocalFileStore sourceFileStore = new LocalFileStore(
                STORE_NAME,
                getCurrentTestDir().resolve("aggregate-store"),
                "source-writer");
        final FileStoreLocation sourceLocation = writeFileGroup(sourceFileStore);
        final FileGroupQueueMessage sourceMessage = createMessage(sourceLocation);
        final LocalFileStore destinationStore = new LocalFileStore(
                "forwardStore",
                getCurrentTestDir().resolve("forward-store"),
                "destination-writer");
        final LocalFileGroupQueue destinationQueue = new LocalFileGroupQueue(
                "forwardQueue",
                getCurrentTestDir().resolve("forward-queue"));

        final ForwardStageProcessor processor = new ForwardStageProcessor(
                new FileStoreRegistry().register(sourceFileStore),
                new ForwardStageFanOutForwarder(
                        List.of(new ForwardStageFanOutForwarder.Destination(
                                "destination",
                                destinationStore,
                                destinationQueue)),
                        "proxy-node-1"));

        processor.process(new TestQueueItem(sourceMessage));

        assertThat(destinationQueue.getApproximatePendingCount()).isOne();

        try (final FileGroupQueueItem item = destinationQueue.next().orElseThrow()) {
            final FileGroupQueueMessage destinationMessage = item.getMessage();
            final Path destinationPath = destinationStore.resolve(destinationMessage.fileStoreLocation());

            assertThat(destinationMessage.queueName()).isEqualTo("forwardQueue");
            assertThat(destinationMessage.attributes())
                    .containsEntry(ForwardStageFanOutForwarder.ATTRIBUTE_FORWARD_DESTINATION, "destination")
                    .containsEntry(ForwardStageFanOutForwarder.ATTRIBUTE_SOURCE_MESSAGE_ID, sourceMessage.messageId())
                    .containsEntry(ForwardStageFanOutForwarder.ATTRIBUTE_SOURCE_QUEUE_NAME, sourceMessage.queueName());
            assertThat(destinationPath.resolve("proxy.meta")).hasContent("meta");
            assertThat(destinationPath.resolve("proxy.zip")).hasContent("zip");
            assertThat(destinationPath.resolve("proxy.entries")).hasContent("entries");
        }
    }

    @Test
    void testForwardFanOutRequiresAtLeastOneDestination() {
        assertThatThrownBy(() -> new ForwardStageFanOutForwarder(List.of(), "proxy-node-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one forward destination");
    }

    @Test
    void testForwardFanOutDeletesOriginalSourceWhenInputStoreProvided() throws Exception {
        final LocalFileStore sourceFileStore = new LocalFileStore(
                STORE_NAME,
                getCurrentTestDir().resolve("aggregate-store-del"),
                "source-writer");
        final FileStoreLocation sourceLocation = writeFileGroup(sourceFileStore);
        final FileGroupQueueMessage sourceMessage = createMessage(sourceLocation);
        final Path sourcePath = sourceFileStore.resolve(sourceLocation);

        // Confirm source exists before fan-out.
        assertThat(sourcePath).exists().isDirectory();

        final LocalFileStore destinationStore = new LocalFileStore(
                "forwardStoreDel",
                getCurrentTestDir().resolve("forward-store-del"),
                "destination-writer");
        final LocalFileGroupQueue destinationQueue = new LocalFileGroupQueue(
                "forwardQueueDel",
                getCurrentTestDir().resolve("forward-queue-del"));

        // Provide inputFileStore so fan-out deletes the original source.
        final ForwardStageFanOutForwarder fanOutForwarder = new ForwardStageFanOutForwarder(
                List.of(new ForwardStageFanOutForwarder.Destination(
                        "destination",
                        destinationStore,
                        destinationQueue)),
                sourceFileStore,
                "proxy-node-1");

        fanOutForwarder.forward(sourceMessage, sourcePath);

        // Original source should have been deleted by the fan-out forwarder.
        assertThat(sourcePath).doesNotExist();

        // But the destination copy should still exist.
        assertThat(destinationQueue.getApproximatePendingCount()).isOne();
        try (final FileGroupQueueItem item = destinationQueue.next().orElseThrow()) {
            final Path destPath = destinationStore.resolve(item.getMessage().fileStoreLocation());
            assertThat(destPath).exists().isDirectory();
            assertThat(destPath.resolve("proxy.meta")).hasContent("meta");
            assertThat(destPath.resolve("proxy.zip")).hasContent("zip");
            assertThat(destPath.resolve("proxy.entries")).hasContent("entries");
            item.acknowledge();
        }
    }

    @Test
    void testForwardFanOutKeepsOriginalSourceWhenNoInputStore() throws Exception {
        final LocalFileStore sourceFileStore = new LocalFileStore(
                STORE_NAME,
                getCurrentTestDir().resolve("aggregate-store-keep"),
                "source-writer");
        final FileStoreLocation sourceLocation = writeFileGroup(sourceFileStore);
        final FileGroupQueueMessage sourceMessage = createMessage(sourceLocation);
        final Path sourcePath = sourceFileStore.resolve(sourceLocation);

        final LocalFileStore destinationStore = new LocalFileStore(
                "forwardStoreKeep",
                getCurrentTestDir().resolve("forward-store-keep"),
                "destination-writer");
        final LocalFileGroupQueue destinationQueue = new LocalFileGroupQueue(
                "forwardQueueKeep",
                getCurrentTestDir().resolve("forward-queue-keep"));

        // No inputFileStore — original source should be kept.
        final ForwardStageFanOutForwarder fanOutForwarder = new ForwardStageFanOutForwarder(
                List.of(new ForwardStageFanOutForwarder.Destination(
                        "destination",
                        destinationStore,
                        destinationQueue)),
                "proxy-node-1");

        fanOutForwarder.forward(sourceMessage, sourcePath);

        // Original source should still exist — no inputFileStore provided.
        assertThat(sourcePath).exists().isDirectory();
        assertThat(sourcePath.resolve("proxy.meta")).hasContent("meta");
    }

    private FileStoreLocation writeFileGroup(final FileStore fileStore) throws IOException {
        return writeFileGroup(fileStore, true, true, true);
    }

    private FileStoreLocation writeFileGroup(final FileStore fileStore,
                                             final boolean includeMeta,
                                             final boolean includeZip,
                                             final boolean includeEntries) throws IOException {
        try (final FileStoreWrite write = fileStore.newWrite()) {
            if (includeMeta) {
                Files.writeString(write.getPath().resolve("proxy.meta"), "meta");
            }
            if (includeZip) {
                Files.writeString(write.getPath().resolve("proxy.zip"), "zip");
            }
            if (includeEntries) {
                Files.writeString(write.getPath().resolve("proxy.entries"), "entries");
            }
            return write.commit();
        }
    }

    private FileGroupQueueMessage createMessage(final FileStoreLocation location) {
        return FileGroupQueueMessage.create(
                "message-" + FILE_GROUP_ID,
                QUEUE_NAME,
                FILE_GROUP_ID,
                location,
                "aggregate",
                "proxy-node-1",
                CREATED_TIME,
                "trace-" + FILE_GROUP_ID,
                Map.of("feed", "TEST_FEED"));
    }

    private record TestQueueItem(FileGroupQueueMessage message) implements FileGroupQueueItem {

        @Override
        public String getId() {
            return "item-" + message.messageId();
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
