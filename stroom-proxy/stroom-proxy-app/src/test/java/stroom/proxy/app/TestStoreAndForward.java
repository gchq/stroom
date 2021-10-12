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
import stroom.proxy.app.handler.ReceiveStreamHandlers;
import stroom.proxy.repo.ProgressHandler;
import stroom.proxy.repo.ProxyRepoDbConnProvider;
import stroom.proxy.repo.ProxyRepoFileNames;
import stroom.proxy.repo.ProxyRepoSourceEntries;
import stroom.proxy.repo.ProxyRepoSources;
import stroom.proxy.repo.dao.SourceDao;
import stroom.proxy.repo.dao.SourceDao.Source;
import stroom.proxy.repo.dao.SourceEntryDao;
import stroom.proxy.repo.db.jooq.tables.records.SourceEntryRecord;
import stroom.proxy.repo.db.jooq.tables.records.SourceItemRecord;
import stroom.test.common.util.test.FileSystemTestUtil;
import stroom.util.io.StreamUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.time.StroomDuration;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import javax.inject.Inject;

@Disabled
class TestStoreAndForward {

    private final byte[] buffer = new byte[StreamUtil.BUFFER_SIZE];

    @Inject
    ProxyLifecycle proxyLifecycle;
    @Inject
    ReceiveStreamHandlers receiveStreamHandlers;
    @Inject
    private ProxyRepoSources proxyRepoSources;
    @Inject
    private ProxyRepoSourceEntries proxyRepoSourceEntries;
    @Inject
    private SourceDao sourceDao;
    @Inject
    private SourceEntryDao sourceEntryDao;
    @Inject
    private ProxyRepoDbConnProvider proxyRepoDbConnProvider;

