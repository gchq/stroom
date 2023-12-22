package stroom.proxy.app.handler;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class Index {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(Index.class);

    private final Path indexFile;
    private final Path tempIndexFile;

    public Index(final Path parent) {
        // Create the index file path.
        indexFile = parent.resolve("index");
        tempIndexFile = parent.resolve("index.tmp");
    }

    public synchronized long readIndex() {
        if (Files.exists(indexFile)) {
            try {
                try (final DataInputStream inputStream = new DataInputStream(Files.newInputStream(indexFile))) {
                    return inputStream.readLong();
                }
            } catch (final IOException e) {
                LOGGER.error(() -> "Failed to get current write id from index file", e);
            }
        }
        return -1;
    }

    public synchronized void writeIndex(final long index) throws IOException {
        try (final DataOutputStream outputStream = new DataOutputStream(Files.newOutputStream(tempIndexFile))) {
            outputStream.writeLong(index);
        }
        Files.move(tempIndexFile, indexFile, StandardCopyOption.ATOMIC_MOVE);
    }
}
