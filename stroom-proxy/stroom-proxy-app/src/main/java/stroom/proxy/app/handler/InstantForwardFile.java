package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.repo.LogStream;
import stroom.receive.common.AttributeMapFilter;
import stroom.receive.common.ProgressHandler;
import stroom.util.io.ByteCountInputStream;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.apache.http.HttpStatus;
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
import javax.inject.Inject;

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
                              final TempDirProvider tempDirProvider,
                              final DropReceiver dropReceiver,
                              final ForwardFileDestinationFactory forwardFileDestinationFactory,
                              final LogStream logStream) {
        this.attributeMapFilterFactory = attributeMapFilterFactory;
        this.dropReceiver = dropReceiver;
        this.forwardFileDestinationFactory = forwardFileDestinationFactory;
        this.logStream = logStream;

        // Create a direct forwarding file receiver.
        // Make receiving zip dir.
        final Path receivingDir = tempDirProvider.get().resolve("proxy_receiving_temp");
        DirUtil.ensureDirExists(receivingDir);

        // This is a temporary location and can be cleaned completely on startup.
        if (!FileUtil.deleteContents(receivingDir)) {
            LOGGER.error(() -> "Failed to delete contents of " + FileUtil.getCanonicalPath(receivingDir));
        }

        receivingDirProvider = new NumberedDirProvider(receivingDir);
    }

    public ReceiverFactory get(final ForwardFileConfig forwardFileConfig) {
        final DirectForwardFileReceiver directForwardFileReceiver = new DirectForwardFileReceiver(
                receivingDirProvider,
                forwardFileDestinationFactory.create(forwardFileConfig),
                logStream);
        return new DirectForwardFileReceiverFactory(
                attributeMapFilterFactory.create(),
                directForwardFileReceiver,
                dropReceiver);
    }

    private static class DirectForwardFileReceiverFactory implements ReceiverFactory {

        private final AttributeMapFilter attributeMapFilter;
        private final DirectForwardFileReceiver receiver;
        private final DropReceiver dropReceiver;

        public DirectForwardFileReceiverFactory(final AttributeMapFilter attributeMapFilter,
                                                final DirectForwardFileReceiver receiver,
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

    private static class DirectForwardFileReceiver implements Receiver {

        private final NumberedDirProvider receivingDirProvider;
        private final ForwardFileDestination forwardFileDestination;
        private final LogStream logStream;

        public DirectForwardFileReceiver(final NumberedDirProvider receivingDirProvider,
                                         final ForwardFileDestination forwardFileDestination,
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
            try {
                final Path dir = receivingDirProvider.get();

                // Write meta.
                final Path metaFile = dir.resolve("meta.meta");
                try (final OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(metaFile))) {
                    AttributeMapUtil.write(attributeMap, outputStream);
                }

                // Write data.
                final String compression = attributeMap.get(StandardHeaderArguments.COMPRESSION);
                String fileName;
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
                        StreamUtil.streamToStream(
                                byteCountInputStream,
                                outputStream,
                                buffer,
                                new ProgressHandler("Receiving"));
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
                        "RECEIVE",
                        requestUri,
                        HttpStatus.SC_OK,
                        receivedBytes,
                        duration.toMillis());

            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
