package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.proxy.app.handler.ZipReceiver.ReceiveResult;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.io.FileUtil;

import org.junit.jupiter.api.Test;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class TestZipReceiver extends StroomUnitTest {

    @Test
    void testReceiveSimpleZipStream() throws IOException {
        final String defaultFeedName = "test-feed";
        final String defaultTypeName = null;
        final byte[] buffer = LocalByteBuffer.get();

        final AttributeMap attributeMap = new AttributeMap();
        AttributeMapUtil.addFeedAndType(attributeMap, defaultFeedName, defaultTypeName);

        final Path testZipFile = writeSimpleZip(buffer);

        final ReceiveResult receiveResult = receive(
                testZipFile,
                defaultFeedName,
                defaultTypeName,
                attributeMap,
                buffer);
        assertThat(receiveResult.feedGroups().size()).isEqualTo(1);
        assertThat(receiveResult.receivedBytes()).isEqualTo(257);
    }

    @Test
    void testReceiveComplexZipStream() throws IOException {
        final String defaultFeedName = "test-feed";
        final String defaultTypeName = null;
        final byte[] buffer = LocalByteBuffer.get();

        final AttributeMap attributeMap = new AttributeMap();
        AttributeMapUtil.addFeedAndType(attributeMap, defaultFeedName, defaultTypeName);

        final Path testZipFile = writeMultiFeedZip(buffer);

        final ReceiveResult receiveResult = receive(
                testZipFile,
                defaultFeedName,
                defaultTypeName,
                attributeMap,
                buffer);
        assertThat(receiveResult.feedGroups().size()).isEqualTo(2);
        assertThat(receiveResult.receivedBytes()).isEqualTo(518);

        final Path splitDir = Files.createTempDirectory("test");
        ZipReceiver.splitZip(testZipFile, attributeMap, receiveResult.feedGroups(), splitDir, buffer);

        final long count = FileUtil.count(splitDir);
        assertThat(count).isEqualTo(2);

        // Now verify by simulating receiving the data from each split zip.
        for (int i = 1; i <= count; i++) {
            final FileGroup fileGroup2 = new FileGroup(splitDir.resolve(NumericFileNameUtil.create(i)));
            final ReceiveResult receiveResult2 = receive(
                    fileGroup2.getZip(),
                    defaultFeedName,
                    defaultTypeName,
                    attributeMap,
                    buffer);
            assertThat(receiveResult2.feedGroups().size()).isEqualTo(1);
            assertThat(receiveResult2.receivedBytes()).isEqualTo(298);
        }
    }

    private Path writeSimpleZip(final byte[] buffer) throws IOException {
        final String defaultFeedName = "test-feed";
        final String defaultTypeName = null;

        final AttributeMap attributeMap = new AttributeMap();
        AttributeMapUtil.addFeedAndType(attributeMap, defaultFeedName, defaultTypeName);

        final Path testZipFile = Files.createTempFile("test", ".zip");
        try (final ZipWriter zipWriter = new ZipWriter(testZipFile, buffer)) {
            zipWriter.writeAttributeMap("001.meta", attributeMap);
            zipWriter.writeString("001.dat", "test");
        }
        return testZipFile;
    }

    private Path writeMultiFeedZip(final byte[] buffer) throws IOException {
        final Path testZipFile = Files.createTempFile("test", ".zip");
        try (final ZipWriter zipWriter = new ZipWriter(testZipFile, buffer)) {
            final AttributeMap attributeMap1 = new AttributeMap();
            AttributeMapUtil.addFeedAndType(attributeMap1, "test-feed-1", "test-type-1");
            zipWriter.writeAttributeMap("001.meta", attributeMap1);
            zipWriter.writeString("001.dat", "test");

            final AttributeMap attributeMap2 = new AttributeMap();
            AttributeMapUtil.addFeedAndType(attributeMap2, "test-feed-2", "test-type-2");
            zipWriter.writeAttributeMap("002.meta", attributeMap2);
            zipWriter.writeString("002.dat", "test");
        }
        return testZipFile;
    }

    private ReceiveResult receive(final Path testZipFile,
                                  final String defaultFeedName,
                                  final String defaultTypeName,
                                  final AttributeMap attributeMap,
                                  final byte[] buffer) throws IOException {
        final Path receivedZipFile = Files.createTempFile("test", ".zip");
        try (final InputStream inputStream = new BufferedInputStream(Files.newInputStream(testZipFile))) {
            return ZipReceiver.receiveZipStream(inputStream,
                    defaultFeedName,
                    defaultTypeName,
                    attributeMap,
                    receivedZipFile,
                    buffer);
        }
    }
}
