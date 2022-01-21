package stroom.proxy.repo;

import stroom.data.zip.StroomZipFileType;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.repo.dao.SourceItemDao;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class RepoSourceItems {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RepoSourceItems.class);

    private final RepoSources sources;
    private final SourceItemDao sourceItemDao;
    private final Path repoDir;
    private final ErrorReceiver errorReceiver;
    private final ProgressLog progressLog;

    @Inject
    public RepoSourceItems(final RepoSources sources,
                           final SourceItemDao sourceItemDao,
                           final RepoDirProvider repoDirProvider,
                           final ErrorReceiver errorReceiver,
                           final ProgressLog progressLog) {
        this.sources = sources;
        this.sourceItemDao = sourceItemDao;
        this.errorReceiver = errorReceiver;
        this.progressLog = progressLog;
        repoDir = repoDirProvider.get();
    }

    public void examineAll() {
        QueueUtil.consumeAll(() -> sources.getNewSource(0, TimeUnit.MILLISECONDS),
                this::examineSource);
    }

    public void examineNext() {
        sources.getNewSource().ifPresent(this::examineSource);
    }

    public void examineSource(final RepoSource source) {
        final Path fullPath = repoDir.resolve(source.getSourcePath());

        LOGGER.debug(() -> "Examining zip  '" + FileUtil.getCanonicalPath(fullPath) + "'");
        progressLog.increment("ProxyRepoSourceEntries - examineSource");

        final Map<String, RepoSourceItem.Builder> itemNameMap = new HashMap<>();
        try (final ZipFile zipFile = new ZipFile(Files.newByteChannel(fullPath))) {
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

                    final RepoSourceItem.Builder builder = itemNameMap.computeIfAbsent(itemName, k ->
                            RepoSourceItem.builder()
                                    .name(itemName)
                                    .source(source));

                    // If we have an existing source item then update the feed and type names if we have some.
                    if (feedName != null && builder.build().getFeedName() == null) {
                        builder.feedName(feedName);
                    }
                    if (typeName != null && builder.build().getTypeName() == null) {
                        builder.typeName(typeName);
                    }

                    final RepoSourceEntry sourceEntry = RepoSourceEntry.builder()
                            .type(stroomZipFileType)
                            .extension(fileName.getExtension())
                            .byteSize(entry.getSize())
                            .build();
                    builder.addEntry(sourceEntry);
                    itemNameMap.put(fileName.getStem(), builder);
                }
            }

            if (itemNameMap.isEmpty()) {
                errorReceiver.error(fullPath, "Unable to find any entries?");
            }

        } catch (final IOException e) {
            // Unable to open file ... must be bad.
            errorReceiver.fatal(fullPath, e.getMessage());
            LOGGER.debug(e::getMessage, e);
        }

        // We now have a map of all source entries so add them to the DB.
        final List<RepoSourceItem> items = itemNameMap
                .values()
                .stream()
                .map(RepoSourceItem.Builder::build)
                .collect(Collectors.toList());
        sourceItemDao.addItems(fullPath, source.getId(), items);
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

    public Optional<RepoSourceItemRef> getNewSourceItem() {
        return sourceItemDao.getNewSourceItem();
    }

    public Optional<RepoSourceItemRef> getNewSourceItem(final long timeout,
                                                        final TimeUnit timeUnit) {
        return sourceItemDao.getNewSourceItem(timeout, timeUnit);
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
}
