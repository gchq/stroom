package stroom.proxy.app;

import stroom.data.shared.StreamTypeNames;
import stroom.data.zip.StroomFileNameUtil;
import stroom.data.zip.StroomZipFileType;
import stroom.data.zip.StroomZipOutputStream;
import stroom.data.zip.StroomZipOutputStreamImpl;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.app.forwarder.ForwardDestinationConfig;
import stroom.proxy.app.forwarder.ForwarderConfig;
import stroom.proxy.app.handler.ReceiveStreamHandlers;
import stroom.proxy.repo.AggregatorConfig;
import stroom.proxy.repo.FeedKey;
import stroom.proxy.repo.ProgressHandler;
import stroom.proxy.repo.ProgressLog;
import stroom.proxy.repo.ProxyRepoConfig;
import stroom.proxy.repo.ProxyRepoFileNames;
import stroom.proxy.repo.RepoSource;
import stroom.proxy.repo.RepoSourceEntry;
import stroom.proxy.repo.RepoSourceItem;
import stroom.proxy.repo.RepoSourceItems;
import stroom.proxy.repo.RepoSources;
import stroom.proxy.repo.dao.FeedDao;
import stroom.proxy.repo.dao.SourceDao;
import stroom.proxy.repo.dao.SourceItemDao;
import stroom.test.common.util.test.FileSystemTestUtil;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.time.StroomDuration;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;

class TestStoreAndForward {

    private final byte[] buffer = new byte[StreamUtil.BUFFER_SIZE];

    @Inject
    private ProxyLifecycle proxyLifecycle;
    @Inject
    private ReceiveStreamHandlers receiveStreamHandlers;
    @Inject
    private RepoSources proxyRepoSources;
    @Inject
    private RepoSourceItems proxyRepoSourceEntries;
    @Inject
    private FeedDao feedDao;
    @Inject
    private SourceDao sourceDao;
    @Inject
    private SourceItemDao sourceItemDao;
    @Inject
    private ProgressLog progressLog;

