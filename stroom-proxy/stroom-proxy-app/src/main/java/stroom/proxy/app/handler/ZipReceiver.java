package stroom.proxy.app.handler;

import stroom.data.zip.StroomZipFileType;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.app.DataDirProvider;
import stroom.proxy.app.handler.ZipEntryGroup.Entry;
import stroom.proxy.repo.FeedKey;
import stroom.proxy.repo.LogStream;
import stroom.receive.common.AttributeMapFilter;
import stroom.receive.common.AttributeMapFilterFactory;
import stroom.receive.common.StroomStreamException;
import stroom.util.io.ByteCountInputStream;
import stroom.util.io.FileName;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.hc.core5.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * <p>
 * This class deals with the reception of zip files. It will perform the following tasks:
 * 1. Streams the zip file to disk, recording zip entries and reading meta data as it goes.
 * 2. It tries to match all data entries to associated meta data.
 * 3. It finds all unique feeds and checks the status for each feed.
 * 4. It splits the data into separate zip files for each feed.
 * 5. It then sends each new zip to the destination.
 * </p><p>
 * As a result of processing all resulting zip files will be in a known proxy file format consisting of entries of 10
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
public class ZipReceiver implements Receiver {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ZipReceiver.class);
    private static final Logger RECEIVE_LOG = LoggerFactory.getLogger("receive");

    private final AttributeMapFilterFactory attributeMapFilterFactory;
    private final NumberedDirProvider receivingDirProvider;
    private final NumberedDirProvider splitZipDirProvider;
    private final LogStream logStream;
    private Consumer<Path> destination;

    @Inject
    public ZipReceiver(final AttributeMapFilterFactory attributeMapFilterFactory,
                       final DataDirProvider dataDirProvider,
                       final LogStream logStream) {
        this.attributeMapFilterFactory = attributeMapFilterFactory;
        this.logStream = logStream;

        // Make receiving zip dir provider.
        receivingDirProvider = createDirProvider(dataDirProvider, DirNames.RECEIVING_ZIP);

        // Get or create the split zip dir provider.
        splitZipDirProvider = createDirProvider(dataDirProvider, DirNames.SPLIT_ZIP);

//        // Get or create the received dir provider.
//        receivedDirProvider = createDirProvider(dataDirProvider, DirNames.RECEIVED_ZIP);

//        // Move any received data from previous proxy usage to the store.
//        transferOldReceivedData(receivedDir);

    }

    private NumberedDirProvider createDirProvider(final DataDirProvider dataDirProvider, final String dirName) {
        // Make dir
        final Path dir = dataDirProvider.get().resolve(dirName);
        DirUtil.ensureDirExists(dir);

        // This is a temporary location and can be cleaned completely on startup.
        if (!FileUtil.deleteContents(dir)) {
            LOGGER.error(() -> "Failed to delete contents of " + FileUtil.getCanonicalPath(dir));
        }
        return new NumberedDirProvider(dir);
    }

