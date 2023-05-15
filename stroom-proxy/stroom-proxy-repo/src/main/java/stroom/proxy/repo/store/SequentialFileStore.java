package stroom.proxy.repo.store;

import stroom.data.zip.CharsetConstants;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.proxy.repo.RepoDirProvider;
import stroom.proxy.repo.queue.QueueMonitor;
import stroom.proxy.repo.queue.QueueMonitors;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.io.FileUtil;
import stroom.util.io.WrappedOutputStream;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class SequentialFileStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SequentialFileStore.class);

    private final Path tempDir;
    private final Path storeDir;

    private final AtomicLong tempId = new AtomicLong();
    private final AtomicLong storeId = new AtomicLong();

    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final AtomicLong addedStoreId = new AtomicLong();
//    private final DirManager storeDirManager = new DirManager();

    private final QueueMonitor queueMonitor;

    @Inject
    SequentialFileStore(final RepoDirProvider repoDirProvider,
                        final QueueMonitors queueMonitors,
                        final FileStores fileStores) {
        this(repoDirProvider, queueMonitors, fileStores, 1, "File store");
    }

    public SequentialFileStore(final RepoDirProvider repoDirProvider) {
        this(repoDirProvider, new QueueMonitors(), new FileStores(), 1, "test");
    }

    public SequentialFileStore(final RepoDirProvider repoDirProvider,
                               final QueueMonitors queueMonitors,
                               final FileStores fileStores,
                               final int order,
                               final String name) {
        this.queueMonitor = queueMonitors.create(order, name);
        final Path repoDir = repoDirProvider.get();

        // Create the root directory
        ensureDirExists(repoDir);

        // Create the temp directory.
        tempDir = repoDir.resolve("temp");
        fileStores.add(order, name + " - temp", tempDir);
        if (ensureDirExists(tempDir)) {
            if (!FileUtil.deleteContents(tempDir)) {
                throw new RuntimeException("Unable to delete contents of: " + FileUtil.getCanonicalPath(tempDir));
            }
        }

        // Create the store directory and initialise the store id.
        storeDir = repoDir.resolve("store");
        fileStores.add(order, name + " - store", storeDir);
        if (ensureDirExists(storeDir)) {
            long maxId = getMaxId(storeDir);
            boolean deletePartialStore = true;

            // Delete any partially moved/stored data.
            while (deletePartialStore && maxId > 0) {
                final FileSet fileSet = getStoreFileSet(maxId);
                // Ensure the file set is complete.
                if (!Files.exists(fileSet.getZip()) || !Files.exists(fileSet.getMeta())) {
                    LOGGER.info(() -> "Found partially stored data, will delete: " + fileSet);
                    try {
                        fileSet.delete();

//                        storeDirManager.deleteDirsUnderLock(fileSet);
                    } catch (final IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    maxId--;

                } else {
                    deletePartialStore = false;
                }
            }

            storeId.set(maxId);
            addedStoreId.set(maxId);
            queueMonitor.setWritePos(maxId);
        }
    }

    /**
     * Add sources to the DB.
     */
    public long awaitNew(final long lastAddedStoreId) {
        queueMonitor.setReadPos(lastAddedStoreId);
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

    public void deleteSource(final long storeId) throws IOException {
        final FileSet fileSet = getStoreFileSet(storeId);
        fileSet.delete();
//        storeDirManager.deleteDirsUnderLock(fileSet);
    }

    public FileSet getStoreFileSet(final long storeId) {
        return FileSet.get(storeDir, storeId, true);
    }

    public FileSet getTempFileSet(final long tempId) {
        return FileSet.get(tempDir, tempId, false);
    }


    public Entries getEntries(final AttributeMap attributeMap) throws IOException {
        return new SequentialEntries(attributeMap);
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
            queueMonitor.setWritePos(addedStoreId.get());
        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.create(e);
        }
    }

    public long getMaxStoreId() {
        return getMaxId(storeDir);
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

    private class SequentialEntries implements Entries {

        private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SequentialEntries.class);

        private final FileSet tempFileSet;
        private final AttributeMap attributeMap;
        private final ZipOutputStream zipOutputStream;
        private boolean inEntry;
        private long entryCount;
        private boolean closed;

        public SequentialEntries(final AttributeMap attributeMap) throws IOException {
            final long currentTempId = tempId.incrementAndGet();

            // Create a temp location to write the data to.
            tempFileSet = getTempFileSet(currentTempId);

            this.attributeMap = attributeMap;

            OutputStream rawOutputStream = null;
            while (rawOutputStream == null) {
                try {
                    rawOutputStream = Files.newOutputStream(this.tempFileSet.getZip(),
                            StandardOpenOption.CREATE_NEW);
                } catch (final NoSuchFileException e) {
                    LOGGER.debug(e::getMessage, e);
                    ensureDirExists(tempDir);
                }
            }

            final OutputStream bufferedOutputStream = new BufferedOutputStream(rawOutputStream);
            zipOutputStream = new ZipOutputStream(bufferedOutputStream);
        }

        @Override
        public OutputStream addEntry(final String name) throws IOException {
            if (inEntry) {
                throw new RuntimeException("Failed to close last entry");
            }
            entryCount++;
            inEntry = true;
            if (Thread.currentThread().isInterrupted()) {
                throw new IOException("Progress Stopped");
            }
            LOGGER.trace(() -> "addEntry() - " + tempFileSet.getZip() + " - " + name + " - adding");
            zipOutputStream.putNextEntry(new ZipEntry(name));
            return new WrappedOutputStream(zipOutputStream) {
                @Override
                public void close() throws IOException {
                    zipOutputStream.closeEntry();
                    inEntry = false;
                    LOGGER.trace(() -> "addEntry() - " + tempFileSet.getZip() + " - " + name + " - closed");
                }
            };
        }

        @Override
        public void close() throws IOException {
            // ZIP's don't like to be empty !
            if (entryCount == 0) {
                closeDelete();

            } else if (!closed) {
                // Don't try and close more than once.
                closed = true;

                try {
                    // Close the zip file.
                    zipOutputStream.close();

                    // Write the meta data.
                    try (final OutputStream metaOutputStream = Files.newOutputStream(tempFileSet.getMeta(),
                            StandardOpenOption.CREATE_NEW)) {
                        AttributeMapUtil.write(attributeMap, metaOutputStream);
                    }

                } catch (final IOException | RuntimeException e) {
                    LOGGER.error(e::getMessage, e);
                    try {
                        tempFileSet.delete();
                    } catch (final IOException e2) {
                        LOGGER.error(() -> "Failed to delete file " + tempFileSet.getZip());
                    }
                    throw e;
                }

                // Move the new data to the store.
                final long currentStoreId = storeId.incrementAndGet();
                final FileSet storeFileSet = getStoreFileSet(currentStoreId);

                try {
                    move(
                            storeFileSet.getRoot(),
                            storeFileSet.getSubDirs(),
                            tempFileSet.getZip(),
                            storeFileSet.getZip());
                    move(
                            storeFileSet.getRoot(),
                            storeFileSet.getSubDirs(),
                            tempFileSet.getMeta(),
                            storeFileSet.getMeta());
                } catch (final IOException e) {
                    LOGGER.error(e::getMessage, e);
                    throw e;
                }

                // Let consumers know there is new data.
                afterStore(currentStoreId);
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
                    subDirs.forEach(SequentialFileStore.this::ensureDirExists);
                }
            }
        }

        @Override
        public void closeDelete() throws IOException {
            // Don't try and close more than once.
            if (!closed) {
                closed = true;

                try {
                    // ZIP's don't like to be empty !
                    if (entryCount == 0) {
                        try (final OutputStream os = addEntry("NULL.DAT")) {
                            os.write("NULL".getBytes(CharsetConstants.DEFAULT_CHARSET));
                        }
                    }
                    zipOutputStream.close();

                } finally {
                    try {
                        tempFileSet.delete();
                    } catch (final IOException e) {
                        LOGGER.error(() -> "Failed to delete file " + tempFileSet.getZip());
                    }
                }
            }
        }

        @Override
        public String toString() {
            return tempFileSet.toString();
        }
    }
}
