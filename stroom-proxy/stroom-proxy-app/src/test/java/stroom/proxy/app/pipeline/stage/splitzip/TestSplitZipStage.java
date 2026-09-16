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

package stroom.proxy.app.pipeline.stage.splitzip;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.app.handler.FileGroup;
import stroom.proxy.app.handler.TestDataUtil;
import stroom.proxy.app.handler.ZipEntryGroup;
import stroom.proxy.app.pipeline.queue.FileGroupQueueItem;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessage;
import stroom.proxy.app.pipeline.queue.local.LocalFileGroupQueue;
import stroom.proxy.app.pipeline.runtime.FileStoreRegistry;
import stroom.proxy.app.pipeline.store.FileGroupNotFoundException;
import stroom.proxy.app.pipeline.store.FileStoreLocation;
import stroom.proxy.app.pipeline.store.FileStoreWrite;
import stroom.proxy.app.pipeline.store.filesystem.FilesystemFileStore;
import stroom.proxy.repo.FeedKeyInterner;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.shared.FeedKey;
import stroom.util.zip.ZipUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The split-zip contract, S1 to S5 ({@code designs/stages/split-zip.md}).
 */
class TestSplitZipStage extends StroomUnitTest {

    private static final FeedKey FEED_A = FeedKey.of("FEED_A", "Raw Events");
    private static final FeedKey FEED_B = FeedKey.of("FEED_B", "Raw Events");
    private static final FeedKey FEED_C = FeedKey.of("FEED_C", "Context");

    private FilesystemFileStore inputStore;
    private FilesystemFileStore outputStore;
    private LocalFileGroupQueue inputQueue;
    private LocalFileGroupQueue outputQueue;
    private SplitZipStage stage;

    @BeforeEach
    void setUp() throws IOException {
        final Path dir = getCurrentTestDir();
        inputStore = new FilesystemFileStore("receiveStore", dir.resolve("receive-store"));
        outputStore = new FilesystemFileStore("splitStore", dir.resolve("split-store"));
        inputQueue = new LocalFileGroupQueue("splitZipInput", Files.createDirectories(dir.resolve("in")));
        outputQueue = new LocalFileGroupQueue("aggregateInput", Files.createDirectories(dir.resolve("out")));
        stage = new SplitZipStage(new FileStoreRegistry(List.of(inputStore)), outputStore, outputQueue, "node");
    }

    @Test
    void testEveryItemGoesToExactlyOneOutputInInputOrder() throws Exception {
        // Three feeds, two items each, interleaved A B C A B C in the input.
        final FileStoreLocation input = writeMultiFeedInput(2, FEED_A, FEED_B, FEED_C);
        final FileGroupQueueItem item = publishAndClaim(input);

        stage.process(item);
        item.acknowledge();

        final List<FileGroupQueueMessage> outputs = drain(outputQueue);
        assertThat(outputs).extracting(FileGroupQueueMessage::feed)
                .containsExactly("FEED_A", "FEED_B", "FEED_C");
        assertThat(outputs).extracting(FileGroupQueueMessage::type)
                .containsExactly("Raw Events", "Raw Events", "Context");
        assertThat(outputs).extracting(FileGroupQueueMessage::producingStage).containsOnly("splitZip");
        assertThat(outputs).extracting(FileGroupQueueMessage::traceId).containsOnly("trace-1");
        for (final FileGroupQueueMessage output : outputs) {
            final Path dir = outputStore.resolve(output.fileStoreLocation());
            assertCanonical(dir, 2, FeedKey.of(output.feed(), output.type()));
            final AttributeMap meta = new AttributeMap();
            AttributeMapUtil.read(new FileGroup(dir).getMeta(), meta);
            assertThat(meta.get("Environment")).as("the input's headers travel").isEqualTo("test");
        }
        assertThatThrownBy(() -> inputStore.resolve(input))
                .as("the input is deleted once every output is published")
                .isInstanceOf(FileGroupNotFoundException.class);
        assertThat(inputQueue.getApproximateInFlightCount()).isZero();
    }

