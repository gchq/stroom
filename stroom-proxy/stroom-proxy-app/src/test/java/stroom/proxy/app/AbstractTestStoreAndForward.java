package stroom.proxy.app;

import stroom.data.shared.StreamTypeNames;
import stroom.data.zip.StroomZipFileType;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.app.forwarder.ForwardFileConfig;
import stroom.proxy.app.handler.ReceiveStreamHandlers;
import stroom.proxy.repo.AggregatorConfig;
import stroom.proxy.repo.ForwarderDestinations;
import stroom.proxy.repo.ProxyRepoConfig;
import stroom.proxy.repo.RepoSources;
import stroom.proxy.repo.dao.FeedDao;
import stroom.proxy.repo.dao.SourceDao;
import stroom.proxy.repo.dao.SourceItemDao;
import stroom.receive.common.ProgressHandler;
import stroom.test.common.util.test.FileSystemTestUtil;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.io.FileUtil;
import stroom.util.time.StroomDuration;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;

abstract class AbstractTestStoreAndForward {

    @Inject
    private ProxyLifecycle proxyLifecycle;
    @Inject
    private ReceiveStreamHandlers receiveStreamHandlers;
    @Inject
    private RepoSources proxyRepoSources;
    @Inject
    private FeedDao feedDao;
    @Inject
    private SourceDao sourceDao;
    @Inject
    private SourceItemDao sourceItemDao;
    @Inject
    private ForwarderDestinations forwarderDestinations;

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
                            .maxAggregateAge(StroomDuration.ofSeconds(1))
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
                        final byte[] metaBytes = AttributeMapUtil.toByteArray(attributeMap);
                        final byte[] dataBytes = "test".getBytes(StandardCharsets.UTF_8);
                        final ProgressHandler progressHandler = new ProgressHandler("Test");

                        boolean add = true;
                        while (add) {
                            if (count.incrementAndGet() > totalStreams) {
                                add = false;
                            } else {
                                receiveStreamHandlers.handle(feedName,
                                        StreamTypeNames.RAW_EVENTS,
                                        attributeMap,
                                        handler -> {
                                            try {
                                                handler.addEntry("1" + StroomZipFileType.META.getDotExtension(),
                                                        new ByteArrayInputStream(metaBytes), progressHandler);
                                                handler.addEntry("1" + StroomZipFileType.DATA.getDotExtension(),
                                                        new ByteArrayInputStream(dataBytes), progressHandler);
                                            } catch (final IOException e) {
                                                throw new UncheckedIOException(e);
                                            }
                                        });
                            }
                        }
                    } catch (final IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }

            CompletableFuture.allOf(futures).join();

            await(forwarderDestinations);

            // Wait for source dao to be cleared.
            while (sourceDao.countSources() > 0) {
                ThreadUtil.sleep(1000);
            }

        } finally {
            FileUtil.deleteContents(root);
        }
    }

    abstract AbstractModule getModule(final Config configuration,
                                      final Path configFile);

    abstract void await(ForwarderDestinations forwarderDestinations);
}
