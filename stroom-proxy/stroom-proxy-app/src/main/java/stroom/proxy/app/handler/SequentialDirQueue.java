package stroom.proxy.app.handler;

import stroom.proxy.repo.queue.QueueMonitor;
import stroom.proxy.repo.queue.QueueMonitors;
import stroom.proxy.repo.store.FileStores;
import stroom.util.concurrent.UncheckedInterruptedException;
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
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SequentialDirQueue {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SequentialDirQueue.class);
    private final Path rootDir;

    private final AtomicLong writeId = new AtomicLong();
    private final AtomicLong readId = new AtomicLong();

    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();

    private final QueueMonitor queueMonitor;
    private final boolean nested;

    public SequentialDirQueue(final Path rootDir,
                              final QueueMonitors queueMonitors,
                              final FileStores fileStores,
                              final int order,
                              final String name,
                              final boolean nested) {
        this.queueMonitor = queueMonitors.create(order, name);
        this.nested = nested;

        // Create the root directory
        ensureDirExists(rootDir);

        // Create the store directory and initialise the store id.
        this.rootDir = rootDir;
        fileStores.add(order, name + " - store", this.rootDir);

        final long maxId = NumericFileNameUtil.getMaxId(this.rootDir);
        final long minId = NumericFileNameUtil.getMinId(this.rootDir);

        writeId.set(maxId);
        readId.set(minId);
        queueMonitor.setWritePos(maxId);
        queueMonitor.setReadPos(minId);
    }

    /**
     * Add sources to the DB.
     */
    public SequentialDir next() {
        final long id = readId.incrementAndGet();
        queueMonitor.setReadPos(id);
        try {
            lock.lockInterruptibly();
            try {
                while (id > writeId.get()) {
                    condition.await();
                }
            } finally {
                lock.unlock();
            }
        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.create(e);
        }
        return getDir(id);
    }

    private SequentialDir getDir(final long dirId) {
        return SequentialDir.get(rootDir, dirId, nested);
    }

    public void add(final Path dir) {
        try {
            lock.lockInterruptibly();
            try {
                // Record the sequence id for future use.
                final long commitId = writeId.incrementAndGet();
                try {
                    final SequentialDir sequentialDir = SequentialDir.get(rootDir, commitId, nested);
                    move(sequentialDir.getRoot(), sequentialDir.getSubDirs(), dir, sequentialDir.getDir());
                } catch (final IOException e) {
                    LOGGER.error(e::getMessage, e);
                    throw new UncheckedIOException(e);
                }
                condition.signalAll();
            } finally {
                lock.unlock();
            }
            queueMonitor.setWritePos(writeId.get());
        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.create(e);
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