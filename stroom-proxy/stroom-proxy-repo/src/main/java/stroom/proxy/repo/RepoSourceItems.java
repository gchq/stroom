package stroom.proxy.repo;

import stroom.data.zip.StroomZipFileType;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.repo.dao.FeedDao;
import stroom.proxy.repo.dao.SourceItemDao;
import stroom.proxy.repo.queue.Batch;
import stroom.proxy.repo.store.FileSet;
import stroom.proxy.repo.store.SequentialFileStore;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.Metrics;

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
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class RepoSourceItems {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RepoSourceItems.class);

    private final SourceItemDao sourceItemDao;
    private final FeedDao feedDao;
    private final ErrorReceiver errorReceiver;
    private final ProgressLog progressLog;
    private final SequentialFileStore sequentialFileStore;

    @Inject
    public RepoSourceItems(final SourceItemDao sourceItemDao,
                           final FeedDao feedDao,
                           final ErrorReceiver errorReceiver,
                           final ProgressLog progressLog,
                           final SequentialFileStore sequentialFileStore) {
        this.sourceItemDao = sourceItemDao;
        this.feedDao = feedDao;
        this.errorReceiver = errorReceiver;
        this.progressLog = progressLog;
        this.sequentialFileStore = sequentialFileStore;
    }

    public void examineSource(final RepoSource source) {
        final FeedKey feedKey = feedDao.getKey(source.feedId());
        Metrics.measure("Examine Source", () -> {
            final FileSet fileSet = sequentialFileStore.getStoreFileSet(source.fileStoreId());
            final Path zipPath = fileSet.getZip();

            LOGGER.debug(() -> "Examining zip  '" + FileUtil.getCanonicalPath(zipPath) + "'");
            progressLog.increment("ProxyRepoSourceEntries - examineSource");

            final Map<String, RepoSourceItemBuilder> itemNameMap = new HashMap<>();
            try (final ZipFile zipFile = new ZipFile(Files.newByteChannel(zipPath))) {
                final Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
                while (entries.hasMoreElements()) {
                    final ZipArchiveEntry entry = entries.nextElement();
                    LOGGER.trace(() -> "Examining zip entry '" + entry.getName() + "'");

                    // Skip directories
                    if (!entry.isDirectory()) {
                        final FileName fileName = parseFileName(entry.getName());
                        final String itemName = fileName.getStem();
                        final StroomZipFileType stroomZipFileType =
                                StroomZipFileType.fromExtension(fileName.getExtension());

                        // If this is a meta entry then get the feed name.
                        String feedName = feedKey.feed();
                        String typeName = feedKey.type();

                        if (StroomZipFileType.META.equals(stroomZipFileType)) {
                            try (final InputStream metaStream = zipFile.getInputStream(entry)) {
                                if (metaStream == null) {
                                    errorReceiver.error(fileSet, "Unable to find meta");
                                    LOGGER.error(() -> zipPath + ": unable to find meta");
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
                                errorReceiver.error(fileSet, e.getMessage());
                                LOGGER.error(() -> zipPath + " " + e.getMessage());
                                LOGGER.debug(e::getMessage, e);
                            }
                        }

                        final RepoSourceItemBuilder builder = itemNameMap.computeIfAbsent(itemName, k ->
                                new RepoSourceItemBuilder()
                                        .name(itemName)
                                        .source(source));

                        // If we have an existing source item then update the feed and type names if we have some.
                        if (feedName != null && builder.getFeedName() == null) {
                            builder.feedName(feedName);
                        }
                        if (typeName != null && builder.getTypeName() == null) {
                            builder.typeName(typeName);
                        }

                        final RepoSourceEntry sourceEntry = new RepoSourceEntry(
                                stroomZipFileType,
                                fileName.getExtension(),
                                entry.getSize());
                        builder.addEntry(sourceEntry);
                        itemNameMap.put(fileName.getStem(), builder);
                    }
                }

                if (itemNameMap.isEmpty()) {
                    errorReceiver.error(fileSet, "Unable to find any entries?");
                }

            } catch (final IOException e) {
                // Unable to open file ... must be bad.
                errorReceiver.fatal(fileSet, e.getMessage());
                LOGGER.debug(e::getMessage, e);
            }

            // We now have a map of all source entries so add them to the DB.
            final List<RepoSourceItem> items = itemNameMap
                    .values()
                    .stream()
                    .map(builder -> builder.build(feedDao))
                    .collect(Collectors.toList());
            sourceItemDao.addItems(source, items);
        });
    }

    private FileName parseFileName(final String fileName) {
        Objects.requireNonNull(fileName, "fileName is null");
        final int extensionIndex = fileName.lastIndexOf(".");
        String stem;
        String extension;
        if (extensionIndex == -1) {
            stem = fileName;
            extension = "";
        } else {
            stem = fileName.substring(0, extensionIndex);
            extension = fileName.substring(extensionIndex);
        }
        return new FileName(fileName, stem, extension);
    }

    public void clear() {
        sourceItemDao.clear();
    }

    public Batch<RepoSourceItemRef> getNewSourceItems() {
        return sourceItemDao.getNewSourceItems();
    }

    public Batch<RepoSourceItemRef> getNewSourceItems(final long timeout,
                                                      final TimeUnit timeUnit) {
        return sourceItemDao.getNewSourceItems(timeout, timeUnit);
    }

    private static class FileName {

        private final String fullName;
        private final String stem;
        private final String extension;

        public FileName(final String fullName, final String stem, final String extension) {
            this.fullName = fullName;
            this.stem = stem;
            this.extension = extension;
        }

        public String getFullName() {
            return fullName;
        }

        public String getStem() {
            return stem;
        }

        public String getExtension() {
            return extension;
        }

        @Override
        public String toString() {
            return fullName;
        }
    }

    private final class RepoSourceItemBuilder {

        private RepoSource source;
        private String name;
        private String feedName;
        private String typeName;
        private Long aggregateId;
        private long totalByteSize;
        private final List<RepoSourceEntry> entries;

        public RepoSourceItemBuilder() {
            entries = new ArrayList<>();
        }

        public RepoSourceItemBuilder source(final RepoSource source) {
            this.source = source;
            return this;
        }

        public RepoSourceItemBuilder name(final String name) {
            this.name = name;
            return this;
        }

        public RepoSourceItemBuilder feedName(final String feedName) {
            this.feedName = feedName;
            return this;
        }

        public RepoSourceItemBuilder typeName(final String typeName) {
            this.typeName = typeName;
            return this;
        }

        public RepoSourceItemBuilder aggregateId(final Long aggregateId) {
            this.aggregateId = aggregateId;
            return this;
        }

        public RepoSourceItemBuilder addEntry(final RepoSourceEntry entry) {
            this.entries.add(entry);
            this.totalByteSize += entry.byteSize();
            return this;
        }

        public String getTypeName() {
            return typeName;
        }

        public String getFeedName() {
            return feedName;
        }

        public RepoSourceItem build(final FeedDao feedDao) {
            final long feedId = feedDao.getId(new FeedKey(feedName, typeName));
            return new RepoSourceItem(
                    source,
                    name,
                    feedId,
                    aggregateId,
                    totalByteSize,
                    entries);
        }
    }
}
