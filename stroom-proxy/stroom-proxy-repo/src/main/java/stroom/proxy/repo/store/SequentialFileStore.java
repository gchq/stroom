package stroom.proxy.repo.store;

import stroom.data.zip.CharsetConstants;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.repo.RepoDirProvider;
import stroom.proxy.repo.RepoSources;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.io.FileUtil;
import stroom.util.io.WrappedOutputStream;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(SequentialFileStore.class);

    private final RepoSources repoSources;

    private final Path tempDir;
    private final Path storeDir;

    private final AtomicLong tempId = new AtomicLong();
    private final AtomicLong storeId = new AtomicLong();

    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final AtomicLong addedStoreId = new AtomicLong();

    //    private final SequenceFile sequenceFile;
    private final DirManager dirManager = new DirManager();

    @Inject
    public SequentialFileStore(final RepoDirProvider repoDirProvider,
                               final RepoSources repoSources) {
        this.repoSources = repoSources;
        final Path repoDir = repoDirProvider.get();

        // Create the root directory
        ensureDirExists(repoDir);

        // Create the temp directory.
        tempDir = repoDir.resolve("temp");
        if (ensureDirExists(tempDir)) {
            if (!FileUtil.deleteContents(tempDir)) {
                throw new RuntimeException("Unable to delete contents of: " + FileUtil.getCanonicalPath(tempDir));
            }
        }

        // Create the store directory and initialise the store id.
        storeDir = repoDir.resolve("store");
//        sequenceFile = new SequenceFile(storeDir);
        if (ensureDirExists(storeDir)) {

            storeId.set(getMaxId(storeDir));
            addedStoreId.set(storeId.get());

//            storeId.set(getMaxId(storeDir));
//
//            try {
//                storeId.set(sequenceFile.read());
//                addedStoreId.set(storeId.get());
//            } catch (final IOException e) {
//                throw new UncheckedIOException(e);
//            }
        }
    }

    /**
     * Add sources to the DB.
     */
    public void addSources() {
        try {
            long lastAddedStoreId = repoSources.getMaxFileStoreId();
            long currentStoreId;
            while (true) {
                lock.lockInterruptibly();
                try {
                    currentStoreId = addedStoreId.get();
                    if (currentStoreId == lastAddedStoreId) {
                        condition.await();
                    }
                } finally {
                    lock.unlock();
                }

                for (long i = lastAddedStoreId + 1; i <= currentStoreId; i++) {
                    addSource(i);
                }
                lastAddedStoreId = currentStoreId;
            }
        } catch (final InterruptedException e) {
            UncheckedInterruptedException.resetAndThrow(e);
        }
    }

    private void addSource(final long storeId) {
        final FileSet fileSet = getStoreFileSet(storeId);

        // Read the meta data.
        try (final InputStream inputStream = Files.newInputStream(fileSet.getMeta())) {
            final AttributeMap attributeMap = new AttributeMap();
            AttributeMapUtil.read(inputStream, attributeMap);
            final String feedName = attributeMap.get(StandardHeaderArguments.FEED);
            final String typeName = attributeMap.get(StandardHeaderArguments.TYPE);

            // If we have added a new source to the repo then add a DB record for it.
            repoSources.addSource(storeId, feedName, typeName, attributeMap);

        } catch (final IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    public void deleteSource(final long storeId) throws IOException {
        final FileSet fileSet = getStoreFileSet(storeId);
        fileSet.delete();

        dirManager.deleteDirsUnderLock(fileSet);
    }

    public FileSet getStoreFileSet(final long storeId) {
        return FileSet.get(storeDir, storeId);
    }

    public Entries getEntries(final AttributeMap attributeMap) throws IOException {
        return new SequentialEntries(attributeMap);
    }

    private void afterStore(final long storeId) throws IOException {
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
//                    sequenceFile.write(storeId);
                        addedStoreId.incrementAndGet();
                        condition.signalAll();
                        done = true;
                    }
                } finally {
                    lock.unlock();
                }
            }
        } catch (final InterruptedException e) {
            UncheckedInterruptedException.resetAndThrow(e);
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

//        try {
//            Files.walkFileTree(path,
//                    EnumSet.of(FileVisitOption.FOLLOW_LINKS),
//                    Integer.MAX_VALUE,
//                    new AbstractFileVisitor() {
//                        @Override
//                        public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
//                            try {
//                                if (file.toString().endsWith(ProxyRepoFileNames.ZIP_EXTENSION)) {
//                                    final String idString = getIdPart(file);
//                                    if (idString.length() == 0) {
//                                        LOGGER.warn("File is not a valid repository file " + file);
//                                    } else {
//                                        final long id = Long.parseLong(idString);
//                                        maxId.set(Math.max(maxId.get(), id));
//                                    }
//                                }
//                            } catch (final RuntimeException e) {
//                                LOGGER.error(e.getMessage(), e);
//                            }
//                            return super.visitFile(file, attrs);
//                        }
//                    });
//        } catch (final IOException e) {
//            LOGGER.error(e.getMessage(), e);
//        }
//        return maxId.get();
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

//    private String getIdPart(final Path path) {
//        final String fileName = path.getFileName().toString();
//
//        // Turn the file name into a char array.
//        final char[] chars = fileName.toCharArray();
//
//        // Find the index of the first non digit character.
//        int index = -1;
//        for (int i = 0; i < chars.length && index == -1; i++) {
//            if (!Character.isDigit(chars[i])) {
//                index = i;
//            }
//        }
//
//        // If we found a non digit character at a position greater than 0
//        // but that is a modulus of 3 (id's are of the form 001 or 001001 etc)
//        // then this is a valid repository zip file.
//        if (index > 0 && index % 3 == 0) {
//            return fileName.substring(0, index);
//        }
//
//        return "";
//    }

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
            tempFileSet = FileSet.get(tempDir, currentTempId);
            // Create directories.
            Files.createDirectories(tempFileSet.getDir());

            this.attributeMap = attributeMap;
            final OutputStream rawOutputStream = Files.newOutputStream(this.tempFileSet.getZip());
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
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("addEntry() - " + tempFileSet.getZip() + " - " + name + " - adding");
            }
            zipOutputStream.putNextEntry(new ZipEntry(name));
            return new WrappedOutputStream(zipOutputStream) {
                @Override
                public void close() throws IOException {
                    zipOutputStream.closeEntry();
                    inEntry = false;
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("addEntry() - " + tempFileSet.getZip() + " - " + name + " - closed");
                    }
                }
            };
        }

        @Override
        public void close() throws IOException {
            // Don't try and close more than once.
            if (!closed) {
                closed = true;

                // Write the meta data.
                try (final OutputStream metaOutputStream = Files.newOutputStream(tempFileSet.getMeta())) {
                    AttributeMapUtil.write(attributeMap, metaOutputStream);
                    // ZIP's don't like to be empty !
                    if (entryCount == 0) {
                        closeDelete();
                    } else {
                        zipOutputStream.close();
                    }

                    // Move the new data to the store.
                    final long currentStoreId = storeId.incrementAndGet();
                    final FileSet storeFileSet = FileSet.get(storeDir, currentStoreId);

                    dirManager.createDirsUnderLock(storeFileSet, () -> {
                        try {
                            Files.move(tempFileSet.getZip(),
                                    storeFileSet.getZip(),
                                    StandardCopyOption.ATOMIC_MOVE);
                            Files.move(tempFileSet.getMeta(),
                                    storeFileSet.getMeta(),
                                    StandardCopyOption.ATOMIC_MOVE);
                        } catch (final IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });

                    afterStore(currentStoreId);

                } catch (final IOException | RuntimeException e) {
                    LOGGER.error(e.getMessage(), e);
                    closeDelete();
                    throw e;
                }
            }
        }

        @Override
        public void closeDelete() throws IOException {
            // Don't try and close more than once.
            if (!closed) {
                closed = true;
                // ZIP's don't like to be empty !
                if (entryCount == 0) {
                    final OutputStream os = addEntry("NULL.DAT");
                    os.write("NULL".getBytes(CharsetConstants.DEFAULT_CHARSET));
                    os.close();
                }

                zipOutputStream.close();
                if (tempFileSet.getZip() != null) {
                    try {
                        Files.delete(tempFileSet.getZip());
                    } catch (final RuntimeException e) {
                        throw new IOException("Failed to delete file " + tempFileSet.getZip());
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
