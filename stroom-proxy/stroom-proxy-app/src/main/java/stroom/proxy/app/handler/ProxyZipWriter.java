package stroom.proxy.app.handler;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;

public class ProxyZipWriter extends ZipWriter {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProxyZipWriter.class);

    private final ProxyZipValidator validator = new ProxyZipValidator();

    public ProxyZipWriter(final Path path, final byte[] buffer) throws IOException {
        super(path, buffer);
    }

    public ProxyZipWriter(final OutputStream outputStream, final byte[] buffer) {
        super(outputStream, buffer);
    }

    public ProxyZipWriter(final ZipArchiveOutputStream zipArchiveOutputStream,
                          final byte[] buffer) {
        super(zipArchiveOutputStream, buffer);
    }

    @Override
    void putArchiveEntry(final ZipArchiveEntry zipArchiveEntry) throws IOException {
        super.putArchiveEntry(zipArchiveEntry);
        validator.addEntry(zipArchiveEntry.getName());
    }

    @Override
    public void close() throws IOException {
        super.close();

        // Assert that we have written a valid proxy zip.
        if (!validator.isValid()) {
            LOGGER.error(validator.getErrorMessage());
            throw new RuntimeException(validator.getErrorMessage());
        }
    }
}
