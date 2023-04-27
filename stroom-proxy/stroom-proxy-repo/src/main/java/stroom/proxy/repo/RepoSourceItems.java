package stroom.proxy.repo;

import stroom.data.zip.StroomZipFileType;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.repo.RepoSourceItem.RepoSourceItemBuilder;
import stroom.proxy.repo.dao.FeedDao;
import stroom.proxy.repo.dao.SourceItemDao;
import stroom.proxy.repo.queue.Batch;
import stroom.proxy.repo.store.FileSet;
import stroom.proxy.repo.store.SequentialFileStore;
import stroom.util.io.FileName;
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
            final List<RepoSourceItemBuilder> builderList = new ArrayList<>();
            try (final ZipFile zipFile = new ZipFile(Files.newByteChannel(zipPath))) {
                final Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
                while (entries.hasMoreElements()) {
                    final ZipArchiveEntry entry = entries.nextElement();
                    LOGGER.trace(() -> "Examining zip entry '" + entry.getName() + "'");

                    // Skip directories
                    if (!entry.isDirectory()) {
                        final FileName fileName = FileName.parse(entry.getName());
                        final String baseName = fileName.getBaseName();
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

                        final RepoSourceItemBuilder builder = itemNameMap.computeIfAbsent(baseName, k -> {
                            final RepoSourceItemBuilder b = new RepoSourceItemBuilder()
                                    .repoSource(source)
                                    .name(baseName);
                            // Use a list to keep the items in the same order as the source.
                            builderList.add(b);
                            return b;
                        });

                        // If we have an existing source item then update the feed and type names if we have some.
                        if (feedName != null && builder.getFeedName() == null) {
                            builder.feedName(feedName);
                        }
                        if (typeName != null && builder.getTypeName() == null) {
                            builder.typeName(typeName);
                        }

                        builder.addEntry(fileName.getExtension(), entry.getSize());
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
            final List<RepoSourceItem> items = builderList
                    .stream()
                    .map(builder -> builder.build(feedDao))
                    .collect(Collectors.toList());
            sourceItemDao.addItems(source, items);
        });
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
}
