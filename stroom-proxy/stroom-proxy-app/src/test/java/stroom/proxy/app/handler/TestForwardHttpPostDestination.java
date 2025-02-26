package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.proxy.repo.ProxyServices;
import stroom.proxy.repo.queue.QueueMonitors;
import stroom.proxy.repo.store.FileStores;
import stroom.test.common.TestUtil;
import stroom.util.NullSafe;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.exception.ThrowingConsumer;
import stroom.util.exception.ThrowingSupplier;
import stroom.util.io.FileUtil;
import stroom.util.io.SimplePathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.time.StroomDuration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestForwardHttpPostDestination {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestForwardHttpPostDestination.class);

    @Mock
    private StreamDestination mockStreamDestination;

    private Path baseDir;
    private Path dataDir;
    private Path homeDir;
    private Path tempDir;
    private Path sourcesDir;
    private ProxyServices proxyServices;
    private CleanupDirQueue cleanupDirQueue;
    private DirQueueFactory dirQueueFactory;

    @BeforeEach
    void setUp(@TempDir Path baseDir) {
        this.baseDir = baseDir;
        this.dataDir = baseDir.resolve("data");
        this.homeDir = baseDir.resolve("home");
        this.tempDir = baseDir.resolve("temp");
        this.sourcesDir = baseDir.resolve("sources");
        this.proxyServices = new ProxyServices();
        this.cleanupDirQueue = new CleanupDirQueue(this::getDataDir);
        this.dirQueueFactory = new DirQueueFactory(this::getDataDir,
                new QueueMonitors(),
                new FileStores());
    }

    @Test
    void test_success() throws Exception {
        final ForwardHttpPostConfig forwardHttpPostConfig = ForwardHttpPostConfig.builder()
                .build();
        final ThreadConfig threadConfig = new ThreadConfig(2, 2);
        final ForwardHttpPostDestination forwardHttpPostDestination = new ForwardHttpPostDestination(
                "TestDest",
                mockStreamDestination,
//                new MyStreamDestination(),
                cleanupDirQueue,
                forwardHttpPostConfig,
                proxyServices,
                dirQueueFactory,
                threadConfig,
                this::getDataDir,
                new SimplePathCreator(() -> homeDir, () -> tempDir));

        proxyServices.start();

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
        proxyServices.stop();
    }

    @Test
    void test_retryThenFail() throws Exception {
        final ForwardHttpPostConfig forwardHttpPostConfig = ForwardHttpPostConfig.builder()
                .retryDelay(StroomDuration.ofMillis(200))
                .retryDelayGrowthFactor(2)
                .maxRetryDelay(StroomDuration.ofSeconds(10))
                .maxRetryAge(StroomDuration.ofSeconds(5))
                .build();
        final ThreadConfig threadConfig = new ThreadConfig(2, 2);
        final ForwardHttpPostDestination forwardHttpPostDestination = new ForwardHttpPostDestination(
                "TestDest",
                mockStreamDestination,
                cleanupDirQueue,
                forwardHttpPostConfig,
                proxyServices,
                dirQueueFactory,
                threadConfig,
                this::getDataDir,
                new SimplePathCreator(() -> homeDir, () -> tempDir));

        proxyServices.start();

        final Instant startTime = Instant.now();
        final CountDownLatch sentLatch = new CountDownLatch(10);
        final AtomicInteger callCount = new AtomicInteger();
        Mockito.doAnswer(
                        invocation -> {
                            callCount.incrementAndGet();
                            sentLatch.countDown();
                            throw new RuntimeException("Send failed");
                        })
                .when(mockStreamDestination).send(Mockito.any(), Mockito.any());

        final Path source1 = createSourceDir(1);
        forwardHttpPostDestination.add(source1);

//        final boolean didCountDown = sentLatch.await(5, TimeUnit.SECONDS);
//        ThreadUtil.sleep(forwardHttpPostConfig.getMaxRetryAge());
//        assertThat(didCountDown)
//                .isTrue();

        final Path failureDir = forwardHttpPostDestination.getFailureDir();
        TestUtil.waitForIt(
                ThrowingSupplier.unchecked(() -> FileUtil.isEmptyDirectory(failureDir)),
                false,
                () -> "failureDir to be not empty",
                Duration.ofSeconds(10),
                Duration.ofMillis(100),
                Duration.ofSeconds(1));

        assertThat(callCount)
                .hasValueGreaterThan(1);

        assertThat(failureDir)
                .isNotEmptyDirectory();
        proxyServices.stop();
    }

    @Test
    void test_retryThenSuccess() throws Exception {
        final ForwardHttpPostConfig forwardHttpPostConfig = ForwardHttpPostConfig.builder()
                .maxRetryAge(StroomDuration.ofSeconds(10))
                .retryDelay(StroomDuration.ofMillis(500))
                .build();
        final ThreadConfig threadConfig = new ThreadConfig(2, 2);
        final ForwardHttpPostDestination forwardHttpPostDestination = new ForwardHttpPostDestination(
                "TestDest",
                mockStreamDestination,
                cleanupDirQueue,
                forwardHttpPostConfig,
                proxyServices,
                dirQueueFactory,
                threadConfig,
                this::getDataDir,
                new SimplePathCreator(() -> homeDir, () -> tempDir));

        proxyServices.start();

        final CountDownLatch sentLatch = new CountDownLatch(2);
        final AtomicInteger callCount = new AtomicInteger();
        Mockito.doAnswer(
                        invocation -> {
                            callCount.incrementAndGet();
                            sentLatch.countDown();
                            if (callCount.get() == 1) {
                                throw new RuntimeException("Send failed");
                            } else {
                                return null;
                            }
                        })
                .when(mockStreamDestination).send(Mockito.any(), Mockito.any());

        final Path source1 = createSourceDir(1);
        forwardHttpPostDestination.add(source1);

        final boolean didCountDown = sentLatch.await(5, TimeUnit.SECONDS);
        assertThat(didCountDown)
                .isTrue();

        // One fail, one success
        assertThat(callCount)
                .hasValue(2);

        // Give the failure dir a change to get a failure, which we expect not to happen
        ThreadUtil.sleep(500);

        final Path failureDir = forwardHttpPostDestination.getFailureDir();
        assertThat(failureDir)
                .isEmptyDirectory();
        proxyServices.stop();
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


    // --------------------------------------------------------------------------------


    private static class MyStreamDestination implements StreamDestination {

        @Override
        public void send(final AttributeMap attributeMap, final InputStream inputStream) throws ForwardException {

        }
    }
}
