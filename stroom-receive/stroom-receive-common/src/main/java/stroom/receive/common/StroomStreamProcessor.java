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

package stroom.receive.common;

import stroom.data.zip.StroomZipEntries;
import stroom.data.zip.StroomZipEntries.StroomZipEntryGroup;
import stroom.data.zip.StroomZipEntry;
import stroom.data.zip.StroomZipFile;
import stroom.data.zip.StroomZipFileType;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.util.exception.ThrowingConsumer;
import stroom.util.io.ByteCountInputStream;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.zip.ZipUtil;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.input.BoundedInputStream;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public class StroomStreamProcessor {

    private static final String ZERO_CONTENT = "0";
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StroomStreamProcessor.class);

    private final AttributeMap globalAttributeMap;
    private final StreamHandler handler;
    private final Consumer<Long> progressHandler;
    private final ReceiveDataConfig receiveDataConfig;

    public StroomStreamProcessor(final AttributeMap attributeMap,
                                 final StreamHandler handler,
                                 final Consumer<Long> progressHandler) {
        this(attributeMap, handler, progressHandler, null);
    }

    @SuppressWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2"})
    public StroomStreamProcessor(final AttributeMap attributeMap,
                                 final StreamHandler handler,
                                 final Consumer<Long> progressHandler,
                                 final ReceiveDataConfig receiveDataConfig) {
        this.globalAttributeMap = attributeMap;
        this.handler = handler;
        this.progressHandler = progressHandler;
        this.receiveDataConfig = receiveDataConfig;
    }

    public void processZipFile(final Path zipFilePath) {
        try (final StroomZipFile stroomZipFile = new StroomZipFile(zipFilePath)) {
            try {
                final List<StroomZipFileType> zipFileTypes = List.of(
                        StroomZipFileType.MANIFEST,
                        StroomZipFileType.META,
                        StroomZipFileType.CONTEXT,
                        StroomZipFileType.DATA);

                final List<String> baseNames = stroomZipFile.getBaseNames();
                for (final String baseName : baseNames) {
                    for (final StroomZipFileType zipFileType : zipFileTypes) {
                        addEntry(stroomZipFile, baseName, zipFileType);
                    }
                }
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }

        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void addEntry(final StroomZipFile stroomZipFile,
                          final String baseName,
                          final StroomZipFileType stroomZipFileType) throws IOException {
        try (final InputStream inputStream = stroomZipFile.getInputStream(baseName, stroomZipFileType)) {
            if (inputStream != null) {
                handler.addEntry(
                        baseName + stroomZipFileType.getDotExtension(),
                        inputStream,
                        progressHandler);
            }
        }
    }

    public void processInputStream(final InputStream inputStream) {
        processInputStream(inputStream, "");
    }

    public void processInputStream(final InputStream inputStream, final String prefix) {

        final String compression = AttributeMapUtil.validateAndNormaliseCompression(
                globalAttributeMap,
                compressionVal -> new StroomStreamException(
                        StroomStatusCode.UNKNOWN_COMPRESSION, globalAttributeMap, compressionVal));

        if (ZERO_CONTENT.equals(globalAttributeMap.get(StandardHeaderArguments.CONTENT_LENGTH))) {
            LOGGER.warn("process() - Skipping Zero Content " + globalAttributeMap);
            return;
        }

        try {
            if (StandardHeaderArguments.COMPRESSION_ZIP.equalsIgnoreCase(compression)) {
                // Handle a zip stream.
                processZipStream(inputStream, prefix);
            } else {
                if (StandardHeaderArguments.COMPRESSION_GZIP.equalsIgnoreCase(compression)) {
                    // Handle a gzip stream.
                    processGZipStream(inputStream);
                } else {
                    // Handle an uncompressed stream.
                    processStream(inputStream);
                }
            }
        } catch (final IOException e) {
            throw StroomStreamException.create(e, globalAttributeMap);
        }

    }

    private void processGZipStream(final InputStream inputStream) {
        // We have to wrap our stream reading code in a individual
        // try/catch so we can return to the client an error in the
        // case of a corrupt stream.
        try {
            // Use the APACHE GZIP de-compressor as it handles
            // nested compressed streams
            processStream(new GzipCompressorInputStream(inputStream, true));

        } catch (final IOException e) {
            throw new StroomStreamException(
                    StroomStatusCode.COMPRESSED_STREAM_INVALID,
                    globalAttributeMap,
                    e.getMessage());
        }
    }

    private void processStream(final InputStream inputStream) throws IOException {
        try (final BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream)) {
            // Read an initial buffer full so we can see if there is any data
            bufferedInputStream.mark(1);
            if (bufferedInputStream.read() == -1) {
                LOGGER.warn("process() - Skipping Zero Content Stream" + globalAttributeMap);
            } else {
                bufferedInputStream.reset();

                try (final BoundedInputStream boundedInputStream = InputStreamUtils.getBoundedInputStream(
                        bufferedInputStream, receiveDataConfig == null ? null :
                                receiveDataConfig.getMaxRequestSize())) {
                    final long totalRead = handler.addEntry(
                            StroomZipEntry.SINGLE_DATA_ENTRY.getFullName(),
                            boundedInputStream,
                            progressHandler);

                    final AttributeMap entryAttributeMap = AttributeMapUtil.cloneAllowable(globalAttributeMap);
                    entryAttributeMap.put(StandardHeaderArguments.STREAM_SIZE, String.valueOf(totalRead));
                    sendHeader(StroomZipEntry.SINGLE_META_ENTRY, entryAttributeMap);
                }
            }
        }
    }

    private void processZipStream(final InputStream inputStream,
                                  final String prefix) throws IOException {
        final BoundedInputStream boundedInputStream = InputStreamUtils.getBoundedInputStream(inputStream,
                receiveDataConfig == null ? null : receiveDataConfig.getMaxRequestSize());

        final Map<String, AttributeMap> bufferedAttributeMap = new HashMap<>();
        final Map<String, Long> dataStreamSizeMap = new HashMap<>();
        final StroomZipEntries stroomZipEntries = new StroomZipEntries();

        try (final ZipArchiveInputStream zipArchiveInputStream = new ZipArchiveInputStream(boundedInputStream)) {
            ZipArchiveEntry zipEntry;
            while (true) {
                // We have to wrap our stream reading code in a individual try/catch
                // so we can return to the client an error in the case of a corrupt
                // stream.
                try {
                    // TODO See the javadoc for ZipArchiveInputStream as getNextZipEntry
                    //  may return an entry that is not in the zip dictionary or it may
                    //  return multiple entries with the same name. Our code probably
                    //  works because we would not expect the zips to have been mutated which
                    //  may cause these cases, however we are on slightly shaky ground grabbing
                    //  entries without consulting the zip's dictionary.
                    //  We could write the stream to a file then read it via ZipFile as we
                    //  do in proxy, but this has the added cost of the extra write to disk.
                    //  If the zip has been sent by a v7.8+ proxy then we are assured that the
                    //  zip stream is clean as proxy creates a zip from a ZipFile on receipt, thus
                    //  omitting any 'deleted' entries.
                    zipEntry = zipArchiveInputStream.getNextEntry();
                } catch (final IOException ioEx) {
                    throw new StroomStreamException(
                            StroomStatusCode.COMPRESSED_STREAM_INVALID, globalAttributeMap, ioEx.getMessage());
                }

                if (zipEntry == null) {
                    // All done
                    break;
                }

                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("process() - " + zipEntry);
                }

                final String entryName = prefix + zipEntry.getName();
                if (zipEntry.isDirectory()) {
                    // No point sending the directory entries over
                    LOGGER.debug("Skipping directory zip entry {}", entryName);
                    continue;
                }
                checkZipEntry(zipEntry);

                final long uncompressedSize = zipEntry.getSize();
                final StroomZipEntry stroomZipEntry = stroomZipEntries.addFile(entryName);

                if (uncompressedSize == 0) {
                    // Ideally we would want to ignore empty entries but because there may be multiple child
                    // streams for the same base name (dat/meta/ctx) we don't really want to ignore the dat if
                    // there are non-empty meta/ctx entries as that will probably cause problems elsewhere in
                    // stroom as we expect to always have a data  child stream. As the entries may be in any order
                    // we can check the dat size first, and as we are streaming we can't inspect the dictionary
                    // to find out. Thus the best we can do is warn.
                    LOGGER.warn("processZipStream() - zip entry {} is empty. {}", entryName, globalAttributeMap);
                }

                if (StroomZipFileType.META.equals(stroomZipEntry.getStroomZipFileType())) {
                    final AttributeMap entryAttributeMap;
                    // We have to wrap our stream reading code in an individual
                    // try/catch, so we can return to the client an error in the case
                    // of a corrupt stream.
                    try {
                        // This will overwrite any entries that have already been set from HTTP headers
                        // or by the receipt code prior to this. E.g. if the .meta in the zip contains ReceivedTime
                        // it will overwrite the value set when this stream was received by this thread.
                        // Thus, some keys need to be set below to ensure we have them.
                        entryAttributeMap = AttributeMapUtil.mergeAttributeMaps(
                                globalAttributeMap,
                                ThrowingConsumer.unchecked(derivedAttributeMap ->
                                        AttributeMapUtil.read(zipArchiveInputStream, derivedAttributeMap)));

                    } catch (final UncheckedIOException ioEx) {
                        throw new StroomStreamException(
                                StroomStatusCode.COMPRESSED_STREAM_INVALID,
                                globalAttributeMap,
                                ioEx.getMessage());
                    }

                    // Here we build up a list of stroom servers that have received
                    // the message

                    // The entry one will be initially set at the boundary Stroom
                    // server
//                    final String hostName = getHostName();
//                    entryAttributeMap.appendItemIf(
//                            StandardHeaderArguments.RECEIVED_PATH,
//                            hostName,
//                            curVal -> !NullSafe.contains(curVal, hostName));

                    // Set RECEIVED_TIME and append to RECEIVED_TIME_HISTORY in the meta
//                    AttributeMapUtil.setAndAppendReceivedTime(entryAttributeMap, receivedTime);

                    if (entryAttributeMap.containsKey(StandardHeaderArguments.STREAM_SIZE)) {
                        // Header already has stream size so just send it on
                        sendHeader(stroomZipEntry, entryAttributeMap);
                    } else {
                        // We need to add the stream size
                        // Send the data file yet ?
                        final Optional<StroomZipEntry> dataFile = stroomZipEntries.getByType(
                                stroomZipEntry.getBaseName(),
                                StroomZipFileType.DATA);
                        if (dataFile.isPresent() && dataStreamSizeMap.containsKey(dataFile.get().getFullName())) {
                            // Yes we can send the header now
                            entryAttributeMap.put(StandardHeaderArguments.STREAM_SIZE,
                                    String.valueOf(dataStreamSizeMap.get(dataFile.get().getFullName())));
                            sendHeader(stroomZipEntry, entryAttributeMap);
                        } else {
                            // Else we have to buffer it
                            bufferedAttributeMap.put(stroomZipEntry.getBaseName(), entryAttributeMap);
                        }
                    }
                } else {
                    long totalRead = 0;

                    try {
                        final ByteCountInputStream byteCountInputStreamUncompressed =
                                new ByteCountInputStream(zipArchiveInputStream);
                        handler.addEntry(
                                stroomZipEntry.getFullName(),
                                byteCountInputStreamUncompressed,
                                progressHandler);
                        totalRead += byteCountInputStreamUncompressed.getCount();
                    } catch (final Exception ioEx) {
                        throw new StroomStreamException(
                                StroomStatusCode.COMPRESSED_STREAM_INVALID,
                                globalAttributeMap,
                                ioEx.getMessage());
                    }
                    if (StroomZipFileType.DATA.equals(stroomZipEntry.getStroomZipFileType())) {
                        dataStreamSizeMap.put(entryName, totalRead);
                    }

                    // Buffered header can now be sent as we have sent the
                    // data
                    if (stroomZipEntry.getBaseName() != null) {
                        final AttributeMap entryAttributeMap = bufferedAttributeMap
                                .remove(stroomZipEntry.getBaseName());
                        if (entryAttributeMap != null) {
                            entryAttributeMap.put(StandardHeaderArguments.STREAM_SIZE, String.valueOf(totalRead));
                            final StroomZipEntry entry = StroomZipEntry.createFromBaseName(
                                    stroomZipEntry.getBaseName(),
                                    StroomZipFileType.META);
                            final byte[] headerBytes = AttributeMapUtil.toByteArray(entryAttributeMap);
                            handler.addEntry(
                                    entry.getFullName(),
                                    new ByteArrayInputStream(headerBytes),
                                    progressHandler);
                        }
                    }
                }
            }

            if (stroomZipEntries.getGroups().isEmpty()) {
                // A zip stream with no entries is always 22 bytes in size.
                if (boundedInputStream.getCount() > 22) {
                    throw new StroomStreamException(
                            StroomStatusCode.COMPRESSED_STREAM_INVALID, globalAttributeMap, "No Zip Entries");
                } else {
                    LOGGER.warn("processZipStream() - Zip stream with no entries! {}", globalAttributeMap);
                }
            }

            // Add missing headers
            for (final StroomZipEntryGroup group : stroomZipEntries.getGroups()) {
                final Optional<StroomZipEntry> optionalMeta = group.getByType(StroomZipFileType.META);

                // Send Generic Header
                if (optionalMeta.isEmpty()) {
                    final AttributeMap entryAttributeMap = AttributeMapUtil.cloneAllowable(globalAttributeMap);

                    final Optional<StroomZipEntry> optionalData = group.getByType(StroomZipFileType.DATA);
                    if (optionalData.isPresent()) {
                        final Long size = dataStreamSizeMap.remove(optionalData.get().getFullName());
                        if (size != null) {
                            entryAttributeMap.put(StandardHeaderArguments.STREAM_SIZE, String.valueOf(size));
                        }
                    }

                    final StroomZipEntry metaEntry =
                            StroomZipEntry.createFromBaseName(group.getBaseName(), StroomZipFileType.META);
                    sendHeader(metaEntry, entryAttributeMap);
                }
            }
        } catch (final IOException e) {
            throw StroomStreamException.create(e, globalAttributeMap);
        }
    }

    private void checkZipEntry(final ZipArchiveEntry zipEntry) {
        final String fileName = zipEntry.getName();
        if (!ZipUtil.isSafeZipPath(Path.of(fileName))) {
            // Only a warning as we do not use the zip entry name when extracting from the zip.
            LOGGER.warn("Zip archive stream contains a path that would extract to outside the " +
                        "target directory '{}'. Stroom will not use this path but this is " +
                        "dangerous behaviour.", fileName);
        }
    }

    private void sendHeader(final StroomZipEntry stroomZipEntry, final AttributeMap attributeMap) throws IOException {
        // Try and use the buffer
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        AttributeMapUtil.write(attributeMap, byteArrayOutputStream);
        handler.addEntry(
                stroomZipEntry.getFullName(),
                new ByteArrayInputStream(byteArrayOutputStream.toByteArray()),
                progressHandler);
    }
}
