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

package stroom.proxy.app.pipeline.stage.forward;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.proxy.app.handler.Destination;
import stroom.proxy.app.handler.FileGroup;
import stroom.proxy.app.handler.NullDestination;
import stroom.proxy.app.handler.RecordingDestination;
import stroom.proxy.app.pipeline.queue.FileGroupQueue;
import stroom.proxy.app.pipeline.queue.FileGroupQueueItem;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessage;
import stroom.proxy.app.pipeline.queue.local.LocalFileGroupQueue;
import stroom.proxy.app.pipeline.runtime.FileStoreRegistry;
import stroom.proxy.app.pipeline.stage.FileGroupQueueWorker;
import stroom.proxy.app.pipeline.store.FileGroupNotFoundException;
import stroom.proxy.app.pipeline.store.FileStore;
import stroom.proxy.app.pipeline.store.FileStoreLocation;
import stroom.proxy.app.pipeline.store.FileStoreWrite;
import stroom.proxy.app.pipeline.store.filesystem.FilesystemFileStore;
import stroom.test.common.util.test.StroomUnitTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestFanOutStage extends StroomUnitTest {

    private static final ForwardBounds LENIENT = new ForwardBounds(
            Duration.ofDays(7), Duration.ZERO, 1, Duration.ZERO);

    private Path root;
    private FilesystemFileStore inputStore;
    private LocalFileGroupQueue inputQueue;
    private FilesystemFileStore storeA;
    private FilesystemFileStore storeB;
    private LocalFileGroupQueue queueA;
    private LocalFileGroupQueue queueB;
    private FileStoreRegistry registry;

    @BeforeEach
    void setUp() throws IOException {
        root = getCurrentTestDir();
        inputStore = new FilesystemFileStore("aggregateStore", root.resolve("aggregate-store"));
        inputQueue = new LocalFileGroupQueue("forwardingInput", Files.createDirectories(root.resolve("in")));
        storeA = new FilesystemFileStore(FanOutStage.storeNameFor("a"), root.resolve("store-a"));
        storeB = new FilesystemFileStore(FanOutStage.storeNameFor("b"), root.resolve("store-b"));
        queueA = new LocalFileGroupQueue(FanOutStage.queueNameFor("a"), Files.createDirectories(root.resolve("q-a")));
        queueB = new LocalFileGroupQueue(FanOutStage.queueNameFor("b"), Files.createDirectories(root.resolve("q-b")));
        registry = new FileStoreRegistry(List.of(inputStore, storeA, storeB));
    }

    @Test
    void testEachDestinationGetsItsOwnCopyAndMessageAndTheInputGoes() throws Exception {
        final FileStoreLocation input = publish();
        final FanOutStage stage = new FanOutStage(registry, List.of(
                new FanOutStage.Target("a", storeA, queueA),
                new FanOutStage.Target("b", storeB, queueB)), "node");

        assertThat(new FileGroupQueueWorker(inputQueue, stage).processNext().isProcessed()).isTrue();

        assertThatThrownBy(() -> inputStore.resolve(input)).isInstanceOf(FileGroupNotFoundException.class);
        assertThat(inputQueue.getApproximatePendingCount()).isZero();
        assertThat(inputQueue.getApproximateInFlightCount()).isZero();
        for (final var pair : List.of(Map.entry(queueA, storeA), Map.entry(queueB, storeB))) {
            final List<FileGroupQueueMessage> messages = drain(pair.getKey());
            assertThat(messages).hasSize(1);
            final FileGroupQueueMessage copy = messages.getFirst();
            assertThat(copy.feed()).isEqualTo("TEST_FEED");
            assertThat(copy.producingStage()).isEqualTo("forward");
            assertThat(copy.attributes()).containsEntry(FanOutStage.DESTINATION_ATTRIBUTE,
                    pair.getValue().getName().substring("forward-".length()));
            assertThat(copy.fileStoreLocation().storeName()).isEqualTo(pair.getValue().getName());
            final Path dir = pair.getValue().resolve(copy.fileStoreLocation());
            new FileGroup(dir).requireComplete("copy");
            assertThat(dir.resolve("proxy.zip")).hasContent("zip");
        }
    }

    @Test
    void testCopiesArePublishedBeforeTheInputIsDeleted() throws Exception {
        final List<String> calls = new ArrayList<>();
        final FileStore recordingInput = recording(inputStore, calls);
        final FanOutStage stage = new FanOutStage(new FileStoreRegistry(List.of(recordingInput, storeA, storeB)),
                List.of(new FanOutStage.Target("a", storeA, recording(queueA, calls)),
                        new FanOutStage.Target("b", storeB, recording(queueB, calls))), "node");
        publish();

        new FileGroupQueueWorker(inputQueue, stage).processNext();

        assertThat(calls).containsExactly("publish " + queueA.getName(), "publish " + queueB.getName(), "delete");
    }

    @Test
    void testADestinationThatIsDownDelaysNeitherTheOtherNorTheFanOut() throws Exception {
        final FanOutStage fanOut = new FanOutStage(registry, List.of(
                new FanOutStage.Target("a", storeA, queueA),
                new FanOutStage.Target("b", storeB, queueB)), "node");
        final List<Path> deliveredToA = new ArrayList<>();
        final ForwardStage stageA = new ForwardStage(registry, new RecordingDestination(deliveredToA),
                new GiveUp(new NullDestination()), LENIENT, () -> false);
        final ForwardStage stageB = new ForwardStage(registry, new Destination() {
            @Override
            public String getName() {
                return "b";
            }

            @Override
            public String getDescription() {
                return "down";
            }

            @Override
            public void deliver(final Path group) throws IOException {
                throw new IOException("b is down");
            }
        }, new GiveUp(new NullDestination()), LENIENT, () -> false);
        final FileGroupQueueWorker workerA = new FileGroupQueueWorker(queueA, stageA);
        final FileGroupQueueWorker workerB = new FileGroupQueueWorker(queueB, stageB);

        for (int i = 0; i < 3; i++) {
            publish();
            assertThat(new FileGroupQueueWorker(inputQueue, fanOut).processNext().isProcessed()).isTrue();
            assertThat(workerA.processNext().isProcessed()).isTrue();
            assertThat(workerB.processNext().isFailed()).isTrue();
        }

        assertThat(deliveredToA).as("every group delivered to the healthy destination exactly once").hasSize(3);
        assertThat(queueA.getApproximatePendingCount()).isZero();
        assertThat(queueB.getApproximatePendingCount()).as("the down destination's queue fills").isEqualTo(3);
        assertThat(inputQueue.getApproximatePendingCount()).isZero();
    }

    @Test
    void testFanOutNeedsAtLeastTwoDestinations() {
        assertThatThrownBy(() -> new FanOutStage(
                registry, List.of(new FanOutStage.Target("a", storeA, queueA)), "node"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --------------------------------------------------------------------------------

    private FileStoreLocation publish() throws IOException {
        final FileStoreLocation location;
        try (final FileStoreWrite write = inputStore.newWrite()) {
            final FileGroup group = new FileGroup(write.getPath());
            final AttributeMap meta = new AttributeMap();
            AttributeMapUtil.addFeedAndType(meta, "TEST_FEED", "Raw Events");
            AttributeMapUtil.write(meta, group.getMeta());
            Files.writeString(group.getZip(), "zip");
            Files.writeString(group.getEntries(), "");
            location = write.commit();
        }
        inputQueue.publish(FileGroupQueueMessage.create(
                location, "TEST_FEED", "Raw Events", "aggregate", "node", "trace", Map.of()));
        return location;
    }

    private static List<FileGroupQueueMessage> drain(final LocalFileGroupQueue queue) throws IOException {
        final List<FileGroupQueueMessage> messages = new ArrayList<>();
        Optional<FileGroupQueueItem> item;
        while ((item = queue.next()).isPresent()) {
            messages.add(item.get().getMessage());
            item.get().acknowledge();
        }
        return messages;
    }

    private static FileStore recording(final FileStore delegate, final List<String> calls) {
        return new FileStore() {
            @Override
            public String getName() {
                return delegate.getName();
            }

            @Override
            public FileStoreWrite newWrite() throws IOException {
                return delegate.newWrite();
            }

            @Override
            public Path resolve(final FileStoreLocation location) throws IOException {
                return delegate.resolve(location);
            }

            @Override
            public void delete(final FileStoreLocation location) throws IOException {
                calls.add("delete");
                delegate.delete(location);
            }
        };
    }

    private static FileGroupQueue recording(final FileGroupQueue delegate, final List<String> calls) {
        return new FileGroupQueue() {
            @Override
            public String getName() {
                return delegate.getName();
            }

            @Override
            public stroom.proxy.app.pipeline.queue.QueueType getType() {
                return delegate.getType();
            }

            @Override
            public void publish(final FileGroupQueueMessage message) throws IOException {
                calls.add("publish " + delegate.getName());
                delegate.publish(message);
            }

            @Override
            public Optional<FileGroupQueueItem> next(final Duration maxWait) throws IOException {
                return delegate.next(maxWait);
            }

            @Override
            public void close() throws IOException {
                delegate.close();
            }
        };
    }
}
