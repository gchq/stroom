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

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.proxy.app.DataDirProvider;
import stroom.proxy.app.handler.ZipEntryGroup.Entry;
import stroom.proxy.repo.LogStream;
import stroom.proxy.repo.LogStream.EventType;
import stroom.receive.common.AttributeMapFilter;
import stroom.receive.common.AttributeMapFilterFactory;
import stroom.receive.common.InputStreamUtils;
import stroom.receive.common.ReceiveDataConfig;
import stroom.receive.common.StroomStreamException;
import stroom.security.api.CommonSecurityContext;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;

@Singleton
public class SimpleReceiver implements Receiver {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SimpleReceiver.class);
    private static final Logger RECEIVE_LOG = LoggerFactory.getLogger("receive");
    private static final String META_FILE_NAME = "0000000001.meta";
    private static final String DATA_FILE_NAME = "0000000001.dat";

    private final ReceiveDataConfig receiveDataConfig;
    private final AttributeMapFilterFactory attributeMapFilterFactory;
    private final NumberedDirProvider receivingDirProvider;
    private final CommonSecurityContext commonSecurityContext;
    private final LogStream logStream;
    private final DropReceiver dropReceiver;
    private Consumer<Path> destination;

    @Inject
    public SimpleReceiver(final AttributeMapFilterFactory attributeMapFilterFactory,
                          final DataDirProvider dataDirProvider,
                          final CommonSecurityContext commonSecurityContext,
                          final LogStream logStream,
                          final DropReceiver dropReceiver,
                          final Provider<ReceiveDataConfig> receiveDataConfigProvider) {
        this.attributeMapFilterFactory = attributeMapFilterFactory;
        this.commonSecurityContext = commonSecurityContext;
        this.logStream = logStream;
        this.dropReceiver = dropReceiver;
        this.receiveDataConfig = receiveDataConfigProvider.get();

        // Make receiving zip dir.
        final Path receivingDir = dataDirProvider.get().resolve(DirNames.RECEIVING_SIMPLE);
        DirUtil.ensureDirExists(receivingDir);

        // This is a temporary location and can be cleaned completely on startup.
        if (!FileUtil.deleteContents(receivingDir)) {
            LOGGER.error(() -> "Failed to delete contents of " + FileUtil.getCanonicalPath(receivingDir));
        }
        receivingDirProvider = new NumberedDirProvider(receivingDir);
    }

    @Override
    public void receive(final Instant startTime,
                        final AttributeMap attributeMap,
                        final String requestUri,
                        final InputStreamSupplier inputStreamSupplier) {
//        commonSecurityContext.asProcessingUser(() -> {
        // Determine if the feed is allowed to receive data or if we should ignore it.
        // Throws an exception if we should reject.
        final AttributeMapFilter attributeMapFilter = attributeMapFilterFactory.create();
        final String receiptId = NullSafe.get(attributeMap, map -> map.get(StandardHeaderArguments.RECEIPT_ID));
        if (attributeMapFilter.filter(attributeMap)) {
            doReceive(startTime, attributeMap, requestUri, inputStreamSupplier, receiptId);
        } else {
            // Drop the data.
            dropReceiver.receive(startTime, attributeMap, requestUri, inputStreamSupplier);
        }
//        });
    }

    private void doReceive(final Instant startTime,
                           final AttributeMap attributeMap,
                           final String requestUri,
                           final InputStreamSupplier inputStreamSupplier,
                           final String receiptId) {
        long bytesRead = 0;
        try (final BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStreamSupplier.get())) {
            // Read an initial buffer full, so we can see if there is any data
            bufferedInputStream.mark(1);
            if (bufferedInputStream.read() == -1) {
                LOGGER.warn("process() - Skipping Zero Content Stream" + attributeMap);
            } else {
                bufferedInputStream.reset();

                final Path receivingDir = receivingDirProvider.get();
                final FileGroup fileGroup = new FileGroup(receivingDir);

                // Get a buffer to help us transfer data.
                final byte[] buffer = LocalByteBuffer.get();

                try (final ProxyZipWriter zipWriter = new ProxyZipWriter(fileGroup.getZip(), buffer)) {
                    // Write .meta in the zip first
                    final AttributeMap entryAttributeMap = AttributeMapUtil.cloneAllowable(attributeMap);
                    final byte[] metaBytes = AttributeMapUtil.toByteArray(entryAttributeMap);
                    zipWriter.writeStream(META_FILE_NAME, new ByteArrayInputStream(metaBytes));

                    // Deal with GZIP compression.
                    final String compression = attributeMap.get(StandardHeaderArguments.COMPRESSION);
                    final InputStream in = StandardHeaderArguments.COMPRESSION_GZIP.equalsIgnoreCase(compression)
                                ? new GzipCompressorInputStream(bufferedInputStream, true)
                            : bufferedInputStream;

                    // Write the .dat file in the zip
                    try (final InputStream boundedInputStream = InputStreamUtils.getBoundedInputStream(
                            in, receiveDataConfig.getMaxRequestSize())) {
                        bytesRead = zipWriter.writeStream(DATA_FILE_NAME, boundedInputStream);
                    }

                    final String feedName = attributeMap.get(StandardHeaderArguments.FEED);
                    final String typeName = attributeMap.get(StandardHeaderArguments.TYPE);
                    // Write the entries for quick reference.
                    final ZipEntryGroup zipEntryGroup = new ZipEntryGroup(
                            feedName,
                            typeName,
                            null,
                            new Entry(META_FILE_NAME, metaBytes.length),
                            null,
                            new Entry(DATA_FILE_NAME, bytesRead));

                    // Write .entries file, so we know what is in the zip
                    try (final Writer entryWriter = Files.newBufferedWriter(fileGroup.getEntries())) {
                        zipEntryGroup.write(entryWriter);
                    }

                    // Write the .meta file
                    AttributeMapUtil.write(entryAttributeMap, fileGroup.getMeta());
                }

                // Now move the temp files to the file store or forward if there is a single destination.
                destination.accept(receivingDir);
            }

            final Duration duration = Duration.between(startTime, Instant.now());
            LOGGER.debug("receive() - Received simple stream, duration: {}, attributeMap: {}",
                    duration, attributeMap);
            logStream.log(
                    RECEIVE_LOG,
                    attributeMap,
                    EventType.RECEIVE,
                    requestUri,
                    StroomStatusCode.OK,
                    receiptId,
                    bytesRead,
                    duration.toMillis());
        } catch (final Exception e) {
            throw StroomStreamException.create(e, attributeMap);
        }
    }

    public void setDestination(final Consumer<Path> destination) {
        this.destination = destination;
    }
}