    @Test
    void testASingleFeedInputYieldsOneOutput() throws Exception {
        final FileStoreLocation input = writeMultiFeedInput(3, FEED_A);
        final FileGroupQueueItem item = publishAndClaim(input);

        stage.process(item);

        final List<FileGroupQueueMessage> outputs = drain(outputQueue);
        assertThat(outputs).hasSize(1);
        assertCanonical(outputStore.resolve(outputs.getFirst().fileStoreLocation()), 3, FEED_A);
    }

    @Test
    void testCommitPublishThenDeleteInThatOrder() throws Exception {
        final List<String> calls = new ArrayList<>();
        final FilesystemFileStore recordingOutput = new FilesystemFileStore("splitStore",
                getCurrentTestDir().resolve("recording-store")) {
            @Override
            public FileStoreWrite newWrite() throws IOException {
                final FileStoreWrite delegate = super.newWrite();
                return new FileStoreWrite() {
                    @Override
                    public Path getPath() {
                        return delegate.getPath();
                    }

                    @Override
                    public FileStoreLocation commit() throws IOException {
                        calls.add("commit");
                        return delegate.commit();
                    }

                    @Override
                    public void close() throws IOException {
                        delegate.close();
                    }
                };
            }
        };
        final FilesystemFileStore recordingInput = new FilesystemFileStore("receiveStore",
                getCurrentTestDir().resolve("recording-input")) {
            @Override
            public void delete(final FileStoreLocation location) throws IOException {
                calls.add("delete");
                super.delete(location);
            }
        };
        final LocalFileGroupQueue recordingQueue = new LocalFileGroupQueue("aggregateInput",
                Files.createDirectories(getCurrentTestDir().resolve("recording-out"))) {
            @Override
            public void publish(final FileGroupQueueMessage message) throws IOException {
                calls.add("publish");
                super.publish(message);
            }
        };
        final SplitZipStage recordingStage = new SplitZipStage(
                new FileStoreRegistry(List.of(recordingInput)), recordingOutput, recordingQueue, "node");
        final FileStoreLocation input = writeMultiFeedInput(recordingInput, 1, FEED_A, FEED_B);

        recordingStage.process(publishAndClaim(input));

        assertThat(calls).containsExactly("commit", "publish", "commit", "publish", "delete");
    }

    /**
     * The delete of the input is housekeeping after the durable step - every output is committed and
     * published - so it must not throw (contracts §3.2). It used to: the worker then failed the
     * message and the redelivery re-split the input, publishing every output again, for as long as
     * the delete kept failing.
     */
    @Test
    void testADeleteThatFailsAfterEveryOutputIsPublishedDoesNotFailTheMessage() throws Exception {
        final FilesystemFileStore undeletableInput = new FilesystemFileStore("receiveStore",
                getCurrentTestDir().resolve("undeletable-input")) {
            @Override
            public void delete(final FileStoreLocation location) throws IOException {
                throw new IOException("the mount is away");
            }
        };
        final SplitZipStage undeletableStage = new SplitZipStage(
                new FileStoreRegistry(List.of(undeletableInput)), outputStore, outputQueue, "node");
        final FileStoreLocation input = writeMultiFeedInput(undeletableInput, 1, FEED_A, FEED_B);

        undeletableStage.process(publishAndClaim(input));

        assertThat(drain(outputQueue))
                .as("every output was published exactly once, and the worker will acknowledge")
                .hasSize(2);
        assertThat(undeletableInput.resolve(input))
                .as("the input is left for the store's sweep, not for a redelivery to re-split")
                .isDirectory();
    }

    @Test
    void testARedeliveredMessageWhoseInputIsGoneIsReportedAbsent() throws Exception {
        final FileStoreLocation input = writeMultiFeedInput(1, FEED_A, FEED_B);
        inputStore.delete(input);

        assertThatThrownBy(() -> stage.process(publishAndClaim(input)))
                .as("the worker acknowledges on this exception (R12)")
                .isInstanceOf(FileGroupNotFoundException.class);
    }

