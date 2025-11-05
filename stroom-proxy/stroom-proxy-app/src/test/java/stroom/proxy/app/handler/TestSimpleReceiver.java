package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.proxy.repo.FeedKey;
import stroom.proxy.repo.LogStream;
import stroom.receive.common.AttributeMapFilter;
import stroom.receive.common.AttributeMapFilterFactory;
import stroom.receive.common.ReceiveDataConfig;
import stroom.receive.common.StroomStreamException;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.exception.ThrowingConsumer;
import stroom.util.io.ByteSize;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class TestSimpleReceiver extends StroomUnitTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestSimpleReceiver.class);

    public static final String FEED_1 = "test-feed-1";

    @Mock
    private AttributeMapFilterFactory mockAttributeMapFilterFactory;
    @Mock
    private LogStream mockLogStream;
    @Mock
    private ReceiveDataConfig mockReceiveDataConfig;

    @TempDir
    private Path dataDir;
    @TempDir
    private Path destinationDir;

    @ParameterizedTest
    @ValueSource(ints = {1})
    @NullSource
    void testReceive(final Integer maxSize) throws IOException {
        Mockito.lenient().when(mockReceiveDataConfig.getMaxRequestSize()).thenReturn(
                maxSize == null ? null : ByteSize.ofGibibytes(maxSize));

        final String defaultFeedName = FEED_1;
        final String defaultTypeName = null;

        final AttributeMap attributeMap = new AttributeMap();
        AttributeMapUtil.addFeedAndType(attributeMap, defaultFeedName, defaultTypeName);

        final Path testZipFile = TestDataUtil.writeZip(new FeedKey(defaultFeedName, defaultTypeName));

        LOGGER.info("testZipFile {}", testZipFile.toAbsolutePath());

        final List<Path> results = doReceive(testZipFile, attributeMap, attrMap -> true);

        assertThat(results).hasSize(1);
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

        final SimpleReceiver simpleReceiver = new SimpleReceiver(
                mockAttributeMapFilterFactory,
                () -> dataDir,
                null,
                mockLogStream,
                null,
                () -> mockReceiveDataConfig);

        final List<Path> consumedPaths = new ArrayList<>();
        final AtomicLong counter = new AtomicLong();
        simpleReceiver.setDestination(ThrowingConsumer.unchecked(aPath -> {
            final Path destPath = destinationDir.resolve(
                    NumericFileNameUtil.create(counter.incrementAndGet()));
            Files.move(aPath, destPath);
            consumedPaths.add(destPath);
        }));

        try (final InputStream inputStream = new BufferedInputStream(Files.newInputStream(testZipFile))) {
            simpleReceiver.receive(
                    Instant.now(),
                    attributeMap,
                    "aURI",
                    () -> inputStream);
        }

        return consumedPaths;
    }

}
