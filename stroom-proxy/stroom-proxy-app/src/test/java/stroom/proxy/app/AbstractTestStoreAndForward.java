package stroom.proxy.app;

import stroom.data.shared.StreamTypeNames;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.app.handler.ForwardFileConfig;
import stroom.proxy.app.handler.NumericFileNameUtil;
import stroom.proxy.app.handler.ReceiverFactory;
import stroom.proxy.repo.AggregatorConfig;
import stroom.proxy.repo.ProxyRepoConfig;
import stroom.test.common.util.test.FileSystemTestUtil;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.io.FileUtil;
import stroom.util.time.StroomDuration;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;

abstract class AbstractTestStoreAndForward {

    @Inject
    private ProxyLifecycle proxyLifecycle;
    @Inject
    private ReceiverFactory receiverFactory;

    void testStoreAndForward() throws Exception {
        final Path root = Files.createTempDirectory("stroom-proxy");
        try {
            FileUtil.deleteContents(root);

            final Path tempDir = root.resolve("temp");
            final Path homeDir = root.resolve("home");
            final Path outDir = root.resolve("out");
            Files.createDirectories(tempDir);
            Files.createDirectories(homeDir);
            Files.createDirectories(outDir);
            final Path configPath = tempDir.resolve("temp-config.yml");

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
                            FileUtil.getCanonicalPath(outDir)))
                    .build();

            final Config config = new Config();
            config.setProxyConfig(proxyConfig);
            final AbstractModule proxyModule = getModule(config, configPath);
            final Injector injector = Guice.createInjector(proxyModule);
            injector.injectMembers(this);

            proxyLifecycle.start();

            final String feedName = FileSystemTestUtil.getUniqueTestString();

            final int threadCount = 1;
            final int totalStreams = 1_000;
            final AtomicInteger count = new AtomicInteger();
            final CompletableFuture[] futures = new CompletableFuture[threadCount];
            for (int i = 0; i < threadCount; i++) {
                futures[i] = CompletableFuture.runAsync(() -> {
                    try {
                        final AttributeMap attributeMap = new AttributeMap();
                        attributeMap.put(StandardHeaderArguments.FEED, feedName);
                        attributeMap.put(StandardHeaderArguments.TYPE, StreamTypeNames.RAW_EVENTS);
                        final byte[] dataBytes = "test".getBytes(StandardCharsets.UTF_8);
                        boolean add = true;
                        while (add) {
                            if (count.incrementAndGet() > totalStreams) {
                                add = false;
                            } else {
                                try (final InputStream inputStream = new ByteArrayInputStream(dataBytes)) {
                                    receiverFactory.get(attributeMap).receive(
                                            Instant.now(),
                                            attributeMap,
                                            "test",
                                            () -> inputStream
                                    );
                                }
                            }
                        }
                    } catch (final IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }

            CompletableFuture.allOf(futures).join();

            // Wait for all of the data to arrive.
            long maxId = NumericFileNameUtil.getMaxId(outDir);
            while (maxId < totalStreams) {
                ThreadUtil.sleep(1000);
                maxId = NumericFileNameUtil.getMaxId(outDir);
            }

        } finally {
            FileUtil.deleteContents(root);
        }
    }

    abstract AbstractModule getModule(final Config configuration,
                                      final Path configFile);
}
