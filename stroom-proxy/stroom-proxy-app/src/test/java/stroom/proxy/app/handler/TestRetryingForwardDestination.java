/*
 * Copyright 2025 Crown Copyright
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
import stroom.proxy.StroomStatusCode;
import stroom.proxy.app.handler.HttpSender.ResponseStatus;
import stroom.proxy.repo.ProxyServices;
import stroom.proxy.repo.queue.QueueMonitors;
import stroom.proxy.repo.store.FileStores;
import stroom.test.common.MockMetrics;
import stroom.test.common.TestUtil;
import stroom.util.exception.ThrowingConsumer;
import stroom.util.exception.ThrowingSupplier;
import stroom.util.io.FileUtil;
import stroom.util.io.SimplePathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.time.StroomDuration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestRetryingForwardDestination {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestRetryingForwardDestination.class);

    @Mock
    private ForwardDestination mockDelegateDestination;
    @Mock
    private FileStores mockFileStores;

    private Path dataDir;
    private Path homeDir;
    private Path tempDir;
    private Path sourcesDir;
    private ProxyServices proxyServices;
    private DirQueueFactory dirQueueFactory;

    @BeforeEach
    void setUp(@TempDir final Path baseDir) {
        this.dataDir = baseDir.resolve("data");
        this.homeDir = baseDir.resolve("home");
        this.tempDir = baseDir.resolve("temp");
        this.sourcesDir = baseDir.resolve("sources");
        this.proxyServices = new ProxyServices();
        this.dirQueueFactory = new DirQueueFactory(this::getDataDir,
                new QueueMonitors(new MockMetrics()),
                mockFileStores);

        Mockito.when(mockDelegateDestination.getName())
                .thenReturn("TestDest");
    }

    @Test
    void test_success() throws Exception {
        final ForwardHttpQueueConfig forwardQueueConfig = new ForwardHttpQueueConfig();

        final RetryingForwardDestination retryingForwardDestination = new RetryingForwardDestination(
                forwardQueueConfig,
                mockDelegateDestination,
                this::getDataDir,
                new SimplePathCreator(() -> homeDir, () -> tempDir),
                dirQueueFactory,
                proxyServices,
                mockFileStores);

        proxyServices.start();

        final CountDownLatch sentLatch = new CountDownLatch(1);
        Mockito.doAnswer(
                        invocation -> {
                            final Path dir = invocation.getArgument(0, Path.class);
                            LOGGER.info("send called");
                            sentLatch.countDown();
                            // The non-mocked delegate dest would delete on successful add()
                            FileUtil.deleteDir(dir);
                            return null;
                        })
                .when(mockDelegateDestination).add(Mockito.any());

        final Path source1 = createSourceDir(1);
        retryingForwardDestination.add(source1);

        final boolean didCountDown = sentLatch.await(5, TimeUnit.SECONDS);
        assertThat(didCountDown)
                .isTrue();
        proxyServices.stop();
    }

    @Test
    void test_retryAllFail() throws Exception {
        final ForwardHttpQueueConfig forwardQueueConfig = ForwardHttpQueueConfig.builder()
                .retryDelay(StroomDuration.ofMillis(200))
                .retryDelayGrowthFactor(2)
                .maxRetryDelay(StroomDuration.ofSeconds(10))
                .maxRetryAge(StroomDuration.ofSeconds(5))
                .build();

        final RetryingForwardDestination retryingForwardDestination = new RetryingForwardDestination(
                forwardQueueConfig,
                mockDelegateDestination,
                this::getDataDir,
                new SimplePathCreator(() -> homeDir, () -> tempDir),
                dirQueueFactory,
                proxyServices,
                mockFileStores);

        proxyServices.start();

        final CountDownLatch sentLatch = new CountDownLatch(10);
        final AtomicInteger callCount = new AtomicInteger();
        Mockito.doAnswer(
                        invocation -> {
                            callCount.incrementAndGet();
                            sentLatch.countDown();
                            throw new RuntimeException("Send failed");
                        })
                .when(mockDelegateDestination).add(Mockito.any());

        final Path source1 = createSourceDir(1);
        retryingForwardDestination.add(source1);

        final Path failureDir = retryingForwardDestination.getFailureDir();
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
        final ForwardHttpQueueConfig forwardQueueConfig = ForwardHttpQueueConfig.builder()
                .maxRetryAge(StroomDuration.ofSeconds(10))
                .retryDelay(StroomDuration.ofMillis(500))
                .build();

        final RetryingForwardDestination retryingForwardDestination = new RetryingForwardDestination(
                forwardQueueConfig,
                mockDelegateDestination,
                this::getDataDir,
                new SimplePathCreator(() -> homeDir, () -> tempDir),
                dirQueueFactory,
                proxyServices,
                mockFileStores);

        proxyServices.start();

        final CountDownLatch sentLatch = new CountDownLatch(2);
        final AtomicInteger callCount = new AtomicInteger();
        Mockito.doAnswer(
                        invocation -> {
                            LOGGER.debug("add called on mockDelegateDestination");
                            final Path dir = invocation.getArgument(0, Path.class);
                            callCount.incrementAndGet();
                            sentLatch.countDown();
                            if (callCount.get() == 1) {
                                throw new RuntimeException("Send failed");
                            } else {
                                // The non-mocked delegate dest would delete on successful add()
                                LOGGER.debug("Deleting {}", dir);
                                FileUtil.deleteDir(dir);
                                return null;
                            }
                        })
                .when(mockDelegateDestination).add(Mockito.any());

        final Path source1 = createSourceDir(1);
        retryingForwardDestination.add(source1);

        final boolean didCountDown = sentLatch.await(10, TimeUnit.SECONDS);
        assertThat(didCountDown)
                .isTrue();

        final Path failureDir = retryingForwardDestination.getFailureDir();
        TestUtil.waitForIt(
                ThrowingSupplier.unchecked(() ->
                        FileUtil.isEmptyDirectory(failureDir)),
                true,
                () -> "failureDir to be not empty",
                Duration.ofSeconds(10000),
                Duration.ofMillis(100),
                Duration.ofSeconds(1));

        // One fail, one success
        assertThat(callCount)
                .hasValue(2);
        proxyServices.stop();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void test_errorLogIncludesStroomStatus(final boolean queueAndRetryEnabled) throws Exception {
        final ForwardException exception = ForwardException.nonRecoverable(
                new ResponseStatus(StroomStatusCode.FEED_IS_NOT_SET_TO_RECEIVE_DATA,
                        null, "Not Acceptable", 406),
                new AttributeMap());

        final String errorLog = readFailureLog(exception, queueAndRetryEnabled);

        assertThat(errorLog)
                .contains(" - ForwardException - Not Acceptable")
                .contains("Stroom status: 110 - Feed is not set to receive data")
                .contains("HTTP status: 406");
        assertThat(errorLog.lines().count())
                .isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void test_errorLogPreservesUnknownStatusDetails(final boolean queueAndRetryEnabled) throws Exception {
        final ForwardException exception = ForwardException.recoverable(
                new ResponseStatus(StroomStatusCode.UNKNOWN_ERROR, null,
                        "Downstream unavailable\nPlease try later", 503),
                new AttributeMap());

        final String errorLog = readFailureLog(exception, queueAndRetryEnabled);

        assertThat(errorLog)
                .contains("Downstream unavailable Please try later")
                .contains("Stroom status: " + StroomStatusCode.UNKNOWN_ERROR.getCode())
                .contains(StroomStatusCode.UNKNOWN_ERROR.getMessage())
                .contains("HTTP status: 503");
        assertThat(errorLog.lines().count())
                .isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void test_errorLogPreservesOtherExceptions(final boolean queueAndRetryEnabled) throws Exception {
        final String errorLog = readFailureLog(new IllegalStateException("Connection failed"), queueAndRetryEnabled);

        assertThat(errorLog)
                .endsWith(" - IllegalStateException - Connection failed\n")
                .doesNotContain("Stroom status", "HTTP status");
    }

    private String readFailureLog(final Exception exception,
                                  final boolean queueAndRetryEnabled) throws Exception {
        final ForwardHttpQueueConfig config = ForwardHttpQueueConfig.builder()
                .forwardDelay(queueAndRetryEnabled)
                .maxRetryAge(StroomDuration.ZERO)
                .build();
        final RetryingForwardDestination destination = new RetryingForwardDestination(
                config, mockDelegateDestination, this::getDataDir,
                new SimplePathCreator(() -> homeDir, () -> tempDir),
                dirQueueFactory, proxyServices, mockFileStores);
        Mockito.doThrow(exception)
                .when(mockDelegateDestination)
                .add(Mockito.any());
        proxyServices.start();
        try {
            destination.add(createSourceDir(1));
            TestUtil.waitForIt(
                    () -> findErrorLogs(destination.getFailureDir()).size(),
                    1,
                    () -> "failure error.log to be written",
                    Duration.ofSeconds(5),
                    Duration.ofMillis(20),
                    Duration.ofSeconds(1));
            return Files.readString(findErrorLogs(destination.getFailureDir()).getFirst());
        } finally {
            proxyServices.stop();
        }
    }

    private List<Path> findErrorLogs(final Path failureDir) {
        try (final Stream<Path> paths = Files.walk(failureDir)) {
            return paths.filter(path -> path.getFileName().toString().equals("error.log"))
                    .toList();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
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
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return sourceDir;
    }
}
