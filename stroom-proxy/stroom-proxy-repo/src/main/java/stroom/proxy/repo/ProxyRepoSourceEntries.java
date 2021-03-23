package stroom.proxy.repo;

import stroom.data.zip.StroomZipEntry;
import stroom.data.zip.StroomZipFileType;
import stroom.data.zip.StroomZipNameSet;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.repo.dao.SourceDao;
import stroom.proxy.repo.dao.SourceDao.Source;
import stroom.proxy.repo.dao.SourceEntryDao;
import stroom.proxy.repo.db.jooq.tables.records.SourceEntryRecord;
import stroom.proxy.repo.db.jooq.tables.records.SourceItemRecord;
import stroom.util.concurrent.ScalingThreadPoolExecutor;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.thread.CustomThreadFactory;
import stroom.util.thread.StroomThreadGroup;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ProxyRepoSourceEntries implements HasShutdown {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProxyRepoSourceEntries.class);

    private static final int BATCH_SIZE = 1000000;

    private final ThreadFactory threadFactory = new CustomThreadFactory(
            "Examine Proxy File",
            StroomThreadGroup.instance(),
            Thread.NORM_PRIORITY - 1);
    private final ExecutorService executor = ScalingThreadPoolExecutor.newScalingThreadPool(
            1,
            10,
            100,
            10,
            TimeUnit.MINUTES,
            threadFactory);

    private final SourceDao sourceDao;
    private final SourceEntryDao sourceEntryDao;
    private final Path repoDir;
    private final ErrorReceiver errorReceiver;

    private final List<ChangeListener> listeners = new CopyOnWriteArrayList<>();

    private volatile boolean shutdown;

    @Inject
    public ProxyRepoSourceEntries(final SourceDao sourceDao,
                                  final SourceEntryDao sourceEntryDao,
                                  final RepoDirProvider repoDirProvider,
                                  final ErrorReceiver errorReceiver) {
        this.sourceDao = sourceDao;
        this.sourceEntryDao = sourceEntryDao;
        this.errorReceiver = errorReceiver;
        repoDir = repoDirProvider.get();
    }

    public synchronized void examine() {
        boolean run = true;
        while (run && !shutdown) {

            final AtomicInteger count = new AtomicInteger();

            final List<CompletableFuture<Void>> futures = new ArrayList<>();
            final List<Source> sources = sourceDao.getNewSources(BATCH_SIZE);
            sources.forEach(source -> {
                if (!shutdown) {
                    count.incrementAndGet();
                    final CompletableFuture<Void> completableFuture =
                            CompletableFuture.runAsync(() -> examineSource(source), executor);
                    futures.add(completableFuture);
                }
            });

            // Wait for all futures to complete.
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // Stop examining if the last query did not return a result as big as the batch size.
            if (sources.size() < BATCH_SIZE || Thread.currentThread().isInterrupted()) {
                run = false;
            }
        }
    }

    public void examineSource(final Source source) {
        if (!shutdown) {
            final Path fullPath = repoDir.resolve(source.getSourcePath());

            LOGGER.debug(() -> "Examining zip  '" + FileUtil.getCanonicalPath(fullPath) + "'");

            final Map<String, SourceItemRecord> itemNameMap = new HashMap<>();
            final Map<Long, List<SourceEntryRecord>> entryMap = new HashMap<>();

            final StroomZipNameSet stroomZipNameSet = new StroomZipNameSet(false);
            try (final ZipFile zipFile = new ZipFile(Files.newByteChannel(fullPath))) {
                final Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
                while (entries.hasMoreElements()) {
                    final ZipArchiveEntry entry = entries.nextElement();

                    // Skip directories
                    if (!entry.isDirectory()) {
                        final String fileName = entry.getName();
                        final StroomZipEntry stroomZipEntry = stroomZipNameSet.add(fileName);
                        final String baseName = stroomZipEntry.getBaseName();
                        final String extension = stroomZipEntry.getFullName().substring(baseName.length());
                        final StroomZipFileType stroomZipFileType = stroomZipEntry.getStroomZipFileType();

                        // If this is a meta entry then get the feed name.
                        String feedName = source.getFeedName();
                        String typeName = source.getTypeName();

                        if (StroomZipFileType.META.equals(stroomZipFileType)) {
                            try (final InputStream metaStream = zipFile.getInputStream(entry)) {
                                if (metaStream == null) {
                                    errorReceiver.error(fullPath, "Unable to find meta");
                                    LOGGER.error(() -> fullPath + ": unable to find meta");
                                } else {
                                    final AttributeMap attributeMap = new AttributeMap();
                                    AttributeMapUtil.read(metaStream, attributeMap);
                                    if (attributeMap.get(StandardHeaderArguments.FEED) != null) {
                                        feedName = attributeMap.get(StandardHeaderArguments.FEED);
                                    }
                                    if (attributeMap.get(StandardHeaderArguments.TYPE) != null) {
                                        typeName = attributeMap.get(StandardHeaderArguments.TYPE);
                                    }
                                }
                            } catch (final RuntimeException e) {
                                errorReceiver.error(fullPath, e.getMessage());
                                LOGGER.error(() -> fullPath + " " + e.getMessage());
                                LOGGER.debug(e::getMessage, e);
                            }
                        }

                        long sourceItemId;
                        SourceItemRecord sourceItemRecord = itemNameMap.get(baseName);

                        if (sourceItemRecord != null) {
                            sourceItemId = sourceItemRecord.getId();

                            // If we have an existing source item then update the feed and type names if we have
                            // some.
                            if (feedName != null && sourceItemRecord.getFeedName() == null) {
                                sourceItemRecord.setFeedName(feedName);
                            }
                            if (typeName != null && sourceItemRecord.getTypeName() == null) {
                                sourceItemRecord.setTypeName(typeName);
                            }

                        } else {
                            sourceItemId = sourceEntryDao.nextSourceItemId();
                            sourceItemRecord = new SourceItemRecord(
                                    sourceItemId,
                                    baseName,
                                    feedName,
                                    typeName,
                                    source.getSourceId(),
                                    false);
                            itemNameMap.put(baseName, sourceItemRecord);
                        }

                        final long sourceEntryId = sourceEntryDao.nextSourceEntryId();
                        entryMap
                                .computeIfAbsent(sourceItemId, k -> new ArrayList<>())
                                .add(new SourceEntryRecord(
                                        sourceEntryId,
                                        extension,
                                        stroomZipFileType.getId(),
                                        entry.getSize(),
                                        sourceItemId));
                    }
                }

                if (stroomZipNameSet.getBaseNameSet().isEmpty()) {
                    errorReceiver.error(fullPath, "Unable to find any entries?");
                }

            } catch (final IOException e) {
                // Unable to open file ... must be bad.
                errorReceiver.fatal(fullPath, e.getMessage());
                LOGGER.debug(e::getMessage, e);
            }

            // We now have a map of all source entries so add them to the DB.
            sourceEntryDao.addEntries(fullPath, source.getSourceId(), itemNameMap, entryMap);

            // Let others know there are new source entries to consume.
            listeners.forEach(listener -> listener.onChange(source.getSourceId()));
        }
    }

    @Override
    public void shutdown() {
        shutdown = true;
        executor.shutdown();
        try {
            while (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                LOGGER.debug(() -> "Shutting down");
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void addChangeListener(final ChangeListener changeListener) {
        listeners.add(changeListener);
    }

    public void clear() {
        sourceEntryDao.clear();
    }

    public interface ChangeListener {

        void onChange(long sourceId);
    }
}
