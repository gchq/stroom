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
import stroom.proxy.repo.ProgressHandler;
import stroom.proxy.repo.ProgressLog;
import stroom.proxy.repo.ProxyRepoConfig;
import stroom.proxy.repo.ProxyRepoDbConnProvider;
import stroom.proxy.repo.ProxyRepoFileNames;
import stroom.proxy.repo.ProxyRepoFileScannerConfig;
import stroom.proxy.repo.RepoSource;
import stroom.proxy.repo.RepoSourceEntry;
import stroom.proxy.repo.RepoSourceItem;
import stroom.proxy.repo.RepoSourceItems;
import stroom.proxy.repo.RepoSources;
import stroom.proxy.repo.dao.SourceDao;
import stroom.proxy.repo.dao.SourceItemDao;
import stroom.test.common.util.test.FileSystemTestUtil;
import stroom.util.io.FileUtil;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.inject.Inject;

class TestStoreAndForward {

    private final byte[] buffer = new byte[StreamUtil.BUFFER_SIZE];

    @Inject
    ProxyLifecycle proxyLifecycle;
    @Inject
    ReceiveStreamHandlers receiveStreamHandlers;
    @Inject
    private RepoSources proxyRepoSources;
    @Inject
    private RepoSourceItems proxyRepoSourceEntries;
    @Inject
    private SourceDao sourceDao;
    @Inject
    private SourceItemDao sourceItemDao;
    @Inject
    private ProxyRepoDbConnProvider proxyRepoDbConnProvider;
    @Inject
    private ProgressLog progressLog;

