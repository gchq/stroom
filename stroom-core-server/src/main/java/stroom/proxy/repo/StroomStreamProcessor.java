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

package stroom.proxy.repo;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.data.meta.api.AttributeMap;
import stroom.feed.AttributeMapUtil;
import stroom.feed.StroomHeaderArguments;
import stroom.datafeed.StroomStatusCode;
import stroom.datafeed.StroomStreamException;
import stroom.util.date.DateUtil;
import stroom.util.io.ByteCountInputStream;
import stroom.util.io.CloseableUtil;
import stroom.util.io.InitialByteArrayOutputStream;
import stroom.util.io.InitialByteArrayOutputStream.BufferPos;
import stroom.data.store.StreamProgressMonitor;
import stroom.util.io.StreamUtil;

import javax.servlet.http.HttpServletRequest;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class StroomStreamProcessor {
    private static final String ZERO_CONTENT = "0";
    private static final Logger LOGGER = LoggerFactory.getLogger(StroomStreamProcessor.class);
    private static String hostName;

    private final AttributeMap globalAttributeMap;
    private final List<? extends StroomStreamHandler> stroomStreamHandlerList;
    private final byte[] buffer;
    private StreamProgressMonitor streamProgressMonitor = new StreamProgressMonitor("StroomStreamProcessor ");
    private boolean appendReceivedPath = true;

    @SuppressWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2"})
    public StroomStreamProcessor(final AttributeMap attributeMap, final List<? extends StroomStreamHandler> stroomStreamHandlerList,
                                 final byte[] buffer, final String logPrefix) {
        this.globalAttributeMap = attributeMap;
        this.buffer = buffer;
        this.stroomStreamHandlerList = stroomStreamHandlerList;
    }

    public String getHostName() {
        if (hostName == null) {
            try {
                setHostName(InetAddress.getLocalHost().getHostName());
            } catch (final UnknownHostException e) {
                setHostName("Unknown");
            }
        }
        return hostName;
    }

    public static void setHostName(final String hostName) {
        StroomStreamProcessor.hostName = hostName;
    }

    public void setAppendReceivedPath(final boolean appendReceivedPath) {
        this.appendReceivedPath = appendReceivedPath;
    }

    public void setStreamProgressMonitor(final StreamProgressMonitor streamProgressMonitor) {
        this.streamProgressMonitor = streamProgressMonitor;
    }

    public void processRequestHeader(final HttpServletRequest httpServletRequest) {
        String guid = globalAttributeMap.get(StroomHeaderArguments.GUID);

        // Allocate a GUID if we have not got one.
        if (guid == null) {
            guid = UUID.randomUUID().toString();
            globalAttributeMap.put(StroomHeaderArguments.GUID, guid);

            // Only allocate RemoteXxx details if the GUID has not been
            // allocated.

            // Allocate remote address if not set.
            if (httpServletRequest.getRemoteAddr() != null && !httpServletRequest.getRemoteAddr().isEmpty()) {
                globalAttributeMap.put(StroomHeaderArguments.REMOTE_ADDRESS, httpServletRequest.getRemoteAddr());
            }

            // Save the time the data was received.
            globalAttributeMap.put(StroomHeaderArguments.RECEIVED_TIME, DateUtil.createNormalDateTimeString());

            // Allocate remote address if not set.
            if (httpServletRequest.getRemoteHost() != null && !httpServletRequest.getRemoteHost().isEmpty()) {
                globalAttributeMap.put(StroomHeaderArguments.REMOTE_HOST, httpServletRequest.getRemoteHost());
            }
        }
    }

    /**
     * @param inputStream
     * @param prefix
     */
    public void process(InputStream inputStream, final String prefix) {
        try {
            handleHeader();

            boolean compressed = false;

            String compression = globalAttributeMap.get(StroomHeaderArguments.COMPRESSION);

            if (compression != null && !compression.isEmpty()) {
                compression = compression.toUpperCase(StreamUtil.DEFAULT_LOCALE);
                if (!StroomHeaderArguments.VALID_COMPRESSION_SET.contains(compression)) {
                    throw new StroomStreamException(StroomStatusCode.UNKNOWN_COMPRESSION, compression);
                }
            }

            if (ZERO_CONTENT.equals(globalAttributeMap.get(StroomHeaderArguments.CONTENT_LENGTH))) {
                LOGGER.warn("process() - Skipping Zero Content " + globalAttributeMap);
                return;
            }

            if (StroomHeaderArguments.COMPRESSION_ZIP.equals(compression)) {
                // Handle a zip stream.
                processZipStream(inputStream, prefix);

            } else {
                if (StroomHeaderArguments.COMPRESSION_GZIP.equals(compression)) {
                    // We have to wrap our stream reading code in a individual
                    // try/catch so we can return to the client an error in the
                    // case of a corrupt stream.
                    try {
                        // Use the APACHE GZIP de-compressor as it handles
                        // nested compressed streams
                        inputStream = new GzipCompressorInputStream(inputStream, true);
                        compressed = true;
                    } catch (final IOException ioEx) {
                        throw new StroomStreamException(StroomStatusCode.COMPRESSED_STREAM_INVALID, ioEx.getMessage());
                    }
                }

                handleEntryStart(StroomZipFile.SINGLE_DATA_ENTRY);
                int read = 0;
                long totalRead = 0;

                while (true) {
                    // We have to wrap our stream reading code in a individual
                    // try/catch so we can return to the client an error in the
                    // case of a corrupt stream.
                    try {
                        read = StreamUtil.eagerRead(inputStream, buffer);
                    } catch (final IOException ioEx) {
                        if (compressed == true) {
                            throw new StroomStreamException(StroomStatusCode.COMPRESSED_STREAM_INVALID, ioEx.getMessage());
                        } else {
                            throw ioEx;
                        }
                    }
                    if (read == -1) {
                        break;
                    }
                    streamProgressMonitor.progress(read);
                    handleEntryData(buffer, 0, read);
                    totalRead += read;
                }
                handleEntryEnd();
                final AttributeMap entryAttributeMap = AttributeMapUtil.cloneAllowable(globalAttributeMap);
                entryAttributeMap.put(StroomHeaderArguments.STREAM_SIZE, String.valueOf(totalRead));
                sendHeader(StroomZipFile.SINGLE_META_ENTRY, entryAttributeMap);
            }
        } catch (final IOException zex) {
            StroomStreamException.create(zex);
        } finally {
            CloseableUtil.closeLogAndIgnoreException(inputStream);
        }

    }

    private void processZipStream(final InputStream inputStream, final String prefix) throws IOException {
        final ByteCountInputStream byteCountInputStream = new ByteCountInputStream(inputStream);

        final Map<String, AttributeMap> bufferedAttributeMap = new HashMap<>();
        final Map<String, Long> dataStreamSizeMap = new HashMap<>();
        final List<String> sendDataList = new ArrayList<>();
        final StroomZipNameSet stroomZipNameSet = new StroomZipNameSet(false);

        final ZipArchiveInputStream zipArchiveInputStream = new ZipArchiveInputStream(byteCountInputStream);

        ZipArchiveEntry zipEntry = null;
        while (true) {
            // We have to wrap our stream reading code in a individual try/catch
            // so we can return to the client an error in the case of a corrupt
            // stream.
            try {
                zipEntry = zipArchiveInputStream.getNextZipEntry();
            } catch (final IOException ioEx) {
                throw new StroomStreamException(StroomStatusCode.COMPRESSED_STREAM_INVALID, ioEx.getMessage());
            }

            if (zipEntry == null) {
                // All done
                break;
            }

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("process() - " + zipEntry);
            }

            final String entryName = prefix + zipEntry.getName();
            final StroomZipEntry stroomZipEntry = stroomZipNameSet.add(entryName);

            if (StroomZipFileType.Meta.equals(stroomZipEntry.getStroomZipFileType())) {
                final AttributeMap entryAttributeMap = AttributeMapUtil.cloneAllowable(globalAttributeMap);
                // We have to wrap our stream reading code in a individual
                // try/catch so we can return to the client an error in the case
                // of a corrupt stream.
                try {
                    AttributeMapUtil.read(zipArchiveInputStream, false, entryAttributeMap);
                } catch (final IOException ioEx) {
                    throw new StroomStreamException(StroomStatusCode.COMPRESSED_STREAM_INVALID, ioEx.getMessage());
                }

                if (appendReceivedPath) {
                    // Here we build up a list of stroom servers that have received
                    // the message

                    // The entry one will be initially set at the boundary Stroom
                    // server
                    final String entryReceivedServer = entryAttributeMap.get(StroomHeaderArguments.RECEIVED_PATH);

                    if (entryReceivedServer != null) {
                        if (!entryReceivedServer.contains(getHostName())) {
                            entryAttributeMap.put(StroomHeaderArguments.RECEIVED_PATH,
                                    entryReceivedServer + "," + getHostName());
                        }
                    } else {
                        entryAttributeMap.put(StroomHeaderArguments.RECEIVED_PATH, getHostName());
                    }
                }

                if (entryAttributeMap.containsKey(StroomHeaderArguments.STREAM_SIZE)) {
                    // Header already has stream size so just send it on
                    sendHeader(stroomZipEntry, entryAttributeMap);
                } else {
                    // We need to add the stream size
                    // Send the data file yet ?
                    final String dataFile = stroomZipNameSet.getName(stroomZipEntry.getBaseName(), StroomZipFileType.Data);
                    if (dataFile != null && dataStreamSizeMap.containsKey(dataFile)) {
                        // Yes we can send the header now
                        entryAttributeMap.put(StroomHeaderArguments.STREAM_SIZE,
                                String.valueOf(dataStreamSizeMap.get(dataFile)));
                        sendHeader(stroomZipEntry, entryAttributeMap);
                    } else {
                        // Else we have to buffer it
                        bufferedAttributeMap.put(stroomZipEntry.getBaseName(), entryAttributeMap);
                    }
                }
            } else {
                handleEntryStart(stroomZipEntry);
                long totalRead = 0;
                int read = 0;
                while (true) {
                    // We have to wrap our stream reading code in a individual
                    // try/catch so we can return to the client an error in the
                    // case of a corrupt stream.
                    try {
                        read = StreamUtil.eagerRead(zipArchiveInputStream, buffer);
                    } catch (final IOException ioEx) {
                        throw new StroomStreamException(StroomStatusCode.COMPRESSED_STREAM_INVALID, ioEx.getMessage());
                    }
                    if (read == -1) {
                        break;
                    }
                    streamProgressMonitor.progress(read);
                    handleEntryData(buffer, 0, read);
                    totalRead += read;
                }
                handleEntryEnd();

                if (StroomZipFileType.Data.equals(stroomZipEntry.getStroomZipFileType())) {
                    sendDataList.add(entryName);
                    dataStreamSizeMap.put(entryName, totalRead);
                }

                // Buffered header can now be sent as we have sent the
                // data
                if (stroomZipEntry.getBaseName() != null) {
                    final AttributeMap entryAttributeMap = bufferedAttributeMap.remove(stroomZipEntry.getBaseName());
                    if (entryAttributeMap != null) {
                        entryAttributeMap.put(StroomHeaderArguments.STREAM_SIZE, String.valueOf(totalRead));
                        handleEntryStart(new StroomZipEntry(null, stroomZipEntry.getBaseName(), StroomZipFileType.Meta));
                        final byte[] headerBytes = AttributeMapUtil.toByteArray(entryAttributeMap);
                        handleEntryData(headerBytes, 0, headerBytes.length);
                        handleEntryEnd();
                    }
                }
            }

        }

        if (stroomZipNameSet.getBaseNameSet().isEmpty()) {
            if (byteCountInputStream.getByteCount() > 22) {
                throw new StroomStreamException(StroomStatusCode.COMPRESSED_STREAM_INVALID, "No Zip Entries");
            } else {
                LOGGER.warn("processZipStream() - Zip stream with no entries ! {}", globalAttributeMap);
            }
        }

        // Add missing headers
        for (final String baseName : stroomZipNameSet.getBaseNameList()) {
            final String headerName = stroomZipNameSet.getName(baseName, StroomZipFileType.Meta);
            // Send Generic Header
            if (headerName == null) {
                final String dataFileName = stroomZipNameSet.getName(baseName, StroomZipFileType.Data);
                final AttributeMap entryAttributeMap = AttributeMapUtil.cloneAllowable(globalAttributeMap);
                entryAttributeMap.put(StroomHeaderArguments.STREAM_SIZE,
                        String.valueOf(dataStreamSizeMap.remove(dataFileName)));
                sendHeader(new StroomZipEntry(null, baseName, StroomZipFileType.Meta), entryAttributeMap);
            }
        }
    }

    public void closeHandlers() {
        for (final StroomStreamHandler handler : stroomStreamHandlerList) {
            if (handler instanceof Closeable) {
                CloseableUtil.closeLogAndIgnoreException((Closeable) handler);
            }
        }
    }

    private void sendHeader(final StroomZipEntry stroomZipEntry, final AttributeMap attributeMap) throws IOException {
        handleEntryStart(stroomZipEntry);
        // Try and use the buffer
        InitialByteArrayOutputStream byteArrayOutputStream = null;
        try (final InitialByteArrayOutputStream initialByteArrayOutputStream = new InitialByteArrayOutputStream(buffer)) {
            byteArrayOutputStream = initialByteArrayOutputStream;
            AttributeMapUtil.write(attributeMap, initialByteArrayOutputStream);
        }

        final BufferPos bufferPos = byteArrayOutputStream.getBufferPos();
        handleEntryData(bufferPos.getBuffer(), 0, bufferPos.getBufferPos());
        handleEntryEnd();
    }

    private void handleHeader() throws IOException {
        for (final StroomStreamHandler stroomStreamHandler : stroomStreamHandlerList) {
            if (stroomStreamHandler instanceof StroomHeaderStreamHandler) {
                ((StroomHeaderStreamHandler) stroomStreamHandler).handleHeader(globalAttributeMap);
            }
        }
    }

    private void handleEntryStart(final StroomZipEntry stroomZipEntry) throws IOException {
        for (final StroomStreamHandler stroomStreamHandler : stroomStreamHandlerList) {
            stroomStreamHandler.handleEntryStart(stroomZipEntry);
        }
    }

    private void handleEntryEnd() throws IOException {
        for (final StroomStreamHandler stroomStreamHandler : stroomStreamHandlerList) {
            stroomStreamHandler.handleEntryEnd();
        }
    }

    private void handleEntryData(final byte[] data, final int off, final int len) throws IOException {
        for (final StroomStreamHandler stroomStreamHandler : stroomStreamHandlerList) {
            stroomStreamHandler.handleEntryData(data, off, len);
        }
    }
}
