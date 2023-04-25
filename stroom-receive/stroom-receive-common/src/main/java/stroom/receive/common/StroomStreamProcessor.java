/*
 * Copyright 2017 Crown Copyright
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
import stroom.util.date.DateUtil;
import stroom.util.io.ByteCountInputStream;
import stroom.util.io.StreamUtil;
import stroom.util.net.HostNameUtil;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.UUID;
import java.util.function.Consumer;
import javax.servlet.http.HttpServletRequest;

public class StroomStreamProcessor {

    private static final String ZERO_CONTENT = "0";
    private static final Logger LOGGER = LoggerFactory.getLogger(StroomStreamProcessor.class);
    private static volatile String hostName;

    private final AttributeMap globalAttributeMap;
    private final StreamHandler handler;
    private final Consumer<Long> progressHandler;
    private boolean appendReceivedPath = true;

    @SuppressWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2"})
    public StroomStreamProcessor(final AttributeMap attributeMap,
                                 final StreamHandler handler,
                                 final Consumer<Long> progressHandler) {
        this.globalAttributeMap = attributeMap;
        this.handler = handler;
        this.progressHandler = progressHandler;
    }

    public String getHostName() {
        if (hostName == null) {
            StroomStreamProcessor.hostName = HostNameUtil.determineHostName();
        }
        return hostName;
    }

    public static void setHostName(final String hostName) {
        StroomStreamProcessor.hostName = hostName;
    }

    public void setAppendReceivedPath(final boolean appendReceivedPath) {
        this.appendReceivedPath = appendReceivedPath;
    }

    public void processRequestHeader(final HttpServletRequest httpServletRequest) {
        String guid = globalAttributeMap.get(StandardHeaderArguments.GUID);

        // Allocate a GUID if we have not got one.
        if (guid == null) {
            guid = UUID.randomUUID().toString();
            globalAttributeMap.put(StandardHeaderArguments.GUID, guid);

            // Only allocate RemoteXxx details if the GUID has not been
            // allocated.

            // Allocate remote address if not set.
            if (httpServletRequest.getRemoteAddr() != null && !httpServletRequest.getRemoteAddr().isEmpty()) {
                globalAttributeMap.put(StandardHeaderArguments.REMOTE_ADDRESS, httpServletRequest.getRemoteAddr());
            }

            // Save the time the data was received.
            globalAttributeMap.put(StandardHeaderArguments.RECEIVED_TIME, DateUtil.createNormalDateTimeString());

            // Allocate remote address if not set.
            if (httpServletRequest.getRemoteHost() != null && !httpServletRequest.getRemoteHost().isEmpty()) {
                globalAttributeMap.put(StandardHeaderArguments.REMOTE_HOST, httpServletRequest.getRemoteHost());
            }
        }
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
                        baseName + stroomZipFileType.getExtension(),
                        inputStream,
                        progressHandler);
            }
        }
    }

    public void processInputStream(InputStream inputStream, final String prefix) {
        String compression = globalAttributeMap.get(StandardHeaderArguments.COMPRESSION);
        if (compression != null && !compression.isEmpty()) {
            compression = compression.toUpperCase(StreamUtil.DEFAULT_LOCALE);
            if (!StandardHeaderArguments.VALID_COMPRESSION_SET.contains(compression)) {
                throw new StroomStreamException(
                        StroomStatusCode.UNKNOWN_COMPRESSION, globalAttributeMap, compression);
            }
        }

        if (ZERO_CONTENT.equals(globalAttributeMap.get(StandardHeaderArguments.CONTENT_LENGTH))) {
            LOGGER.warn("process() - Skipping Zero Content " + globalAttributeMap);
            return;
        }

        if (StandardHeaderArguments.COMPRESSION_ZIP.equals(compression)) {
            // Handle a zip stream.
            processZipStream(inputStream, prefix);

        } else {
            if (StandardHeaderArguments.COMPRESSION_GZIP.equals(compression)) {
                // Handle a gzip stream.
                processGZipStream(inputStream, prefix);
            } else {
                try {
                    // Handle an uncompressed stream.
                    processStream(inputStream, prefix);
                } catch (final IOException e) {
                    throw StroomStreamException.create(e, globalAttributeMap);
                }
            }
        }
    }

    private void processGZipStream(InputStream inputStream, final String prefix) {
        // We have to wrap our stream reading code in a individual
        // try/catch so we can return to the client an error in the
        // case of a corrupt stream.
        try {
            // Use the APACHE GZIP de-compressor as it handles
            // nested compressed streams
            inputStream = new GzipCompressorInputStream(inputStream, true);
            processStream(inputStream, prefix);

        } catch (final IOException e) {
            throw new StroomStreamException(
                    StroomStatusCode.COMPRESSED_STREAM_INVALID,
                    globalAttributeMap,
                    e.getMessage());
        }
    }

    private void processStream(InputStream inputStream, final String prefix) throws IOException {
        try (final BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream)) {
            // Read an initial buffer full so we can see if there is any data
            bufferedInputStream.mark(1);
            if (bufferedInputStream.read() == -1) {
                LOGGER.warn("process() - Skipping Zero Content Stream" + globalAttributeMap);
            } else {
                bufferedInputStream.reset();

                final long totalRead = handler.addEntry(
                        StroomZipEntry.SINGLE_DATA_ENTRY.getFullName(),
                        bufferedInputStream,
                        progressHandler);

                final AttributeMap entryAttributeMap = AttributeMapUtil.cloneAllowable(globalAttributeMap);
                entryAttributeMap.put(StandardHeaderArguments.STREAM_SIZE, String.valueOf(totalRead));
                sendHeader(StroomZipEntry.SINGLE_META_ENTRY, entryAttributeMap);
            }
        }
    }

    private void processZipStream(final InputStream inputStream, final String prefix) {
        try {
            final ByteCountInputStream byteCountInputStream = new ByteCountInputStream(inputStream);

            final Map<String, AttributeMap> bufferedAttributeMap = new HashMap<>();
            final Map<String, Long> dataStreamSizeMap = new HashMap<>();
            final StroomZipEntries stroomZipEntries = new StroomZipEntries();

            final ZipArchiveInputStream zipArchiveInputStream = new ZipArchiveInputStream(byteCountInputStream);

            ZipArchiveEntry zipEntry;
            while (true) {
                // We have to wrap our stream reading code in a individual try/catch
                // so we can return to the client an error in the case of a corrupt
                // stream.
                try {
                    // TODO See the javadoc for ZipArchiveInputStream as getNextZipEntry
                    // may return an entry that is not in the zip dictionary or it may
                    // return multiple entries with the same name. Our code probably
                    // works because we would not expect the zips to have been mutated which
                    // may cause these cases, however we are on slightly shaky ground grabbing
                    // entries without consulting the zip's dictionary.
                    zipEntry = zipArchiveInputStream.getNextZipEntry();
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
                    final AttributeMap entryAttributeMap = AttributeMapUtil.cloneAllowable(globalAttributeMap);
                    // We have to wrap our stream reading code in a individual
                    // try/catch so we can return to the client an error in the case
                    // of a corrupt stream.
                    try {
                        AttributeMapUtil.read(zipArchiveInputStream, entryAttributeMap);
                    } catch (final IOException ioEx) {
                        throw new StroomStreamException(
                                StroomStatusCode.COMPRESSED_STREAM_INVALID,
                                globalAttributeMap,
                                ioEx.getMessage());
                    }

                    if (appendReceivedPath) {
                        // Here we build up a list of stroom servers that have received
                        // the message

                        // The entry one will be initially set at the boundary Stroom
                        // server
                        final String entryReceivedServer = entryAttributeMap.get(StandardHeaderArguments.RECEIVED_PATH);

                        if (entryReceivedServer != null) {
                            if (!entryReceivedServer.contains(getHostName())) {
                                entryAttributeMap.put(StandardHeaderArguments.RECEIVED_PATH,
                                        entryReceivedServer + "," + getHostName());
                            }
                        } else {
                            entryAttributeMap.put(StandardHeaderArguments.RECEIVED_PATH, getHostName());
                        }
                    }

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
                if (byteCountInputStream.getCount() > 22) {
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
