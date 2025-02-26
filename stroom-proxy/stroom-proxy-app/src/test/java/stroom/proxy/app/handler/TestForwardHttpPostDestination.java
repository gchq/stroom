package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.util.NullSafe;
import stroom.util.exception.ThrowingConsumer;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestForwardHttpPostDestination {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestForwardHttpPostDestination.class);

    @Mock
    private StreamDestination mockStreamDestination;

    private Path dataDir;
    private Path sourcesDir;
    private CleanupDirQueue cleanupDirQueue;

    @BeforeEach
    void setUp(@TempDir Path baseDir) {
        this.dataDir = baseDir.resolve("data");
        this.sourcesDir = baseDir.resolve("sources");
        this.cleanupDirQueue = new CleanupDirQueue(this::getDataDir);
    }

    @Test
    void test_success() throws Exception {
        final ForwardHttpPostConfig forwardHttpPostConfig = ForwardHttpPostConfig.builder()
                .build();
        final ForwardHttpPostDestination forwardHttpPostDestination = new ForwardHttpPostDestination(
                "TestDest",
                mockStreamDestination,
                cleanupDirQueue,
                forwardHttpPostConfig);

        final CountDownLatch sentLatch = new CountDownLatch(1);
        Mockito.doAnswer(
                        invocation -> {
                            LOGGER.info("send called");
                            sentLatch.countDown();
                            return null;
                        })
                .when(mockStreamDestination).send(Mockito.any(), Mockito.any());

        final Path source1 = createSourceDir(1);
        forwardHttpPostDestination.add(source1);

        final boolean didCountDown = sentLatch.await(5, TimeUnit.SECONDS);
        assertThat(didCountDown)
                .isTrue();

        // Success so the source is gone
        Assertions.assertThat(source1)
                .doesNotExist();
        Assertions.assertThat(sourcesDir)
                .isEmptyDirectory();
    }

    @Test
    void test_Fail() throws Exception {
        final ForwardHttpPostConfig forwardHttpPostConfig = ForwardHttpPostConfig.builder()
                .build();
        final ForwardHttpPostDestination forwardHttpPostDestination = new ForwardHttpPostDestination(
                "TestDest",
                mockStreamDestination,
                cleanupDirQueue,
                forwardHttpPostConfig);

        Mockito.doThrow(new RuntimeException("Send failed"))
                .when(mockStreamDestination).send(Mockito.any(), Mockito.any());

        final Path source1 = createSourceDir(1);

        Assertions.assertThatThrownBy(
                        () -> forwardHttpPostDestination.add(source1))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Send failed");

        // Success so the source is gone
        Assertions.assertThat(source1)
                .exists()
                .isDirectory();
        Assertions.assertThat(sourcesDir)
                .isNotEmptyDirectory();
    }

    private Path getDataDir() {
        return dataDir;
    }

    private Path createSourceDir(final int num) {
        return createSourceDir(num, null);
    }

    private Path createSourceDir(final int num, final Map<String, String> attrs) {
        final Path sourceDir = sourcesDir.resolve("source_" + num);
        FileUtil.ensureDirExists(sourceDir);
        assertThat(sourceDir)
                .isDirectory()
                .exists();

        final FileGroup fileGroup = new FileGroup(sourceDir);
        fileGroup.items()
                .forEach(ThrowingConsumer.unchecked(FileUtil::touch));

        try {
            if (NullSafe.hasEntries(attrs)) {
                final Path meta = fileGroup.getMeta();
                final AttributeMap attributeMap = new AttributeMap(attrs);
                AttributeMapUtil.write(attributeMap, meta);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return sourceDir;
    }
}