    @Test
    void testAnInputMissingItsZipIsFailedAndNothingIsDeleted() throws Exception {
        final FileStoreLocation input;
        try (final FileStoreWrite write = inputStore.newWrite()) {
            final FileGroup group = new FileGroup(write.getPath());
            AttributeMapUtil.write(new AttributeMap(), group.getMeta());
            TestDataUtil.writeEntries(group.getEntries(), FEED_A, FEED_B);
            input = write.commit();
        }

        assertThatThrownBy(() -> stage.process(publishAndClaim(input)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("zip");
        assertThat(inputStore.resolve(input)).isDirectory();
        assertThat(outputQueue.getApproximatePendingCount()).isZero();
    }

    @Test
    void testAnEntriesFileNamingAnEntryTheZipDoesNotHoldFailsTheInput() throws Exception {
        final FileStoreLocation input = writeMultiFeedInput(1, FEED_A, FEED_B);
        final FileGroup group = new FileGroup(inputStore.resolve(input));
        Files.writeString(group.getEntries(), Files.readString(group.getEntries())
                .replace("0000000002.dat", "0000000099.dat"));

        assertThatThrownBy(() -> stage.process(publishAndClaim(input)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("0000000099.dat");
        assertThat(inputStore.resolve(input)).isDirectory();
    }

    @Test
    void testAnEmptyEntriesFileFailsTheInput() throws Exception {
        final FileStoreLocation input = writeMultiFeedInput(1, FEED_A);
        Files.writeString(new FileGroup(inputStore.resolve(input)).getEntries(), "");

        assertThatThrownBy(() -> stage.process(publishAndClaim(input)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("no items");
        assertThat(inputStore.resolve(input)).isDirectory();
    }

    @Test
    void testNothingIsWrittenOutsideTheStores() throws Exception {
        stage.process(publishAndClaim(writeMultiFeedInput(2, FEED_A, FEED_B)));

        try (final var children = Files.list(getCurrentTestDir())) {
            assertThat(children.map(path -> path.getFileName().toString()))
                    .containsExactlyInAnyOrder("receive-store", "split-store", "in", "out");
        }
    }

    // --------------------------------------------------------------------------------

    private FileStoreLocation writeMultiFeedInput(final int itemsPerFeed, final FeedKey... keys) throws IOException {
        return writeMultiFeedInput(inputStore, itemsPerFeed, keys);
    }

    private static FileStoreLocation writeMultiFeedInput(final FilesystemFileStore store,
                                                         final int itemsPerFeed,
                                                         final FeedKey... keys) throws IOException {
        try (final FileStoreWrite write = store.newWrite()) {
            final FileGroup group = new FileGroup(write.getPath());
            final AttributeMap headers = new AttributeMap();
            headers.put("Environment", "test");
            TestDataUtil.writeZip(group, itemsPerFeed, headers, new LinkedHashSet<>(List.of(keys)), null);
            AttributeMapUtil.write(headers, group.getMeta());
            return write.commit();
        }
    }

    private FileGroupQueueItem publishAndClaim(final FileStoreLocation location) throws IOException {
        inputQueue.publish(FileGroupQueueMessage.create(location, null, null, "receive", "node", "trace-1", Map.of()));
        return inputQueue.next().orElseThrow();
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

    /**
     * The output is a canonical proxy zip: renumbered, entries describing exactly the zip, the key
     * in {@code proxy.meta}.
     */
    static void assertCanonical(final Path groupDir, final int expectedItems, final FeedKey key) throws IOException {
        final FileGroup group = new FileGroup(groupDir);
        final List<String> names = ZipUtil.pathList(group.getZip());
        final List<String> expectedNames = new ArrayList<>();
        for (int i = 1; i <= expectedItems; i++) {
            expectedNames.add(String.format("%010d.meta", i));
            expectedNames.add(String.format("%010d.dat", i));
        }
        assertThat(names).isEqualTo(expectedNames);
        final List<ZipEntryGroup> entries = ZipEntryGroup.read(group.getEntries(), FeedKeyInterner.create());
        assertThat(entries).hasSize(expectedItems);
        assertThat(entries).extracting(ZipEntryGroup::getFeedKey).containsOnly(key);
        assertThat(entries).flatExtracting(e -> List.of(e.getMetaEntry().getName(), e.getDataEntry().getName()))
                .as("proxy.entries names exactly the zip's entries")
                .isEqualTo(names);
        final AttributeMap meta = new AttributeMap();
        AttributeMapUtil.read(group.getMeta(), meta);
        assertThat(meta.get(StandardHeaderArguments.FEED)).isEqualTo(key.feed());
        assertThat(meta.get(StandardHeaderArguments.TYPE)).isEqualTo(key.type());
    }
}
