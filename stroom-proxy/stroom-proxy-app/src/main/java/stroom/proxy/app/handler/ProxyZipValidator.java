package stroom.proxy.app.handler;

import stroom.data.zip.StroomZipFileType;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.app.handler.ZipEntryGroup.Entry;
import stroom.proxy.repo.FeedKey;
import stroom.util.io.ByteCountInputStream;
import stroom.util.io.FileName;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ProxyZipValidator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProxyZipValidator.class);

    private final byte[] buffer;

    public ProxyZipValidator(final byte[] buffer) {
        this.buffer = buffer;
    }

    public Result receive(final InputStream inputStream,
                          final String defaultFeedName,
                          final String defaultTypeName,
                          final AttributeMap attributeMap,
                          final Path outputFile) throws IOException {
        // TODO : Worry about memory usage here storing potentially 1000's of data entries and groups.
        final List<Entry> dataEntries = new ArrayList<>();
        final Map<String, ZipEntryGroup> groups = new HashMap<>();
        final long receivedBytes;

        // Receive data.
        try (final ByteCountInputStream byteCountInputStream =
                new ByteCountInputStream(inputStream)) {
            try (final ZipArchiveInputStream zipInputStream = new ZipArchiveInputStream(byteCountInputStream)) {
                // Write the incoming zip data to temp and record info about the entries as we go.
                try (final ZipWriter zipWriter = new ZipWriter(outputFile, buffer)) {

                    ZipArchiveEntry entry = zipInputStream.getNextEntry();
                    while (entry != null) {
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

        return new Result(feedGroups, receivedBytes);
    }

    public record Result(Map<FeedKey, List<ZipEntryGroup>> feedGroups, long receivedBytes) {

    }
}
