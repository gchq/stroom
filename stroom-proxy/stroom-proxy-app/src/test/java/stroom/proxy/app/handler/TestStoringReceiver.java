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

package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.proxy.app.pipeline.queue.FileGroupQueue;
import stroom.proxy.app.pipeline.queue.FileGroupQueueItem;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessage;
import stroom.proxy.app.pipeline.queue.local.LocalFileGroupQueue;
import stroom.proxy.app.pipeline.store.filesystem.FilesystemFileStore;
import stroom.proxy.repo.FeedKeyInterner;
import stroom.proxy.repo.LogStream;
import stroom.proxy.repo.LogStream.EventType;
import stroom.receive.common.AttributeMapFilter;
import stroom.receive.common.AttributeMapFilterFactory;
import stroom.receive.common.ReceiveDataConfig;
import stroom.receive.common.StroomStreamException;
import stroom.util.io.ByteSize;
import stroom.util.shared.FeedKey;
import stroom.util.zip.ZipUtil;

import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestStoringReceiver {

    private static final byte[] BODY = "hello".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path dir;

    private FilesystemFileStore store;
    private LocalFileGroupQueue outputQueue;
    private LocalFileGroupQueue splitZipQueue;
    private LogStream logStream;
    private Predicate<AttributeMap> policy;
    private ReceiveDataConfig receiveDataConfig;
    private Path spoolDir;

    @BeforeEach
    void setUp() throws IOException {
        store = new FilesystemFileStore("receive-store", dir.resolve("store"));
        outputQueue = new LocalFileGroupQueue("output", Files.createDirectories(dir.resolve("output")));
        splitZipQueue = new LocalFileGroupQueue("split", Files.createDirectories(dir.resolve("split")));
        logStream = Mockito.mock(LogStream.class);
        policy = attributeMap -> true;
        receiveDataConfig = ReceiveDataConfig.builder().build();
        spoolDir = dir.resolve("spool");
    }

    @Test
    void testAPlainBodyBecomesOneCanonicalGroupPublishedWithItsFeed() throws IOException {
        final StoringReceiver receiver = receiver(true);

        receiver.receive(Instant.now(), headers("FEED_A", null), "test", () -> new ByteArrayInputStream(BODY));

        final FileGroupQueueMessage message = onlyMessage(outputQueue);
        assertThat(message.feed()).isEqualTo("FEED_A");
        assertThat(message.type()).isEqualTo("Raw Events");
        assertThat(message.producingStage()).isEqualTo("receive");
        assertThat(message.producerId()).isEqualTo("node-1");

        final Path group = store.resolve(message.fileStoreLocation());
        assertThat(ZipUtil.pathList(group.resolve("proxy.zip")))
                .containsExactly("0000000001.meta", "0000000001.dat");
        assertThat(metaOf(group).get(StandardHeaderArguments.FEED)).isEqualTo("FEED_A");
        assertThat(entriesOf(group)).hasSize(1);
        assertThat(entriesOf(group).getFirst().getDataEntry().getUncompressedSize()).isEqualTo(BODY.length);
        assertThat(splitZipQueue.next()).isEmpty();
        assertThat(spoolDir).isEmptyDirectory();
        verifyLogged(EventType.RECEIVE);
    }

    @Test
    void testAGzipBodyIsStoredDecompressed() throws IOException {
        final ByteArrayOutputStream gzipped = new ByteArrayOutputStream();
        try (final GzipCompressorOutputStream out = new GzipCompressorOutputStream(gzipped)) {
            out.write(BODY);
        }
        final StoringReceiver receiver = receiver(true);

        receiver.receive(Instant.now(), headers("FEED_A", StandardHeaderArguments.COMPRESSION_GZIP), "test",
                () -> new ByteArrayInputStream(gzipped.toByteArray()));

        final Path group = store.resolve(onlyMessage(outputQueue).fileStoreLocation());
        assertThat(entriesOf(group).getFirst().getDataEntry().getUncompressedSize()).isEqualTo(BODY.length);
    }

    @Test
    void testASingleFeedZipGoesToTheOutputQueueAndAMultiFeedZipToTheSplitZipQueue() throws IOException {
        final StoringReceiver receiver = receiver(true);

        receiver.receive(Instant.now(), headers("FEED_A", StandardHeaderArguments.COMPRESSION_ZIP), "test",
                () -> new ByteArrayInputStream(zip(Map.of("a.log", "1", "b.log", "2"))));
        final FileGroupQueueMessage single = onlyMessage(outputQueue);
        assertThat(single.feed()).isEqualTo("FEED_A");
        assertThat(ZipUtil.pathList(store.resolve(single.fileStoreLocation()).resolve("proxy.zip"))).hasSize(4);

        final Map<String, String> multi = new LinkedHashMap<>();
        multi.put("1.meta", "Feed:X\n");
        multi.put("1.dat", "x");
        multi.put("2.meta", "Feed:Y\n");
        multi.put("2.dat", "y");
        receiver.receive(Instant.now(), headers("FEED_A", StandardHeaderArguments.COMPRESSION_ZIP), "test",
                () -> new ByteArrayInputStream(zip(multi)));
        final FileGroupQueueMessage split = onlyMessage(splitZipQueue);
        assertThat(split.feed()).as("a multi-feed group carries no aggregation key").isNull();
        final Path group = store.resolve(split.fileStoreLocation());
        assertThat(metaOf(group).get(StandardHeaderArguments.FEED))
                .as("a multi-feed group names no feed; the headers' feed describes none of its entries")
                .isNull();
        assertThat(entriesOf(group).stream().map(ZipEntryGroup::getFeedKey))
                .containsExactly(FeedKey.of("X", "Raw Events"), FeedKey.of("Y", "Raw Events"));
        assertThat(spoolDir).isEmptyDirectory();
    }

    @Test
    void testAMultiFeedZipIsRefusedWhenThereIsNowhereToSplitIt() throws IOException {
        final StoringReceiver receiver = receiver(false);
        final Map<String, String> multi = new LinkedHashMap<>();
        multi.put("1.meta", "Feed:X\n");
        multi.put("1.dat", "x");
        multi.put("2.meta", "Feed:Y\n");
        multi.put("2.dat", "y");

        assertThatThrownBy(() -> receiver.receive(Instant.now(),
                headers("FEED_A", StandardHeaderArguments.COMPRESSION_ZIP), "test",
                () -> new ByteArrayInputStream(zip(multi))))
                .isInstanceOf(StroomStreamException.class)
                .hasMessageContaining("no split-zip queue");
        assertNothingStored();
    }

    @Test
    void testARejectedFeedRefusesTheWholeZipAndStoresNothing() throws IOException {
        policy = attributeMap -> {
            if ("Y".equals(attributeMap.get(StandardHeaderArguments.FEED))) {
                throw new StroomStreamException(StroomStatusCode.FEED_IS_NOT_SET_TO_RECEIVE_DATA, attributeMap);
            }
            return true;
        };
        final StoringReceiver receiver = receiver(true);
        final Map<String, String> multi = new LinkedHashMap<>();
        multi.put("1.meta", "Feed:X\n");
        multi.put("1.dat", "x");
        multi.put("2.meta", "Feed:Y\n");
        multi.put("2.dat", "y");

        assertThatThrownBy(() -> receiver.receive(Instant.now(),
                headers("FEED_A", StandardHeaderArguments.COMPRESSION_ZIP), "test",
                () -> new ByteArrayInputStream(zip(multi))))
                .isInstanceOf(StroomStreamException.class)
                .extracting(e -> ((StroomStreamException) e).getStroomStatusCode())
                .isEqualTo(StroomStatusCode.FEED_IS_NOT_SET_TO_RECEIVE_DATA);
        assertNothingStored();
    }

    @Test
    void testADroppedFeedIsNeverWrittenAndDroppingEveryFeedStoresNothing() throws IOException {
        policy = attributeMap -> !"Y".equals(attributeMap.get(StandardHeaderArguments.FEED));
        final StoringReceiver receiver = receiver(true);
        final Map<String, String> multi = new LinkedHashMap<>();
        multi.put("1.meta", "Feed:X\n");
        multi.put("1.dat", "x");
        multi.put("2.meta", "Feed:Y\n");
        multi.put("2.dat", "y");
        multi.put("3.meta", "Feed:X\n");
        multi.put("3.dat", "x2");

        receiver.receive(Instant.now(), headers("FEED_A", StandardHeaderArguments.COMPRESSION_ZIP), "test",
                () -> new ByteArrayInputStream(zip(multi)));
        final FileGroupQueueMessage message = onlyMessage(outputQueue);
        assertThat(message.feed()).as("one feed is left, so the group is single-feed").isEqualTo("X");
        final Path group = store.resolve(message.fileStoreLocation());
        assertThat(ZipUtil.pathList(group.resolve("proxy.zip")))
                .containsExactly("0000000001.meta", "0000000001.dat", "0000000002.meta", "0000000002.dat");
        assertThat(metaOf(group).get(StandardHeaderArguments.FEED)).isEqualTo("X");

        policy = attributeMap -> false;
        final StoringReceiver dropping = receiver(true);
        dropping.receive(Instant.now(), headers("FEED_A", StandardHeaderArguments.COMPRESSION_ZIP), "test",
                () -> new ByteArrayInputStream(zip(multi)));
        dropping.receive(Instant.now(), headers("FEED_A", null), "test", () -> new ByteArrayInputStream(BODY));
        assertThat(outputQueue.next()).isEmpty();
        assertThat(splitZipQueue.next()).isEmpty();
        Mockito.verify(logStream, Mockito.times(2)).log(
                ArgumentMatchers.any(Logger.class),
                ArgumentMatchers.any(AttributeMap.class),
                ArgumentMatchers.eq(EventType.DROP),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.eq(StroomStatusCode.OK),
                ArgumentMatchers.any(),
                ArgumentMatchers.anyLong(),
                ArgumentMatchers.anyLong());
    }

    @Test
    void testAFeedThePolicyGeneratesIsTheFeedThatIsStored() throws IOException {
        policy = attributeMap -> {
            if (attributeMap.get(StandardHeaderArguments.FEED) == null) {
                attributeMap.put(StandardHeaderArguments.FEED, "GENERATED");
            }
            return true;
        };
        final StoringReceiver receiver = receiver(true);

        receiver.receive(Instant.now(), headers(null, StandardHeaderArguments.COMPRESSION_ZIP), "test",
                () -> new ByteArrayInputStream(zip(Map.of("a.log", "1"))));

        final FileGroupQueueMessage message = onlyMessage(outputQueue);
        assertThat(message.feed()).isEqualTo("GENERATED");
        final Path group = store.resolve(message.fileStoreLocation());
        assertThat(metaOf(group).get(StandardHeaderArguments.FEED)).isEqualTo("GENERATED");
        assertThat(entriesOf(group).getFirst().getFeedKey()).isEqualTo(FeedKey.of("GENERATED", "Raw Events"));
        assertThat(entryMeta(group.resolve("proxy.zip"), "0000000001.meta").get(StandardHeaderArguments.FEED))
                .as("the entry's own meta names the feed the policy accepted")
                .isEqualTo("GENERATED");
    }

    @Test
    void testAGzipHeaderOnABodyThatIsNotGzipIsTheSendersFault() throws IOException {
        final StoringReceiver receiver = receiver(true);

        assertThatThrownBy(() -> receiver.receive(Instant.now(),
                headers("FEED_A", StandardHeaderArguments.COMPRESSION_GZIP), "test",
                () -> new ByteArrayInputStream(BODY)))
                .isInstanceOf(StroomStreamException.class)
                .extracting(e -> ((StroomStreamException) e).getStroomStatusCode())
                .isEqualTo(StroomStatusCode.COMPRESSED_STREAM_INVALID);
        assertNothingStored();
    }

    @Test
    void testABodyOverTheLimitIsRefusedAndLeavesNothing() throws IOException {
        receiveDataConfig = ReceiveDataConfig.builder().withMaxRequestSize(ByteSize.ofBytes(3)).build();
        final StoringReceiver receiver = receiver(true);

        assertThatThrownBy(() -> receiver.receive(Instant.now(), headers("FEED_A", null), "test",
                () -> new ByteArrayInputStream(BODY)))
                .isInstanceOf(StroomStreamException.class)
                .extracting(e -> ((StroomStreamException) e).getStroomStatusCode())
                .isEqualTo(StroomStatusCode.CONTENT_TOO_LARGE);
        assertThatThrownBy(() -> receiver.receive(Instant.now(),
                headers("FEED_A", StandardHeaderArguments.COMPRESSION_ZIP), "test",
                () -> new ByteArrayInputStream(zip(Map.of("a.log", "1")))))
                .isInstanceOf(StroomStreamException.class)
                .extracting(e -> ((StroomStreamException) e).getStroomStatusCode())
                .isEqualTo(StroomStatusCode.CONTENT_TOO_LARGE);
        assertNothingStored();
    }

    @Test
    void testAnEmptyBodyStoresNothingAndIsNotAnError() throws IOException {
        final StoringReceiver receiver = receiver(true);

        receiver.receive(Instant.now(), headers("FEED_A", null), "test", () -> new ByteArrayInputStream(new byte[0]));

        assertNothingStored();
    }

    @Test
    void testAZipOnDiskIsReadInPlaceAndLeftToTheCaller() throws IOException {
        final Path zipFile = dir.resolve("scanned.zip");
        Files.write(zipFile, zip(Map.of("a.log", "1")));
        final StoringReceiver receiver = receiver(true);

        receiver.receiveZip(Instant.now(), headers("FEED_A", null), "file://x", zipFile);

        assertThat(zipFile).exists();
        assertThat(onlyMessage(outputQueue).feed()).isEqualTo("FEED_A");
        assertThat(spoolDir).isEmptyDirectory();
    }

    @Test
    void testAFailedPublishIsReportedAndLeavesTheSpoolEmpty() throws IOException {
        final FileGroupQueue failing = Mockito.mock(FileGroupQueue.class);
        Mockito.doThrow(new IOException("queue down")).when(failing).publish(ArgumentMatchers.any());
        final StoringReceiver receiver = new StoringReceiver(store, failing, null, "node-1",
                filterFactory(), () -> receiveDataConfig, logStream, spoolDir);

        assertThatThrownBy(() -> receiver.receive(Instant.now(),
                headers("FEED_A", StandardHeaderArguments.COMPRESSION_ZIP), "test",
                () -> new ByteArrayInputStream(zip(Map.of("a.log", "1")))))
                .isInstanceOf(StroomStreamException.class)
                .hasMessageContaining("queue down");
        assertThat(spoolDir).isEmptyDirectory();
        assertThat(committedGroups()).as("the group is committed, an orphan for the sweep").isEqualTo(1);
        assertThat(stagedFiles()).isZero();
    }

    @Test
    void testTheSpoolDirectoryIsClearedAtConstruction() throws IOException {
        Files.createDirectories(spoolDir);
        Files.writeString(spoolDir.resolve("upload-stale.zip"), "left over");

        receiver(true);

        assertThat(spoolDir).isEmptyDirectory();
    }

    private StoringReceiver receiver(final boolean withSplitZipQueue) {
        return new StoringReceiver(
                store,
                outputQueue,
                withSplitZipQueue ? splitZipQueue : null,
                "node-1",
                filterFactory(),
                () -> receiveDataConfig,
                logStream,
                spoolDir);
    }

    private AttributeMapFilterFactory filterFactory() {
        final AttributeMapFilterFactory factory = Mockito.mock(AttributeMapFilterFactory.class);
        final AttributeMapFilter filter = attributeMap -> policy.test(attributeMap);
        Mockito.when(factory.create()).thenReturn(filter);
        return factory;
    }

    private static AttributeMap headers(final String feed, final String compression) {
        final AttributeMap attributeMap = new AttributeMap();
        if (feed != null) {
            AttributeMapUtil.addFeedAndType(attributeMap, feed, "Raw Events");
        } else {
            attributeMap.put(StandardHeaderArguments.TYPE, "Raw Events");
        }
        attributeMap.put(StandardHeaderArguments.RECEIPT_ID, "rid-1");
        if (compression != null) {
            attributeMap.put(StandardHeaderArguments.COMPRESSION, compression);
        }
        return attributeMap;
    }

    private static byte[] zip(final Map<String, String> entries) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (final ZipWriter writer = new ZipWriter(bytes, LocalByteBuffer.get())) {
            for (final Map.Entry<String, String> entry : entries.entrySet()) {
                writer.writeString(entry.getKey(), entry.getValue());
            }
        }
        return bytes.toByteArray();
    }

    private static FileGroupQueueMessage onlyMessage(final FileGroupQueue queue) throws IOException {
        final Optional<FileGroupQueueItem> item = queue.next();
        assertThat(item).isPresent();
        final FileGroupQueueMessage message = item.get().getMessage();
        item.get().acknowledge();
        assertThat(queue.next()).isEmpty();
        return message;
    }

    private void assertNothingStored() throws IOException {
        assertThat(outputQueue.next()).isEmpty();
        assertThat(splitZipQueue.next()).isEmpty();
        assertThat(spoolDir).isEmptyDirectory();
        assertThat(committedGroups()).isZero();
        assertThat(stagedFiles()).as("a discarded write leaves nothing in staging").isZero();
    }

    private long committedGroups() throws IOException {
        try (final Stream<Path> files = Files.walk(dir.resolve("store"))) {
            return files.filter(path -> path.getFileName().toString().equals("proxy.zip"))
                    .filter(path -> !path.toString().contains(".staging"))
                    .count();
        }
    }

    private long stagedFiles() throws IOException {
        try (final Stream<Path> files = Files.walk(dir.resolve("store"))) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().contains(".staging"))
                    .count();
        }
    }

    private static AttributeMap entryMeta(final Path zip, final String entryName) throws IOException {
        try (final org.apache.commons.compress.archivers.zip.ZipFile zipFile = ZipUtil.createZipFile(zip);
                final InputStream in = zipFile.getInputStream(zipFile.getEntry(entryName))) {
            final AttributeMap attributeMap = new AttributeMap();
            AttributeMapUtil.read(in, attributeMap);
            return attributeMap;
        }
    }

    private static AttributeMap metaOf(final Path group) throws IOException {
        final AttributeMap attributeMap = new AttributeMap();
        AttributeMapUtil.read(group.resolve("proxy.meta"), attributeMap);
        return attributeMap;
    }

    private static List<ZipEntryGroup> entriesOf(final Path group) {
        return ZipEntryGroup.read(group.resolve("proxy.entries"), FeedKeyInterner.create());
    }

    private void verifyLogged(final EventType eventType) {
        Mockito.verify(logStream).log(
                ArgumentMatchers.any(Logger.class),
                ArgumentMatchers.any(AttributeMap.class),
                ArgumentMatchers.eq(eventType),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.eq(StroomStatusCode.OK),
                ArgumentMatchers.any(),
                ArgumentMatchers.anyLong(),
                ArgumentMatchers.anyLong());
    }
}
