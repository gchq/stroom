package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.proxy.app.handler.TestDataUtil.ItemGroup;
import stroom.proxy.app.handler.TestDataUtil.ProxyZipSnapshot;
import stroom.proxy.app.handler.ZipReceiver.ReceiveResult;
import stroom.proxy.repo.FeedKey;
import stroom.proxy.repo.LogStream;
import stroom.receive.common.AttributeMapFilter;
import stroom.receive.common.AttributeMapFilterFactory;
import stroom.receive.common.ReceiveAllAttributeMapFilter;
import stroom.receive.common.ReceiveDataConfig;
import stroom.receive.common.StroomStreamException;
import stroom.test.common.DirectorySnapshot;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.exception.ThrowingConsumer;
import stroom.util.io.ByteSize;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
public class TestZipReceiver extends StroomUnitTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestZipReceiver.class);

    public static final String FEED_1 = "test-feed-1";
    public static final String FEED_2 = "test-feed-2";
    public static final String TYPE_1 = "test-type-1";
    public static final String TYPE_2 = "test-type-2";
    public static final FeedKey FEED_KEY_1_1 = new FeedKey(FEED_1, TYPE_1);
    public static final FeedKey FEED_KEY_1_2 = new FeedKey(FEED_1, TYPE_2);
    public static final FeedKey FEED_KEY_2_1 = new FeedKey(FEED_2, TYPE_1);
    public static final FeedKey FEED_KEY_2_2 = new FeedKey(FEED_2, TYPE_2);
    public static final int ZIP_ENTRY_COUNT_PER_FEED_KEY = 2;

    @Mock
    private AttributeMapFilterFactory mockAttributeMapFilterFactory;
    @Mock
    private LogStream mockLogStream;
    @Mock
    private ZipSplitter mockZipSplitter;
    @Mock
    private ReceiveDataConfig mockReceiveDataConfig;

    @TempDir
    private Path dataDir;
    @TempDir
    private Path destinationDir;
    @TempDir
    private Path inputDir;

    @Test
    void testReceiveSimpleZipStream() throws IOException {
        final String defaultFeedName = FEED_1;
        final String defaultTypeName = null;
        final byte[] buffer = LocalByteBuffer.get();

        final AttributeMap attributeMap = new AttributeMap();
        AttributeMapUtil.addFeedAndType(attributeMap, defaultFeedName, defaultTypeName);

        final Path testZipFile = TestDataUtil.writeZip(new FeedKey(defaultFeedName, defaultTypeName));

        LOGGER.info("testZipFile {}", testZipFile.toAbsolutePath());

        final ReceiveResult receiveResult = receive(
                testZipFile,
                attributeMap);
        assertThat(receiveResult.feedGroups().size()).isEqualTo(1);
        assertThat(receiveResult.receivedBytes()).isEqualTo(459);
    }

    @Test
    void testReceiveComplexZipStream() throws IOException {
        final String defaultFeedName = "test-feed";
        final String defaultTypeName = null;
        final byte[] buffer = LocalByteBuffer.get();

        final AttributeMap attributeMap = new AttributeMap();
        AttributeMapUtil.addFeedAndType(attributeMap, defaultFeedName, defaultTypeName);

        final Path testZipFile = TestDataUtil.writeZip(FEED_KEY_1_1, FEED_KEY_2_2);

        final ReceiveResult receiveResult = receive(
                testZipFile,
                attributeMap);
        assertThat(receiveResult.feedGroups().size()).isEqualTo(2);
        assertThat(receiveResult.receivedBytes()).isEqualTo(842);

//        final Path splitDir = Files.createTempDirectory("test");
//        ZipReceiver.splitZip(testZipFile, attributeMap, receiveResult.feedGroups(), splitDir, buffer);
//
//        final long count = FileUtil.count(splitDir);
//        assertThat(count).isEqualTo(2);
//
//        // Now verify by simulating receiving the data from each split zip.
//        for (int i = 1; i <= count; i++) {
//            final FileGroup fileGroup2 = new FileGroup(splitDir.resolve(NumericFileNameUtil.create(i)));
//            final ReceiveResult receiveResult2 = receive(
//                    fileGroup2.getZip(),
//                    defaultFeedName,
//                    defaultTypeName,
//                    attributeMap,
//                    buffer);
//            assertThat(receiveResult2.feedGroups().size()).isEqualTo(1);
//            assertThat(receiveResult2.receivedBytes()).isEqualTo(470);
//        }
    }

    private ReceiveResult receive(final Path testZipFile,
                                  final AttributeMap attributeMap) throws IOException {
        final Path receivedZipFile = Files.createTempFile("test", ".zip");
        try (final InputStream inputStream = new BufferedInputStream(Files.newInputStream(testZipFile))) {
            return ZipReceiver.receiveZipStream(
                    inputStream,
                    attributeMap,
                    receivedZipFile);
        }
    }

    @Test
    void test_dropAll() throws IOException {
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put("Foo", "Bar");
        final Set<FeedKey> feedKeys = Set.of(FEED_KEY_1_1, FEED_KEY_2_2);
        final FileGroup fileGroup = createZip(attributeMap, feedKeys);

        final List<Path> destinationPaths = doReceive(fileGroup.getZip(), attributeMap, attrMap -> false);

        // All dropped so nothing goes to splitter or destination
        assertThat(destinationPaths)
                .isEmpty();
        Mockito.verify(mockZipSplitter, Mockito.never())
                .add(Mockito.any());
    }

    private FileGroup createZip(final AttributeMap attributeMap, final Set<FeedKey> feedKeys) throws IOException {
        // This also creates an entries file, but we ignore that
        final FileGroup fileGroup = new FileGroup(inputDir);
        TestDataUtil.writeZip(
                fileGroup,
                ZIP_ENTRY_COUNT_PER_FEED_KEY,
                attributeMap,
                feedKeys,
                null);
        return fileGroup;
    }

    @Test
    void test_allRejected() throws IOException {
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put("Foo", "Bar");
        final Set<FeedKey> feedKeys = Set.of(FEED_KEY_1_1, FEED_KEY_2_2);
        // This also creates an entries file, but we ignore that
        final FileGroup fileGroup = createZip(attributeMap, feedKeys);

        Assertions.assertThatThrownBy(
                        () -> {
                            doReceive(
                                    fileGroup.getZip(),
                                    attributeMap,
                                    attrMap -> {
                                        throw new StroomStreamException(
                                                StroomStatusCode.FEED_IS_NOT_SET_TO_RECEIVE_DATA, attributeMap);
                                    });
                        })
                .isInstanceOf(StroomStreamException.class);

        // All rejected so nothing passed along
        Mockito.verify(mockZipSplitter, Mockito.never())
                .add(Mockito.any());
    }

    @Test
    void test_oneRejected() throws IOException {
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put("Foo", "Bar");
        final Set<FeedKey> feedKeys = Set.of(FEED_KEY_1_1, FEED_KEY_2_2);
        // This also creates an entries file, but we ignore that
        final FileGroup fileGroup = createZip(attributeMap, feedKeys);

        Assertions.assertThatThrownBy(
                        () -> {
                            doReceive(
                                    fileGroup.getZip(),
                                    attributeMap,
                                    attrMap -> {
                                        // Reject one of the feeds
                                        if (FEED_1.equals(attrMap.get(StandardHeaderArguments.FEED))) {
                                            throw new StroomStreamException(
                                                    StroomStatusCode.FEED_IS_NOT_SET_TO_RECEIVE_DATA, attributeMap);
                                        } else {
                                            return true;
                                        }
                                    });
                        })
                .isInstanceOf(StroomStreamException.class);

        // All rejected so nothing passed along
        Mockito.verify(mockZipSplitter, Mockito.never())
                .add(Mockito.any());
    }

    @Test
    void test_singleFeedKeyZip() throws IOException {
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put("Foo", "Bar");
        final Set<FeedKey> feedKeys = Set.of(FEED_KEY_1_1);
        // This also creates an entries file, but we ignore that
        final FileGroup fileGroup = createZip(attributeMap, feedKeys);

        final List<Path> destinationPaths = doReceive(
                fileGroup.getZip(),
                attributeMap,
                ReceiveAllAttributeMapFilter.INSTANCE);

        assertThat(destinationPaths)
                .hasSize(1);

        for (final Path destinationPath : destinationPaths) {
            LOGGER.info("destinationPath: {}", destinationPath);
            final Path zipFilePath = TestDataUtil.getZipFile(destinationPath);
            final ProxyZipSnapshot proxyZipSnapshot = ProxyZipSnapshot.of(zipFilePath);
            assertThat(proxyZipSnapshot.getItemGroups())
                    .hasSize(ZIP_ENTRY_COUNT_PER_FEED_KEY);
            // Make sure feed+type is in the meta for each entry
            for (final ItemGroup itemGroup : proxyZipSnapshot.getItemGroups()) {
                final String feed = itemGroup.meta().content().get(StandardHeaderArguments.FEED);
                final String type = itemGroup.meta().content().get(StandardHeaderArguments.TYPE);
                assertThat(feed)
                        .isEqualTo(FEED_KEY_1_1.feed());
                assertThat(type)
                        .isEqualTo(FEED_KEY_1_1.type());
            }

            final List<ZipEntryGroup> entries = TestDataUtil.getEntries(destinationPath);
            assertThat(entries)
                    .hasSize(ZIP_ENTRY_COUNT_PER_FEED_KEY);
            for (final ZipEntryGroup zipEntryGroup : entries) {
                assertThat(zipEntryGroup.getFeedName())
                        .isEqualTo(FEED_KEY_1_1.feed());
                assertThat(zipEntryGroup.getTypeName())
                        .isEqualTo(FEED_KEY_1_1.type());
            }
        }

        // Single feed zip so no split needed
        Mockito.verify(mockZipSplitter, Mockito.never())
                .add(Mockito.any());
    }

    @Test
    void test_dropOneAllowOne() throws IOException {
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put("Foo", "Bar");
        final FeedKey allowedFeedKey = FEED_KEY_1_1;
        final FeedKey droppedFeedKey = FEED_KEY_2_2;
        final Set<FeedKey> feedKeys = Set.of(allowedFeedKey, droppedFeedKey);

        // This also creates an entries file, but we ignore that
        final FileGroup fileGroup = createZip(attributeMap, feedKeys);

        final ArgumentCaptor<Path> pathCaptor = ArgumentCaptor.forClass(Path.class);
        Mockito.doNothing()
                .when(mockZipSplitter)
                .add(pathCaptor.capture());

        final List<Path> destinationPaths = doReceive(
                fileGroup.getZip(),
                attributeMap,
                attrMap ->
                        allowedFeedKey.feed()
                                .equals(attrMap.get(StandardHeaderArguments.FEED)));

        final List<Path> zipSplitterPaths = pathCaptor.getAllValues();

        // zip needs splitting so not sent to dest
        assertThat(destinationPaths)
                .hasSize(0);
        assertThat(zipSplitterPaths)
                .hasSize(1);

        final Path zipSplitterPath = zipSplitterPaths.getFirst();
        LOGGER.info("Snapshot of {}\n{}", zipSplitterPath, DirectorySnapshot.of(zipSplitterPath));
        final FileGroup outputFileGroup = new FileGroup(zipSplitterPath);

        final ProxyZipSnapshot proxyZipSnapshot = ProxyZipSnapshot.of(outputFileGroup.getZip());
        // The dropped entries are still in the zip at this point, ZipSplitter will remove them,
        // but they won't be in the entries file.
        assertThat(proxyZipSnapshot.getItemGroups())
                .hasSize(ZIP_ENTRY_COUNT_PER_FEED_KEY * feedKeys.size());

        final List<ZipEntryGroup> entries = ZipEntryGroup.read(outputFileGroup.getEntries());
        assertThat(entries)
                .hasSize(ZIP_ENTRY_COUNT_PER_FEED_KEY);

        assertThat(entries.stream()
                .allMatch(entry -> Objects.equals(entry.getFeedKey(), allowedFeedKey)))
                .isTrue();

        // No feed/type in the meta file, as the zip may contain many different feeds
        final AttributeMap meta = TestDataUtil.getMeta(zipSplitterPath);
        assertThat(meta.get(StandardHeaderArguments.FEED))
                .isNull();
        assertThat(meta.get(StandardHeaderArguments.TYPE))
                .isNull();
    }

    @Test
    void test_dropOneAllowTwo() throws IOException {
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put("Foo", "Bar");
        final FeedKey allowedFeedKey1 = FEED_KEY_1_1;
        final FeedKey allowedFeedKey2 = FEED_KEY_1_2;
        final FeedKey droppedFeedKey = FEED_KEY_2_2;
        final Set<FeedKey> allowedFeedKeys = Set.of(allowedFeedKey1, allowedFeedKey2);
        final Set<FeedKey> feedKeys = Set.of(allowedFeedKey1, allowedFeedKey2, droppedFeedKey);

        // This also creates an entries file, but we ignore that
        final FileGroup fileGroup = createZip(attributeMap, feedKeys);

        final ArgumentCaptor<Path> pathCaptor = ArgumentCaptor.forClass(Path.class);
        Mockito.doNothing()
                .when(mockZipSplitter)
                .add(pathCaptor.capture());

        final List<Path> destinationPaths = doReceive(
                fileGroup.getZip(),
                attributeMap,
                attrMap -> {
                    final FeedKey feedKey = FeedKey.of(
                            attrMap.get(StandardHeaderArguments.FEED),
                            attrMap.get(StandardHeaderArguments.TYPE));
                    return allowedFeedKeys.contains(feedKey);
                });

        final List<Path> zipSplitterPaths = pathCaptor.getAllValues();

        // zip needs splitting so not sent to dest
        assertThat(destinationPaths)
                .hasSize(0);
        assertThat(zipSplitterPaths)
                .hasSize(1);

        final Path zipSplitterPath = zipSplitterPaths.getFirst();
        LOGGER.info("Snapshot of {}\n{}", zipSplitterPath, DirectorySnapshot.of(zipSplitterPath));
        final FileGroup outputFileGroup = new FileGroup(zipSplitterPath);

        final ProxyZipSnapshot proxyZipSnapshot = ProxyZipSnapshot.of(outputFileGroup.getZip());
        // The dropped entries are still in the zip at this point, ZipSplitter will remove them,
        // but they won't be in the entries file.
        assertThat(proxyZipSnapshot.getItemGroups())
                .hasSize(ZIP_ENTRY_COUNT_PER_FEED_KEY * feedKeys.size());

        final List<ZipEntryGroup> entries = ZipEntryGroup.read(outputFileGroup.getEntries());
        assertThat(entries)
                .hasSize(ZIP_ENTRY_COUNT_PER_FEED_KEY * allowedFeedKeys.size());

        assertThat(entries.stream()
                .allMatch(entry -> allowedFeedKeys.contains(entry.getFeedKey())))
                .isTrue();

        // No feed/type in the meta file, as the zip may contain many different feeds
        final AttributeMap meta = TestDataUtil.getMeta(zipSplitterPath);
        assertThat(meta.get(StandardHeaderArguments.FEED))
                .isNull();
        assertThat(meta.get(StandardHeaderArguments.TYPE))
                .isNull();
    }

    @Test
    void testReceiveContentTooLarge() throws IOException {
        final String defaultFeedName = FEED_1;
        final String defaultTypeName = null;

        final AttributeMap attributeMap = new AttributeMap();
        AttributeMapUtil.addFeedAndType(attributeMap, defaultFeedName, defaultTypeName);

        final Path testZipFile = TestDataUtil.writeZip(new FeedKey(defaultFeedName, defaultTypeName));

        LOGGER.info("testZipFile {}", testZipFile.toAbsolutePath());

        Mockito.lenient().when(mockReceiveDataConfig.getMaxRequestSize()).thenReturn(ByteSize.ofBytes(10));

        assertThatThrownBy(() -> doReceive(testZipFile, attributeMap, attrMap -> true))
                .isInstanceOf(StroomStreamException.class)
                .hasMessageContaining("Maximum request size exceeded");
    }

    private List<Path> doReceive(final Path testZipFile,
                                 final AttributeMap attributeMap,
                                 final AttributeMapFilter attributeMapFilter) throws IOException {

        Mockito.lenient().when(mockAttributeMapFilterFactory.create())
                .thenReturn(attributeMapFilter);

        final ZipReceiver zipReceiver = new ZipReceiver(
                mockAttributeMapFilterFactory,
                () -> dataDir,
                mockLogStream,
                mockZipSplitter,
                () -> mockReceiveDataConfig);

        final List<Path> consumedPaths = new ArrayList<>();
        final AtomicLong counter = new AtomicLong();
        zipReceiver.setDestination(ThrowingConsumer.unchecked(aPath -> {
            final Path destPath = destinationDir.resolve(
                    NumericFileNameUtil.create(counter.incrementAndGet()));
            Files.move(aPath, destPath);
            consumedPaths.add(destPath);
        }));

        try (final InputStream inputStream = new BufferedInputStream(Files.newInputStream(testZipFile))) {
            zipReceiver.receive(
                    Instant.now(),
                    attributeMap,
                    "aURI",
                    () -> inputStream);
        }

        return consumedPaths;
    }
}
