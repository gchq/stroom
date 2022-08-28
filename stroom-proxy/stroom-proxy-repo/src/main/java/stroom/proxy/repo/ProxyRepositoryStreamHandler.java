package stroom.proxy.repo;

import stroom.meta.api.AttributeMap;
import stroom.proxy.repo.store.Entries;
import stroom.proxy.repo.store.SequentialFileStore;
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

    private final Entries entryOutputStream;
    private final byte[] buffer = new byte[StreamUtil.BUFFER_SIZE];

    private boolean doneOne = false;

    public ProxyRepositoryStreamHandler(final SequentialFileStore sequentialFileStore,
                                        final AttributeMap attributeMap) throws IOException {
        entryOutputStream = sequentialFileStore.getEntries(attributeMap);
    }

    @Override
    public long addEntry(final String entry,
                         final InputStream inputStream,
                         final Consumer<Long> progressHandler) throws IOException {
        doneOne = true;
        long bytesWritten;
        try (final OutputStream outputStream = entryOutputStream.addEntry(entry)) {
            bytesWritten = StreamUtil.streamToStream(inputStream, outputStream, buffer, progressHandler);
        }
        return bytesWritten;
    }

    public void error() {
        try {
            LOGGER.info("Error writing file {}", entryOutputStream);
            entryOutputStream.closeDelete();
        } catch (final IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    public void close() throws IOException {
        if (doneOne) {
            entryOutputStream.close();
        } else {
            LOGGER.info("Removing part written file {}", entryOutputStream);
            entryOutputStream.closeDelete();
        }
    }
}
