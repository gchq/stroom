/*
 * Copyright 2026 Crown Copyright
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
import stroom.proxy.repo.LogStream;
import stroom.receive.common.AttributeMapFilterFactory;
import stroom.receive.common.StroomStreamException;
import stroom.util.io.ByteCountInputStream;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

/**
 * The receiver when a forwarder is configured {@code instant}: the body is relayed to that one
 * destination during the receipt, with no store write and no queue, and the sender is answered only
 * when the destination has accepted it. The sender owns the retry, which is the point of the mode.
 * <p>
 * The receipt policy still runs, once per request on the headers; a dropped body is drained and
 * logged as dropped. To an HTTP destination the body is streamed as it arrives. To a file
 * destination it is written first, with its headers beside it, to a directory the destination then
 * takes; a directory the destination refuses is removed here, because nothing else will.
 * </p>
 */
public class InstantForwardReceiver implements Receiver {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(InstantForwardReceiver.class);

    private final AttributeMapFilterFactory attributeMapFilterFactory;
    private final LogStream logStream;
    private final Relay relay;

    private InstantForwardReceiver(final AttributeMapFilterFactory attributeMapFilterFactory,
                                   final LogStream logStream,
                                   final Relay relay) {
        this.attributeMapFilterFactory = Objects.requireNonNull(attributeMapFilterFactory);
        this.logStream = Objects.requireNonNull(logStream);
        this.relay = Objects.requireNonNull(relay);
    }

    public static InstantForwardReceiver toHttp(final HttpSender httpSender,
                                                final AttributeMapFilterFactory attributeMapFilterFactory,
                                                final LogStream logStream) {
        Objects.requireNonNull(httpSender);
        return new InstantForwardReceiver(attributeMapFilterFactory, logStream, (attributeMap, body) -> {
            try (final ByteCountInputStream in = ByteCountInputStream.wrap(body.get())) {
                httpSender.send(attributeMap, in);
                return in.getCount();
            }
        });
    }

    /**
     * @param receivingDir Node-local scratch the file groups are built in before the destination
     *                     takes them. Cleared here, at construction.
     */
    public static InstantForwardReceiver toFile(final Destination destination,
                                                final Path receivingDir,
                                                final AttributeMapFilterFactory attributeMapFilterFactory,
                                                final LogStream logStream) {
        Objects.requireNonNull(destination);
        DirUtil.ensureDirExists(receivingDir);
        if (!FileUtil.deleteContents(receivingDir)) {
            LOGGER.error(() -> "Failed to clear the receiving directory " + FileUtil.getCanonicalPath(receivingDir));
        }
        final NumberedDirProvider dirProvider = new NumberedDirProvider(receivingDir);
        return new InstantForwardReceiver(attributeMapFilterFactory, logStream, (attributeMap, body) ->
                writeAndAdd(destination, dirProvider.get(), attributeMap, body));
    }

    @Override
    public void receive(final Instant startTime,
                        final AttributeMap attributeMap,
                        final String source,
                        final InputStreamSupplier body) {
        try {
            if (attributeMapFilterFactory.create().filter(attributeMap)) {
                ReceiveLog.received(logStream, attributeMap, source, relay.relay(attributeMap, body), startTime);
            } else {
                ReceiveLog.dropped(logStream, attributeMap, source, ReceiveLog.drain(body), startTime);
            }
        } catch (final ForwardException e) {
            // The downstream's answer is the sender's answer: a feed it refuses is refused here with
            // the same code, so the sender does not retry what will never be accepted.
            throw new StroomStreamException(e.getStroomStatusCode(), attributeMap, e.getMessage());
        } catch (final IOException | RuntimeException e) {
            throw StroomStreamException.create(e, attributeMap);
        }
    }

    @Override
    public void receiveZip(final Instant startTime,
                           final AttributeMap attributeMap,
                           final String source,
                           final Path zipFile) {
        final AttributeMap zipAttributes = new AttributeMap(attributeMap);
        zipAttributes.put(StandardHeaderArguments.COMPRESSION, StandardHeaderArguments.COMPRESSION_ZIP);
        receive(startTime, zipAttributes, source, () -> Files.newInputStream(zipFile));
    }

    private static long writeAndAdd(final Destination destination,
                                    final Path dir,
                                    final AttributeMap attributeMap,
                                    final InputStreamSupplier body) throws IOException {
        final long bytes;
        try {
            AttributeMapUtil.write(attributeMap, dir.resolve("meta.meta"));
            final String compression = attributeMap.get(StandardHeaderArguments.COMPRESSION);
            final String dataFileName;
            if (StandardHeaderArguments.COMPRESSION_ZIP.equalsIgnoreCase(compression)) {
                dataFileName = "data.zip";
            } else if (StandardHeaderArguments.COMPRESSION_GZIP.equalsIgnoreCase(compression)) {
                dataFileName = "data.gz";
            } else {
                dataFileName = "data.dat";
            }
            try (final ByteCountInputStream in = ByteCountInputStream.wrap(body.get());
                    final OutputStream out = new BufferedOutputStream(
                            Files.newOutputStream(dir.resolve(dataFileName)))) {
                TransferUtil.transfer(in, out, LocalByteBuffer.get());
                bytes = in.getCount();
            }
        } catch (final IOException | RuntimeException e) {
            // Still ours: nothing has taken the directory.
            removeDir(dir);
            throw e;
        }

        try {
            destination.deliver(dir);
        } catch (final Refused e) {
            // A file destination has no refusals; if one ever arrives the sender is told, as for
            // any other failure.
            removeDir(dir);
            throw new IOException(e.getMessage(), e);
        } catch (final IOException | RuntimeException e) {
            // The destination did not take it, so ownership never transferred and nothing else will
            // remove it. A destination that moved it leaves nothing here to remove.
            removeDir(dir);
            throw e;
        }
        return bytes;
    }

    private static void removeDir(final Path dir) {
        if (!FileUtil.deleteDir(dir)) {
            LOGGER.warn(() -> LogUtil.message(
                    "Failed to delete the receiving directory {} after a failed instant forward. Nothing was "
                    + "forwarded, so this is disk left behind rather than data at risk.",
                    FileUtil.getCanonicalPath(dir)));
        }
    }


    // --------------------------------------------------------------------------------


    @FunctionalInterface
    private interface Relay {

        /**
         * @return The number of bytes relayed.
         */
        long relay(AttributeMap attributeMap, InputStreamSupplier body) throws IOException;
    }
}