    @Test
    void testStoreAndForward() throws Exception {
        final ForwardDestinationConfig forwardDestinationConfig = new ForwardDestinationConfig();
        forwardDestinationConfig.setForwardUrl("null");

        final Path tempDir = Files.createTempDirectory("stroom-proxy-temp");
        final Path homeDir = Files.createTempDirectory("stroom-proxy-home");
        final Path configPath = tempDir.resolve("temp-config.yml");
        final ProxyConfig proxyConfig = new ProxyConfig();
        proxyConfig.getPathConfig().setTemp(tempDir.toAbsolutePath().toString());
        proxyConfig.getPathConfig().setHome(homeDir.toAbsolutePath().toString());
        proxyConfig.getProxyRepositoryConfig().setStoringEnabled(true);
        proxyConfig.getProxyRepositoryConfig().setFormat("${pathId}/${id}");
        proxyConfig.getProxyRepositoryConfig().setCleanupFrequency(StroomDuration.ofHours(1));
        proxyConfig.getProxyRepositoryConfig().setLockDeleteAge(StroomDuration.ofHours(1));
        proxyConfig.getProxyRepositoryConfig().setDirCleanDelay(StroomDuration.ofSeconds(10));
        proxyConfig.getProxyRepoFileScannerConfig().setScanningEnabled(false);
        proxyConfig.getProxyRepoFileScannerConfig().setScanFrequency(StroomDuration.ofSeconds(10));
        proxyConfig.getAggregatorConfig().setMaxItemsPerAggregate(1000);
        proxyConfig.getAggregatorConfig().setMaxUncompressedByteSizeString("1G");
        proxyConfig.getAggregatorConfig().setMaxAggregateAge(StroomDuration.ofMinutes(10));
        proxyConfig.getAggregatorConfig().setAggregationFrequency(StroomDuration.ofMinutes(1));
        proxyConfig.getForwarderConfig().setForwardingEnabled(true);
        proxyConfig.getForwarderConfig().setForwardDestinations(List.of(forwardDestinationConfig));

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

        for (int i = 0; i < 1000000; i++) {
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
    void testForwardOldStore() throws Exception {
//        final ForwardDestinationConfig forwardDestinationConfig = new ForwardDestinationConfig();
//        forwardDestinationConfig.setForwardUrl("null");
//
        final Path tempDir = Paths.get("/home/stroomdev66/tmp/proxy_test/tmp");
        //Files.createTempDirectory("stroom-proxy-temp");
        final Path homeDir = Paths.get("/home/stroomdev66/tmp/proxy_test");
        //Files.createTempDirectory("stroom-proxy-home");
        final Path configPath = tempDir.resolve("temp-config.yml");
//        final ProxyConfig proxyConfig = new ProxyConfig();
//        proxyConfig.getPathConfig().setTemp(tempDir.toAbsolutePath().toString());
//        proxyConfig.getPathConfig().setHome(homeDir.toAbsolutePath().toString());
//        proxyConfig.getProxyRepositoryConfig().setStoringEnabled(true);
//        proxyConfig.getProxyRepositoryConfig().setFormat("${pathId}/${id}");
//
//        final Config config = new Config();
//        config.setProxyConfig(proxyConfig);
//        final StoreAndForwardTestModule proxyModule = new StoreAndForwardTestModule(config, configPath);
//        Injector injector = Guice.createInjector(proxyModule);
//        injector.injectMembers(this);
//
//        proxyLifecycle.start();
//
        final String feedName1 = FileSystemTestUtil.getUniqueTestString();
        final AttributeMap attributeMap1 = new AttributeMap();
        attributeMap1.put(StandardHeaderArguments.FEED, feedName1);
        attributeMap1.put(StandardHeaderArguments.TYPE, StreamTypeNames.RAW_EVENTS);
        final ProgressHandler progressHandler = new ProgressHandler("Test");

        final Path repoDir = homeDir.resolve("repo");
        final AtomicLong fileCount = new AtomicLong();
        for (int i = 0; i < 100000; i++) {
            try (final StroomZipOutputStream stroomZipOutputStream =
                    getStroomZipOutputStream(
                            "test",
                            repoDir,
                            fileCount,
                            attributeMap1)) {
                addEntry("1" + StroomZipFileType.META.getExtension(),
                        new ByteArrayInputStream(new byte[100]), progressHandler, stroomZipOutputStream);
                addEntry("1" + StroomZipFileType.CONTEXT.getExtension(),
                        new ByteArrayInputStream(new byte[100]), progressHandler, stroomZipOutputStream);
                addEntry("1" + StroomZipFileType.DATA.getExtension(),
                        new ByteArrayInputStream(new byte[100]), progressHandler, stroomZipOutputStream);
            }
        }

        final ForwardDestinationConfig forwardDestinationConfig = new ForwardDestinationConfig();
        forwardDestinationConfig.setForwardUrl("null");

        final ProxyConfig proxyConfig = new ProxyConfig();
        proxyConfig.getPathConfig().setTemp(tempDir.toAbsolutePath().toString());
        proxyConfig.getPathConfig().setHome(homeDir.toAbsolutePath().toString());
        proxyConfig.getProxyRepositoryConfig().setStoringEnabled(true);
        proxyConfig.getProxyRepositoryConfig().setFormat("${pathId}/${id}");
        proxyConfig.getProxyRepositoryConfig().setCleanupFrequency(StroomDuration.ofHours(1));
        proxyConfig.getProxyRepositoryConfig().setLockDeleteAge(StroomDuration.ofHours(1));
        proxyConfig.getProxyRepositoryConfig().setDirCleanDelay(StroomDuration.ofSeconds(10));
        proxyConfig.getProxyRepoFileScannerConfig().setScanningEnabled(true);
        proxyConfig.getProxyRepoFileScannerConfig().setScanFrequency(StroomDuration.ofSeconds(10));
        proxyConfig.getAggregatorConfig().setMaxItemsPerAggregate(100);
        proxyConfig.getAggregatorConfig().setMaxUncompressedByteSizeString("1G");
        proxyConfig.getAggregatorConfig().setMaxAggregateAge(StroomDuration.ofMinutes(1));
        proxyConfig.getAggregatorConfig().setAggregationFrequency(StroomDuration.ofMinutes(1));
        proxyConfig.getForwarderConfig().setForwardingEnabled(true);
        proxyConfig.getForwarderConfig().setForwardDestinations(List.of(forwardDestinationConfig));

        final Config config = new Config();
        config.setProxyConfig(proxyConfig);
        final StoreAndForwardTestModule proxyModule = new StoreAndForwardTestModule(config, configPath);
        final Injector injector = Guice.createInjector(proxyModule);
        injector.injectMembers(this);

        proxyLifecycle.start();
//
        Thread.sleep(1000000);
    }

    public long addEntry(final String entry,
                         final InputStream inputStream,
                         final Consumer<Long> progressHandler,
                         final StroomZipOutputStream stroomZipOutputStream) throws IOException {
        long bytesWritten;
        try (final OutputStream outputStream = stroomZipOutputStream.addEntry(entry)) {
            bytesWritten = StreamUtil.streamToStream(inputStream, outputStream, buffer, progressHandler);
        }
        return bytesWritten;
    }


    @Test
    void testForwardPerformance() throws Exception {
//        final ForwardDestinationConfig forwardDestinationConfig = new ForwardDestinationConfig();
//        forwardDestinationConfig.setForwardUrl("null");
//
        final Path tempDir = Paths.get("/home/stroomdev66/tmp/proxy_test/tmp");
        //Files.createTempDirectory("stroom-proxy-temp");
        final Path homeDir = Paths.get("/home/stroomdev66/tmp/proxy_test");
        //Files.createTempDirectory("stroom-proxy-home");
        final Path configPath = tempDir.resolve("temp-config.yml");


        final ForwardDestinationConfig forwardDestinationConfig = new ForwardDestinationConfig();
        forwardDestinationConfig.setForwardUrl("null");

        final ProxyConfig proxyConfig = new ProxyConfig();
        proxyConfig.getPathConfig().setTemp(tempDir.toAbsolutePath().toString());
        proxyConfig.getPathConfig().setHome(homeDir.toAbsolutePath().toString());
        proxyConfig.getProxyRepositoryConfig().setStoringEnabled(true);
        proxyConfig.getProxyRepositoryConfig().setFormat("${pathId}/${id}");
        proxyConfig.getProxyRepositoryConfig().setCleanupFrequency(StroomDuration.ofHours(1));
        proxyConfig.getProxyRepositoryConfig().setLockDeleteAge(StroomDuration.ofHours(1));
        proxyConfig.getProxyRepositoryConfig().setDirCleanDelay(StroomDuration.ofSeconds(10));
        proxyConfig.getProxyRepoFileScannerConfig().setScanningEnabled(false);
        proxyConfig.getProxyRepoFileScannerConfig().setScanFrequency(StroomDuration.ofSeconds(10));
        proxyConfig.getAggregatorConfig().setMaxItemsPerAggregate(100);
        proxyConfig.getAggregatorConfig().setMaxUncompressedByteSizeString("1G");
        proxyConfig.getAggregatorConfig().setMaxAggregateAge(StroomDuration.ofMinutes(1));
        proxyConfig.getAggregatorConfig().setAggregationFrequency(StroomDuration.ofMinutes(1));
        proxyConfig.getForwarderConfig().setForwardingEnabled(true);
        proxyConfig.getForwarderConfig().setForwardDestinations(List.of(forwardDestinationConfig));

        final Config config = new Config();
        config.setProxyConfig(proxyConfig);
        final StoreAndForwardTestModule proxyModule = new StoreAndForwardTestModule(config, configPath);
        final Injector injector = Guice.createInjector(proxyModule);
        injector.injectMembers(this);

//        proxyLifecycle.start();

        final ExecutorService executorService = Executors.newFixedThreadPool(24);

        long start = System.currentTimeMillis();
        final CompletableFuture[] futures = new CompletableFuture[2500]; //4000 = 16 s      100000 = 5.6m   10000 = 38s
        for (int i = 0; i < futures.length; i++) {
            final CompletableFuture completableFuture = CompletableFuture.runAsync(this::testUnique, executorService);
            futures[i] = completableFuture;
        }

        CompletableFuture.allOf(futures).join();

        System.out.println("Completed in " + ModelStringUtil.formatDurationString(System.currentTimeMillis() - start));
    }

    void testUnique() {
        final Source source = proxyRepoSources.addSource(
                UUID.randomUUID().toString(),
                "test",
                null,
                System.currentTimeMillis(),
                null);
        final long sourceId = source.getSourceId();
        final String path = source.getSourcePath();

        addEntriesToSource(sourceId, path, 10, 10);
    }

    void addEntriesToSource(final long sourceId,
                            final String path,
                            final int loopCount,
                            final int feedCount) {
        final Map<String, SourceItemRecord> itemNameMap = new HashMap<>();
        final Map<Long, List<SourceEntryRecord>> entryMap = new HashMap<>();

        final List<StroomZipFileType> types = List.of(
                StroomZipFileType.META,
                StroomZipFileType.CONTEXT,
                StroomZipFileType.DATA);

        for (int i = 0; i < loopCount; i++) {
            for (int j = 0; j < feedCount; j++) {
                final String dataName = "entry_" + i + "_" + j;
                final String feedName = "feed_" + j;
                final String typeName = StreamTypeNames.RAW_EVENTS;

                for (final StroomZipFileType type : types) {
                    long sourceItemId;
                    int extensionType = -1;
                    if (StroomZipFileType.META.equals(type)) {
                        extensionType = 1;
                    } else if (StroomZipFileType.CONTEXT.equals(type)) {
                        extensionType = 2;
                    } else if (StroomZipFileType.DATA.equals(type)) {
                        extensionType = 3;
                    }

                    SourceItemRecord sourceItemRecord = itemNameMap.get(dataName);
                    if (sourceItemRecord == null) {
                        sourceItemId = sourceEntryDao.nextSourceItemId();
                        sourceItemRecord = new SourceItemRecord(
                                sourceItemId,
                                dataName,
                                feedName,
                                typeName,
                                sourceId,
                                false);
                        itemNameMap.put(dataName, sourceItemRecord);
                    } else {
                        sourceItemId = sourceItemRecord.getId();
                    }

                    entryMap
                            .computeIfAbsent(sourceItemId, k -> new ArrayList<>())
                            .add(new SourceEntryRecord(
                                    sourceEntryDao.nextSourceEntryId(),
                                    type.getExtension(),
                                    extensionType,
                                    1000L,
                                    sourceItemId));
                }
            }
        }

        sourceEntryDao.addEntries(
                Paths.get(path),
                sourceId,
                itemNameMap,
                entryMap);
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