    @Test
    void testStoreAndForward() throws Exception {
        final Path tempDir = Files.createTempDirectory("stroom-proxy-temp");
        final Path homeDir = Files.createTempDirectory("stroom-proxy-home");
        final Path configPath = tempDir.resolve("temp-config.yml");
        FileUtil.deleteContents(homeDir);

        final ProxyConfig proxyConfig = ProxyConfig.builder()
                .withPathConfig(new ProxyPathConfig(
                        homeDir.toAbsolutePath().toString(),
                        tempDir.toAbsolutePath().toString()))
                .withProxyRepoConfig(ProxyRepoConfig.builder()
                        .withStoringEnabled(true)
                        .withFormat("${pathId}/${id}")
                        .withCleanupFrequency(StroomDuration.ofHours(1))
                        .withLockDeleteAge(StroomDuration.ofHours(1))
                        .withDirCleanDelay(StroomDuration.ofSeconds(10))
                        .build())
                .withProxyRepoFileScannerConfig(new ProxyRepoFileScannerConfig()
                        .withScanningEnabled(false)
                        .withScanFrequency(StroomDuration.ofSeconds(10)))
                .withAggregatorConfig(AggregatorConfig.builder()
                        .withMaxItemsPerAggregate(1000)
                        .withMaxUncompressedByteSizeString("1G")
                        .withMaxAggregateAge(StroomDuration.ofMinutes(10))
                        .withAggregationFrequency(StroomDuration.ofMinutes(1))
                        .build())
                .withForwarderConfig(new ForwarderConfig()
                        .withForwardingEnabled(true)
                        .withForwardDestinations(new ForwardDestinationConfig()
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

    @Disabled
    @Test
    void testForwardOldStore() throws Exception {
//        final ForwardDestinationConfig forwardDestinationConfig = new ForwardDestinationConfig();
//        forwardDestinationConfig.setForwardUrl("null");

        final long iterations = 1000000;
//
        final Path tempDir = Files.createTempDirectory("stroom-proxy-temp");
        final Path homeDir = Files.createTempDirectory("stroom-proxy-home");
        final Path configPath = tempDir.resolve("temp-config.yml");
        FileUtil.deleteContents(homeDir);

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
        for (int i = 0; i < iterations; i++) {
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

        final ProxyConfig proxyConfig = ProxyConfig.builder()
                .withPathConfig(new ProxyPathConfig(
                        homeDir.toAbsolutePath().toString(),
                        tempDir.toAbsolutePath().toString()))
                .withProxyRepoConfig(ProxyRepoConfig.builder()
                        .withStoringEnabled(true)
                        .withFormat("${pathId}/${id}")
                        .withCleanupFrequency(StroomDuration.ofHours(1))
                        .withLockDeleteAge(StroomDuration.ofHours(1))
                        .withDirCleanDelay(StroomDuration.ofSeconds(10))
                        .build())
                .withProxyRepoFileScannerConfig(new ProxyRepoFileScannerConfig()
                        .withScanningEnabled(true)
                        .withScanFrequency(StroomDuration.ofSeconds(10)))
                .withAggregatorConfig(AggregatorConfig.builder()
                        .withMaxItemsPerAggregate(100)
                        .withMaxUncompressedByteSizeString("1G")
                        .withMaxAggregateAge(StroomDuration.ofMinutes(1))
                        .withAggregationFrequency(StroomDuration.ofMinutes(1))
                        .build())
                .withForwarderConfig(new ForwarderConfig()
                        .withForwardingEnabled(true)
                        .withForwardDestinations(new ForwardDestinationConfig()
                                .withForwardUrl("null")))
                .build();

        final Config config = new Config();
        config.setProxyConfig(proxyConfig);
        final StoreAndForwardTestModule proxyModule = new StoreAndForwardTestModule(config, configPath);
        final Injector injector = Guice.createInjector(proxyModule);
        injector.injectMembers(this);

        // Always log after we hot the iteration count.
        progressLog.selAutoLogCount(iterations);

        proxyLifecycle.start();

//        while (true) {
//            progressLog.report();
//            Thread.sleep(1000);
//        }
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
        final Path tempDir = Files.createTempDirectory("stroom-proxy-temp");
        final Path homeDir = Files.createTempDirectory("stroom-proxy-home");
        final Path configPath = tempDir.resolve("temp-config.yml");
        FileUtil.deleteContents(homeDir);

        final ProxyConfig proxyConfig = ProxyConfig.builder()
                .withPathConfig(new ProxyPathConfig(
                        homeDir.toAbsolutePath().toString(),
                        tempDir.toAbsolutePath().toString()))
                .withProxyRepoConfig(ProxyRepoConfig.builder()
                        .withStoringEnabled(true)
                        .withFormat("${pathId}/${id}")
                        .withCleanupFrequency(StroomDuration.ofHours(1))
                        .withLockDeleteAge(StroomDuration.ofHours(1))
                        .withDirCleanDelay(StroomDuration.ofSeconds(10))
                        .build())
                .withProxyRepoFileScannerConfig(new ProxyRepoFileScannerConfig()
                        .withScanningEnabled(false)
                        .withScanFrequency(StroomDuration.ofSeconds(10)))
                .withAggregatorConfig(AggregatorConfig.builder()
                        .withMaxItemsPerAggregate(100)
                        .withMaxUncompressedByteSizeString("1G")
                        .withMaxAggregateAge(StroomDuration.ofMinutes(1))
                        .withAggregationFrequency(StroomDuration.ofMinutes(1))
                        .build())
                .withForwarderConfig(new ForwarderConfig()
                        .withForwardingEnabled(true)
                        .withForwardDestinations(new ForwardDestinationConfig()
                                .withForwardUrl("null")))
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
        for (int i = 0; i < futures.length; i++) {
            final CompletableFuture<?> completableFuture =
                    CompletableFuture.runAsync(this::testUnique, executorService);
            futures[i] = completableFuture;
        }

        CompletableFuture.allOf(futures).join();

        System.out.println("Completed in " + ModelStringUtil.formatDurationString(System.currentTimeMillis() - start));
    }

    void testUnique() {
        proxyRepoSources
                .addSource(
                        UUID.randomUUID().toString(),
                        "test",
                        null,
                        System.currentTimeMillis(),
                        null);
        proxyRepoSources.getNewSource().ifPresent(source ->
                addEntriesToSource(source, 10, 10));
    }

    void addEntriesToSource(final RepoSource source,
                            final int loopCount,
                            final int feedCount) {
        final Map<String, RepoSourceItem.Builder> itemNameMap = new HashMap<>();

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
                    final RepoSourceItem.Builder builder = itemNameMap.computeIfAbsent(dataName, k ->
                            RepoSourceItem.builder()
                                    .source(source)
                                    .name(dataName)
                                    .feedName(feedName)
                                    .typeName(typeName));

                    builder.addEntry(RepoSourceEntry.builder()
                            .type(type)
                            .extension(type.getExtension())
                            .byteSize(1000L)
                            .build());
                }
            }
        }

        sourceItemDao.addItems(
                Paths.get(source.getSourcePath()),
                source.getId(),
                itemNameMap
                        .values()
                        .stream()
                        .map(RepoSourceItem.Builder::build)
                        .collect(Collectors.toList()));
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
