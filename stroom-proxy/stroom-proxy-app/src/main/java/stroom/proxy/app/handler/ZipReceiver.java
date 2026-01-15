/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.proxy.app.handler;

import stroom.data.zip.StroomZipFileType;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.proxy.app.DataDirProvider;
import stroom.proxy.app.handler.ZipEntryGroup.Entry;
import stroom.proxy.repo.FeedKey;
import stroom.proxy.repo.FeedKey.FeedKeyInterner;
import stroom.proxy.repo.LogStream;
import stroom.proxy.repo.LogStream.EventType;
import stroom.receive.common.AttributeMapFilter;
import stroom.receive.common.AttributeMapFilterFactory;
import stroom.receive.common.InputStreamUtils;
import stroom.receive.common.ReceiveDataConfig;
import stroom.receive.common.StroomStreamException;
import stroom.util.exception.ThrowingConsumer;
import stroom.util.io.ByteCountInputStream;
import stroom.util.io.ByteSize;
import stroom.util.io.FileName;
import stroom.util.io.FileUtil;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.net.HostNameUtil;
import stroom.util.zip.ZipUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * <p>
 * This class deals with the reception of zip files. It will perform the following tasks:
 * 1. Writes the inputStream to a temporary zip file on local disk.
 * 2. It then clones this temporary zip to a zip file in a managed directory, updating the .meta
 * files with the headers. All other entries are unchanged. In the process it records what
 * entries are in the zip and what feed/type they belong to.
 * 3. It tries to match all data entries to associated meta data.
 * 4. It finds all unique feeds and checks the status for each feed. An entries file is created containing
 * a line for all allowed entries in the zip, which will be used by {@link ZipSplitter} to split the zips.
 * 5. If the zip contains multiple feedKeys or is not in proper proxy zip format it passes
 * it to the ZipSplitter, else if passed it to the destination.
 * </p><p>
 * Along with the final zip files there will be associated meta files written to use for forwarding.
 * There will also be an entries file that tells us the size of each file set. The entries file has a JSON string
 * for each set of files in the zip. This data allows us to understand how best to aggregate this data if required.
 * </p>
 */
