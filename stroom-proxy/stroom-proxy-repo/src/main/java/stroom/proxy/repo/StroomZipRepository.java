/*
 * Copyright 2017 Crown Copyright
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

package stroom.proxy.repo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.data.zip.StroomFileNameUtil;
import stroom.data.zip.StroomZipOutputStream;
import stroom.data.zip.StroomZipOutputStreamImpl;
import stroom.meta.api.AttributeMap;
import stroom.util.io.AbstractFileVisitor;
import stroom.util.io.FileUtil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

/**
 * Class that represents a repository on the file system. By default files are
 * created in this repo using the stroom id structure where 1000 files are stored
 * per dir and dir's are created by padding the id to multiplier of 3 and using
 * each 3 part as a dir separator.
 */
public class StroomZipRepository {
    final static String LOCK_EXTENSION = ".lock";
    public final static String ZIP_EXTENSION = ".zip";

    private final static Logger LOGGER = LoggerFactory.getLogger(StroomZipRepository.class);

    private static final String DEFAULT_REPOSITORY_FORMAT = "${pathId}/${id}";
    private static final String ID_VAR = "${id}";
    private static final String EXECUTION_UUID_PARAM = "${" + StroomFileNameUtil.EXECUTION_UUID + "}";

    // 1 hour
    private final static int DEFAULT_LOCK_AGE_MS = 1000 * 60 * 60;

    // A somewhat hacky way of preventing a directory from being deleted before data can be written to it
    // which assumes the data is written within 10s.
    private final static int DEFAULT_CLEAN_DELAY = 1000 * 10; // Ten seconds

    private final AtomicLong fileCount = new AtomicLong(0);
    private final int lockDeleteAgeMs;
    private final int cleanDelayMs;

    private final String repositoryFormat;

    /**
     * Name of the repository while open
     */
    private Path currentDir;

    /**
     * Final name once finished (may be null)
     */
    private Path baseResultantDir;

    private final boolean readOnly;
    private final String executionUuid;

    private final LinkedBlockingDeque<StroomZipRepository> rolledRepositoryQueue;
    private int openStreamCount;
    private boolean rolled;
    private boolean finished;

    public StroomZipRepository(final String dir, final boolean readOnly) {
        this(dir,
                null,
                false,
                DEFAULT_LOCK_AGE_MS,
                DEFAULT_CLEAN_DELAY,
                readOnly,
                null);
    }

//    /**
//     * Open a repository (with or without locking) with the default repository format.
//     */
//    public StroomZipRepository(final String dir, final boolean lock, final int lockDeleteAgeMs) {
//        this(dir, null, lock, lockDeleteAgeMs);
//    }


    // For use in tests
    StroomZipRepository(final String dir,
                        final String repositoryFormat,
                        final boolean lock,
                        final int lockDeleteAgeMs,
                        final int cleanDelayMs,
                        final boolean readOnly) {
        this(dir, repositoryFormat, lock, lockDeleteAgeMs, cleanDelayMs, readOnly, null);
    }

    StroomZipRepository(final String dir,
                        final String repositoryFormat,
                        final boolean lock,
                        final int lockDeleteAgeMs,
                        final boolean readOnly,
                        final LinkedBlockingDeque<StroomZipRepository> rolledRepositoryQueue) {
        this(dir, repositoryFormat, lock, lockDeleteAgeMs, DEFAULT_CLEAN_DELAY, readOnly, rolledRepositoryQueue);
    }

