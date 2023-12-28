package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.proxy.app.handler.ZipEntryGroup.Entry;
import stroom.proxy.repo.LogStream;
import stroom.receive.common.AttributeMapFilter;
import stroom.receive.common.ProgressHandler;
import stroom.receive.common.StroomStreamException;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public class SimpleReceiver implements Receiver {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SimpleReceiver.class);
    private static final Logger RECEIVE_LOG = LoggerFactory.getLogger("receive");
    private static final String META_FILE_NAME = "0000000001.meta";
    private static final String DATA_FILE_NAME = "0000000001.dat";

    private final AttributeMapFilter attributeMapFilter;
    private final NumberedDirProvider receivingDirProvider;
    private final LogStream logStream;
    private final Provider<DirDest> destinationProvider;
    private final DropReceiver dropReceiver;

    @Inject
    public SimpleReceiver(final AttributeMapFilter attributeMapFilter,
                          final TempDirProvider tempDirProvider,
                          final LogStream logStream,
                          final Provider<DirDest> destinationProvider,
                          final DropReceiver dropReceiver) {
        this.attributeMapFilter = attributeMapFilter;
        this.logStream = logStream;
        this.destinationProvider = destinationProvider;
        this.dropReceiver = dropReceiver;

        // Make receiving zip dir.
        final Path receivingDir = tempDirProvider.get().resolve("01_receiving_simple");
        ensureDirExists(receivingDir);

        // This is a temporary location and can be cleaned completely on startup.
        if (!FileUtil.deleteContents(receivingDir)) {
            LOGGER.error(() -> "Failed to delete contents of " + FileUtil.getCanonicalPath(receivingDir));
        }
        receivingDirProvider = new NumberedDirProvider(receivingDir);
    }

    private void ensureDirExists(final Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (final IOException e) {
            LOGGER.error(() -> "Failed to create " + FileUtil.getCanonicalPath(dir), e);
            throw new UncheckedIOException(e);
        }
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

                    try (final ZipOutputStream zipOutputStream =
                            new ZipOutputStream(
                                    new BufferedOutputStream(Files.newOutputStream(fileGroup.getZip())))) {

                        // Get a buffer to help us transfer data.
                        final byte[] buffer = LocalByteBuffer.get();

                        // Write meta first.
                        final AttributeMap entryAttributeMap = AttributeMapUtil.cloneAllowable(attributeMap);
                        final byte[] metaBytes = AttributeMapUtil.toByteArray(entryAttributeMap);
                        zipOutputStream.putNextEntry(new ZipEntry(META_FILE_NAME));
                        transfer(new ByteArrayInputStream(metaBytes), zipOutputStream, buffer);

                        // Deal with GZIP compression.
                        final InputStream in;
                        final String compression = attributeMap.get(StandardHeaderArguments.COMPRESSION);
                        if (StandardHeaderArguments.COMPRESSION_GZIP.equals(compression)) {
                            in = new GZIPInputStream(bufferedInputStream);
                        } else {
                            in = bufferedInputStream;
                        }

                        // Write the data.
                        zipOutputStream.putNextEntry(new ZipEntry(DATA_FILE_NAME));
                        bytesRead = transfer(in, zipOutputStream, buffer);

                        // Write the entries for quick reference.
                        final ZipEntryGroup zipEntryGroup = new ZipEntryGroup(
                                feedName,
                                typeName,
                                null,
                                new Entry(META_FILE_NAME, metaBytes.length),
                                null,
                                new Entry(DATA_FILE_NAME, bytesRead));
                        ZipEntryGroupUtil.write(fileGroup.getEntries(), Collections.singletonList(zipEntryGroup));

                        // Write the meta.
                        try (final Writer writer = Files.newBufferedWriter(fileGroup.getMeta())) {
                            AttributeMapUtil.write(entryAttributeMap, writer);
                        }
                    }

                    // Now move the temp files to the file store or forward if there is a single destination.
                    destinationProvider.get().add(receivingDir);
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

    private long transfer(final InputStream in, final OutputStream out, final byte[] buffer) {
        return StreamUtil
                .streamToStream(in,
                        out,
                        buffer,
                        new ProgressHandler("Receiving data"));
    }
}
