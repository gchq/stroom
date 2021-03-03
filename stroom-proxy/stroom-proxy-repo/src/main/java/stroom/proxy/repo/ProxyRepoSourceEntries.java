package stroom.proxy.repo;

import stroom.data.zip.StroomZipFileType;
import stroom.proxy.repo.SqliteJooqUtil;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.repo.db.jooq.tables.records.SourceItemRecord;
import stroom.util.io.FileUtil;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.jooq.DSLContext;
import org.jooq.Record2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;

import static stroom.proxy.repo.db.jooq.tables.Source.SOURCE;
import static stroom.proxy.repo.db.jooq.tables.SourceEntry.SOURCE_ENTRY;
import static stroom.proxy.repo.db.jooq.tables.SourceItem.SOURCE_ITEM;

@Singleton
public class ProxyRepoSourceEntries {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyRepoSourceEntries.class);

    private final ProxyRepoDbConnProvider connProvider;
    private final Path repoDir;

    private final List<ChangeListener> listeners = new CopyOnWriteArrayList<>();

    @Inject
    public ProxyRepoSourceEntries(final ProxyRepoDbConnProvider connProvider,
                                  final ProxyRepoConfig proxyRepoConfig) {
        this.connProvider = connProvider;
        repoDir = Paths.get(proxyRepoConfig.getRepoDir());
    }

    public void examine() {
        final List<CompletableFuture<Void>> futures = new ArrayList<>();
        SqliteJooqUtil.context(connProvider, context -> {
            try (final Stream<Record2<Integer, String>> stream = context
                    .select(SOURCE.ID, SOURCE.PATH)
                    .from(SOURCE)
                    .where(SOURCE.EXAMINED.isFalse())
                    .orderBy(SOURCE.LAST_MODIFIED_TIME_MS, SOURCE.ID)
                    .stream()) {
                stream.forEach(record -> {
                    final int id = record.get(SOURCE.ID);
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

    public void examineSource(final int sourceId,
                              final String sourcePath) {
        final Path fullPath = repoDir.resolve(sourcePath);
        final AtomicInteger itemNumber = new AtomicInteger();

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Storing zip info for  '" + FileUtil.getCanonicalPath(fullPath) + "'");
        }

        // Start a transaction for all of the database changes.
        SqliteJooqUtil.transaction(connProvider, context -> {
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
                                final int dataId = addItem(
                                        context,
                                        sourceId,
                                        itemNumber,
                                        dataName,
                                        feedName,
                                        typeName);
                                addItemEntry(context,
                                        dataId,
                                        extension,
                                        extensionType,
                                        entry.getSize());
                            }
                        }
                    }
                }

                // Mark the source as having been examined.
                context
                        .update(SOURCE)
                        .set(SOURCE.EXAMINED, true)
                        .where(SOURCE.ID.eq(sourceId))
                        .execute();

                // Let others know there are new source entries to consume.
                listeners.forEach(listener -> listener.onChange(sourceId));

            } catch (final IOException | RuntimeException e) {
                // Unable to open file ... must be bad.
                LOGGER.error(fullPath + " " + e.getMessage());
                LOGGER.error(e.getMessage(), e);
            }
        });
    }

    private int addItem(final DSLContext context,
                        final int sourceId,
                        final AtomicInteger itemNumber,
                        final String name,
                        final String feedName,
                        final String typeName) {
        final Optional<SourceItemRecord> optional = context
                .selectFrom(SOURCE_ITEM)
                .where(SOURCE_ITEM.FK_SOURCE_ID.eq(sourceId))
                .and(SOURCE_ITEM.NAME.eq(name))
                .fetchOptional();

        if (optional.isPresent()) {
            final SourceItemRecord record = optional.get();
            if ((feedName != null && record.getFeedName() == null) ||
                    (typeName != null && record.getTypeName() == null)) {
                // Update the record with the feed and type name.
                context
                        .update(SOURCE_ITEM)
                        .set(SOURCE_ITEM.FEED_NAME, feedName)
                        .set(SOURCE_ITEM.TYPE_NAME, typeName)
                        .where(SOURCE_ITEM.ID.eq(record.getId()))
                        .execute();
            }

            return record.getId();
        }

        return context
                .insertInto(SOURCE_ITEM,
                        SOURCE_ITEM.FK_SOURCE_ID,
                        SOURCE_ITEM.NUMBER,
                        SOURCE_ITEM.NAME,
                        SOURCE_ITEM.FEED_NAME,
                        SOURCE_ITEM.TYPE_NAME)
                .values(sourceId, itemNumber.incrementAndGet(), name, feedName, typeName)
                .returning(SOURCE_ITEM.ID)
                .fetchOptional()
                .map(SourceItemRecord::getId)
                .orElse(-1);
    }

    private void addItemEntry(final DSLContext context,
                              final int dataId,
                              final String extension,
                              final int extensionType,
                              final long size) {
        context
                .insertInto(SOURCE_ENTRY,
                        SOURCE_ENTRY.FK_SOURCE_ITEM_ID,
                        SOURCE_ENTRY.EXTENSION,
                        SOURCE_ENTRY.EXTENSION_TYPE,
                        SOURCE_ENTRY.BYTE_SIZE)
                .values(dataId, extension, extensionType, size)
                .execute();
    }

    public void addChangeListener(final ChangeListener changeListener) {
        listeners.add(changeListener);
    }

    public interface ChangeListener {

        void onChange(int sourceId);
    }
}