    @Test
    void testStoreAndForward() throws Exception {
        final Path tempDir = Files.createTempDirectory("stroom-proxy-temp");
        final Path homeDir = Files.createTempDirectory("stroom-proxy-home");
        final Path configPath = tempDir.resolve("temp-config.yml");
        FileUtil.deleteContents(homeDir);

        final ProxyConfig proxyConfig = ProxyConfig.builder()
                .pathConfig(new ProxyPathConfig(
                        homeDir.toAbsolutePath().toString(),
                        tempDir.toAbsolutePath().toString()))
                .proxyRepoConfig(ProxyRepoConfig.builder()
                        .storingEnabled(true)
                        .format("${pathId}/${id}")
                        .cleanupFrequency(StroomDuration.ofHours(1))
                        .lockDeleteAge(StroomDuration.ofHours(1))
                        .dirCleanDelay(StroomDuration.ofSeconds(10))
                        .build())
                .aggregatorConfig(AggregatorConfig.builder()
                        .maxItemsPerAggregate(1000)
                        .maxUncompressedByteSizeString("1G")
                        .maxAggregateAge(StroomDuration.ofMinutes(10))
                        .aggregationFrequency(StroomDuration.ofMinutes(1))
                        .build())
                .forwarderConfig(new ForwarderConfig()
                        .forwardingEnabled(true)
                        .forwardDestinations(new ForwardDestinationConfig()
                                .withForwardUrl("null")))
                .build();

        final Config config = new Config();
        config.setProxyConfig(proxyConfig);
        final StoreAndForwardTestModule proxyModule = new StoreAndForwardTestModule(config, configPath);
        final Injector injector = Guice.createInjector(proxyModule);
        injector.injectMembers(this);

        proxyLifecycle.start();

        final String feedName1 = FileSystemTestUtil.getUniqueTestString();
        final AttributeMap attributeMap1 = new AttributeMap();
        attributeMap1.put(StandardHeaderArguments.FEED, feedName1);
        attributeMap1.put(StandardHeaderArguments.TYPE, StreamTypeNames.RAW_EVENTS);
        final ProgressHandler progressHandler = new ProgressHandler("Test");

        for (int i = 0; i < 10; i++) {
            receiveStreamHandlers.handle(feedName1, StreamTypeNames.RAW_EVENTS, attributeMap1, handler -> {
                try {
                    handler.addEntry("1" + StroomZipFileType.META.getExtension(),
                            new ByteArrayInputStream(new byte[0]), progressHandler);
                    handler.addEntry("1" + StroomZipFileType.CONTEXT.getExtension(),
                            new ByteArrayInputStream(new byte[0]), progressHandler);
                    handler.addEntry("1" + StroomZipFileType.DATA.getExtension(),
                            new ByteArrayInputStream(new byte[0]), progressHandler);
                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    @Test
    void testForwardPerformance() throws Exception {
        final Path tempDir = Files.createTempDirectory("stroom-proxy-temp");
        final Path homeDir = Files.createTempDirectory("stroom-proxy-home");
        final Path configPath = tempDir.resolve("temp-config.yml");
        FileUtil.deleteContents(homeDir);

        final ProxyConfig proxyConfig = ProxyConfig.builder()
                .pathConfig(new ProxyPathConfig(
                        homeDir.toAbsolutePath().toString(),
                        tempDir.toAbsolutePath().toString()))
                .proxyRepoConfig(ProxyRepoConfig.builder()
                        .storingEnabled(true)
                        .format("${pathId}/${id}")
                        .cleanupFrequency(StroomDuration.ofHours(1))
                        .lockDeleteAge(StroomDuration.ofHours(1))
                        .dirCleanDelay(StroomDuration.ofSeconds(10))
                        .build())
                .aggregatorConfig(AggregatorConfig.builder()
                        .maxItemsPerAggregate(100)
                        .maxUncompressedByteSizeString("1G")
                        .maxAggregateAge(StroomDuration.ofMinutes(1))
                        .aggregationFrequency(StroomDuration.ofMinutes(1))
                        .build())
                .forwarderConfig(new ForwarderConfig()
                        .forwardingEnabled(true)
                        .forwardDestinations(new ForwardDestinationConfig().withForwardUrl("null"))
                )
                .build();

        final Config config = new Config();
        config.setProxyConfig(proxyConfig);
        final StoreAndForwardTestModule proxyModule = new StoreAndForwardTestModule(config, configPath);
        final Injector injector = Guice.createInjector(proxyModule);
        injector.injectMembers(this);

//        proxyLifecycle.start();

        final ExecutorService executorService = Executors.newFixedThreadPool(24);

        long start = System.currentTimeMillis();
        final CompletableFuture<?>[] futures = new CompletableFuture[10]; // 4000 = 16 s  100000 = 5.6m  10000 = 38s
        final AtomicLong fileStoreId = new AtomicLong();
        for (int i = 0; i < futures.length; i++) {
            final CompletableFuture<?> completableFuture =
                    CompletableFuture.runAsync(() -> testUnique(fileStoreId.incrementAndGet()), executorService);
            futures[i] = completableFuture;
        }

        CompletableFuture.allOf(futures).join();

        System.out.println("Completed in " + ModelStringUtil.formatDurationString(System.currentTimeMillis() - start));
    }

    void testUnique(final long fileStoreId) {
        proxyRepoSources
                .addSource(
                        fileStoreId,
                        "test",
                        null,
                        null);
        proxyRepoSources.flush();
        proxyRepoSources.getNewSources(0, TimeUnit.SECONDS).list().forEach(source ->
                addEntriesToSource(source, 10, 10));
    }

    void addEntriesToSource(final RepoSource source,
                            final int loopCount,
                            final int feedCount) {
        final Map<String, RepoSourceItem> itemNameMap = new HashMap<>();
        final List<StroomZipFileType> types = List.of(
                StroomZipFileType.META,
                StroomZipFileType.CONTEXT,
                StroomZipFileType.DATA);

        for (int i = 0; i < loopCount; i++) {
            for (int j = 0; j < feedCount; j++) {
                final String dataName = "entry_" + i + "_" + j;
                final String feedName = "feed_" + j;
                final String typeName = StreamTypeNames.RAW_EVENTS;
                final long feedId = feedDao.getId(new FeedKey(feedName, typeName));

                for (final StroomZipFileType type : types) {
                    final RepoSourceItem item = new RepoSourceItem(
                            source,
                            dataName,
                            feedId,
                            null,
                            0,
                            new ArrayList<>());

                    final RepoSourceEntry entry = new RepoSourceEntry(
                            type,
                            type.getExtension(),
                            1000L);
                    item.addEntry(entry);
                }
            }
        }

        sourceItemDao.addItems(source, itemNameMap.values());
        sourceItemDao.flush();
    }


    public synchronized StroomZipOutputStream getStroomZipOutputStream(final String executionUuid,
                                                                       final Path repoDir,
                                                                       final AtomicLong fileCount,
                                                                       final AttributeMap attributeMap)
            throws IOException {
        // Create directories and files in a synchronized way so that the clean() method will not remove empty
        // directories that we are just about to write to.
        final String fileName = StroomFileNameUtil.constructFilename(executionUuid,
                fileCount.incrementAndGet(),
                "${pathId}/${id}",
                attributeMap);

        final String zipFileName = ProxyRepoFileNames.getZip(fileName);
        final String metaFileName = ProxyRepoFileNames.getMeta(fileName);

        final Path zipFile = repoDir.resolve(zipFileName);
        final Path metaFile = repoDir.resolve(metaFileName);

        return new StroomZipOutputStreamImpl(zipFile) {
            private boolean closed = false;

            @Override
            public void close() throws IOException {
                // Don't try and close more than once.
                if (!closed) {
                    closed = true;

                    // Write the meta data.
                    try (final OutputStream metaOutputStream = Files.newOutputStream(metaFile)) {
                        AttributeMapUtil.write(attributeMap, metaOutputStream);
                        super.close();

                    } catch (final IOException e) {
                        super.closeDelete();
                        throw new UncheckedIOException(e);
                    }
                }
            }

            @Override
            public void closeDelete() throws IOException {
                // Don't try and close more than once.
                if (!closed) {
                    closed = true;

                    super.closeDelete();
                }
            }
        };
    }
}