    /**
     * Open a repository (with or without locking).
     */
    StroomZipRepository(final String dir,
                        final String repositoryFormat,
                        final boolean lock,
                        final int lockDeleteAgeMs,
                        final int cleanDelayMs,
                        final boolean readOnly,
                        final LinkedBlockingDeque<StroomZipRepository> rolledRepositoryQueue) {
        this.readOnly = readOnly;
        this.executionUuid = UUID.randomUUID().toString();
        this.rolledRepositoryQueue = rolledRepositoryQueue;

        if (repositoryFormat == null || repositoryFormat.trim().length() == 0) {
            LOGGER.info("Using default repository format: {} in directory {}", DEFAULT_REPOSITORY_FORMAT, dir);
            this.repositoryFormat = DEFAULT_REPOSITORY_FORMAT;

        } else {
            LOGGER.info("Using repository format: {} in directory {}", repositoryFormat, dir);

            // Validate the proxy repository format.
            final int index = repositoryFormat.indexOf(ID_VAR);
            if (index == -1) {
                throw new RuntimeException("The proxy repository format must contain " + ID_VAR);
            }
            if (index > 0) {
                final char c = repositoryFormat.charAt(index - 1);
                if (c != '/' && c != '_') {
                    throw new RuntimeException("The " + ID_VAR + " replacement variable in the proxy repository format must be proceeded by '/' or '_'");
                }
            }
            if (index + ID_VAR.length() < repositoryFormat.length()) {
                final char c = repositoryFormat.charAt(index + ID_VAR.length());
                if (c != '_') {
                    throw new RuntimeException("The ${id} replacement variable in the proxy repository format must be followed by '_'");
                }
            }

            this.repositoryFormat = repositoryFormat;
        }

        this.lockDeleteAgeMs = lockDeleteAgeMs;
        this.cleanDelayMs = cleanDelayMs;
        if (lock) {
            currentDir = Paths.get(dir + LOCK_EXTENSION);
            baseResultantDir = Paths.get(dir);
            if (Files.isDirectory(baseResultantDir)) {
                throw new RuntimeException("Rolled directory already exists " + baseResultantDir);
            }
        } else {
            currentDir = Paths.get(dir);
        }

        // Create the root directory
        if (!Files.isDirectory(currentDir)) {
            try {
                Files.createDirectories(currentDir);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        // We may be an existing repository so check for the last ID.
        if (!readOnly) {
            // If we have a unique repository then there is no need to calculate the current max id as it will be unique
            // for each execution.
            final boolean uniqueRepo = this.repositoryFormat.contains(EXECUTION_UUID_PARAM);
            if (!uniqueRepo) {
                scanRepository((min, max) -> {
                    LOGGER.info("First repository id = " + min);
                    LOGGER.info("Last repository id = " + max);
                    fileCount.set(max);
                });
            }
        }

        LOGGER.debug("() - Opened REPO {} lastId = {}", currentDir, fileCount.get());
    }

    /**
     * Gets the minimum and maximum repository ids
     *
     * @param consumer The consumer for the min and max values.
     */
    void scanRepository(final BiConsumer<Long, Long> consumer) {
        long firstFileId = 0;
        long lastFileId = 0;

        // We may be an existing repository so check for the last ID.
        LOGGER.info("Scanning repository to find existing files");
        final AtomicLong minId = new AtomicLong(Long.MAX_VALUE);
        final AtomicLong maxId = new AtomicLong(Long.MIN_VALUE);

        final Path path = getRootDir();

        try {
            if (path != null && Files.isDirectory(path)) {
                scanDir(path, minId, maxId);
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }

        if (minId.get() < Long.MAX_VALUE) {
            firstFileId = minId.get();
        }
        if (maxId.get() > Long.MIN_VALUE) {
            lastFileId = maxId.get();
        }

        consumer.accept(firstFileId, lastFileId);
    }

    private void scanDir(final Path dir, final AtomicLong minId, final AtomicLong maxId) {
        try {
            Files.walkFileTree(dir, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new AbstractFileVisitor() {
                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
                    try {
                        if (file.toString().endsWith(ZIP_EXTENSION)) {
                            LOGGER.debug("Examining " + file.toString());

                            final String idString = getIdPart(file);
                            if (idString.length() == 0) {
                                LOGGER.warn("File is not a valid repository file " + file.toString());
                            } else {
                                final long id = Long.valueOf(idString);

                                boolean success = false;
                                while (!success) {
                                    final long min = minId.get();
                                    success = id >= min || minId.compareAndSet(min, id);
                                }

                                success = false;
                                while (!success) {
                                    final long max = maxId.get();
                                    success = id <= max || maxId.compareAndSet(max, id);
                                }
                            }
                        }
                    } catch (final RuntimeException e) {
                        LOGGER.error(e.getMessage(), e);
                    }
                    return super.visitFile(file, attrs);
                }
            });
        } catch (final IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    /**
     * @param newCount new higher sequencer (used during testing)
     */
    synchronized void setCount(final long newCount) {
        if (fileCount.get() > newCount) {
            throw new IllegalArgumentException("Can't reduce the size of count");
        }
        fileCount.set(newCount);
    }

    private String getIdPart(final Path path) {
        final String fileName = path.getFileName().toString();

        // Turn the file name into a char array.
        final char[] chars = fileName.toCharArray();

        // Find the index of the first non digit character.
        int index = -1;
        for (int i = 0; i < chars.length && index == -1; i++) {
            if (!Character.isDigit(chars[i])) {
                index = i;
            }
        }

        // If we found a non digit character at a position greater than 0
        // but that is a modulus of 3 (id's are of the form 001 or 001001 etc)
        // then this is a valid repository zip file.
        if (index > 0 && index % 3 == 0) {
            return fileName.substring(0, index);
        }

        return "";
    }

    public Path getRootDir() {
        if (baseResultantDir != null) {
            return baseResultantDir;
        }
        return currentDir;
    }

    StroomZipOutputStream getStroomZipOutputStream() throws IOException {
        return getStroomZipOutputStream(null);
    }

    StroomZipOutputStream getStroomZipOutputStream(final AttributeMap attributeMap)
            throws IOException {
        if (readOnly) {
            throw new RuntimeException("This is a read only repository");
        }

        final String filename = StroomFileNameUtil.constructFilename(executionUuid, fileCount.incrementAndGet(), repositoryFormat,
                attributeMap, ZIP_EXTENSION);
        final Path file = currentDir.resolve(filename);

        // Check that we aren't going to clash with the directories and files made by the zip fragmentation process
        // that is part of proxy aggregation.
        PartsPathUtil.checkPath(filename);

        // Create directories and files in a synchronized way so that the clean() method will not remove empty
        // directories that we are just about to write to.
        return createStroomZipOutputStream(file);
    }

    private synchronized StroomZipOutputStreamImpl createStroomZipOutputStream(final Path file) throws IOException {
        StroomZipOutputStreamImpl outputStream;

        // If this repo has been marked as rolled then make attempt to finish and return null.
        if (rolled) {
            finish();
            return null;
        }

        final Path dir = file.getParent();
        // Ensure parent dir's exist
        Files.createDirectories(dir);

        outputStream = new StroomZipOutputStreamImpl(file) {
            private boolean closed = false;

            @Override
            public void close() throws IOException {
                if (!closed) {
                    closed = true;
                    closeStream();
                    super.close();
                }
            }

            @Override
            public void closeDelete() throws IOException {
                if (!closed) {
                    closed = true;
                    closeStream();
                    super.closeDelete();
                }
            }
        };

        openStreamCount++;
        return outputStream;
    }

    private synchronized void closeStream() {
        openStreamCount--;
        if (rolled) {
            finish();
        }
    }

    synchronized void roll() {
        if (!rolled) {
            rolled = true;
        }
        if (openStreamCount == 0) {
            finish();
        }
    }

    private synchronized void finish() {
        if (rolled && openStreamCount == 0 && !finished) {
            finished = true;
            removeLock();

            if (rolledRepositoryQueue != null) {
                rolledRepositoryQueue.add(this);
            }
        }
    }

    void clean(final boolean deleteRootDirectory) {
        LOGGER.info("clean() " + currentDir);
        clean(currentDir, deleteRootDirectory);
    }

    private void clean(final Path path, final boolean deleteRootDirectory) {
        try {
            if (path != null && Files.isDirectory(path)) {
                final long oldestDirMs = System.currentTimeMillis() - cleanDelayMs;
                final long oldestLockFileMs = System.currentTimeMillis() - lockDeleteAgeMs;

                cleanDir(path, oldestDirMs, oldestLockFileMs, deleteRootDirectory);
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private void cleanDir(final Path dir,
                          final long oldestDirMs,
                          final long oldestLockFileMs,
                          final boolean deleteRootDirectory) {
        try {
            Files.walkFileTree(dir, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new AbstractFileVisitor() {
                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
                    try {
                        if (file.toString().endsWith(".zip.lock")) {
                            final FileTime lastModified = attrs.lastModifiedTime();
                            if (lastModified != null && lastModified.toMillis() < oldestLockFileMs) {
                                try {
                                    Files.delete(file);
                                    LOGGER.info("Removed old lock file due to age " + file.toString());
                                } catch (final IOException e) {
                                    LOGGER.error("Unable to remove old lock file due to age " + file.toString());
                                }
                            }
                        }
                    } catch (final RuntimeException e) {
                        LOGGER.debug(e.getMessage(), e);
                    }
                    return super.visitFile(file, attrs);
                }

                @Override
                public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) {
                    if (getRootDir().equals(dir) && !deleteRootDirectory) {
                        LOGGER.debug("Won't attempt to delete directory {} as it is the root", dir);
                    } else {
                        attemptDirDeletion(dir, oldestDirMs);
                    }
                    return super.postVisitDirectory(dir, exc);
                }
            });
        } catch (final IOException e) {
            LOGGER.debug(e.getMessage(), e);
        }
    }

    private void attemptDirDeletion(final Path dir, final long oldestDirMs) {
        try {
            // Only try and delete directories that are at least 10 seconds old.
            final BasicFileAttributes attr = Files.readAttributes(dir, BasicFileAttributes.class);
            final FileTime creationTime = attr.creationTime();
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("attemptDirDeletion({}, {}) creationTime: {}",
                        FileUtil.getCanonicalPath(dir),
                        Instant.ofEpochMilli(oldestDirMs),
                        creationTime);
            }
            if (creationTime.toMillis() < oldestDirMs) {
                // Synchronize deletion of directories so that the getStroomOutputStream() method has a
                // chance to create dirs and place files inside them before this method cleans them up.
                synchronized (StroomZipRepository.this) {
                    // Have a go at deleting this directory if it is empty and not just about to be written to.
                    deleteDir(dir);
                }
            } else if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("attemptDirDeletion() - Dir too young for deletion: " + FileUtil.getCanonicalPath(dir));
            }
        } catch (final IOException e) {
            LOGGER.debug(e.getMessage(), e);
        }
    }

    private void removeLock() {
        if (baseResultantDir != null) {
            try {
                Files.move(currentDir, baseResultantDir);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
            currentDir = baseResultantDir;
            baseResultantDir = null;
        }
    }

    boolean deleteIfEmpty() {
        if (deleteEmptyDir(currentDir)) {
            LOGGER.debug("deleteIfEmpty() - Removed " + currentDir);

            return baseResultantDir == null || deleteEmptyDir(baseResultantDir);
        }

        return false;
    }

    private boolean deleteEmptyDir(final Path path) {
        boolean success = true;
        try {
            if (Files.isDirectory(path)) {
                Files.walkFileTree(path, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new AbstractFileVisitor() {
                    @Override
                    public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) {
                        try {
                            if (!dir.equals(path)) {
                                if (LOGGER.isDebugEnabled()) {
                                    LOGGER.debug("Attempting to delete dir: " + dir.toString());
                                }
                                Files.delete(dir);
                                if (LOGGER.isDebugEnabled()) {
                                    LOGGER.debug("Deleted dir: " + dir.toString());
                                }
                            }
                        } catch (final RuntimeException | IOException e) {
                            if (LOGGER.isDebugEnabled()) {
                                LOGGER.debug("Failed to delete dir: " + dir.toString());
                            }
                            LOGGER.trace(e.getMessage(), e);
                        }
                        return super.postVisitDirectory(dir, exc);
                    }
                });

                // Remove the directory.
                Files.delete(path);
            }
        } catch (final IOException e) {
            LOGGER.error(e.getMessage(), e);
            success = false;
        }
        if (success) {
            LOGGER.debug("Deleted dir: " + path.toString());
        } else {
            LOGGER.debug("Failed to delete dir: " + path.toString());
        }
        return success;
    }

    private void deleteDir(final Path path) throws IOException {
        try {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("delete() - Attempting to delete: " + path.toString());
            }
            Files.delete(path);
        } catch (final DirectoryNotEmptyException e) {
            LOGGER.trace("delete() - Unable to delete dir as it was not empty: " + path.toString());
        }
    }

    List<Path> listAllZipFiles() {
        final List<Path> list = new ArrayList<>();
        try {
            Files.walkFileTree(getRootDir(), EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new AbstractFileVisitor() {
                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
                    try {
                        if (file.toString().endsWith(StroomZipRepository.ZIP_EXTENSION)) {
                            list.add(file);
                        }
                    } catch (final RuntimeException e) {
                        LOGGER.error(e.getMessage(), e);
                    }
                    return super.visitFile(file, attrs);
                }
            });
        } catch (final IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
        return list;
    }
}
