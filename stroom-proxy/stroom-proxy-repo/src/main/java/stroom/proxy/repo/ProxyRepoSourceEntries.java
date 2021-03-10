package stroom.proxy.repo;

import stroom.data.zip.StroomZipEntry;
import stroom.data.zip.StroomZipFileType;
import stroom.data.zip.StroomZipNameSet;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
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
import org.jooq.Record2;
import org.jooq.Result;

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
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.inject.Singleton;

import static stroom.proxy.repo.db.jooq.tables.Source.SOURCE;
import static stroom.proxy.repo.db.jooq.tables.SourceEntry.SOURCE_ENTRY;
import static stroom.proxy.repo.db.jooq.tables.SourceItem.SOURCE_ITEM;

@Singleton
public class ProxyRepoSourceEntries {

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

    private final SqliteJooqHelper jooq;
    private final Path repoDir;
    private final ErrorReceiver errorReceiver;

    private final List<ChangeListener> listeners = new CopyOnWriteArrayList<>();

    private final AtomicLong sourceItemRecordId = new AtomicLong();
    private final AtomicLong sourceEntryRecordId = new AtomicLong();

    private volatile boolean shutdown;

    @Inject
    public ProxyRepoSourceEntries(final ProxyRepoDbConnProvider connProvider,
                                  final RepoDirProvider repoDirProvider,
                                  final ErrorReceiver errorReceiver) {
        this.jooq = new SqliteJooqHelper(connProvider);
        this.errorReceiver = errorReceiver;
        repoDir = repoDirProvider.get();

        init();
    }

    private void init() {
        final long maxSourceItemRecordId = jooq.getMaxId(SOURCE_ITEM, SOURCE_ITEM.ID).orElse(0L);
        sourceItemRecordId.set(maxSourceItemRecordId);

        final long maxSourceEntryRecordId = jooq.getMaxId(SOURCE_ENTRY, SOURCE_ENTRY.ID).orElse(0L);
        sourceEntryRecordId.set(maxSourceEntryRecordId);
    }

    public synchronized void examine() {
        boolean run = true;
        while (run && !shutdown) {

            final AtomicInteger count = new AtomicInteger();

            final Result<Record2<Long, String>> result = getNewSources();

            final List<CompletableFuture<Void>> futures = new ArrayList<>();
            result.forEach(record -> {
                if (!shutdown) {
                    final long id = record.get(SOURCE.ID);
                    final String path = record.get(SOURCE.PATH);

                    count.incrementAndGet();
                    final CompletableFuture<Void> completableFuture =
                            CompletableFuture.runAsync(() -> examineSource(id, path), executor);
                    futures.add(completableFuture);
                }
            });

            // Wait for all futures to complete.
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // Stop examining if the last query did not return a result as big as the batch size.
            if (count.get() < BATCH_SIZE || Thread.currentThread().isInterrupted()) {
                run = false;
            }
        }
    }

    Result<Record2<Long, String>> getNewSources() {
        return jooq.contextResult(context -> context
                .select(SOURCE.ID, SOURCE.PATH)
                .from(SOURCE)
                .where(SOURCE.EXAMINED.isFalse())
                .orderBy(SOURCE.LAST_MODIFIED_TIME_MS, SOURCE.ID)
                .limit(BATCH_SIZE)
                .fetch());
    }

    int countSources() {
        return jooq.count(SOURCE);
    }

    int countItems() {
        return jooq.count(SOURCE_ITEM);
    }

    int countEntries() {
        return jooq.count(SOURCE_ENTRY);
    }

    public void examineSource(final long sourceId,
                              final String sourcePath) {
        if (!shutdown) {
            final Path fullPath = repoDir.resolve(sourcePath);

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
                        String feedName = null;
                        String typeName = null;

                        if (StroomZipFileType.META.equals(stroomZipFileType)) {
                            try (final InputStream metaStream = zipFile.getInputStream(entry)) {
                                if (metaStream == null) {
                                    errorReceiver.error(fullPath, "Unable to find meta");
                                    LOGGER.error(() -> fullPath + ": unable to find meta");
                                } else {
                                    final AttributeMap attributeMap = new AttributeMap();
                                    AttributeMapUtil.read(metaStream, attributeMap);
                                    feedName = attributeMap.get(StandardHeaderArguments.FEED);
                                    typeName = attributeMap.get(StandardHeaderArguments.TYPE);
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
                            sourceItemId = this.sourceItemRecordId.incrementAndGet();
                            sourceItemRecord = new SourceItemRecord(
                                    sourceItemId,
                                    baseName,
                                    feedName,
                                    typeName,
                                    sourceId,
                                    false);
                            itemNameMap.put(baseName, sourceItemRecord);
                        }

                        entryMap
                                .computeIfAbsent(sourceItemId, k -> new ArrayList<>())
                                .add(new SourceEntryRecord(
                                        sourceEntryRecordId.incrementAndGet(),
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
            addEntries(fullPath, sourceId, itemNameMap, entryMap);

            // Let others know there are new source entries to consume.
            listeners.forEach(listener -> listener.onChange(sourceId));
        }
    }

    void addEntries(final Path fullPath,
                    final long sourceId,
                    final Map<String, SourceItemRecord> itemNameMap,
                    final Map<Long, List<SourceEntryRecord>> entryMap) {
        jooq.transaction(context -> {
            final List<SourceItemRecord> sourceItemRecords = new ArrayList<>(itemNameMap.size());
            final List<SourceEntryRecord> sourceEntryRecords = new ArrayList<>();
            for (final SourceItemRecord sourceItemRecord : itemNameMap.values()) {
                if (sourceItemRecord.getFeedName() == null) {
                    LOGGER.error(() -> "Source item has no feed name: " + fullPath + " - " + sourceItemRecord.getName());
                } else {
                    sourceItemRecords.add(sourceItemRecord);
                    final List<SourceEntryRecord> entries = entryMap.get(sourceItemRecord.getId());
                    sourceEntryRecords.addAll(entries);
                }
            }

            context.batchInsert(sourceItemRecords).execute();
            context.batchInsert(sourceEntryRecords).execute();

            // Mark the source as having been examined.
            context
                    .update(SOURCE)
                    .set(SOURCE.EXAMINED, true)
                    .where(SOURCE.ID.eq(sourceId))
                    .execute();
        });
    }

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
        jooq.deleteAll(SOURCE_ENTRY);
        jooq.deleteAll(SOURCE_ITEM);

        jooq
                .getMaxId(SOURCE_ENTRY, SOURCE_ENTRY.ID)
                .ifPresent(id -> {
                    throw new RuntimeException("Unexpected ID");
                });
        jooq
                .getMaxId(SOURCE_ITEM, SOURCE_ITEM.ID)
                .ifPresent(id -> {
                    throw new RuntimeException("Unexpected ID");
                });

        init();
    }

    public interface ChangeListener {

        void onChange(long sourceId);
    }
}
