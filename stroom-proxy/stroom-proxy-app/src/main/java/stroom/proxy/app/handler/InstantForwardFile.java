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
import stroom.proxy.repo.LogStream;
import stroom.proxy.repo.LogStream.EventType;
import stroom.receive.common.AttributeMapFilter;
import stroom.receive.common.AttributeMapFilterFactory;
import stroom.util.io.ByteCountInputStream;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

public class InstantForwardFile {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(InstantForwardFile.class);
    private static final Logger RECEIVE_LOG = LoggerFactory.getLogger("receive");

    private final AttributeMapFilterFactory attributeMapFilterFactory;
    private final DropReceiver dropReceiver;
    private final NumberedDirProvider receivingDirProvider;
    private final ForwardFileDestinationFactory forwardFileDestinationFactory;
    private final LogStream logStream;

    @Inject
    public InstantForwardFile(final AttributeMapFilterFactory attributeMapFilterFactory,
                              final DataDirProvider dataDirProvider,
                              final DropReceiver dropReceiver,
                              final ForwardFileDestinationFactory forwardFileDestinationFactory,
                              final LogStream logStream) {
        this.attributeMapFilterFactory = attributeMapFilterFactory;
        this.dropReceiver = dropReceiver;
        this.forwardFileDestinationFactory = forwardFileDestinationFactory;
        this.logStream = logStream;

        // Create a direct forwarding file receiver.
        // Make receiving zip dir.
        final Path receivingDir = dataDirProvider.get().resolve("proxy_receiving_temp");
        DirUtil.ensureDirExists(receivingDir);

        // This is a temporary location and can be cleaned completely on startup.
        LOGGER.info("Deleting contents of {}", receivingDir);
        if (!FileUtil.deleteContents(receivingDir)) {
            LOGGER.error(() -> "Failed to delete contents of " + FileUtil.getCanonicalPath(receivingDir));
        }

        receivingDirProvider = new NumberedDirProvider(receivingDir);
    }

    public ReceiverFactory get(final ForwardFileConfig forwardFileConfig) {
        LOGGER.info("Creating instant file forward destination to {}",
                forwardFileConfig.getPath());
        final InstantForwardFileReceiver instantForwardFileReceiver = new InstantForwardFileReceiver(
                receivingDirProvider,
                forwardFileDestinationFactory.create(forwardFileConfig),
                logStream);
        return new InstantForwardFileReceiverFactory(
                attributeMapFilterFactory.create(),
                instantForwardFileReceiver,
                dropReceiver);
    }


    // --------------------------------------------------------------------------------


    private static class InstantForwardFileReceiverFactory implements ReceiverFactory {

        private final AttributeMapFilter attributeMapFilter;
        private final InstantForwardFileReceiver receiver;
        private final DropReceiver dropReceiver;

        public InstantForwardFileReceiverFactory(final AttributeMapFilter attributeMapFilter,
                                                 final InstantForwardFileReceiver receiver,
                                                 final DropReceiver dropReceiver) {
            this.attributeMapFilter = attributeMapFilter;
            this.receiver = receiver;
            this.dropReceiver = dropReceiver;
        }

        @Override
        public Receiver get(final AttributeMap attributeMap) {
            if (attributeMapFilter.filter(attributeMap)) {
                return receiver;
            } else {
                return dropReceiver;
            }
        }
    }


    // --------------------------------------------------------------------------------


    private static class InstantForwardFileReceiver implements Receiver {

        private final NumberedDirProvider receivingDirProvider;
        private final ForwardDestination forwardFileDestination;
        private final LogStream logStream;

        public InstantForwardFileReceiver(final NumberedDirProvider receivingDirProvider,
                                          final ForwardDestination forwardFileDestination,
                                          final LogStream logStream) {
            this.receivingDirProvider = receivingDirProvider;
            this.forwardFileDestination = forwardFileDestination;
            this.logStream = logStream;
        }

        @Override
        public void receive(final Instant startTime,
                            final AttributeMap attributeMap,
                            final String requestUri,
                            final InputStreamSupplier inputStreamSupplier) {
            final String receiptId = NullSafe.get(attributeMap, map -> map.get(StandardHeaderArguments.RECEIPT_ID));
            try {
                final Path dir = receivingDirProvider.get();

                // Write meta.
                final Path metaFile = dir.resolve("meta.meta");
                try (final OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(metaFile))) {
                    AttributeMapUtil.write(attributeMap, outputStream);
                }

                // Write data.
                final String compression = attributeMap.get(StandardHeaderArguments.COMPRESSION);
                final String fileName;
                if (StandardHeaderArguments.COMPRESSION_ZIP.equalsIgnoreCase(compression)) {
                    fileName = "data.zip";
                } else if (StandardHeaderArguments.COMPRESSION_GZIP.equalsIgnoreCase(compression)) {
                    fileName = "data.gz";
                } else {
                    fileName = "data.dat";
                }
                final Path dataFile = dir.resolve(fileName);

                final byte[] buffer = LocalByteBuffer.get();
                final long receivedBytes;
                try (final ByteCountInputStream byteCountInputStream =
                        new ByteCountInputStream(inputStreamSupplier.get())) {
                    try (final OutputStream outputStream =
                            new BufferedOutputStream(Files.newOutputStream(dataFile))) {
                        TransferUtil.transfer(byteCountInputStream, outputStream, buffer);
                    }

                    // Find out how much data we received.
                    receivedBytes = byteCountInputStream.getCount();
                }

                // Now perform atomic move of dir to the file store.
                forwardFileDestination.add(dir);

                final Duration duration = Duration.between(startTime, Instant.now());
                logStream.log(
                        RECEIVE_LOG,
                        attributeMap,
                        EventType.RECEIVE,
                        requestUri,
                        StroomStatusCode.OK,
                        receiptId,
                        receivedBytes,
                        duration.toMillis());
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
