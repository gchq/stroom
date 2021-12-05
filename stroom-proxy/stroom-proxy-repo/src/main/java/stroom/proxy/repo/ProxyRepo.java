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

import stroom.data.zip.StroomFileNameUtil;
import stroom.data.zip.StroomZipOutputStream;
import stroom.data.zip.StroomZipOutputStreamImpl;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.util.io.AbstractFileVisitor;
import stroom.util.io.FileUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Class that represents a repository on the file system. By default files are
 * created in this repo using the stroom id structure where 1000 files are stored
 * per dir and dir's are created by padding the id to multiplier of 3 and using
 * each 3 part as a dir separator.
 */
@Singleton
public class ProxyRepo {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyRepo.class);

    private static final String DEFAULT_REPOSITORY_FORMAT = "${pathId}/${id}";
    private static final String ID_VAR = "${id}";
    private static final String EXECUTION_UUID_PARAM = "${" + StroomFileNameUtil.EXECUTION_UUID + "}";

    private final AtomicLong fileCount = new AtomicLong(0);
    private final long lockDeleteAgeMs;
    private final long cleanDelayMs;

    private final String repositoryFormat;
    private final RepoSources proxyRepoSources;

    /**
     * Name of the repository while open
     */
    private final Path repoDir;
    private final String executionUuid;

    @Inject
    ProxyRepo(final ProxyRepoConfig proxyRepoConfig,
              final RepoSources proxyRepoSources,
              final RepoDirProvider repoDirProvider) {
        this(repoDirProvider,
                proxyRepoConfig.getFormat(),
                proxyRepoSources,
                proxyRepoConfig.getLockDeleteAge().toMillis(),
                proxyRepoConfig.getDirCleanDelay().toMillis());
    }

    /**
     * Open a repository (with or without locking).
     */
    public ProxyRepo(final RepoDirProvider repoDirProvider,
                     final String repositoryFormat,
                     final RepoSources proxyRepoSources,
                     final long lockDeleteAgeMs,
                     final long cleanDelayMs) {
        this.proxyRepoSources = proxyRepoSources;
        this.executionUuid = UUID.randomUUID().toString();
        this.repoDir = repoDirProvider.get();

        if (repositoryFormat == null || repositoryFormat.trim().length() == 0) {
            LOGGER.info("Using default repository format: {} in directory {}", DEFAULT_REPOSITORY_FORMAT, repoDir);
            this.repositoryFormat = DEFAULT_REPOSITORY_FORMAT;

        } else {
            LOGGER.info("Using repository format: {} in directory {}", repositoryFormat, repoDir);

            // Validate the proxy repository format.
            final int index = repositoryFormat.indexOf(ID_VAR);
            if (index == -1) {
                throw new RuntimeException("The proxy repository format must contain " + ID_VAR);
            }
            if (index > 0) {
                final char c = repositoryFormat.charAt(index - 1);
                if (c != '/' && c != '_') {
                    throw new RuntimeException("The " + ID_VAR + " replacement variable in the proxy repository " +
                            "format must be proceeded by '/' or '_'");
                }
            }
            if (index + ID_VAR.length() < repositoryFormat.length()) {
                final char c = repositoryFormat.charAt(index + ID_VAR.length());
                if (c != '_') {
                    throw new RuntimeException(
                            "The ${id} replacement variable in the proxy repository format must be followed by '_'");
                }
            }

            this.repositoryFormat = repositoryFormat;
        }

        this.lockDeleteAgeMs = lockDeleteAgeMs;
        this.cleanDelayMs = cleanDelayMs;

        // Create the root directory
        if (!Files.isDirectory(repoDir)) {
            try {
                Files.createDirectories(repoDir);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }

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

        LOGGER.debug("() - Opened REPO {} lastId = {}", repoDir, fileCount.get());
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

        final Path path = getRepoDir();

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
            Files.walkFileTree(dir,
                    EnumSet.of(FileVisitOption.FOLLOW_LINKS),
                    Integer.MAX_VALUE,
                    new AbstractFileVisitor() {
                        @Override
                        public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
                            try {
                                if (file.toString().endsWith(ProxyRepoFileNames.ZIP_EXTENSION)) {
                                    LOGGER.debug("Examining " + file);

                                    final String idString = getIdPart(file);
                                    if (idString.length() == 0) {
                                        LOGGER.warn("File is not a valid repository file " + file);
                                    } else {
                                        final long id = Long.parseLong(idString);

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

    public Path getRepoDir() {
        return repoDir;
    }

    public synchronized StroomZipOutputStream getStroomZipOutputStream(final AttributeMap attributeMap)
            throws IOException {
        // Create directories and files in a synchronized way so that the clean() method will not remove empty
        // directories that we are just about to write to.
        final String fileName = StroomFileNameUtil.constructFilename(executionUuid,
                fileCount.incrementAndGet(),
                repositoryFormat,
                attributeMap);

        final String zipFileName = ProxyRepoFileNames.getZip(fileName);
        final String metaFileName = ProxyRepoFileNames.getMeta(fileName);

        final Path zipFile = repoDir.resolve(zipFileName);
        final Path metaFile = repoDir.resolve(metaFileName);

        return new StroomZipOutputStreamImpl(zipFile) {
            private boolean closed = false;

            @Override
            public void close() throws IOException {
                // Don't try and close more than once.
                if (!closed) {
                    closed = true;

                    // Write the meta data.
                    try (final OutputStream metaOutputStream = Files.newOutputStream(metaFile)) {
                        AttributeMapUtil.write(attributeMap, metaOutputStream);

                        final String feedName = attributeMap.get(StandardHeaderArguments.FEED);
                        final String typeName = attributeMap.get(StandardHeaderArguments.TYPE);

                        super.close();

                        // If we have added a new source to the repo then add a DB record for it.
                        final long lastModifiedTime = System.currentTimeMillis();
                        proxyRepoSources.addSource(zipFileName, feedName, typeName, lastModifiedTime, attributeMap);

                    } catch (final IOException e) {
                        LOGGER.error(e.getMessage(), e);
                        super.closeDelete();
                    }
                }
            }

            @Override
            public void closeDelete() throws IOException {
                // Don't try and close more than once.
                if (!closed) {
                    closed = true;

                    super.closeDelete();
                }
            }
        };
    }

    public void clean() {
        clean(false);
    }

    public void clean(final boolean deleteRootDirectory) {
        LOGGER.info("clean() " + repoDir);
        clean(repoDir, deleteRootDirectory);
    }

    private void clean(final Path path, final boolean deleteRootDirectory) {
        try {
            if (path != null && Files.isDirectory(path)) {
                final long now = System.currentTimeMillis();
                final long oldestDirMs = now - cleanDelayMs;
                final long oldestLockFileMs = now - lockDeleteAgeMs;

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
            Files.walkFileTree(dir,
                    EnumSet.of(FileVisitOption.FOLLOW_LINKS),
                    Integer.MAX_VALUE,
                    new AbstractFileVisitor() {
                        @Override
                        public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
                            try {
                                if (file.toString().endsWith(".zip.lock")) {
                                    deleteLockFile(file, attrs, oldestLockFileMs);
                                }
                            } catch (final RuntimeException e) {
                                LOGGER.debug(e.getMessage(), e);
                            }
                            return super.visitFile(file, attrs);
                        }

                        @Override
                        public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) {
                            if (getRepoDir().equals(dir) && !deleteRootDirectory) {
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

    private void deleteLockFile(final Path file,
                                final BasicFileAttributes attrs,
                                final long oldestLockFileMs) {
        final FileTime lastModified = attrs.lastModifiedTime();
        if (lastModified != null && lastModified.toMillis() < oldestLockFileMs) {
            try {
                Files.delete(file);
                LOGGER.info("Removed old lock file due to age " + file);
            } catch (final IOException e) {
                LOGGER.error("Unable to remove old lock file due to age " + file);
            }
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
                synchronized (ProxyRepo.this) {
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

    boolean deleteIfEmpty() {
        if (deleteEmptyDir(repoDir)) {
            LOGGER.debug("deleteIfEmpty() - Removed " + repoDir);
            return true;
        }

        return false;
    }

    private boolean deleteEmptyDir(final Path path) {
        boolean success = true;
        try {
            if (Files.isDirectory(path)) {
                Files.walkFileTree(path,
                        EnumSet.of(FileVisitOption.FOLLOW_LINKS),
                        Integer.MAX_VALUE,
                        new AbstractFileVisitor() {
                            @Override
                            public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) {
                                try {
                                    if (!dir.equals(path)) {
                                        if (LOGGER.isDebugEnabled()) {
                                            LOGGER.debug("Attempting to delete dir: " + dir);
                                        }
                                        Files.delete(dir);
                                        if (LOGGER.isDebugEnabled()) {
                                            LOGGER.debug("Deleted dir: " + dir);
                                        }
                                    }
                                } catch (final RuntimeException | IOException e) {
                                    if (LOGGER.isDebugEnabled()) {
                                        LOGGER.debug("Failed to delete dir: " + dir);
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
            LOGGER.debug("Deleted dir: " + path);
        } else {
            LOGGER.debug("Failed to delete dir: " + path);
        }
        return success;
    }

    private void deleteDir(final Path path) throws IOException {
        try {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("delete() - Attempting to delete: " + path);
            }
            Files.delete(path);
        } catch (final DirectoryNotEmptyException e) {
            LOGGER.trace("delete() - Unable to delete dir as it was not empty: " + path);
        }
    }

    List<Path> listAllZipFiles() {
        final List<Path> list = new ArrayList<>();
        try {
            Files.walkFileTree(getRepoDir(),
                    EnumSet.of(FileVisitOption.FOLLOW_LINKS),
                    Integer.MAX_VALUE,
                    new AbstractFileVisitor() {
                        @Override
                        public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
                            try {
                                if (file.toString().endsWith(ProxyRepoFileNames.ZIP_EXTENSION)) {
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
