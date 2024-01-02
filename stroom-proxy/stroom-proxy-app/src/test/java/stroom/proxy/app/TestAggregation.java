package stroom.proxy.app;

import stroom.data.shared.StreamTypeNames;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.app.handler.DirUtil;
import stroom.proxy.app.handler.ForwardFileConfig;
import stroom.proxy.app.handler.LocalByteBuffer;
import stroom.proxy.app.handler.MockForwardFileDestination;
import stroom.proxy.app.handler.MockForwardFileDestinationFactory;
import stroom.proxy.app.handler.ReceiverFactory;
import stroom.proxy.app.handler.ZipWriter;
import stroom.proxy.repo.AggregatorConfig;
import stroom.proxy.repo.ProxyRepoConfig;
import stroom.test.common.util.test.FileSystemTestUtil;
import stroom.util.io.FileUtil;
import stroom.util.time.StroomDuration;

import com.codahale.metrics.health.HealthCheckRegistry;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.dropwizard.setup.Environment;
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
import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

class TestAggregation {

    @Inject
    private ProxyLifecycle proxyLifecycle;
    @Inject
    private ReceiverFactory receiverFactory;
    @Inject
    private MockForwardFileDestinationFactory forwardFileDestinationFactory;

    @Test
    void testSimple() throws Exception {
        final String feedName = FileSystemTestUtil.getUniqueTestString();
        test(1, 10001, 10, () -> sendSimpleData(feedName));
    }

    @Test
    void testSimpleZip() throws Exception {
        final String feedName = FileSystemTestUtil.getUniqueTestString();
        test(1, 10001, 10, () -> sendSimpleZip(feedName));
    }

    @Test
    void testComplexZip() throws Exception {
        final String feedName1 = FileSystemTestUtil.getUniqueTestString();
        final String feedName2 = FileSystemTestUtil.getUniqueTestString();
        test(1, 10001, 20, () -> sendComplexZip(feedName1, feedName2));
    }


    private void test(final int threadCount,
                      final int totalStreams,
                      final int expectedOutputStreamCount,
                      final Runnable sender) throws Exception {

        final Path root = Files.createTempDirectory("stroom-proxy");
        try {
            FileUtil.deleteContents(root);

            final Path tempDir = root.resolve("temp");
            final Path homeDir = root.resolve("home");
            Files.createDirectories(tempDir);
            Files.createDirectories(homeDir);

            final ProxyConfig proxyConfig = ProxyConfig.builder()
                    .pathConfig(new ProxyPathConfig(
                            homeDir.toAbsolutePath().toString(),
                            tempDir.toAbsolutePath().toString()))
                    .proxyRepoConfig(ProxyRepoConfig.builder()
                            .storingEnabled(true)
                            .build())
                    .aggregatorConfig(AggregatorConfig.builder()
                            .maxItemsPerAggregate(1000)
                            .maxUncompressedByteSizeString("1G")
                            .maxAggregateAge(StroomDuration.ofSeconds(5))
                            .aggregationFrequency(StroomDuration.ofSeconds(1))
                            .build())
                    .addForwardDestination(new ForwardFileConfig(true, "test",
                            "test"))
                    .build();

            final AbstractModule proxyModule = getModule(proxyConfig);
            final Injector injector = Guice.createInjector(proxyModule);
            injector.injectMembers(this);

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
                            sender.run();
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
            long maxId = DirUtil.getMaxDirId(storeDir);

            // Cope with final rolling output (hence +1).
            assertThat(maxId).isGreaterThanOrEqualTo(expectedOutputStreamCount);
            assertThat(maxId).isLessThanOrEqualTo(expectedOutputStreamCount + 1);

        } finally {
            FileUtil.deleteContents(root);
        }
    }

    private void sendSimpleData(final String feedName) {
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put(StandardHeaderArguments.FEED, feedName);
        attributeMap.put(StandardHeaderArguments.TYPE, StreamTypeNames.RAW_EVENTS);
        final byte[] dataBytes = "test".getBytes(StandardCharsets.UTF_8);
        try (final InputStream inputStream = new ByteArrayInputStream(dataBytes)) {
            receiverFactory.get(attributeMap).receive(
                    Instant.now(),
                    attributeMap,
                    "test",
                    () -> inputStream
            );
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void sendSimpleZip(final String feedName) {
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put(StandardHeaderArguments.FEED, feedName);
        attributeMap.put(StandardHeaderArguments.TYPE, StreamTypeNames.RAW_EVENTS);
        attributeMap.put(StandardHeaderArguments.COMPRESSION, StandardHeaderArguments.COMPRESSION_ZIP);
        final byte[] dataBytes;

        try (final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            try (final ZipWriter zipWriter = new ZipWriter(byteArrayOutputStream, LocalByteBuffer.get())) {
                zipWriter.writeAttributeMap("001.meta", attributeMap);
                zipWriter.writeString("001.dat", "test");
            }

            dataBytes = byteArrayOutputStream.toByteArray();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }

        try (final InputStream inputStream = new ByteArrayInputStream(dataBytes)) {
            receiverFactory.get(attributeMap).receive(
                    Instant.now(),
                    attributeMap,
                    "test",
                    () -> inputStream
            );
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void sendComplexZip(final String feedName1, final String feedName2) {
        final byte[] dataBytes;

        try (final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            try (final ZipWriter zipWriter = new ZipWriter(byteArrayOutputStream, LocalByteBuffer.get())) {
                final AttributeMap attributeMap1 = new AttributeMap();
                attributeMap1.put(StandardHeaderArguments.FEED, feedName1);
                attributeMap1.put(StandardHeaderArguments.TYPE, StreamTypeNames.RAW_EVENTS);
                zipWriter.writeAttributeMap("001.meta", attributeMap1);
                zipWriter.writeString("001.dat", "test");

                final AttributeMap attributeMap2 = new AttributeMap();
                attributeMap2.put(StandardHeaderArguments.FEED, feedName2);
                attributeMap2.put(StandardHeaderArguments.TYPE, StreamTypeNames.RAW_EVENTS);
                zipWriter.writeAttributeMap("002.meta", attributeMap2);
                zipWriter.writeString("002.dat", "test");
            }

            dataBytes = byteArrayOutputStream.toByteArray();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }

        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put(StandardHeaderArguments.COMPRESSION, StandardHeaderArguments.COMPRESSION_ZIP);
        try (final InputStream inputStream = new ByteArrayInputStream(dataBytes)) {
            receiverFactory.get(attributeMap).receive(
                    Instant.now(),
                    attributeMap,
                    "test",
                    () -> inputStream
            );
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

        return new ProxyTestModule(
                config,
                new Environment("TestEnvironment"),
                Path.of("dummy/path/to/config.yml"));
    }
}
