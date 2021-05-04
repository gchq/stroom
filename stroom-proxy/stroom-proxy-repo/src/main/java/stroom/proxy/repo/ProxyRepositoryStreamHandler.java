package stroom.proxy.repo;

import stroom.data.zip.StroomZipOutputStream;
import stroom.meta.api.AttributeMap;
import stroom.receive.common.StreamHandler;
import stroom.util.io.StreamUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Consumer;

/**
 * Factory to return back handlers for incoming and outgoing requests.
 */
public class ProxyRepositoryStreamHandler implements StreamHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyRepositoryStreamHandler.class);

    private final StroomZipOutputStream stroomZipOutputStream;
    private final byte[] buffer = new byte[StreamUtil.BUFFER_SIZE];

    private boolean doneOne = false;

    public ProxyRepositoryStreamHandler(final ProxyRepo proxyRepo,
                                        final AttributeMap attributeMap) throws IOException {
        stroomZipOutputStream = proxyRepo.getStroomZipOutputStream(attributeMap);
    }

    @Override
    public long addEntry(final String entry,
                         final InputStream inputStream,
                         final Consumer<Long> progressHandler) throws IOException {
        doneOne = true;
        long bytesWritten;
        try (final OutputStream outputStream = stroomZipOutputStream.addEntry(entry)) {
            bytesWritten = StreamUtil.streamToStream(inputStream, outputStream, buffer, progressHandler);
        }
        return bytesWritten;
    }

    void error() {
        try {
            LOGGER.info("Error writing file {}", stroomZipOutputStream);
            stroomZipOutputStream.closeDelete();
        } catch (final IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    void close() throws IOException {
        if (doneOne) {
            stroomZipOutputStream.close();
        } else {
            LOGGER.info("Removing part written file {}", stroomZipOutputStream);
            stroomZipOutputStream.closeDelete();
        }
    }
}
