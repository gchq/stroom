package stroom.planb.impl.data;

import stroom.planb.impl.io.StatePaths;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

@Singleton
class SequentialFileStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SequentialFileStore.class);

    private final Path receiveDir;
    private final Path stagingDir;

    private final AtomicLong tempId = new AtomicLong();
    private final AtomicLong storeId = new AtomicLong();

    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final AtomicLong addedStoreId = new AtomicLong();

    @Inject
    SequentialFileStore(final StatePaths statePaths) {

        // Create the root directory
        ensureDirExists(statePaths.getRootDir());

        // Create the receive directory.
        receiveDir = statePaths.getReceiveDir();
        if (ensureDirExists(receiveDir)) {
            if (!FileUtil.deleteContents(receiveDir)) {
                throw new RuntimeException("Unable to delete contents of: " + FileUtil.getCanonicalPath(receiveDir));
            }
        }

        // Create the store directory and initialise the store id.
        stagingDir = statePaths.getStagingDir();
        if (ensureDirExists(stagingDir)) {
            long maxId = getMaxId(stagingDir);
            storeId.set(maxId);
            addedStoreId.set(maxId);
        }
    }

    public void add(final FileDescriptor fileDescriptor,
                    final Path path) throws IOException {
        final String fileHash = FileHashUtil.hash(path);
        if (!Objects.equals(fileHash, fileDescriptor.fileHash())) {
            throw new IOException("File hash is not equal");
        }
        add(path);
    }

    public SequentialFile createTemp() {
        final long currentStoreId = tempId.incrementAndGet();
        return getTempFileSet(currentStoreId);
    }

    /**
     * Add sources to the DB.
     */
    public long awaitNew(final long lastAddedStoreId) {
        long currentStoreId;
        try {
            lock.lockInterruptibly();
            try {
                currentStoreId = addedStoreId.get();
                while (currentStoreId <= lastAddedStoreId) {
                    condition.await();
                    currentStoreId = addedStoreId.get();
                }
            } finally {
                lock.unlock();
            }
        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.create(e);
        }
        return currentStoreId;
    }

    public void delete(final long storeId) throws IOException {
        final SequentialFile fileSet = getStoreFileSet(storeId);
        fileSet.delete();
//        storeDirManager.deleteDirsUnderLock(fileSet);
    }

    public SequentialFile getTempFileSet(final long storeId) {
        return SequentialFile.get(receiveDir, storeId, true);
    }

    public SequentialFile getStoreFileSet(final long storeId) {
        return SequentialFile.get(stagingDir, storeId, true);
    }

    private void add(final Path tempFile) throws IOException {
        // Move the new data to the store.
        final long currentStoreId = storeId.incrementAndGet();
        final SequentialFile storeFileSet = getStoreFileSet(currentStoreId);

        try {
            move(
                    storeFileSet.getRoot(),
                    storeFileSet.getSubDirs(),
                    tempFile,
                    storeFileSet.getZip());
        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }

        // Let consumers know there is new data.
        afterStore(currentStoreId);
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
                subDirs.forEach(SequentialFileStore.this::ensureDirExists);
            }
        }
    }

    private void afterStore(final long storeId) {
        try {
            boolean done = false;
            while (!done) {
                lock.lockInterruptibly();
                try {
                    // Ensure we are adding items in order.
                    if (addedStoreId.get() + 1 != storeId) {
                        condition.await();
                    } else {
                        // Record the sequence id for future use.
                        addedStoreId.incrementAndGet();
                        condition.signalAll();
                        done = true;
                    }
                } finally {
                    lock.unlock();
                }
            }
        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.create(e);
        }
    }

    public long getMaxStoreId() {
        return getMaxId(stagingDir);
    }

    private long getMaxId(final Path path) {
        // First find the depth dir.
        Optional<NumericFile> optional = getMaxFile(path);
        if (optional.isPresent()) {
            final NumericFile depthDir = optional.get();
            NumericFile parent = depthDir;

            // Get the max file.
            for (int i = 0; i <= depthDir.num; i++) {
                optional = getMaxFile(parent.dir);
                if (optional.isPresent()) {
                    parent = optional.get();
                } else {
                    break;
                }
            }

            return parent.num;
        }

        return 0;
    }

    private Optional<NumericFile> getMaxFile(final Path path) {
        final AtomicReference<NumericFile> result = new AtomicReference<>();
        try (final Stream<Path> stream = Files.list(path)) {
            stream.forEach(file -> {
                // Get the dir name.
                final String name = file.getFileName().toString();

                // Strip leading 0's.
                int start = 0;
                for (int i = 0; i < name.length(); i++) {
                    if (name.charAt(i) != '0') {
                        break;
                    }
                    start = i + 1;
                }
                int end = name.indexOf(".");
                final String numericPart;
                if (start == 0 && end == -1) {
                    numericPart = name;
                } else if (end == -1) {
                    numericPart = name.substring(start);
                } else {
                    numericPart = name.substring(start, end);
                }

                // Parse numeric part of dir.
                long num = 0;
                if (numericPart.length() > 0) {
                    num = Long.parseLong(numericPart);
                }

                // If this is the biggest num we have seen then set it and remember the dir.
                final NumericFile current = result.get();
                if (current == null || num > current.num) {
                    result.set(new NumericFile(file, num));
                }
            });
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return Optional.ofNullable(result.get());
    }

    private record NumericFile(Path dir, long num) {

    }

    private boolean ensureDirExists(final Path path) {
        if (Files.isDirectory(path)) {
            return true;
        }

        try {
            Files.createDirectories(path);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }

        return false;
    }
}
