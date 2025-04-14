package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.proxy.repo.CSVFormatter;
import stroom.proxy.repo.LogStream;
import stroom.proxy.repo.LogStream.EventType;
import stroom.receive.common.StroomStreamException;
import stroom.util.io.ByteCountInputStream;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

public class DropReceiver implements Receiver {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DropReceiver.class);
    private static final Logger RECEIVE_LOG = LoggerFactory.getLogger("receive");
    private final LogStream logStream;

    @Inject
    public DropReceiver(final LogStream logStream) {
        this.logStream = logStream;
    }

    @Override
    public void receive(final Instant startTime,
                        final AttributeMap attributeMap,
                        final String requestUri,
                        final InputStreamSupplier inputStreamSupplier) {
        try (final ByteCountInputStream byteCountInputStream = new ByteCountInputStream(inputStreamSupplier.get())) {
            try (final BufferedInputStream bufferedInputStream = new BufferedInputStream(byteCountInputStream)) {
                // Just read the stream in and ignore it
                final byte[] buffer = LocalByteBuffer.get();
                while (bufferedInputStream.read(buffer) >= 0) {
                    // Ignore data.
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace(new String(buffer));
                    }
                }
            }

            LOGGER.warn("\"Dropped\",{}", CSVFormatter.format(attributeMap, true));

            final Duration duration = Duration.between(startTime, Instant.now());
            logStream.log(
                    RECEIVE_LOG,
                    attributeMap,
                    EventType.DROP,
                    requestUri,
                    StroomStatusCode.OK,
                    NullSafe.get(
                            attributeMap,
                            map -> map.get(StandardHeaderArguments.RECEIPT_ID)),
                    byteCountInputStream.getCount(),
                    duration.toMillis());
        } catch (final IOException e) {
            throw StroomStreamException.create(e, attributeMap);
        }
    }
}