@Singleton
public class ZipReceiver implements Receiver {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ZipReceiver.class);
    private static final Logger RECEIVE_LOG = LoggerFactory.getLogger("receive");

    private final ReceiveDataConfig receiveDataConfig;
    private final AttributeMapFilterFactory attributeMapFilterFactory;
    private final NumberedDirProvider receivingDirProvider;
    private final ZipSplitter zipSplitter;
    private final LogStream logStream;
    private Consumer<Path> destination;

    @Inject
    public ZipReceiver(final AttributeMapFilterFactory attributeMapFilterFactory,
                       final DataDirProvider dataDirProvider,
                       final LogStream logStream,
                       final ZipSplitter zipSplitter,
                       final Provider<ReceiveDataConfig> receiveDataConfigProvider) {
        this.attributeMapFilterFactory = attributeMapFilterFactory;
        this.logStream = logStream;
        this.zipSplitter = zipSplitter;

        // Make receiving zip dir provider.
        receivingDirProvider = createDirProvider(dataDirProvider, DirNames.RECEIVING_ZIP);

//        // Get or create the received dir provider.
//        receivedDirProvider = createDirProvider(dataDirProvider, DirNames.RECEIVED_ZIP);

//        // Move any received data from previous proxy usage to the store.
//        transferOldReceivedData(receivedDir);

        this.receiveDataConfig = receiveDataConfigProvider.get();

        LOGGER.info("Initialised ZipReceiver, receivingDir base: {}", receivingDirProvider.getParentDir());
    }

    private NumberedDirProvider createDirProvider(final DataDirProvider dataDirProvider,
                                                  final String dirName) {
        // Make dir
        final Path dir = dataDirProvider.get().resolve(dirName);
        DirUtil.ensureDirExists(dir);

        // This is a temporary location and can be cleaned completely on startup.
        if (!FileUtil.deleteContents(dir)) {
            LOGGER.error(() -> "Failed to delete contents of " + FileUtil.getCanonicalPath(dir));
        }
        return new NumberedDirProvider(dir);
    }

    /**
     * Receive a proxy zip file that is located on disk.
     * Caller is responsible for deciding what to do with sourceZipFile after this method
     * returns successfully, e.g. deleting it.
     *
     * @param sourceZipFile The zip file.
     * @param attributeMap  Additional attributes that are global to all entries in the zip.
     */
    public void receive(final Path sourceZipFile,
                        final AttributeMap attributeMap) {
        Objects.requireNonNull(sourceZipFile);
        Objects.requireNonNull(attributeMap);
        if (!Files.isRegularFile(sourceZipFile)) {
            throw new RuntimeException(LogUtil.message(
                    "Zip file '{}' is not a regular file or does not exist", sourceZipFile));
        }

        final Instant startTime = Instant.now();
        final Path receivingDir;
        final ReceiveResult receiveResult;
        try {
            receivingDir = receivingDirProvider.get();
            final FileGroup destFileGroup = new FileGroup(receivingDir);
            final Path destZipFile = destFileGroup.getZip();
            final long receivedBytes = Files.size(sourceZipFile);
            try {
                receiveResult = receiveZipStream(
                        attributeMap,
                        sourceZipFile,
                        destZipFile,
                        receivedBytes);
            } catch (final Exception e) {
                LOGGER.debug(() -> LogUtil.exceptionMessage(e), e);
                // Cleanup.
                Files.deleteIfExists(destZipFile);
                deleteDir(receivingDir);
                throw StroomStreamException.create(e, attributeMap);
            }

            handleReceiveResult(attributeMap, receiveResult, destFileGroup, receivingDir, destZipFile);
        } catch (final IOException e) {
            throw StroomStreamException.create(e, attributeMap);
        }

        final Duration duration = Duration.between(startTime, Instant.now());
        logStream.log(
                RECEIVE_LOG,
                attributeMap,
                EventType.RECEIVE,
                pathToUri(sourceZipFile),
                StroomStatusCode.OK,
                attributeMap.get(StandardHeaderArguments.RECEIPT_ID),
                receiveResult.receivedBytes,
                duration.toMillis());
    }

    final String pathToUri(final Path path) {
        return String.join(
                "/",
                "file:/",
                HostNameUtil.determineHostName(),
                path.toAbsolutePath().toString());
    }

    @Override
    public void receive(final Instant startTime,
                        final AttributeMap attributeMap,
                        final String requestUri,
                        final InputStreamSupplier inputStreamSupplier) {
        final Path receivingDir;
        final ReceiveResult receiveResult;
        try {
            receivingDir = receivingDirProvider.get();
            final FileGroup destFileGroup = new FileGroup(receivingDir);
            final Path destZipFile = destFileGroup.getZip();
            try (final InputStream boundedInputStream = InputStreamUtils.getBoundedInputStream(
                    inputStreamSupplier.get(), receiveDataConfig.getMaxRequestSize())) {
                receiveResult = receiveZipStream(
                        boundedInputStream,
                        attributeMap,
                        destZipFile);
            } catch (final Exception e) {
                LOGGER.debug(() -> LogUtil.exceptionMessage(e), e);
                // Cleanup.
                Files.deleteIfExists(destZipFile);
                deleteDir(receivingDir);
                throw StroomStreamException.create(e, attributeMap);
            }

            handleReceiveResult(attributeMap, receiveResult, destFileGroup, receivingDir, destZipFile);
        } catch (final IOException e) {
            throw StroomStreamException.create(e, attributeMap);
        }

        final Duration duration = Duration.between(startTime, Instant.now());
        logStream.log(
                RECEIVE_LOG,
                attributeMap,
                EventType.RECEIVE,
                requestUri,
                StroomStatusCode.OK,
                attributeMap.get(StandardHeaderArguments.RECEIPT_ID),
                receiveResult.receivedBytes,
                duration.toMillis());
    }

    private void handleReceiveResult(final AttributeMap attributeMap,
                                     final ReceiveResult receiveResult,
                                     final FileGroup fileGroup,
                                     final Path receivingDir,
                                     final Path sourceZip) throws IOException {
        if (LOGGER.isDebugEnabled() && receiveResult.feedGroups.size() > 1) {
            // Log if we received a multi feed zip.
            logFeedGroupsToDebug(receiveResult);
        }

        // Check all the feeds are OK.
        final Map<FeedKey, List<ZipEntryGroup>> allowedEntries = filterAllowedEntries(
                attributeMap, receiveResult);

        // Only keep data for allowed feeds.
        if (!allowedEntries.isEmpty()) {
            // Write out the allowed entries so the destination knows which entries are in the zip
            // that are allowed to be used, i.e. so zipSplitter can drop zip entries that have no
            // corresponding entry in the entries file
            writeZipEntryGroups(fileGroup.getEntries(), allowedEntries);

            // If the data we received was for a perfectly formed zip file with data for a single feed then don't
            // bother to rewrite it in the zipSplitter.
            final int feedGroupCount = receiveResult.feedGroups.size();
            if (receiveResult.valid && feedGroupCount == 1) {
                final FeedKey feedKey = allowedEntries.keySet().iterator().next();

                // Write meta. Single feed/type so add them to the attr map
                AttributeMapUtil.addFeedAndType(attributeMap, feedKey.feed(), feedKey.type());
                AttributeMapUtil.write(attributeMap, fileGroup.getMeta());

                // Move receiving dir to destination.
                LOGGER.debug("Pass {} with feedKey: {} to destination {}", receivingDir, feedKey, destination);
                destination.accept(receivingDir);
            } else {
                // We have more than one feed in the source zip so split the source into a zip file for each feed.
                // Before we can queue the zip for splitting we need to serialise the attr map, so it is
                // available for the split process.
                AttributeMapUtil.write(attributeMap, fileGroup.getMeta());
                LOGGER.debug(() -> LogUtil.message("Pass {} to zipSplitter, isValid: {}, feedGroupCount: {}",
                        receivingDir, receiveResult.valid, feedGroupCount));
                zipSplitter.add(receivingDir);
            }
        } else {
            LOGGER.debug("No allowed feedKeys, all are dropped");
            // Delete the source zip.
            Files.delete(sourceZip);
            deleteDir(receivingDir);
        }
    }

    private Map<FeedKey, List<ZipEntryGroup>> filterAllowedEntries(final AttributeMap attributeMap,
                                                                   final ReceiveResult receiveResult) {
        final Map<FeedKey, List<ZipEntryGroup>> allowed = new HashMap<>();
        final AttributeMapFilter attributeMapFilter = attributeMapFilterFactory.create();
        receiveResult.feedGroups.forEach((feedKey, zipEntryGroups) -> {
            final AttributeMap entryAttributeMap = AttributeMapUtil.cloneAllowable(attributeMap);
            AttributeMapUtil.addFeedAndType(entryAttributeMap, feedKey.feed(), feedKey.type());
            // Note this call will throw an exception if any of the feeds are not allowed and will result in the
            // whole post being rejected. This is what we want to happen.
            if (attributeMapFilter.filter(entryAttributeMap)) {
                allowed.put(feedKey, zipEntryGroups);
            }
        });
        return allowed;
    }

    private static void writeZipEntryGroups(final Path entriesFile,
                                            final Map<FeedKey, List<ZipEntryGroup>> allowed) throws IOException {
        // Write out all the allowed entries so the zip splitter can make use of them
        try (final Writer writer = Files.newBufferedWriter(entriesFile)) {
            allowed.values()
                    .stream()
                    .flatMap(List::stream)
                    .forEach(zipEntryGroup -> {
                        try {
                            zipEntryGroup.write(writer);
                        } catch (final IOException e) {
                            throw new UncheckedIOException(LogUtil.message(
                                    "Error writing entries to {}: {}", entriesFile, LogUtil.exceptionMessage(e)), e);
                        }
                    });
        }
    }

    private static void logFeedGroupsToDebug(final ReceiveResult receiveResult) {
        final int count = receiveResult.feedGroups.size();
        final int limit = 10;
        final String suffix = count > limit
                ? "...<TRUNCATED>"
                : "";
        final String feedKeysStr = receiveResult.feedGroups.keySet()
                .stream()
                .sorted(Comparator.comparing(FeedKey::feed)
                        .thenComparing(FeedKey::type))
                .limit(limit)
                .map(Objects::toString)
                .collect(Collectors.joining(", "));
        LOGGER.debug("Received multi feed zip, feedGroups.size: {}, feedKeys: [{}{}]",
                receiveResult.feedGroups.size(), feedKeysStr, suffix);
    }

    private void deleteDir(final Path path) {
        if (path != null) {
            if (!FileUtil.deleteDir(path)) {
                LOGGER.error("Failed to delete dir " + FileUtil.getCanonicalPath(path));
            }
        }
    }

    /**
     * Static and pkg private to aid testing
     */
    static ReceiveResult receiveZipStream(final InputStream inputStream,
                                          final AttributeMap attributeMap,
                                          final Path destZipFile) throws IOException {

        LOGGER.debug("receiveZipStream() - destZipFile: {}, attributeMap: {}", destZipFile, attributeMap);
        // Create a .zip.staging file for the inputStream to be written to. We can then
        // copy what we want out of that zip into a new zip at zipFilePath.
        // Don't use a temp dir as these files may be very big, so just make it a sibling.
        final Path stagingZipFile = destZipFile.resolveSibling(destZipFile.getFileName() + ".staging");

        final long receivedBytes;
        try {
            // Write the stream to disk, because reading the stream as a ZipArchiveInputStream is risky
            // as it can't read the central directory at the end of the stream, so it doesn't know which
            // entries are actually valid and doesn't know the uncompressed sizes.
            receivedBytes = writeStreamToFile(inputStream, stagingZipFile);
            return receiveZipStream(attributeMap, stagingZipFile, destZipFile, receivedBytes);
        } finally {
            Files.deleteIfExists(stagingZipFile);
        }
    }

    /**
     * Static and pkg private to aid testing
     */
    static ReceiveResult receiveZipStream(final AttributeMap attributeMap,
                                          final Path sourceZipFile,
                                          final Path destZipFile,
                                          final long receivedBytes) throws IOException {
        LOGGER.debug("receiveZipStream() - sourceZipFile: {}, destZipFile: {}, attributeMap: {}",
                sourceZipFile, destZipFile, attributeMap);
        final DurationTimer timer = LogUtil.startTimerIfDebugEnabled(LOGGER);
        final String defaultFeedName = attributeMap.get(StandardHeaderArguments.FEED);
        final String defaultTypeName = attributeMap.get(StandardHeaderArguments.TYPE);
        // This is to reduce the memory used by all the FeedKey objects in the ZipEntryGroups
        final FeedKeyInterner feedKeyInterner = FeedKey.createInterner();
        final FeedKey defaultFeedKey = feedKeyInterner.intern(defaultFeedName, defaultTypeName);

        final Map<String, ZipEntryGroup> baseNameToGroupMap = new HashMap<>();
        final ProxyZipValidator validator = new ProxyZipValidator();
        final List<Entry> dataEntries = new ArrayList<>();

        // Clone the zip with added/updated meta entries
        cloneZipFileWithUpdatedMeta(
                attributeMap,
                defaultFeedKey,
                feedKeyInterner,
                baseNameToGroupMap,
                validator,
                dataEntries,
                sourceZipFile,
                destZipFile);

        // TODO : Worry about memory usage here storing potentially 1000's of data entries and groups.
        // Now look at the entries and see if we can match them to meta.
        final List<ZipEntryGroup> entryList = new ArrayList<>();
        for (final ZipEntryGroup.Entry dataEntry : dataEntries) {
            // We only care about data and any other content used to support it.
            final FileName fileName = FileName.parse(dataEntry.getName());

            // First try the full name of the data file as if it were a base name.
            ZipEntryGroup zipEntryGroup = baseNameToGroupMap.get(fileName.getFullName());
            if (zipEntryGroup == null) {
                // If we didn't get it then try the base name.
                zipEntryGroup = baseNameToGroupMap.get(fileName.getBaseName());
            }

            if (zipEntryGroup == null) {
                // If we can't find a matching group then create a new one.
                zipEntryGroup = new ZipEntryGroup(defaultFeedKey);
                zipEntryGroup.setDataEntry(dataEntry);
                entryList.add(zipEntryGroup);
            } else {
                if (zipEntryGroup.getDataEntry() != null) {
                    // This shouldn't really happen as it means we found meta that could be for more than
                    // one data entry.
                    LOGGER.warn(() -> "Meta for multiple data entries found");
                    // It might not be correct, but we will cope with this by duplicating the meta etc.
                    // associated with the data.
                    zipEntryGroup = new ZipEntryGroup(
                            zipEntryGroup.getFeedKey(),
                            zipEntryGroup.getManifestEntry(),
                            zipEntryGroup.getMetaEntry(),
                            zipEntryGroup.getContextEntry(),
                            dataEntry);
                    entryList.add(zipEntryGroup);
                } else {
                    zipEntryGroup.setDataEntry(dataEntry);
                    entryList.add(zipEntryGroup);
                }
            }
        }

        // Make sure we don't have any hanging meta etc.
        baseNameToGroupMap.values()
                .stream()
                .filter(group -> Objects.isNull(group.getDataEntry()))
                .forEach(group -> {
                    if (group.getManifestEntry() != null) {
                        LOGGER.warn(() -> "Unused manifest: " + group.getManifestEntry());
                    }
                    if (group.getMetaEntry() != null) {
                        LOGGER.warn(() -> "Unused meta: " + group.getMetaEntry());
                    }
                    if (group.getContextEntry() != null) {
                        LOGGER.warn(() -> "Unused context: " + group.getContextEntry());
                    }
                });

        // Split the groups by feed key.
        final Map<FeedKey, List<ZipEntryGroup>> feedGroups = entryList.stream()
                .collect(Collectors.groupingBy(ZipEntryGroup::getFeedKey));

        // We might want to know what was wrong with the received data.
        if (!validator.isValid()) {
            LOGGER.debug(validator.getErrorMessage());
        }

        LOGGER.debug(() -> LogUtil.message(
                "receiveZipStream() - FINISH defaultFeedName: '{}', defaultTypeName: '{}', zipFilePath: {}, " +
                "feedKey count: {}, total entry count: {}, duration: {}",
                defaultFeedName,
                defaultTypeName,
                destZipFile,
                feedGroups.size(),
                LogUtil.swallowExceptions(() -> feedGroups.values().stream().mapToInt(List::size).sum())
                        .orElse(-1),
                timer));

        return new ReceiveResult(feedGroups, receivedBytes, validator.isValid());
    }

    static void cloneZipFileWithUpdatedMeta(final AttributeMap attributeMap,
                                            final FeedKey defaultFeedKey,
                                            final FeedKeyInterner feedKeyInterner,
                                            final Map<String, ZipEntryGroup> baseNameToGroupMap,
                                            final ProxyZipValidator validator,
                                            final List<Entry> dataEntries,
                                            final Path stagingZipFilePath,
                                            final Path zipFilePath) throws IOException {

        LOGGER.debug("cloneZipFileWithUpdateMeta() - START defaultFeedKey: '{}', " +
                     "stagingZipFilePath: {}, zipFilePath: {}",
                defaultFeedKey, stagingZipFilePath, zipFilePath);

        final AtomicLong totalUncompressedSize = new AtomicLong();
        final DurationTimer timer = LogUtil.startTimerIfDebugEnabled(LOGGER);
        // Read the entries from the staging zip and write them to the
        try (final ZipWriter zipWriter = new ZipWriter(zipFilePath, LocalByteBuffer.get())) {
            ZipUtil.forEachEntry(stagingZipFilePath, (stagingZip, entry) -> {
                checkZipEntry(entry);
                final long size = cloneZipEntry(
                        defaultFeedKey,
                        feedKeyInterner,
                        attributeMap,
                        stagingZip,
                        entry,
                        validator,
                        zipWriter,
                        baseNameToGroupMap,
                        dataEntries);
                totalUncompressedSize.addAndGet(size);
            });
        } finally {
            try {
                Files.delete(stagingZipFilePath);
            } catch (final IOException e) {
                LOGGER.error("Error deleting stagingZipFilePath {}, msg: {}",
                        stagingZipFilePath, LogUtil.exceptionMessage(e), e);
            }
        }

        LOGGER.debug("cloneZipFileWithUpdateMeta() - START defaultFeedKey: '{}', " +
                     "stagingZipFilePath: {}, zipFilePath: {}, totalUncompressedSize: {}, duration: {}",
                defaultFeedKey, stagingZipFilePath, zipFilePath, totalUncompressedSize, timer);
    }

    private static void checkZipEntry(final ZipArchiveEntry zipEntry) {
        final String fileName = zipEntry.getName();
        if (!ZipUtil.isSafeZipPath(Path.of(fileName))) {
            // Only a warning as we do not use the zip entry name when extracting from the zip.
            LOGGER.warn("Zip archive stream contains a path that would extract to outside the " +
                        "target directory '{}'. Stroom-Proxy will not use this path but this is " +
                        "dangerous behaviour.", fileName);
        }
    }

    private static long cloneZipEntry(final FeedKey defaultFeedKey,
                                      final FeedKeyInterner feedKeyInterner,
                                      final AttributeMap attributeMap,
                                      final ZipFile stagingZip,
                                      final ZipArchiveEntry entry,
                                      final ProxyZipValidator validator,
                                      final ZipWriter zipWriter,
                                      final Map<String, ZipEntryGroup> baseNameToGroupMap,
                                      final List<Entry> dataEntries) {
        try {
            final String entryName = entry.getName();
            // We will validate the data as we receive it to see if the format is exactly as expected.
            validator.addEntry(entryName);

            final long size;
            if (entry.isDirectory()) {
                zipWriter.writeDir(entryName);
                size = getSize(entry);
            } else {
                final FileName fileName = FileName.parse(entryName);
                final String baseName = fileName.getBaseName();
                final StroomZipFileType stroomZipFileType =
                        StroomZipFileType.fromExtension(fileName.getExtension());

                if (StroomZipFileType.META.equals(stroomZipFileType)) {
                    size = cloneAndUpdateMetaEntry(
                            attributeMap,
                            feedKeyInterner,
                            stagingZip,
                            entry,
                            zipWriter,
                            baseNameToGroupMap,
                            entryName,
                            baseName);
                } else if (StroomZipFileType.CONTEXT.equals(stroomZipFileType)) {
                    final ZipEntryGroup zipEntryGroup = baseNameToGroupMap.computeIfAbsent(baseName, k ->
                            new ZipEntryGroup(defaultFeedKey));
                    if (zipEntryGroup.getContextEntry() != null) {
                        throw new RuntimeException("Duplicate context found: " + entryName);
                    }

                    size = writeUnchangedEntry(zipWriter, stagingZip, entry);
                    zipEntryGroup.setContextEntry(new Entry(entryName, size));
                } else if (StroomZipFileType.MANIFEST.equals(stroomZipFileType)) {
                    final ZipEntryGroup zipEntryGroup = baseNameToGroupMap.computeIfAbsent(baseName, k ->
                            new ZipEntryGroup(defaultFeedKey));
                    if (zipEntryGroup.getManifestEntry() != null) {
                        throw new RuntimeException("Duplicate manifest found: " + entryName);
                    }

                    size = writeUnchangedEntry(zipWriter, stagingZip, entry);
                    zipEntryGroup.setManifestEntry(new Entry(entryName, size));
                } else {
                    size = writeUnchangedEntry(zipWriter, stagingZip, entry);
                    dataEntries.add(new Entry(entryName, size));
                }
            }
            return size;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static AttributeMap mergeAttributeMaps(final ZipFile zipFile,
                                                   final ZipArchiveEntry entry,
                                                   final AttributeMap headerAttributeMap) {

        return AttributeMapUtil.mergeAttributeMaps(
                headerAttributeMap,
                ThrowingConsumer.unchecked(entryAttributeMap ->
                        AttributeMapUtil.read(zipFile.getInputStream(entry), entryAttributeMap)));
    }

    /**
     * @return The uncompressed size of the entry
     */
    private static long cloneAndUpdateMetaEntry(final AttributeMap attributeMap,
                                                final FeedKeyInterner feedKeyInterner,
                                                final ZipFile stagingZip,
                                                final ZipArchiveEntry entry,
                                                final ZipWriter zipWriter,
                                                final Map<String, ZipEntryGroup> baseNameToGroupMap,
                                                final String entryName,
                                                final String baseName) throws IOException {
        final long size;
        final AttributeMap entryAttributeMap = mergeAttributeMaps(stagingZip, entry, attributeMap);

        // Intern the feedKey to save on mem use.
        final FeedKey feedKey = feedKeyInterner.intern(
                entryAttributeMap.get(StandardHeaderArguments.FEED),
                entryAttributeMap.get(StandardHeaderArguments.TYPE));

        final byte[] bytes = AttributeMapUtil.toByteArray(entryAttributeMap);
        zipWriter.writeStream(entryName, new ByteArrayInputStream(bytes));

        final ZipEntryGroup zipEntryGroup = baseNameToGroupMap
                .computeIfAbsent(baseName, k -> new ZipEntryGroup(feedKey));
        // Ensure we override the feed and type names with the meta.
        zipEntryGroup.setFeedKey(feedKey);

        if (zipEntryGroup.getMetaEntry() != null) {
            throw new RuntimeException("Duplicate meta found: " + entryName);
        }
        size = bytes.length;
        zipEntryGroup.setMetaEntry(new Entry(entryName, size));
        return size;
    }

    static long getSize(final ArchiveEntry archiveEntry) {
        final long size = archiveEntry.getSize();
        return size != ArchiveEntry.SIZE_UNKNOWN
                ? size
                : 0;
    }

    static long writeStreamToFile(final InputStream inputStream,
                                  final Path zipFilePath) {
        return LOGGER.logDurationIfDebugEnabled(() -> {
            try (final ByteCountInputStream byteCountInputStream = ByteCountInputStream.wrap(inputStream)) {
                Files.copy(byteCountInputStream, zipFilePath);
                return byteCountInputStream.getCount();
            } catch (final IOException e) {
                throw new UncheckedIOException(LogUtil.message(
                        "Error writing inputStream to file {}: {}",
                        zipFilePath, LogUtil.exceptionMessage(e)), e);
            }
        }, receivedBytes -> LogUtil.message("writeStreamToFile() - zipFilePath: {}, receivedBytes: {}",
                zipFilePath, receivedBytes));
    }

    /**
     * @return The uncompressed size of the entry
     */
    private static long writeUnchangedEntry(final ZipWriter zipWriter,
                                            final ZipFile sourceZipFile,
                                            final ZipArchiveEntry sourceEntry) throws IOException {
        Objects.requireNonNull(sourceEntry);
        final boolean hasKnownSize = ZipUtil.hasKnownUncompressedSize(sourceEntry);
        final long size;
        if (ZipUtil.hasKnownUncompressedSize(sourceEntry)) {
            // We know the size so can just write the raw entry without having to de-compress/compress it
            try (final InputStream rawInputStream = sourceZipFile.getRawInputStream(sourceEntry)) {
                zipWriter.writeRawStream(sourceEntry, rawInputStream);
                size = sourceEntry.getSize();
            }
        } else {
            // We don't know the uncompressed size, so have to effectively de-compress/compress it to find out
            try (final InputStream inputStream = sourceZipFile.getInputStream(sourceEntry)) {
                size = zipWriter.writeStream(sourceEntry.getName(), inputStream);
            }
        }
        LOGGER.debug(() -> LogUtil.message("writeUnchangedEntry() - sourceEntry: {}, hasKnownSize: {}, size: {}",
                sourceEntry,
                hasKnownSize,
                (size != ArchiveEntry.SIZE_UNKNOWN
                        ? ByteSize.ofBytes(size).toString()
                        : "?")));
        return size;
    }

    public void setDestination(final Consumer<Path> destination) {
        this.destination = destination;
        this.zipSplitter.setDestination(destination);
    }


    // --------------------------------------------------------------------------------


    record ReceiveResult(Map<FeedKey, List<ZipEntryGroup>> feedGroups,
                         long receivedBytes,
                         boolean valid) {

    }
}
