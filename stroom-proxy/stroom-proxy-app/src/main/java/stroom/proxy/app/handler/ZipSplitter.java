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
import stroom.proxy.app.DataDirProvider;
import stroom.proxy.app.handler.ZipEntryGroup.Entry;
import stroom.proxy.repo.FeedKey;
import stroom.proxy.repo.ProxyServices;
import stroom.util.io.FileUtil;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.zip.ZipUtil;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * <p>
 * This processor will split an input zip into multiple output zips, grouping entries by {@link FeedKey}
 * (feed and type).
 * It is also used to normalise an input zip that is not in the correct proxy zip format into
 * one that is.
 * </p><p>
 * As a result of processing, all resulting zip files will be in a known proxy file format consisting of entries of 10
 * numeric characters and appropriate file extensions. The entry order will also be specific with associated entries
 * having the order (manifest, meta, context, data).
 * </p><p>
 * Note that it is not necessary for a manifest file (.mf) to exist at all but if must be the first file if it is
 * present. It is also not necessary for context files (.ctx) to exist (in fact they are rarely used), but if present
 * they must precede the meta file (.meta). Finally all entry sets must include the actual data to be valid (.dat).
 * </p><p>
 * An example zip file will look like this:
 * 0000000001.mf
 * 0000000001.meta
 * 0000000001.ctx
 * 0000000001.dat
 * 0000000002.meta
 * 0000000002.ctx
 * 0000000002.dat
 * 0000000003.meta
 * 0000000003.dat
 * ...
 * </p><p>
 * Along with the final zip files there will be associated meta files written to use for forwarding.
 * There will also be an entries file that tells us the size of each file set. The entries file has a JSON string
 * for each set of files in the zip. This data allows us to understand how best to aggregate this data if required.
 * </p>
 */
