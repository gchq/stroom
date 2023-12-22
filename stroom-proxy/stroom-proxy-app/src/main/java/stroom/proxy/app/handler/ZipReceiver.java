package stroom.proxy.app.handler;

import stroom.data.zip.StroomZipFileType;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.repo.FeedKey;
import stroom.proxy.repo.LogStream;
import stroom.proxy.repo.RepoDirProvider;
import stroom.receive.common.AttributeMapFilter;
import stroom.receive.common.ProgressHandler;
import stroom.receive.common.StroomStreamException;
import stroom.util.io.ByteCountInputStream;
import stroom.util.io.FileName;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

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

    private final AttributeMapFilter attributeMapFilter;
    private final NumberedDirProvider receivingDirProvider;
    private final NumberedDirProvider receivedDirProvider;
    private final LogStream logStream;
    private final Provider<Destination> destinationProvider;

    @Inject
    public ZipReceiver(final AttributeMapFilter attributeMapFilter,
                       final TempDirProvider tempDirProvider,
                       final RepoDirProvider repoDirProvider,
                       final LogStream logStream,
                       final Provider<Destination> destinationProvider) {
        this.attributeMapFilter = attributeMapFilter;
        this.logStream = logStream;
        this.destinationProvider = destinationProvider;

        // Make receiving zip dir.
        final Path receivingDir = tempDirProvider.get().resolve("01_receiving_zip");
        ensureDirExists(receivingDir);

        // This is a temporary location and can be cleaned completely on startup.
        if (!FileUtil.deleteContents(receivingDir)) {
            LOGGER.error(() -> "Failed to delete contents of " + FileUtil.getCanonicalPath(receivingDir));
        }
        receivingDirProvider = new NumberedDirProvider(receivingDir);

        // Get or create the received dir.
        final Path receivedDir = repoDirProvider.get().resolve("02_received_zip");
        ensureDirExists(receivedDir);

        // Move any received data from previous proxy usage to the store.
        transferOldReceivedData(receivedDir);
        receivedDirProvider = new NumberedDirProvider(receivedDir);
    }

    private void ensureDirExists(final Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (final IOException e) {
            LOGGER.error(() -> "Failed to create " + FileUtil.getCanonicalPath(dir), e);
            throw new UncheckedIOException(e);
        }
    }

    private void transferOldReceivedData(final Path receivedDir) {
        try {
            try (final Stream<Path> stream = Files.list(receivedDir)) {
                stream.forEach(path -> {
                    try {
                        // We will assume each dir is good to transfer.
                        destinationProvider.get().add(path);
                    } catch (final IOException e) {
                        LOGGER.error(() -> "Failed to move data to store " + FileUtil.getCanonicalPath(path), e);
                        throw new UncheckedIOException(e);
                    }
                });
            }
        } catch (final IOException e) {
            LOGGER.error(() -> "Failed to move data to store " + FileUtil.getCanonicalPath(receivedDir), e);
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void receive(final Instant startTime,
                        final AttributeMap attributeMap,
                        final String requestUri,
                        final InputStreamSupplier inputStreamSupplier) {
        final String defaultFeedName = attributeMap.get(StandardHeaderArguments.FEED);
        final String defaultTypeName = attributeMap.get(StandardHeaderArguments.TYPE);

        // TODO : We could add an optimisation here to identify if the incoming data is in the expected proxy file
        //  format and if so skip the work to repackage. At present it is very unlikely that any senders will be
        //  providing data in the format perfectly but at the very least proxies that send data to one another ought to
        //  be meeting the standard.

        final long receivedBytes;
        Path receivingDir = null;
        try {
            final List<ZipEntryGroup.Entry> dataEntries = new ArrayList<>();
            receivingDir = receivingDirProvider.get();
            final Path zipFilePath = receivingDir.resolve("proxy.zip");
            final Map<String, ZipEntryGroup> groups = new HashMap<>();

            // Receive data.
            try (final ByteCountInputStream byteCountInputStream = new ByteCountInputStream(inputStreamSupplier.get())) {
                try (final ZipInputStream zipInputStream = new ZipInputStream(byteCountInputStream)) {
                    // Get a buffer to help us transfer data.
                    final byte[] buffer = LocalByteBuffer.get();

                    // Write the incoming zip data to temp and record info about the entries as we go.
                    try (final ZipOutputStream zipOutputStream =
                            new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(zipFilePath)))) {

                        ZipEntry entry = zipInputStream.getNextEntry();
                        while (entry != null) {
                            if (entry.isDirectory()) {
                                zipOutputStream.putNextEntry(entry);

                            } else {
                                final FileName fileName = FileName.parse(entry.getName());
                                final String baseName = fileName.getBaseName();
                                final StroomZipFileType stroomZipFileType =
                                        StroomZipFileType.fromExtension(fileName.getExtension());

                                if (StroomZipFileType.META.equals(stroomZipFileType)) {
                                    // Read the meta.
                                    final AttributeMap entryAttributeMap = AttributeMapUtil.cloneAllowable(attributeMap);
                                    AttributeMapUtil.read(zipInputStream, entryAttributeMap);

                                    final String feedName = entryAttributeMap.get(StandardHeaderArguments.FEED);
                                    final String typeName = entryAttributeMap.get(StandardHeaderArguments.TYPE);

                                    final byte[] bytes = AttributeMapUtil.toByteArray(entryAttributeMap);
                                    zipOutputStream.putNextEntry(entry);
                                    transfer(new ByteArrayInputStream(bytes), zipOutputStream, buffer);
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

                                    zipOutputStream.putNextEntry(entry);
                                    final long size = transfer(zipInputStream, zipOutputStream, buffer);
                                    zipEntryGroup.setContextEntry(new ZipEntryGroup.Entry(entry.getName(), size));

                                } else if (StroomZipFileType.MANIFEST.equals(stroomZipFileType)) {
                                    final ZipEntryGroup zipEntryGroup = groups.computeIfAbsent(baseName, k ->
                                            new ZipEntryGroup(defaultFeedName, defaultTypeName));
                                    if (zipEntryGroup.getManifestEntry() != null) {
                                        throw new RuntimeException("Duplicate manifest found: " + entry.getName());
                                    }

                                    zipOutputStream.putNextEntry(entry);
                                    final long size = transfer(zipInputStream, zipOutputStream, buffer);
                                    zipEntryGroup.setManifestEntry(new ZipEntryGroup.Entry(entry.getName(), size));

                                } else {
                                    zipOutputStream.putNextEntry(entry);
                                    final long size = transfer(zipInputStream, zipOutputStream, buffer);
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

            // Check all the feeds are ok.
            final List<FeedKey> includeList = new ArrayList<>();
            for (final FeedKey feedKey : feedGroups.keySet()) {
                final AttributeMap entryAttributeMap = AttributeMapUtil.cloneAllowable(attributeMap);
                AttributeMapUtil.addFeedAndType(entryAttributeMap, feedKey.feed(), feedKey.type());
                // Note this call will throw an exception if any of the feeds are not allowed and will result in the
                // whole post being rejected. This is what we want to happen.
                if (attributeMapFilter.filter(entryAttributeMap)) {
                    includeList.add(feedKey);
                }
            }

            // Now make new zip files for each of the feeds we want to include.
            final Path correctedData = receivingDirProvider.get();
            final List<Path> groupDirs = new ArrayList<>();
            long id = 1;
            try (final ZipFile zipFile = new ZipFile(zipFilePath.toFile())) {
                for (final FeedKey feedKey : includeList) {
                    final String name = NumericFileNameUtil.create(id++);
                    final Path groupDir = correctedData.resolve(name);
                    final FileGroup fileGroup = new FileGroup(groupDir);
                    groupDirs.add(groupDir);

                    final List<ZipEntryGroup> zipEntryGroups = feedGroups.get(feedKey);
                    writeZip(zipFile, zipEntryGroups, feedKey, attributeMap, fileGroup);
                }
            }

            // Now atomically move the receiving data to the received data location as we have definitely received
            // successfully at this point.
            final Path receivedDir = receivedDirProvider.get();
            Files.move(correctedData, receivedDir, StandardCopyOption.ATOMIC_MOVE);

            // Finally move each group dir to the file store or forward if there is a single destination.
            for (final Path groupDir : groupDirs) {
                destinationProvider.get().add(groupDir);
            }

            // We ought to be able to delete the receivedDir now as it ought to be empty.
            Files.delete(receivedDir);

        } catch (final IOException e) {
            throw StroomStreamException.create(e, attributeMap);
        } finally {
            // Delete the temporary receiving data dir.
            deleteDir(receivingDir);
        }

        final Duration duration = Duration.between(startTime, Instant.now());
        logStream.log(
                RECEIVE_LOG,
                attributeMap,
                "RECEIVE",
                requestUri,
                HttpStatus.SC_OK,
                receivedBytes,
                duration.toMillis());
    }

    private void deleteDir(final Path path) {
        if (path != null) {
            if (!FileUtil.deleteDir(path)) {
                LOGGER.error("Failed to delete dir " + FileUtil.getCanonicalPath(path));
            }
        }
    }

    private void writeZip(final ZipFile zip,
                          final List<ZipEntryGroup> zipEntryGroups,
                          final FeedKey feedKey,
                          final AttributeMap attributeMap,
                          final FileGroup fileGroup) throws IOException {
        try {
            // Get a buffer to help us transfer data.
            final byte[] buffer = LocalByteBuffer.get();

            // Write zip.
            int count = 1;
            try (final ZipOutputStream zipOutputStream =
                    new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(fileGroup.getZip())))) {
                for (final ZipEntryGroup zipEntryGroup : zipEntryGroups) {
                    final String baseNameOut = NumericFileNameUtil.create(count);
                    addEntry(zip, zipOutputStream, zipEntryGroup.getManifestEntry(), baseNameOut,
                            StroomZipFileType.MANIFEST, buffer);
                    addEntry(zip, zipOutputStream, zipEntryGroup.getMetaEntry(), baseNameOut,
                            StroomZipFileType.META, buffer);
                    addEntry(zip, zipOutputStream, zipEntryGroup.getContextEntry(), baseNameOut,
                            StroomZipFileType.CONTEXT, buffer);
                    addEntry(zip, zipOutputStream, zipEntryGroup.getDataEntry(), baseNameOut,
                            StroomZipFileType.DATA, buffer);

                    count++;
                }
            }

            // Write zip entries.
            ZipEntryGroupUtil.write(fileGroup.getEntries(), zipEntryGroups);

            // Write meta.
            AttributeMapUtil.addFeedAndType(attributeMap, feedKey.feed(), feedKey.type());
            try (final Writer writer = Files.newBufferedWriter(fileGroup.getMeta())) {
                AttributeMapUtil.write(attributeMap, writer);
            }

        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }
    }

    private void addEntry(final ZipFile zip,
                          final ZipOutputStream zipOutputStream,
                          final ZipEntryGroup.Entry entry,
                          final String baseNameOut,
                          final StroomZipFileType stroomZipFileType,
                          final byte[] buffer) throws IOException {
        if (entry != null) {
            final ZipEntry zipEntry = zip.getEntry(entry.getName());
            final InputStream inputStream = zip.getInputStream(zipEntry);

            final String outEntryName = baseNameOut + stroomZipFileType.getDotExtension();
            zipOutputStream.putNextEntry(new ZipEntry(outEntryName));

            transfer(inputStream, zipOutputStream, buffer);
        }
    }

    private long transfer(final InputStream in, final OutputStream out, final byte[] buffer) {
        return StreamUtil
                .streamToStream(in,
                        out,
                        buffer,
                        new ProgressHandler("Receiving data"));
    }
}
