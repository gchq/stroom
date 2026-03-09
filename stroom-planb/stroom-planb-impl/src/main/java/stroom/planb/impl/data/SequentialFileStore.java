/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.planb.impl.data;

import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.string.StringIdUtil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class SequentialFileStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SequentialFileStore.class);
    private final Map<Long, CountDownLatch> latches = new ConcurrentHashMap<>();

    public static final String ZIP_EXTENSION = ".zip";

    private final Path path;
    private final AtomicLong storeId = new AtomicLong();

    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final AtomicLong addedStoreId = new AtomicLong(-1);

    public SequentialFileStore(final Path path) {

        // Create the store directory and initialise the store id.
        this.path = path;

        if (Files.isDirectory(path)) {
            final long maxId = getMaxId(path);
            storeId.set(maxId + 1);
            addedStoreId.set(maxId);
        } else {
            ensureDirExists(path);
        }
    }

    public void add(final FileDescriptor fileDescriptor,
                    final Path path,
                    final CountDownLatch countDownLatch) throws IOException {
        final String fileHash = FileHashUtil.hash(path);
        if (!Objects.equals(fileHash, fileDescriptor.fileHash())) {
            throw new IOException("File hash is not equal");
        }

        try {
            try {
                lock.lockInterruptibly();
                try {
                    // Move the new data to the store.
                    final long currentStoreId = storeId.getAndIncrement();
                    final SequentialFile storeFileSet = getStoreIdFile(currentStoreId);
                    if (countDownLatch != null) {
                        latches.put(currentStoreId, countDownLatch);
                    }

                    try {
                        Files.move(path,
                                storeFileSet.getZip(),
                                StandardCopyOption.ATOMIC_MOVE);
                    } catch (final NoSuchFileException e) {
                        ensureDirExists(storeFileSet.getRoot());
                        storeFileSet.getSubDirs().forEach(SequentialFileStore.this::ensureDirExists);
                        Files.move(path,
                                storeFileSet.getZip(),
                                StandardCopyOption.ATOMIC_MOVE);
                    }

                    // Record the sequence id for future use.
                    addedStoreId.set(currentStoreId);
                    condition.signalAll();

                } finally {
                    lock.unlock();
                }
            } catch (final InterruptedException e) {
                throw UncheckedInterruptedException.create(e);
            }

        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }
    }

    public SequentialFile awaitNext(final long storeId) {
        try {
            lock.lockInterruptibly();
            try {
                long currentStoreId = addedStoreId.get();
                while (currentStoreId < storeId) {
                    condition.await();
                    currentStoreId = addedStoreId.get();
                }
            } finally {
                lock.unlock();
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw UncheckedInterruptedException.create(e);
        }
        return getStoreIdFile(storeId);
    }

    private SequentialFile getStoreIdFile(final long storeId) {
        // Convert the id to a padded string.
        final String idString = StringIdUtil.idToString(storeId);
        Path dir = path;
        final List<Path> subDirs = new ArrayList<>();

        // Create sub dirs if nested.
        // Add depth.
        final int depth = (idString.length() / 3) - 1;
        dir = dir.resolve(Integer.toString(depth));
        subDirs.add(dir);

        // Add dirs from parts of id string.
        for (int i = 0; i < idString.length() - 3; i += 3) {
            dir = dir.resolve(idString.substring(i, i + 3));
            subDirs.add(dir);
        }

        final Path zip = dir.resolve(idString + ZIP_EXTENSION);
        return new SequentialFile(path, subDirs, zip, latches.remove(storeId));
    }

    public long getMaxStoreId() {
        return getMaxId(path);
    }

    public long getMinStoreId() {
        return getMinId(path);
    }

    private long getMaxId(final Path path) {
        return getId(path, Comparator.naturalOrder());
    }

    private long getMinId(final Path path) {
        return getId(path, Comparator.<Long>naturalOrder().reversed());
    }

    private long getId(final Path path, final Comparator<Long> comparator) {
        // First find the depth dir.
        Optional<NumericFile> optional = getNumericFile(path, comparator);
        if (optional.isPresent()) {
            final NumericFile depthDir = optional.get();
            NumericFile parent = depthDir;

            // Get the max file.
            for (int i = 0; i <= depthDir.num; i++) {
                optional = getNumericFile(parent.dir, comparator);
                if (optional.isPresent()) {
                    parent = optional.get();
                } else {
                    break;
                }
            }

            return parent.num;
        }

        return -1;
    }

    private Optional<NumericFile> getNumericFile(final Path path,
                                                 final Comparator<Long> comparator) {
        final NumericFileTest numericFileTest = new NumericFileTest(comparator);
        try (final Stream<Path> stream = Files.list(path)) {
            stream.forEach(file -> {
                // Get the dir name.
                final String name = file.getFileName().toString();

                // Parse numeric part of dir.
                final long num = parseNumber(name);

                numericFileTest.accept(new NumericFile(file, num));
            });
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return numericFileTest.get();
    }

    private long parseNumber(final String name) {
        // Strip leading 0's.
        int start = 0;
        for (int i = 0; i < name.length(); i++) {
            if (name.charAt(i) != '0') {
                break;
            }
            start = i + 1;
        }
        final int end = name.indexOf(".");
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
        if (!numericPart.isEmpty()) {
            num = Long.parseLong(numericPart);
        }

        return num;
    }

    private void ensureDirExists(final Path path) {
        try {
            Files.createDirectories(path);
        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
            throw new UncheckedIOException(e);
        }
    }

    public void delete(final SequentialFile sequentialFile) throws IOException {
        final Path zip = sequentialFile.getZip();
        LOGGER.debug(() -> "Deleting: " + FileUtil.getCanonicalPath(zip));
        Files.deleteIfExists(zip);

        // Try to delete directories.
        try {
            lock.lockInterruptibly();
            try {
                boolean success = true;
                for (int i = sequentialFile.getSubDirs().size() - 1; i >= 0 && success; i--) {
                    final Path path = sequentialFile.getSubDirs().get(i);
                    success = Files.deleteIfExists(path);
                }
            } catch (final DirectoryNotEmptyException e) {
                // Expected error.
                LOGGER.trace(e::getMessage, e);
            } finally {
                lock.unlock();
            }
        } catch (final InterruptedException e) {
            throw UncheckedInterruptedException.create(e);
        }
    }

    private record NumericFile(Path dir, long num) {

    }

    private static class NumericFileTest implements Consumer<NumericFile> {

        private final Comparator<Long> comparator;
        private NumericFile current;

        public NumericFileTest(final Comparator<Long> comparator) {
            this.comparator = comparator;
        }

        @Override
        public void accept(final NumericFile numericFile) {
            if (current == null || comparator.compare(numericFile.num, current.num) > 0) {
                current = numericFile;
            }
        }

        public Optional<NumericFile> get() {
            return Optional.ofNullable(current);
        }
    }
}
