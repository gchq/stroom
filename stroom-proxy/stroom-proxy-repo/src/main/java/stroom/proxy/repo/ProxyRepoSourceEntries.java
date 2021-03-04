package stroom.proxy.repo;

import stroom.data.zip.StroomZipFileType;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.repo.db.jooq.tables.records.SourceEntryRecord;
import stroom.proxy.repo.db.jooq.tables.records.SourceItemRecord;
import stroom.util.io.FileUtil;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.jooq.Record1;
import org.jooq.Record2;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;

import static stroom.proxy.repo.db.jooq.tables.Source.SOURCE;
import static stroom.proxy.repo.db.jooq.tables.SourceEntry.SOURCE_ENTRY;
import static stroom.proxy.repo.db.jooq.tables.SourceItem.SOURCE_ITEM;

@Singleton
public class ProxyRepoSourceEntries {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyRepoSourceEntries.class);

    private final SqliteJooqHelper jooq;
    private final Path repoDir;

    private final List<ChangeListener> listeners = new CopyOnWriteArrayList<>();

    private final AtomicLong sourceItemRecordId = new AtomicLong();
    private final AtomicLong sourceEntryRecordId = new AtomicLong();

    @Inject
    public ProxyRepoSourceEntries(final ProxyRepoDbConnProvider connProvider,
                                  final ProxyRepoConfig proxyRepoConfig) {
        this.jooq = new SqliteJooqHelper(connProvider);
        repoDir = Paths.get(proxyRepoConfig.getRepoDir());

        final long maxSourceItemRecordId = jooq.contextResult(context -> context
                .select(DSL.max(SOURCE_ITEM.ID))
                .from(SOURCE_ITEM)
                .fetchOptional()
                .map(Record1::value1)
                .orElse(0L));
        sourceItemRecordId.set(maxSourceItemRecordId);

        final long maxSourceEntryRecordId = jooq.contextResult(context -> context
                .select(DSL.max(SOURCE_ENTRY.ID))
                .from(SOURCE_ENTRY)
                .fetchOptional()
                .map(Record1::value1)
                .orElse(0L));
        sourceEntryRecordId.set(maxSourceEntryRecordId);
    }

    public void examine() {
        final List<CompletableFuture<Void>> futures = new ArrayList<>();
        jooq.context(context -> {
            try (final Stream<Record2<Long, String>> stream = context
                    .select(SOURCE.ID, SOURCE.PATH)
                    .from(SOURCE)
                    .where(SOURCE.EXAMINED.isFalse())
                    .orderBy(SOURCE.LAST_MODIFIED_TIME_MS, SOURCE.ID)
                    .stream()) {
                stream.forEach(record -> {
                    final long id = record.get(SOURCE.ID);
                    final String path = record.get(SOURCE.PATH);

                    final CompletableFuture<Void> completableFuture =
                            CompletableFuture.runAsync(() -> examineSource(id, path));
                    futures.add(completableFuture);
                });
            }
        });
        // Wait for all futures to complete.
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    public void examineSource(final long sourceId,
                              final String sourcePath) {
        final Path fullPath = repoDir.resolve(sourcePath);
        final AtomicInteger itemNumber = new AtomicInteger();

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Storing zip info for  '" + FileUtil.getCanonicalPath(fullPath) + "'");
        }

        final Map<String, SourceItemRecord> itemNameMap = new HashMap<>();
        final Map<Long, List<SourceEntryRecord>> entryMap = new HashMap<>();

        try (final ZipFile zipFile = new ZipFile(Files.newByteChannel(fullPath))) {
            final Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
            while (entries.hasMoreElements()) {
                final ZipArchiveEntry entry = entries.nextElement();

                // Skip directories
                if (!entry.isDirectory()) {
                    final String fileName = entry.getName();

                    // Split into stem and extension.
                    final int index = fileName.indexOf(".");
                    if (index != -1) {
                        final String dataName = fileName.substring(0, index);
                        final String extension = fileName.substring(index).toLowerCase();

                        // If this is a meta entry then get the feed name.
                        String feedName = null;
                        String typeName = null;

                        int extensionType = -1;
                        if (StroomZipFileType.META.getExtension().equals(extension)) {
                            // We need to be able to sort by extension so we can get meta data first.
                            extensionType = 1;

                            try (final InputStream metaStream = zipFile.getInputStream(entry)) {
                                if (metaStream == null) {
                                    LOGGER.error(fullPath + ": unable to find meta");
                                } else {
                                    final AttributeMap attributeMap = new AttributeMap();
                                    AttributeMapUtil.read(metaStream, attributeMap);
                                    feedName = attributeMap.get(StandardHeaderArguments.FEED);
                                    typeName = attributeMap.get(StandardHeaderArguments.TYPE);
                                }
                            } catch (final RuntimeException e) {
                                LOGGER.error(fullPath + " " + e.getMessage());
                                LOGGER.debug(e.getMessage(), e);
                            }
                        } else if (StroomZipFileType.CONTEXT.getExtension().equals(extension)) {
                            extensionType = 2;
                        } else if (StroomZipFileType.DATA.getExtension().equals(extension)) {
                            extensionType = 3;
                        }

                        // Don't add unknown types.
                        if (extensionType != -1) {
                            long sourceItemId;
                            SourceItemRecord sourceItemRecord = itemNameMap.get(dataName);

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
                                        itemNumber.incrementAndGet(),
                                        dataName,
                                        feedName,
                                        typeName,
                                        sourceId,
                                        false);
                                itemNameMap.put(dataName, sourceItemRecord);
                            }

                            entryMap
                                    .computeIfAbsent(sourceItemId, k -> new ArrayList<>())
                                    .add(new SourceEntryRecord(
                                            sourceEntryRecordId.incrementAndGet(),
                                            extension,
                                            extensionType,
                                            entry.getSize(),
                                            sourceItemId));
                        }
                    }
                }
            }
        } catch (final IOException e) {
            // Unable to open file ... must be bad.
            LOGGER.error(fullPath + " " + e.getMessage());
            LOGGER.error(e.getMessage(), e);
            throw new UncheckedIOException(e);
        } catch (final RuntimeException e) {
            // Unable to open file ... must be bad.
            LOGGER.error(fullPath + " " + e.getMessage());
            LOGGER.error(e.getMessage(), e);
            throw e;
        }

        // We now have a map of all source entries so add them to the DB.
        jooq.transaction(context -> {
            final List<SourceItemRecord> sourceItemRecords = new ArrayList<>(itemNameMap.size());
            final List<SourceEntryRecord> sourceEntryRecords = new ArrayList<>();
            for (final SourceItemRecord sourceItemRecord : itemNameMap.values()) {
                if (sourceItemRecord.getFeedName() == null) {
                    LOGGER.error("Source item has no feed name: " + fullPath + " - " + sourceItemRecord.getName());
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

        // Let others know there are new source entries to consume.
        listeners.forEach(listener -> listener.onChange(sourceId));
    }

    public void addChangeListener(final ChangeListener changeListener) {
        listeners.add(changeListener);
    }

    public interface ChangeListener {

        void onChange(long sourceId);
    }
}
