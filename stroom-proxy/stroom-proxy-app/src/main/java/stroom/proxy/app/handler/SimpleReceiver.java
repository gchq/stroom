package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.proxy.app.DataDirProvider;
import stroom.proxy.app.handler.ZipEntryGroup.Entry;
import stroom.proxy.repo.LogStream;
import stroom.receive.common.AttributeMapFilter;
import stroom.receive.common.StroomStreamException;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.hc.core5.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
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

    private final AttributeMapFilter attributeMapFilter;
    private final NumberedDirProvider receivingDirProvider;
    private final LogStream logStream;
    private final DropReceiver dropReceiver;
    private Consumer<Path> destination;

    @Inject
    public SimpleReceiver(final AttributeMapFilterFactory attributeMapFilterFactory,
                          final DataDirProvider dataDirProvider,
                          final LogStream logStream,
                          final DropReceiver dropReceiver) {
        this.attributeMapFilter = attributeMapFilterFactory.create();
        this.logStream = logStream;
        this.dropReceiver = dropReceiver;

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
        final String feedName = attributeMap.get(StandardHeaderArguments.FEED);
        final String typeName = attributeMap.get(StandardHeaderArguments.TYPE);
        if (feedName.isEmpty()) {
            throw new StroomStreamException(StroomStatusCode.FEED_MUST_BE_SPECIFIED, attributeMap);
        }

        // Determine if the feed is allowed to receive data or if we should ignore it.
        // Throws an exception if we should reject.
        if (attributeMapFilter.filter(attributeMap)) {
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
                        // Write meta first.
                        final AttributeMap entryAttributeMap = AttributeMapUtil.cloneAllowable(attributeMap);
                        final byte[] metaBytes = AttributeMapUtil.toByteArray(entryAttributeMap);
                        zipWriter.writeStream(META_FILE_NAME, new ByteArrayInputStream(metaBytes));

                        // Deal with GZIP compression.
                        final InputStream in;
                        final String compression = attributeMap.get(StandardHeaderArguments.COMPRESSION);
                        if (StandardHeaderArguments.COMPRESSION_GZIP.equals(compression)) {
                            in = new GzipCompressorInputStream(bufferedInputStream);
                        } else {
                            in = bufferedInputStream;
                        }

                        // Write the data.
                        zipWriter.writeStream(DATA_FILE_NAME, in);

                        // Write the entries for quick reference.
                        final ZipEntryGroup zipEntryGroup = new ZipEntryGroup(
                                feedName,
                                typeName,
                                null,
                                new Entry(META_FILE_NAME, metaBytes.length),
                                null,
                                new Entry(DATA_FILE_NAME, bytesRead));

                        // Write zip entry.
                        try (final Writer entryWriter = Files.newBufferedWriter(fileGroup.getEntries())) {
                            zipEntryGroup.write(entryWriter);
                        }

                        // Write the meta.
                        AttributeMapUtil.write(entryAttributeMap, fileGroup.getMeta());
                    }

                    // Now move the temp files to the file store or forward if there is a single destination.
                    destination.accept(receivingDir);
                }

                final Duration duration = Duration.between(startTime, Instant.now());
                logStream.log(
                        RECEIVE_LOG,
                        attributeMap,
                        "RECEIVE",
                        requestUri,
                        HttpStatus.SC_OK,
                        bytesRead,
                        duration.toMillis());
            } catch (final IOException e) {
                throw StroomStreamException.create(e, attributeMap);
            }

        } else {

            // Drop the data.
            dropReceiver.receive(startTime, attributeMap, requestUri, inputStreamSupplier);
        }
    }

    public void setDestination(final Consumer<Path> destination) {
        this.destination = destination;
    }
}