package stroom.proxy.app.handler;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

public class MockForwardFileDestination implements ForwardFileDestination {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(MockForwardFileDestination.class);

    private final Path storeDir;

    private final AtomicLong writeId = new AtomicLong();
    private volatile CountDownLatch countDownLatch;

    public MockForwardFileDestination() {
        try {
            this.storeDir = Files.createTempDirectory("test");

            // Initialise the store id.
            final long maxId = DirUtil.getMaxDirId(storeDir);
            writeId.set(maxId);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void setCountDownLatch(final CountDownLatch countDownLatch) {
        this.countDownLatch = countDownLatch;
    }

    @Override
    public void add(final Path sourceDir) {
        // Record the sequence id for future use.
        final long commitId = writeId.incrementAndGet();
        final Path targetDir = DirUtil.createPath(storeDir, commitId);
        try {
            move(sourceDir, targetDir);
            if (countDownLatch != null) {
                countDownLatch.countDown();
            }
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

    public Path getStoreDir() {
        return storeDir;
    }
}
