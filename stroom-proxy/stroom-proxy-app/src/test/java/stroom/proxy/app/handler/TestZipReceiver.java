package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.proxy.app.handler.ZipReceiver.ReceiveResult;
import stroom.proxy.repo.FeedKey;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.junit.jupiter.api.Test;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Test
    void testReceiveSimpleZipStream() throws IOException {
        final String defaultFeedName = "test-feed";
        final String defaultTypeName = null;
        final byte[] buffer = LocalByteBuffer.get();

        final AttributeMap attributeMap = new AttributeMap();
        AttributeMapUtil.addFeedAndType(attributeMap, defaultFeedName, defaultTypeName);

        final Path testZipFile = TestDataUtil.writeZip(new FeedKey(defaultFeedName, defaultTypeName));

        LOGGER.info("testZipFile {}", testZipFile.toAbsolutePath());

        final ReceiveResult receiveResult = receive(
                testZipFile,
                attributeMap,
                buffer);
        assertThat(receiveResult.feedGroups().size()).isEqualTo(1);
        assertThat(receiveResult.receivedBytes()).isEqualTo(457);
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
                attributeMap,
                buffer);
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
                                  final AttributeMap attributeMap,
                                  final byte[] buffer) throws IOException {
        final Path receivedZipFile = Files.createTempFile("test", ".zip");
        try (final InputStream inputStream = new BufferedInputStream(Files.newInputStream(testZipFile))) {
            return ZipReceiver.receiveZipStream(
                    inputStream,
                    attributeMap,
                    receivedZipFile,
                    buffer);
        }
    }
}
