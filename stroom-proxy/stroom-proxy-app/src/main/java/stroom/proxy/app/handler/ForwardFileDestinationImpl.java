package stroom.proxy.app.handler;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicLong;

public class ForwardFileDestinationImpl implements ForwardFileDestination {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ForwardFileDestinationImpl.class);
    private final Path storeDir;

    private final AtomicLong writeId = new AtomicLong();

    public ForwardFileDestinationImpl(final Path storeDir) {
        this.storeDir = storeDir;

        // Initialise the store id.
        final long maxId = DirUtil.getMaxDirId(storeDir);
        writeId.set(maxId);
    }

    @Override
    public void add(final Path sourceDir) {
        // Record the sequence id for future use.
        final long commitId = writeId.incrementAndGet();
        final Path targetDir = DirUtil.createPath(storeDir, commitId);
        try {
            move(sourceDir, targetDir);
        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
            throw new UncheckedIOException(e);
        }
    }

    private void move(final Path source, final Path target) throws IOException {
        boolean success = false;
        while (!success) {
            try {
                Files.move(source,
                        target,
                        StandardCopyOption.ATOMIC_MOVE);
                success = true;
            } catch (final NoSuchFileException e) {
                DirUtil.ensureDirExists(target.getParent());
            }
        }
    }
}