//    private void transferOldReceivedData(final Path receivedDir, final Consumer<Path> destination) {
//        try {
//            try (final Stream<Path> stream = Files.list(receivedDir)) {
//                stream.forEach(path -> {
//                    try {
//                        // We will assume each dir is good to transfer.
//                        destination.accept(path);
//                    } catch (final IOException e) {
//                        LOGGER.error(() -> "Failed to move data to store " + FileUtil.getCanonicalPath(path), e);
//                        throw new UncheckedIOException(e);
//                    }
//                });
//            }
//        } catch (final IOException e) {
//            LOGGER.error(() -> "Failed to move data to store " + FileUtil.getCanonicalPath(receivedDir), e);
//            throw new UncheckedIOException(e);
//        }
//    }

    @Override
    public void receive(final Instant startTime,
                        final AttributeMap attributeMap,
                        final String requestUri,
                        final InputStreamSupplier inputStreamSupplier) {
        final String defaultFeedName = attributeMap.get(StandardHeaderArguments.FEED);
        final String defaultTypeName = attributeMap.get(StandardHeaderArguments.TYPE);

        // Get a buffer to help us transfer data.
        final byte[] buffer = LocalByteBuffer.get();

        final Path receivingDir;
        final ReceiveResult receiveResult;
        try {
            receivingDir = receivingDirProvider.get();
            final FileGroup fileGroup = new FileGroup(receivingDir);
            final Path sourceZip = fileGroup.getZip();
            try {
                receiveResult = receiveZipStream(
                        inputStreamSupplier.get(),
                        defaultFeedName,
                        defaultTypeName,
                        attributeMap,
                        sourceZip,
                        buffer);
            } catch (final Exception e) {
                LOGGER.debug(e::getMessage, e);
                // Cleanup.
                Files.deleteIfExists(sourceZip);
                deleteDir(receivingDir);

                throw StroomStreamException.create(e, attributeMap);
            }

            if (receiveResult.feedGroups.size() > 1) {
                // Log if we received a multi feed zip.
                LOGGER.debug("Received multi feed zip");
            }

            // Check all the feeds are ok.
            final Map<FeedKey, List<ZipEntryGroup>> allowed = new HashMap<>();
            final AttributeMapFilter attributeMapFilter = attributeMapFilterFactory.create();
            for (final Map.Entry<FeedKey, List<ZipEntryGroup>> entry : receiveResult.feedGroups.entrySet()) {
                final FeedKey feedKey = entry.getKey();
                final List<ZipEntryGroup> zipEntryGroups = entry.getValue();
                final AttributeMap entryAttributeMap = AttributeMapUtil.cloneAllowable(attributeMap);
                AttributeMapUtil.addFeedAndType(entryAttributeMap, feedKey.feed(), feedKey.type());
                // Note this call will throw an exception if any of the feeds are not allowed and will result in the
                // whole post being rejected. This is what we want to happen.
                if (attributeMapFilter.filter(entryAttributeMap)) {
                    allowed.put(feedKey, zipEntryGroups);
                }
            }

            // Only keep data for allowed feeds.
            if (!allowed.isEmpty()) {

                // If the data we received was for a perfectly formed zip file with data for a single feed then don't
                // bother to rewrite.
                if (receiveResult.valid && receiveResult.feedGroups.size() == 1) {
                    // If we only had a single feed in the incoming zip then just pass on the data as is.

                    final Map.Entry<FeedKey, List<ZipEntryGroup>> entry = allowed.entrySet().iterator().next();
                    final FeedKey feedKey = entry.getKey();
                    final List<ZipEntryGroup> zipEntryGroups = entry.getValue();

                    // Write out entries.
                    try (final Writer writer = Files.newBufferedWriter(fileGroup.getEntries())) {
                        for (final ZipEntryGroup zipEntryGroup : zipEntryGroups) {
                            zipEntryGroup.write(writer);
                        }
                    }

                    // Write meta.
                    AttributeMapUtil.addFeedAndType(attributeMap, feedKey.feed(), feedKey.type());
                    AttributeMapUtil.write(attributeMap, fileGroup.getMeta());

                    // Move receiving dir to destination.
                    destination.accept(receivingDir);

                } else {
                    // We have more than one feed in the source zip so split the source into a zip file for each feed.
                    final Path splitZipDir = splitZipDirProvider.get();
                    final List<Path> groupDirs = splitZip(
                            sourceZip,
                            attributeMap,
                            allowed,
                            splitZipDir,
                            buffer);

                    // Move each group dir to the file store or forward if there is a single destination.
                    for (final Path groupDir : groupDirs) {
                        destination.accept(groupDir);
                    }

                    // We ought to be able to delete the splitZipDir as it ought to be empty.
                    Files.delete(splitZipDir);

                    // Delete the source zip.
                    Files.delete(sourceZip);
                    deleteDir(receivingDir);
                }
            } else {
                // Delete the source zip.
                Files.delete(sourceZip);
                deleteDir(receivingDir);
            }

        } catch (final IOException e) {
            throw StroomStreamException.create(e, attributeMap);
        }

        final Duration duration = Duration.between(startTime, Instant.now());
        logStream.log(
                RECEIVE_LOG,
                attributeMap,
                "RECEIVE",
                requestUri,
                HttpStatus.SC_OK,
                receiveResult.receivedBytes,
                duration.toMillis());
    }

    private void deleteDir(final Path path) {
        if (path != null) {
            if (!FileUtil.deleteDir(path)) {
                LOGGER.error("Failed to delete dir " + FileUtil.getCanonicalPath(path));
            }
        }
    }

    static ReceiveResult receiveZipStream(final InputStream inputStream,
                                          final String defaultFeedName,
                                          final String defaultTypeName,
                                          final AttributeMap attributeMap,
                                          final Path zipFilePath,
                                          final byte[] buffer) throws IOException {
        // TODO : Worry about memory usage here storing potentially 1000's of data entries and groups.
        final List<Entry> dataEntries = new ArrayList<>();
        final Map<String, ZipEntryGroup> groups = new HashMap<>();
        final ProxyZipValidator validator = new ProxyZipValidator();
        final long receivedBytes;

        // Receive data.
        try (final ByteCountInputStream byteCountInputStream =
                new ByteCountInputStream(inputStream)) {
            try (final ZipArchiveInputStream zipInputStream = new ZipArchiveInputStream(byteCountInputStream)) {
                // Write the incoming zip data to temp and record info about the entries as we go.
                try (final ZipWriter zipWriter = new ZipWriter(zipFilePath, buffer)) {

                    ZipArchiveEntry entry = zipInputStream.getNextEntry();
                    while (entry != null) {
                        // We will validate the data as we receive it to see if the format is exactly as expected.
                        validator.addEntry(entry.getName());

                        if (entry.isDirectory()) {
                            zipWriter.writeDir(entry.getName());

                        } else {
                            final FileName fileName = FileName.parse(entry.getName());
                            final String baseName = fileName.getBaseName();
                            final StroomZipFileType stroomZipFileType =
                                    StroomZipFileType.fromExtension(fileName.getExtension());

                            if (StroomZipFileType.META.equals(stroomZipFileType)) {
                                // Read the meta.
                                final AttributeMap entryAttributeMap =
                                        AttributeMapUtil.cloneAllowable(attributeMap);
                                AttributeMapUtil.read(zipInputStream, entryAttributeMap);

                                final String feedName = entryAttributeMap.get(StandardHeaderArguments.FEED);
                                final String typeName = entryAttributeMap.get(StandardHeaderArguments.TYPE);

                                final byte[] bytes = AttributeMapUtil.toByteArray(entryAttributeMap);
                                zipWriter.writeStream(entry.getName(), new ByteArrayInputStream(bytes));

                                final ZipEntryGroup zipEntryGroup = groups
                                        .computeIfAbsent(baseName, k -> new ZipEntryGroup(feedName, typeName));
                                // Ensure we override the feed and type names with the meta.
                                zipEntryGroup.setFeedName(feedName);
                                zipEntryGroup.setTypeName(typeName);

                                if (zipEntryGroup.getMetaEntry() != null) {
                                    throw new RuntimeException("Duplicate meta found: " + entry.getName());
                                }
                                zipEntryGroup.setMetaEntry(new ZipEntryGroup.Entry(entry.getName(), bytes.length));

                            } else if (StroomZipFileType.CONTEXT.equals(stroomZipFileType)) {
                                final ZipEntryGroup zipEntryGroup = groups.computeIfAbsent(baseName, k ->
                                        new ZipEntryGroup(defaultFeedName, defaultTypeName));
                                if (zipEntryGroup.getContextEntry() != null) {
                                    throw new RuntimeException("Duplicate context found: " + entry.getName());
                                }

                                final long size = zipWriter.writeStream(entry.getName(), zipInputStream);
                                zipEntryGroup.setContextEntry(new ZipEntryGroup.Entry(entry.getName(), size));

                            } else if (StroomZipFileType.MANIFEST.equals(stroomZipFileType)) {
                                final ZipEntryGroup zipEntryGroup = groups.computeIfAbsent(baseName, k ->
                                        new ZipEntryGroup(defaultFeedName, defaultTypeName));
                                if (zipEntryGroup.getManifestEntry() != null) {
                                    throw new RuntimeException("Duplicate manifest found: " + entry.getName());
                                }

                                final long size = zipWriter.writeStream(entry.getName(), zipInputStream);
                                zipEntryGroup.setManifestEntry(new ZipEntryGroup.Entry(entry.getName(), size));

                            } else {
                                final long size = zipWriter.writeStream(entry.getName(), zipInputStream);
                                dataEntries.add(new ZipEntryGroup.Entry(entry.getName(), size));
                            }

                            entry = zipInputStream.getNextEntry();
                        }
                    }
                }
            }

            // Find out how much data we received.
            receivedBytes = byteCountInputStream.getCount();
        }

        // Now look at the entries and see if we can match them to meta.
        final List<ZipEntryGroup> entryList = new ArrayList<>();
        for (final ZipEntryGroup.Entry entry : dataEntries) {
            // We only care about data and any other content used to support it.
            final FileName fileName = FileName.parse(entry.getName());

            // First try the full name of the data file as if it were a base name.
            ZipEntryGroup zipEntryGroup = groups.get(fileName.getFullName());
            if (zipEntryGroup == null) {
                // If we didn't get it then try the base name.
                zipEntryGroup = groups.get(fileName.getBaseName());
            }

            if (zipEntryGroup == null) {
                // If we can't find a matching group then create a new one.
                zipEntryGroup = new ZipEntryGroup(defaultFeedName, defaultTypeName);
                zipEntryGroup.setDataEntry(entry);
                entryList.add(zipEntryGroup);

            } else {
                if (zipEntryGroup.getDataEntry() != null) {
                    // This shouldn't really happen as it means we found meta that could be for more than
                    // one data entry.
                    LOGGER.warn(() -> "Meta for multiple data entries found");
                    // It might not be correct, but we will cope with this by duplicating the meta etc.
                    // associated with the data.
                    zipEntryGroup = new ZipEntryGroup(
                            zipEntryGroup.getFeedName(),
                            zipEntryGroup.getTypeName(),
                            zipEntryGroup.getManifestEntry(),
                            zipEntryGroup.getMetaEntry(),
                            zipEntryGroup.getContextEntry(),
                            entry);
                    entryList.add(zipEntryGroup);
                } else {
                    zipEntryGroup.setDataEntry(entry);
                    entryList.add(zipEntryGroup);
                }
            }
        }

        // Make sure we don't have any hanging meta etc.
        groups.values().stream().filter(group -> Objects.isNull(group.getDataEntry())).forEach(group -> {
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
        final Map<FeedKey, List<ZipEntryGroup>> feedGroups = new HashMap<>();
        entryList.forEach(group -> {
            final FeedKey feedKey = new FeedKey(group.getFeedName(), group.getTypeName());
            feedGroups.computeIfAbsent(feedKey, k -> new ArrayList<>()).add(group);
        });

        // We might want to know what was wrong with the received data.
        if (!validator.isValid()) {
            LOGGER.debug(validator.getErrorMessage());
        }

        return new ReceiveResult(feedGroups, receivedBytes, validator.isValid());
    }

    static List<Path> splitZip(final Path zipFilePath,
                               final AttributeMap attributeMap,
                               final Map<FeedKey, List<ZipEntryGroup>> feedGroups,
                               final Path outputParentDir,
                               final byte[] buffer) throws IOException {
        final List<Path> groupDirs = new ArrayList<>();
        long id = 1;
        try (final ZipFile zipFile = new ZipFile(Files.newByteChannel(zipFilePath))) {
            for (final Map.Entry<FeedKey, List<ZipEntryGroup>> entry : feedGroups.entrySet()) {
                final FeedKey feedKey = entry.getKey();
                final List<ZipEntryGroup> zipEntryGroups = entry.getValue();
                final String name = NumericFileNameUtil.create(id++);
                final Path groupDir = outputParentDir.resolve(name);
                Files.createDirectory(groupDir);
                final FileGroup fileGroup = new FileGroup(groupDir);
                groupDirs.add(groupDir);
                writeZip(zipFile, zipEntryGroups, feedKey, attributeMap, fileGroup, buffer);
            }
        }
        return groupDirs;
    }

    private static void writeZip(final ZipFile zip,
                                 final List<ZipEntryGroup> zipEntryGroupsIn,
                                 final FeedKey feedKey,
                                 final AttributeMap attributeMap,
                                 final FileGroup fileGroup,
                                 final byte[] buffer) throws IOException {
        try {
            // Write zip.
            int count = 1;
            try (final Writer entryWriter = Files.newBufferedWriter(fileGroup.getEntries())) {
                try (final ProxyZipWriter zipWriter = new ProxyZipWriter(fileGroup.getZip(), buffer)) {
                    for (final ZipEntryGroup zipEntryGroupIn : zipEntryGroupsIn) {
                        final ZipEntryGroup zipEntryGroupOut = new ZipEntryGroup(feedKey.feed(), feedKey.type());
                        final String baseNameOut = NumericFileNameUtil.create(count);
                        zipEntryGroupOut.setManifestEntry(
                                addEntry(zip,
                                        zipWriter,
                                        zipEntryGroupIn.getManifestEntry(),
                                        baseNameOut,
                                        StroomZipFileType.MANIFEST));
                        zipEntryGroupOut.setMetaEntry(
                                addMetaEntry(zip,
                                        zipWriter,
                                        zipEntryGroupIn.getMetaEntry(),
                                        baseNameOut,
                                        StroomZipFileType.META,
                                        attributeMap));
                        zipEntryGroupOut.setContextEntry(
                                addEntry(zip,
                                        zipWriter,
                                        zipEntryGroupIn.getContextEntry(),
                                        baseNameOut,
                                        StroomZipFileType.CONTEXT));
                        zipEntryGroupOut.setDataEntry(
                                addEntry(zip,
                                        zipWriter,
                                        zipEntryGroupIn.getDataEntry(),
                                        baseNameOut,
                                        StroomZipFileType.DATA));

                        count++;

                        // Write zip entry.
                        zipEntryGroupOut.write(entryWriter);
                    }
                }
            }

            // Write meta.
            AttributeMapUtil.addFeedAndType(attributeMap, feedKey.feed(), feedKey.type());
            AttributeMapUtil.write(attributeMap, fileGroup.getMeta());

        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }
    }

    private static ZipEntryGroup.Entry addEntry(final ZipFile zip,
                                                final ProxyZipWriter zipWriter,
                                                final ZipEntryGroup.Entry entry,
                                                final String baseNameOut,
                                                final StroomZipFileType stroomZipFileType) throws IOException {
        if (entry != null) {
            final ZipArchiveEntry zipEntry = zip.getEntry(entry.getName());
            final InputStream inputStream = zip.getInputStream(zipEntry);
            final String outEntryName = baseNameOut + stroomZipFileType.getDotExtension();
            zipWriter.writeStream(outEntryName, inputStream);
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

    public void setDestination(final Consumer<Path> destination) {
        this.destination = destination;
    }

    record ReceiveResult(Map<FeedKey, List<ZipEntryGroup>> feedGroups,
                         long receivedBytes,
                         boolean valid) {

    }
}
