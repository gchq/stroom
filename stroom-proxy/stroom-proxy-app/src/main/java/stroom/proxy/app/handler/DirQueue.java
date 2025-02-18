package stroom.proxy.app.handler;

import stroom.proxy.repo.queue.QueueMonitor;
import stroom.proxy.repo.queue.QueueMonitors;
import stroom.proxy.repo.store.FileStores;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DirQueue {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DirQueue.class);
    private final Path rootDir;

    /**
     * ID last written to
     */
    private long writeId;
    /**
     * ID to read from next
     */
    private long readId;

    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final QueueMonitor queueMonitor;

    DirQueue(final Path rootDir,
             final QueueMonitors queueMonitors,
             final FileStores fileStores,
             final int order,
             final String name) {
        this.rootDir = rootDir;
        this.queueMonitor = queueMonitors.create(order, name);

        // Create the root directory
        DirUtil.ensureDirExists(rootDir);

        // Create the store directory and initialise the store id.
        fileStores.add(order, name + " - store", this.rootDir);

        final long maxId = DirUtil.getMaxDirId(this.rootDir);
        final long minId = DirUtil.getMinDirId(this.rootDir);

        if (minId > maxId) {
            throw new IllegalStateException(LogUtil.message("minId {} is greater than maxId {}", minId, maxId));
        }

        writeId = maxId;
        readId = Math.max(1, minId);
        queueMonitor.setWritePos(maxId);
        queueMonitor.setReadPos(minId);
        LOGGER.info("Initialising queue {} in {} with readId {} and writeId {}",
                name, LogUtil.path(rootDir), readId, writeId);
    }

    /**
     * Get the next dir that is available in the queue as soon as one is available, block until then.
     *
     * @return A dir that is managed by this queue. The dir should be closed once used to ensure parent dirs are
     * deleted if empty.
     */
    public Dir next() {
        Dir dir = null;
        try {
            lock.lockInterruptibly();
            try {
                while (dir == null) {
                    while (readId > writeId) {
                        condition.await();
                    }
                    final long id = readId++;
                    final Path path = DirUtil.createPath(rootDir, id);
                    if (Files.isDirectory(path)) {
                        queueMonitor.setReadPos(id);
                        dir = new Dir(this, path);
                    }
                }
            } finally {
                lock.unlock();
            }
        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.create(e);
        }
        return dir;
    }

    /**
     * Get the next dir that is available in the queue as soon as one is available, block until then or timeout.
     *
     * @return A dir that is managed by this queue. The dir should be closed once used to ensure parent dirs are
     * deleted if empty.
     */
    public Optional<Dir> next(final long time, final TimeUnit unit) {
        Dir dir = null;
        try {
            lock.lockInterruptibly();
            try {
                while (dir == null) {
                    while (readId > writeId) {
                        if (!condition.await(time, unit)) {
                            return Optional.empty();
                        }
                    }
                    final long id = readId++;
                    final Path path = DirUtil.createPath(rootDir, id);
                    if (Files.isDirectory(path)) {
                        queueMonitor.setReadPos(id);
                        dir = new Dir(this, path);
                    }
                }
            } finally {
                lock.unlock();
            }
        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.create(e);
        }
        return Optional.of(dir);
    }

    /**
     * Add a dir to the queue. In the process this will move the source dir to a dir managed by the queue so that the
     * process providing the dir will no longer be responsible for managing the supplied dir.
     *
     * @param sourceDir The source dir to move to the queue.
     */
    public void add(final Path sourceDir) {
        try {
            lock.lockInterruptibly();
            try {
                // Increment the sequence id.
                final long id = ++writeId;
                queueMonitor.setWritePos(id);
                try {
                    final Path targetDir = DirUtil.createPath(rootDir, id);
                    DirUtil.ensureDirExists(targetDir.getParent());
                    Files.move(sourceDir, targetDir, StandardCopyOption.ATOMIC_MOVE);
                } catch (final IOException e) {
                    LOGGER.error(e::getMessage, e);
                    throw new UncheckedIOException(e);
                }
                condition.signalAll();
            } finally {
                lock.unlock();
            }
        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.create(e);
        }
    }

    /**
     * When we have finished with a dir we should be in a position where the dir has been
     * moved so can try to delete the parent directories. We never want to delete the dir
     * itself as it should have been moved and any failure to do so is an error.
     *
     * @param dir The dir to close.
     */
    public void close(final Dir dir) {
        try {
            lock.lockInterruptibly();
            try {
                // Try to delete parent directories.
                try {
                    boolean success = true;
                    Path path = dir.getPath().getParent();
                    while (!path.equals(rootDir) && success) {
                        success = Files.deleteIfExists(path);
                        path = path.getParent();
                    }
                } catch (final DirectoryNotEmptyException e) {
                    // Expected error.
                    LOGGER.trace(e::getMessage, e);
                } catch (final IOException e) {
                    LOGGER.error(e::getMessage, e);
                }
            } finally {
                lock.unlock();
            }
        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.create(e);
        }
    }
}
