package stroom.proxy.app.forwarder;

import stroom.proxy.app.handler.NumericFileNameUtil;
import stroom.proxy.app.handler.SequentialDir;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class ForwardFileDestination implements DirDest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ForwardFileDestination.class);
    private final Path storeDir;

    private final AtomicLong writeId = new AtomicLong();

    private final boolean nested;

    public ForwardFileDestination(final ForwardFileConfig forwardFileConfig,
                                  final PathCreator pathCreator) {
        final Path repoDir = pathCreator.toAppPath(forwardFileConfig.getPath());

        this.nested = true;

        // Create the root directory
        ensureDirExists(repoDir);

        // Create the store directory and initialise the store id.
        storeDir = repoDir;

        final long maxId = NumericFileNameUtil.getMaxId(storeDir);
        writeId.set(maxId);
    }

    @Override
    public void add(final Path dir) throws IOException {

        // Record the sequence id for future use.
        final long commitId = writeId.incrementAndGet();
        try {
            final SequentialDir sequentialDir = SequentialDir.get(storeDir, commitId, nested);
            move(sequentialDir.getRoot(), sequentialDir.getSubDirs(), dir, sequentialDir.getDir());
        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
            throw new UncheckedIOException(e);
        }
    }

    private void move(final Path root, final List<Path> subDirs, final Path source, final Path dest)
            throws IOException {
        boolean success = false;
        while (!success) {
            try {
                Files.move(source,
                        dest,
                        StandardCopyOption.ATOMIC_MOVE);
                success = true;
            } catch (final NoSuchFileException e) {
                ensureDirExists(root);
                subDirs.forEach(this::ensureDirExists);
            }
        }
    }

    private void ensureDirExists(final Path path) {
        if (Files.isDirectory(path)) {
            return;
        }

        try {
            Files.createDirectories(path);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
