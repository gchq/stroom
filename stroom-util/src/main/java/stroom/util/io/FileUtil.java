/*
 * Copyright 2016 Crown Copyright
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

package stroom.util.io;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class FileUtil {

    public static final int MKDIR_RETRY_COUNT = 2;
    public static final int MKDIR_RETRY_SLEEP_MS = 100;
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FileUtil.class);

    private FileUtil() {
        // Utility.
    }

    public static boolean delete(final Path file) {
        try {
            Files.delete(file);
            return true;
        } catch (final IOException e) {
            return false;
        }
    }

    public static void deleteFile(final Path file) {
        if (Files.exists(file)) {
            if (Files.isDirectory(file)) {
                throw new FileUtilException("Path is directory not file \""
                        + FileUtil.getCanonicalPath(file) + "\"");
            }

            try {
                Files.deleteIfExists(file);
            } catch (final IOException e) {
                throw new FileUtilException("Unable to delete \""
                        + FileUtil.getCanonicalPath(file) + "\"");
            }
        }
    }

    public static boolean deleteDir(final Path path) {
        final AtomicBoolean success = new AtomicBoolean(true);
        if (path != null && Files.isDirectory(path)) {
            recursiveDelete(path, success);
            delete(path, success);
        }
        return success.get();
    }

    public static boolean deleteContents(final Path path) {
        final AtomicBoolean success = new AtomicBoolean(true);
        if (path != null && Files.isDirectory(path)) {
            recursiveDelete(path, success);
        }
        return success.get();
    }

    /**
     * @return True if path contains no files or directories.
     * @throws IOException if it is unable to list the contents of path
     */
    public static boolean isEmptyDirectory(final Path path) throws IOException {
        Objects.requireNonNull(path);
        if (Files.isDirectory(path)) {
            try (final Stream<Path> pathStream = Files.list(path)) {
                return pathStream.findAny().isEmpty();
            }
        } else {
            return false;
        }
    }

    private static void recursiveDelete(final Path path, final AtomicBoolean success) {
        try {
            Files.walkFileTree(
                    path,
                    EnumSet.of(FileVisitOption.FOLLOW_LINKS),
                    Integer.MAX_VALUE,
                    new AbstractFileVisitor() {
                        @Override
                        public FileVisitResult visitFile(final Path file,
                                                         final BasicFileAttributes attrs) {
                            delete(file, success);
                            return super.visitFile(file, attrs);
                        }

                        @Override
                        public FileVisitResult postVisitDirectory(final Path dir,
                                                                  final IOException exc) {
                            if (!dir.equals(path)) {
                                delete(dir, success);
                            }
                            return super.postVisitDirectory(dir, exc);
                        }
                    });
        } catch (final NotDirectoryException e) {
            // Ignore.
        } catch (final IOException e) {
            LOGGER.debug(e.getMessage(), e);
        }
    }

    public static long count(final Path dir) {
        final AtomicLong count = new AtomicLong();
        try (final DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            stream.forEach(file -> {
                try {
                    count.incrementAndGet();
                } catch (final RuntimeException e) {
                    LOGGER.debug(e.getMessage(), e);
                }
            });
        } catch (final IOException e) {
            LOGGER.debug(e.getMessage(), e);
        }
        return count.get();
    }

    public static long countNested(final Path dir) {
        final AtomicLong count = new AtomicLong();

        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs)
                        throws IOException {
                    count.incrementAndGet();
                    return super.preVisitDirectory(dir, attrs);
                }

                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                    count.incrementAndGet();
                    return super.visitFile(file, attrs);
                }
            });
        } catch (final IOException e) {
            // Ignore.
        }

        return count.get();
    }

    public static long countNestedFiles(final Path dir) {
        final AtomicLong count = new AtomicLong();

        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                    count.incrementAndGet();
                    return super.visitFile(file, attrs);
                }
            });
        } catch (final IOException e) {
            // Ignore.
        }

        return count.get();
    }

    public static long count(final Path dir,
                             final Predicate<Path> pathExclusionFilter) {
        final AtomicLong count = new AtomicLong();
        try (final DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            stream
                    .forEach(file -> {
                        if (pathExclusionFilter != null && !pathExclusionFilter.test(file)) {
                            try {
                                count.incrementAndGet();
                            } catch (final RuntimeException e) {
                                LOGGER.debug(e.getMessage(), e);
                            }
                        }
                    });
        } catch (final IOException e) {
            LOGGER.debug(e.getMessage(), e);
        }
        return count.get();
    }

    private static void delete(final Path path, final AtomicBoolean success) {
        try {
            Files.delete(path);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Deleted file " + path);
            }
        } catch (final IOException e) {
            success.set(false);
            LOGGER.error("Failed to delete file " + path);
            LOGGER.trace(e.getMessage(), e);
        }
    }

    /**
     * Similar to the unix touch command. Sets the last modified time to now if the file
     * exists else, creates the file
     *
     * @throws IOException
     */
    public static void touch(Path file) throws IOException {
        Objects.requireNonNull(file, "file is null");
        if (Files.exists(file)) {
            if (!Files.isRegularFile(file)) {
                throw new RuntimeException(String.format("File %s is not a regular file",
                        FileUtil.getCanonicalPath(file)));
            }
            Files.setLastModifiedTime(file, FileTime.from(Instant.now()));
        } else {
            Files.createFile(file);
        }
    }

    public static void mkdirs(final Path dir) {
        if (!Files.isDirectory(dir)) {
            try {
                Files.createDirectories(dir);
            } catch (final IOException e) {
                throw new FileUtilException("Unable to make directory: " + FileUtil.getCanonicalPath(dir));
            }
        }
    }

    public static void rename(final Path src, final Path dest) {
        try {
            Files.move(src, dest);
        } catch (final IOException e) {
            throw new FileUtilException(
                    "Unable to rename file \"" + FileUtil.getCanonicalPath(src)
                            + "\" to \"" + FileUtil.getCanonicalPath(dest) + "\"");
        }
    }

    public static void setLastModified(final Path file, final long time) throws IOException {
        Files.setLastModifiedTime(file, FileTime.fromMillis(time));
    }

    public static void addFilePermission(final Path path,
                                         final PosixFilePermission... posixFilePermission) throws IOException {
        final Set<PosixFilePermission> filePermissions = Files.getPosixFilePermissions(path);
        final Set<PosixFilePermission> newPermissions = new HashSet<>(filePermissions);
        newPermissions.addAll(Arrays.asList(posixFilePermission));
        Files.setPosixFilePermissions(path, newPermissions);
    }

    public static void removeFilePermission(final Path path,
                                            final PosixFilePermission... posixFilePermission) throws IOException {
        final Set<PosixFilePermission> filePermissions = Files.getPosixFilePermissions(path);
        final Set<PosixFilePermission> newPermissions = new HashSet<>(filePermissions);
        newPermissions.removeAll(Arrays.asList(posixFilePermission));
        Files.setPosixFilePermissions(path, newPermissions);
    }

    public static String getCanonicalPath(final Path file) {
        return file.toAbsolutePath().normalize().toString();
    }

    public static Path createTempDirectory(final String prefix) {
        try {
            return Files.createTempDirectory(prefix);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Replaces '~' at the start of the path with the user.home
     */
    public static String replaceHome(final String file) {
        String resolved = file;
        if (resolved != null && resolved.startsWith("~")) {
            resolved = System.getProperty("user.home") + resolved.substring(1);
        }
        return resolved;
    }

    /**
     * Similar to {@link Files#createDirectories(Path, FileAttribute[])} but with logging
     * to log when the dir is created.
     *
     * @param path The path of the dir to ensure exists
     */
    public static void ensureDirExists(final Path path) {
        if (!Files.isDirectory(path)) {
            LOGGER.info("Creating directory {}", path.normalize().toAbsolutePath());
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                throw new RuntimeException("Error creating directory " + path.normalize().toAbsolutePath(), e);
            }
        }
    }

    /**
     * Obtains a write lock on lockFilePath then runs the work. Creates lockFilePath
     * if it doesn't exist. Will block if another thread/jvm holds a lock on the same
     * file.
     */
    public static void doUnderFileLock(final Path lockFilePath, final Runnable work) {
        final Instant start = Instant.now();
        LOGGER.debug("Using lock file {}", lockFilePath.toAbsolutePath());

        try (final FileOutputStream fileOutputStream = new FileOutputStream(lockFilePath.toFile());
                final FileChannel channel = fileOutputStream.getChannel()) {
            channel.lock();

            LOGGER.debug(() -> LogUtil.message("Waited {} for lock",
                    Duration.between(start, Instant.now())));

            // Do the work while under the lock
            work.run();

            LOGGER.debug("Work complete, releasing lock");
        } catch (IOException e) {
            throw new RuntimeException("Error opening lock file " + lockFilePath.toAbsolutePath(), e);
        }
    }

    /**
     * Obtains a write lock on lockFilePath then runs the work. Creates lockFilePath
     * if it doesn't exist. Will block if another thread/jvm holds a lock on the same
     * file.
     */
    public static <T> T getUnderFileLock(final Path lockFilePath, final Supplier<T> work) {

        final Instant start = Instant.now();
        LOGGER.debug("Using lock file {}", lockFilePath.toAbsolutePath());

        try (final FileOutputStream fileOutputStream = new FileOutputStream(lockFilePath.toFile());
                final FileChannel channel = fileOutputStream.getChannel()) {
            channel.lock();

            LOGGER.debug(() -> LogUtil.message("Waited {} for lock",
                    Duration.between(start, Instant.now())));

            // Do the work while under the lock
            T result = work.get();

            LOGGER.debug("Work complete, releasing lock");
            return result;
        } catch (IOException e) {
            throw new RuntimeException("Error opening lock file " + lockFilePath.toAbsolutePath(), e);
        }
    }

    public static long getByteSize(final Path dir) {
        final AtomicLong total = new AtomicLong();
        try {
            try (final Stream<Path> stream = Files.walk(dir)) {
                stream.forEach(path -> {
                    try {
                        if (Files.isRegularFile(path)) {
                            total.addAndGet(Files.size(path));
                        }
                    } catch (final IOException e) {
                        LOGGER.debug(e::getMessage, e);
                    }
                });
            }
        } catch (final IOException e) {
            LOGGER.debug(e::getMessage, e);
        }
        return total.get();
    }
}