@Singleton
public class ZipSplitter {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ZipSplitter.class);
//    private static final String SPLIT_DIR_PREFIX = "split-";

    private final DirQueue splittingQueue;
    private final NumberedDirProvider splitZipDirProvider;
    private Consumer<Path> destination;

    @Inject
    public ZipSplitter(final DataDirProvider dataDirProvider,
                       final DirQueueFactory dirQueueFactory,
                       final ProxyServices proxyServices,
                       final ThreadConfig threadConfig) {
        // Get or create the split zip dir provider.
        splitZipDirProvider = createDirProvider(dataDirProvider, DirNames.SPLIT_ZIP);
        final Path splitZipQueue = dataDirProvider.get().resolve(DirNames.SPLIT_ZIP_QUEUE);

        splittingQueue = dirQueueFactory.create(
                splitZipQueue,
                2,
                "Zip Splitting Input Queue");

        final DirQueueTransfer dirQueueTransfer = new DirQueueTransfer(
                splittingQueue::next,
                sourceDir ->
                        splitZipByFeed(sourceDir, splitZipDirProvider, getDestination()));

        proxyServices.addParallelExecutor(
                "Zip split by feed input queue transfer",
                () -> dirQueueTransfer,
                threadConfig.getZipSplittingInputQueueThreadCount());
    }

    public void setDestination(final Consumer<Path> destination) {
        LOGGER.debug("setDestination() - destination {}", destination);
        this.destination = destination;
    }

    private Consumer<Path> getDestination() {
        return destination;
    }

    public void add(final Path sourceDir) {
        LOGGER.debug("add() - sourceDir: {}", sourceDir);
        splittingQueue.add(sourceDir);
    }

    /**
     * Pkg private to ease testing. splitDirConsumer also there to aid testing.
     */
    static void splitZipByFeed(final Path sourceDir,
                               final NumberedDirProvider splitZipDirProvider,
                               final Consumer<Path> splitDirConsumer) {
        LOGGER.debug("splitZipByFeed() - sourceDir: {}", sourceDir);
        Path splitZipDir = null;
        try {
            Objects.requireNonNull(sourceDir);

            final FileGroup fileGroup = new FileGroup(sourceDir);
            // Read in the attribute map from the client's headers
            final AttributeMap attributeMap = readMetaFile(fileGroup.getMeta());

            // These are all the entries in the zip that have been allowed by the attrMapFilter,
            // so may be less than the number of entries in the zip
            final Map<FeedKey, List<ZipEntryGroup>> allowedEntries = readEntriesFile(fileGroup.getEntries());
            LOGGER.debug(() -> LogUtil.message("allowedEntries size: {}", allowedEntries.size()));

            // Create a dir to put the all splits into
            splitZipDir = splitZipDirProvider.get();
            final List<Path> groupDirs = splitZip(
                    fileGroup.getZip(),
                    attributeMap,
                    allowedEntries,
                    splitZipDir);

            // Move each group dir to onward destination
            for (final Path groupDir : groupDirs) {
                LOGGER.debug("Pass {}, sourceDir: {}, to destination {}",
                        groupDir, sourceDir, splitDirConsumer);
                splitDirConsumer.accept(groupDir);
            }
            // Passed all the splits on, so delete the source
            deleteDir(sourceDir);
        } catch (final Exception e) {
            LOGGER.error("Error splitting zip in {}: {}", sourceDir, LogUtil.exceptionMessage(e), e);
            throw new RuntimeException(e);
        } finally {
            // Whatever happens delete the splits as they can always be re-created from source.
            FileUtil.deleteDir(splitZipDir);
        }
    }

    private static AttributeMap readMetaFile(final Path metaFile) throws IOException {
        final AttributeMap attributeMap = new AttributeMap();
        if (Files.exists(metaFile)) {
            AttributeMapUtil.read(metaFile, attributeMap);
        }
        return attributeMap;
    }

    private static Map<FeedKey, List<ZipEntryGroup>> readEntriesFile(final Path entriesFile) {
        // Read in the allowed (i.e. passed feed status check) zip entry groups.
        // Use the interner so common FeedKeys use the same instance
        if (Files.isRegularFile(entriesFile)) {
            return ZipEntryGroup.read(entriesFile)
                    .stream()
                    .collect(Collectors.groupingBy(
                            ZipEntryGroup::getFeedKey,
                            Collectors.toList()));
        } else {
            throw new RuntimeException(LogUtil.message(
                    "Entries file {} not found. Should not get here with no entries file", entriesFile));
        }
    }

    static List<Path> splitZip(final Path zipFilePath,
                               final AttributeMap attributeMap,
                               final Map<FeedKey, List<ZipEntryGroup>> allowedEntries,
                               final Path outputParentDir) throws IOException {
        LOGGER.debug("splitZip() - START zipFilePath: {}, outputParentDir: {}", zipFilePath, outputParentDir);
        final List<Path> groupDirs = new ArrayList<>();
        final DurationTimer timer = LogUtil.startTimerIfDebugEnabled(LOGGER);
        final long id = 1;
        try (final ZipFile zipFile = ZipUtil.createZipFile(zipFilePath)) {
            // Only the entries in the map are to be included in the splits, so iterate over it
            // then copy all the entries for that feedKey from the source zip into the dest zip.
            final byte[] buffer = LocalByteBuffer.get();
            for (final Map.Entry<FeedKey, List<ZipEntryGroup>> entry : allowedEntries.entrySet()) {
                final FeedKey feedKey = entry.getKey();
                final List<ZipEntryGroup> zipEntryGroups = entry.getValue();
//                final String name = SPLIT_DIR_PREFIX + NumericFileNameUtil.create(id++);
                final String name = DirUtil.makeSafeName(feedKey);
                final Path groupDir = outputParentDir.resolve(name);
                Files.createDirectory(groupDir);
                final FileGroup fileGroup = new FileGroup(groupDir);
                groupDirs.add(groupDir);

                writeSplitToZip(zipFile, zipEntryGroups, feedKey, attributeMap, fileGroup, buffer);
            }
        }
        LOGGER.debug(() -> LogUtil.message(
                "splitZip() - FINISH zipFilePath: {}, outputParentDir: {}, groupDirs count: {}, " +
                "feedGroups count: {}, duration: {}",
                zipFilePath, outputParentDir, groupDirs.size(), allowedEntries.size(), timer));
        return groupDirs;
    }

    private static void writeSplitToZip(final ZipFile sourceZip,
                                        final List<ZipEntryGroup> zipEntryGroupsIn,
                                        final FeedKey feedKey,
                                        final AttributeMap attributeMap,
                                        final FileGroup destFileGroup,
                                        final byte[] buffer) throws IOException {
        LOGGER.debug("writeZip() - START feedKey: {}, destFileGroup: {}", feedKey, destFileGroup);
        final DurationTimer timer = LogUtil.startTimerIfDebugEnabled(LOGGER);
        try {
            // Write zip.
            final AtomicInteger count = new AtomicInteger(0);
            final Path entriesFile = destFileGroup.getEntries();
            try (final Writer entryWriter = Files.newBufferedWriter(entriesFile)) {
                try (final ProxyZipWriter destZipWriter = new ProxyZipWriter(destFileGroup.getZip(), buffer)) {
                    for (final ZipEntryGroup zipEntryGroupIn : zipEntryGroupsIn) {
                        final ZipEntryGroup zipEntryGroupOut = new ZipEntryGroup(feedKey);
                        final String baseNameOut = NumericFileNameUtil.create(count.incrementAndGet());
                        LOGGER.trace("feedKey: {}, baseNameOut: {}, zipEntryGroupIn: {} => zipEntryGroupOut: {}",
                                feedKey, baseNameOut, zipEntryGroupIn, zipEntryGroupOut);
                        zipEntryGroupOut.setManifestEntry(
                                addUnchangedEntry(sourceZip,
                                        destZipWriter,
                                        zipEntryGroupIn.getManifestEntry(),
                                        baseNameOut,
                                        StroomZipFileType.MANIFEST));
                        zipEntryGroupOut.setMetaEntry(
                                addMetaEntry(sourceZip,
                                        destZipWriter,
                                        zipEntryGroupIn.getMetaEntry(),
                                        baseNameOut,
                                        StroomZipFileType.META,
                                        attributeMap));
                        zipEntryGroupOut.setContextEntry(
                                addUnchangedEntry(sourceZip,
                                        destZipWriter,
                                        zipEntryGroupIn.getContextEntry(),
                                        baseNameOut,
                                        StroomZipFileType.CONTEXT));
                        zipEntryGroupOut.setDataEntry(
                                addUnchangedEntry(sourceZip,
                                        destZipWriter,
                                        zipEntryGroupIn.getDataEntry(),
                                        baseNameOut,
                                        StroomZipFileType.DATA));

                        // Write zip entry.
                        LOGGER.trace("Writing entries file {}", entriesFile);
                        zipEntryGroupOut.write(entryWriter);
                    }
                }
            }

            // Write meta.
            AttributeMapUtil.addFeedAndType(attributeMap, feedKey.feed(), feedKey.type());
            AttributeMapUtil.write(attributeMap, destFileGroup.getMeta());
            LOGGER.debug("writeZip() - FINISH feedKey: {}, destFileGroup: {}, count: {}, duration: {}",
                    feedKey, destFileGroup, count, timer);

        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }
    }

    private static ZipEntryGroup.Entry addUnchangedEntry(final ZipFile zip,
                                                         final ProxyZipWriter zipWriter,
                                                         final ZipEntryGroup.Entry entry,
                                                         final String baseNameOut,
                                                         final StroomZipFileType stroomZipFileType)
            throws IOException {

        if (entry != null) {
            final ZipArchiveEntry zipEntry = zip.getEntry(entry.getName());
            // We are just writing an entry from one zip to another, so we can just
            // work with the raw (i.e. compressed) stream, which saves us from having to
            // de-compress/compress it
            final InputStream inputStream = zip.getRawInputStream(zipEntry);
            final String outEntryName = baseNameOut + stroomZipFileType.getDotExtension();
            zipWriter.writeRawStream(zipEntry, outEntryName, inputStream);
            return new Entry(outEntryName, entry.getUncompressedSize());
        }
        return null;
    }

    private static ZipEntryGroup.Entry addMetaEntry(final ZipFile zip,
                                                    final ProxyZipWriter zipWriter,
                                                    final ZipEntryGroup.Entry entry,
                                                    final String baseNameOut,
                                                    final StroomZipFileType stroomZipFileType,
                                                    final AttributeMap globalAttributeMap) throws IOException {
        final AttributeMap entryAttributeMap = AttributeMapUtil.cloneAllowable(globalAttributeMap);
        if (entry != null) {
            final ZipArchiveEntry zipEntry = zip.getEntry(entry.getName());
            final InputStream inputStream = zip.getInputStream(zipEntry);
            AttributeMapUtil.read(inputStream, entryAttributeMap);
        }

        final byte[] bytes = AttributeMapUtil.toByteArray(entryAttributeMap);
        final String outEntryName = baseNameOut + stroomZipFileType.getDotExtension();
        zipWriter.writeStream(outEntryName, new ByteArrayInputStream(bytes));
        return new Entry(outEntryName, bytes.length);
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


    private static void deleteDir(final Path path) {
        if (path != null) {
            if (!FileUtil.deleteDir(path)) {
                LOGGER.error("Failed to delete dir " + FileUtil.getCanonicalPath(path));
            }
        }
    }
}
