/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.proxy.app;

import stroom.data.shared.StreamTypeNames;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.app.handler.DirUtil;
import stroom.proxy.app.handler.ForwardFileConfig;
import stroom.proxy.app.handler.ForwardFileQueueConfig;
import stroom.proxy.app.handler.LocalByteBuffer;
import stroom.proxy.app.handler.MockForwardFileDestination;
import stroom.proxy.app.handler.MockForwardFileDestinationFactory;
import stroom.proxy.app.handler.ReceiverFactory;
import stroom.proxy.app.handler.ZipWriter;
import stroom.proxy.repo.AggregatorConfig;
import stroom.receive.common.ReceiveDataConfig;
import stroom.receive.rules.shared.ReceiptCheckMode;
import stroom.security.api.CommonSecurityContext;
import stroom.test.common.DirectorySnapshot;
import stroom.test.common.DirectorySnapshot.Snapshot;
import stroom.test.common.util.test.FileSystemTestUtil;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.time.StroomDuration;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.google.common.base.Strings;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.dropwizard.core.setup.Environment;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class TestInnerProcessEndToEnd {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestInnerProcessEndToEnd.class);

    @Inject
    private CommonSecurityContext commonSecurityContext;

    @Test
    void testSimple() {
        final String feedName = FileSystemTestUtil.getUniqueTestString();
        test(1, 10_001, 10, receiverFactory ->
                sendSimpleData(receiverFactory, feedName));
    }

    @Test
    void testSimpleZip() {
        final String feedName = FileSystemTestUtil.getUniqueTestString();
        test(1, 10_001, 10, receiverFactory ->
                sendSimpleZip(receiverFactory, feedName, 1));
    }

    @Test
    void testSourceSplitting() {
//        final CompletableFuture[] arr = new CompletableFuture[1000];
//        for (int i = 0; i < arr.length; i++) {
//            final int instanceId = i;
//            final CompletableFuture<?> completableFuture = CompletableFuture.runAsync(() -> {
//                try {
        final String feedName1 = FileSystemTestUtil.getUniqueTestString();
        final String feedName2 = FileSystemTestUtil.getUniqueTestString();
        test(1,
                10001,
                20,
                receiverFactory ->
                        sendComplexZip(receiverFactory, feedName1, feedName2, 1));
//                } catch (final Exception e) {
//                    throw new RuntimeException(e);
//                }
//            });
//            arr[i] = completableFuture;
//        }
//        CompletableFuture.allOf(arr).join();
    }

    @Test
    void testAggregateSplitting() {
        final String feedName = FileSystemTestUtil.getUniqueTestString();
        test(1, 1, 2, receiverFactory ->
                sendSimpleZip(receiverFactory, feedName, 2001));
    }

    @Test
    void testSourceAndAggregateSplitting() {
        final String feedName1 = FileSystemTestUtil.getUniqueTestString();
        final String feedName2 = FileSystemTestUtil.getUniqueTestString();
        test(1, 1, 4, receiverFactory ->
                sendComplexZip(receiverFactory, feedName1, feedName2, 2001));
    }


    private void test(final int threadCount,
                      final int totalStreams,
                      final int expectedOutputStreamCount,
                      final Consumer<ReceiverFactory> sender) {
        try {
            final Path root = Files.createTempDirectory("stroom-proxy");
            LOGGER.debug("root: {}", root);
            ProxyLifecycle proxyLifecycle = null;
            try {
                FileUtil.deleteContents(root);

                final Path dataDir = root.resolve("data");
                final Path tempDir = root.resolve("temp");
                final Path homeDir = root.resolve("home");
                Files.createDirectories(tempDir);
                Files.createDirectories(homeDir);

                final ProxyConfig proxyConfig = ProxyConfig.builder()
                        .pathConfig(new ProxyPathConfig(
                                dataDir.toAbsolutePath().toString(),
                                homeDir.toAbsolutePath().toString(),
                                tempDir.toAbsolutePath().toString()))
                        .aggregatorConfig(AggregatorConfig.builder()
                                .maxItemsPerAggregate(1000)
                                .maxUncompressedByteSizeString("1G")
                                .aggregationFrequency(StroomDuration.ofSeconds(60))
                                .build())
                        .addForwardFileDestination(new ForwardFileConfig(true,
                                false,
                                "test",
                                "test",
                                null,
                                new ForwardFileQueueConfig(),
                                null,
                                null,
                                null))
                        .receiveDataConfig(ReceiveDataConfig.builder()
                                // Stop it trying to call out to a downstream stroom/proxy
                                .withReceiptCheckMode(ReceiptCheckMode.RECEIVE_ALL)
                                .build())
                        .build();

                final AbstractModule proxyModule = getModule(proxyConfig);
                final Injector injector = Guice.createInjector(proxyModule);
                injector.injectMembers(this);

                proxyLifecycle = injector.getInstance(ProxyLifecycle.class);
                final ReceiverFactory receiverFactory = injector.getInstance(ReceiverFactory.class);
                final MockForwardFileDestinationFactory forwardFileDestinationFactory = injector.getInstance(
                        MockForwardFileDestinationFactory.class);

                final CountDownLatch countDownLatch = new CountDownLatch(expectedOutputStreamCount);
                forwardFileDestinationFactory.getForwardFileDestination().setCountDownLatch(countDownLatch);
                proxyLifecycle.start();

                final AtomicInteger count = new AtomicInteger();
                final CompletableFuture<?>[] futures = new CompletableFuture[threadCount];
                for (int i = 0; i < threadCount; i++) {
                    futures[i] = CompletableFuture.runAsync(() -> {
                        boolean add = true;
                        while (add) {
                            if (count.incrementAndGet() > totalStreams) {
                                add = false;
                            } else {
                                sender.accept(receiverFactory);
                            }
                        }
                    });
                }

                CompletableFuture.allOf(futures).join();

                // Wait for all data to arrive.
                final MockForwardFileDestination forwardFileDestination =
                        forwardFileDestinationFactory.getForwardFileDestination();
                countDownLatch.await();

                // Examine data.
                final Path storeDir = forwardFileDestination.getStoreDir();
                final long maxId = DirUtil.getMaxDirId(storeDir);

                final Snapshot snapshot = DirectorySnapshot.of(storeDir);
                LOGGER.debug("snapshot:\n{}", snapshot);

                // Cope with final rolling output (hence +1).
                assertThat(maxId).isGreaterThanOrEqualTo(expectedOutputStreamCount);
                assertThat(maxId).isLessThanOrEqualTo(expectedOutputStreamCount + 1);

            } finally {
                if (proxyLifecycle != null) {
                    proxyLifecycle.stop();
                }
                FileUtil.deleteContents(root);
            }
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void sendSimpleData(final ReceiverFactory receiverFactory,
                                final String feedName) {
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put(StandardHeaderArguments.FEED, feedName);
        attributeMap.put(StandardHeaderArguments.TYPE, StreamTypeNames.RAW_EVENTS);
        final byte[] dataBytes = "test".getBytes(StandardCharsets.UTF_8);
        try (final InputStream inputStream = new ByteArrayInputStream(dataBytes)) {
            // asProcessingUser would normally be done in ProxyRequestHandler
            commonSecurityContext.asProcessingUser(() -> {
                receiverFactory.get(attributeMap).receive(
                        Instant.now(),
                        attributeMap,
                        "test",
                        () -> inputStream);
            });
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void sendSimpleZip(final ReceiverFactory receiverFactory,
                               final String feedName,
                               final int entryCount) {
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put(StandardHeaderArguments.FEED, feedName);
        attributeMap.put(StandardHeaderArguments.TYPE, StreamTypeNames.RAW_EVENTS);
        attributeMap.put(StandardHeaderArguments.COMPRESSION, StandardHeaderArguments.COMPRESSION_ZIP);
        final byte[] dataBytes;

        try (final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            try (final ZipWriter zipWriter = new ZipWriter(byteArrayOutputStream, LocalByteBuffer.get())) {
                for (int i = 1; i <= entryCount; i++) {
                    final String name = Strings.padStart(Integer.toString(i), 10, '0');
                    zipWriter.writeAttributeMap(name + ".meta", attributeMap);
                    zipWriter.writeString(name + ".dat", "test");
                }
            }

            dataBytes = byteArrayOutputStream.toByteArray();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }

        try (final InputStream inputStream = new ByteArrayInputStream(dataBytes)) {
            // asProcessingUser would normally be done in ProxyRequestHandler
            commonSecurityContext.asProcessingUser(() -> {
                receiverFactory.get(attributeMap).receive(
                        Instant.now(),
                        attributeMap,
                        "test",
                        () -> inputStream);
            });
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void sendComplexZip(final ReceiverFactory receiverFactory,
                                final String feedName1,
                                final String feedName2,
                                final int entryCount) {
        final byte[] dataBytes;

        try (final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            try (final ZipWriter zipWriter = new ZipWriter(byteArrayOutputStream, LocalByteBuffer.get())) {
                for (int i = 1; i <= entryCount; i++) {
                    final String name = Strings.padStart(Integer.toString(i), 10, '0');
                    final AttributeMap attributeMap1 = new AttributeMap();
                    attributeMap1.put(StandardHeaderArguments.FEED, feedName1);
                    attributeMap1.put(StandardHeaderArguments.TYPE, StreamTypeNames.RAW_EVENTS);
                    zipWriter.writeAttributeMap(name + "_1.meta", attributeMap1);
                    zipWriter.writeString(name + "_1.dat", "test");

                    final AttributeMap attributeMap2 = new AttributeMap();
                    attributeMap2.put(StandardHeaderArguments.FEED, feedName2);
                    attributeMap2.put(StandardHeaderArguments.TYPE, StreamTypeNames.RAW_EVENTS);
                    zipWriter.writeAttributeMap(name + "_2.meta", attributeMap2);
                    zipWriter.writeString(name + "_2.dat", "test");
                }
            }

            dataBytes = byteArrayOutputStream.toByteArray();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }

        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put(StandardHeaderArguments.COMPRESSION, StandardHeaderArguments.COMPRESSION_ZIP);
        try (final InputStream inputStream = new ByteArrayInputStream(dataBytes)) {
            // asProcessingUser would normally be done in ProxyRequestHandler
            commonSecurityContext.asProcessingUser(() -> {
                receiverFactory.get(attributeMap).receive(
                        Instant.now(),
                        attributeMap,
                        "test",
                        () -> inputStream);
            });
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private AbstractModule getModule(final ProxyConfig proxyConfig) {
        final Config config = new Config();
        config.setProxyConfig(proxyConfig);

        final Environment environmentMock = Mockito.mock(Environment.class);
        Mockito.when(environmentMock.healthChecks())
                .thenReturn(new HealthCheckRegistry());
        Mockito.when(environmentMock.metrics())
                .thenReturn(new MetricRegistry());

        return new ProxyTestModule(
                config,
                environmentMock,
//                new Environment("TestEnvironment"),
                Path.of("dummy/path/to/config.yml"));
    }
}
