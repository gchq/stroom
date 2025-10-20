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

import org.apache.commons.lang3.mutable.MutableLong;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
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
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
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

    /**
     * Attempts to delete any empty directories found while walking the tree starting from rootDir.
     * Will not delete rootDir.
     * No exceptions will be thrown. It will only log errors.
     */
    public static int deleteEmptyDirs(final Path rootDir) {
        final MutableLong deleteCount = new MutableLong();
        try {
            Files.walkFileTree(rootDir, new SimpleFileVisitor<>() {
                final Set<Path> dirsWithFiles = new HashSet<>();

                //
                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                    // Mark all parent directories as having files
                    Path parent = file.getParent();
                    while (parent != null && parent.startsWith(rootDir)) {
                        dirsWithFiles.add(parent);
                        parent = parent.getParent();
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(final Path file, final IOException exc) throws IOException {
                    // Sill an item in the dir even if we can't visit it
                    // Mark all parent directories as having files
                    Path parent = file.getParent();
                    while (parent != null && parent.startsWith(rootDir)) {
                        dirsWithFiles.add(parent);
                        parent = parent.getParent();
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException {
                    if (!Files.isSameFile(rootDir, dir)) {
                        if (!dirsWithFiles.contains(dir)) {
                            try {
                                Files.delete(dir);
                                deleteCount.increment();
                            } catch (final DirectoryNotEmptyException e) {
                                LOGGER.debug("deleteEmptyDirs() - Directory {} is not empty so cannot be deleted.",
                                        dir);
                            } catch (final IOException e) {
                                LOGGER.error("Error while trying to delete directory {} - {}",
                                        dir, LogUtil.exceptionMessage(e));
                            }
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (final IOException e) {
            // Swallow
            LOGGER.error("Error walking directory {} - {}", rootDir, LogUtil.exceptionMessage(e), e);
        }
        LOGGER.debug("deleteEmptyDirs() - Deleted {} empty directories", deleteCount);
        return deleteCount.intValue();
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
                            deleteIfExists(file, success);
                            return super.visitFile(file, attrs);
                        }

                        @Override
                        public FileVisitResult postVisitDirectory(final Path dir,
                                                                  final IOException exc) {
                            if (!dir.equals(path)) {
                                deleteIfExists(dir, success);
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

    private static void deleteIfExists(final Path path, final AtomicBoolean success) {
        try {
            if (Files.deleteIfExists(path)) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Deleted file " + path);
                }
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
    public static void touch(final Path file) throws IOException {
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
    public static Path ensureDirExists(final Path path) {
        if (!Files.isDirectory(path)) {
            LOGGER.info("Creating directory {}", path.normalize().toAbsolutePath());
            try {
                return Files.createDirectories(path);
            } catch (final IOException e) {
                throw new RuntimeException("Error creating directory " + path.normalize().toAbsolutePath(), e);
            }
        } else {
            return path;
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
        } catch (final IOException e) {
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
            final T result = work.get();

            LOGGER.debug("Work complete, releasing lock");
            return result;
        } catch (final IOException e) {
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

    public static void deepCopy(final Path src, final Path dest) throws IOException {
        try (final Stream<Path> stream = Files.walk(src)) {
            stream.forEach(source -> copy(source, dest.resolve(src.relativize(source))));
        }
    }

    private static void copy(final Path source, final Path dest) {
        try {
            Files.copy(source, dest);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Recursively list the contents of path, acquiring the file attributes at the same time to
     * avoid subsequent calls down to the file system for things like {@link Files#isDirectory(Path, LinkOption...)}.
     *
     * <p>
     * Only use this if you need the file attributes of most of the files or you want to filter
     * on the file attributes.
     * </p>
     * <p>
     * Includes path in the output
     * </p>
     */
    public static List<PathWithAttributes> deepListContents(final Path path,
                                                            final boolean parallel,
                                                            final FileVisitOption... fileVisitOptions) {
        return deepListContents(path, parallel, null, fileVisitOptions);
    }

    /**
     * Recursively list the contents of path, acquiring the file attributes at the same time to
     * avoid subsequent calls down to the file system for things like {@link Files#isDirectory(Path, LinkOption...)}.
     *
     * <p>
     * Only use this if you need the file attributes of most of the files or you want to filter
     * on the file attributes.
     * </p>
     * <p>
     * Includes path in the output
     * </p>
     *
     * @param filter Optional filter to limit the results.
     */
    public static List<PathWithAttributes> deepListContents(final Path path,
                                                            final boolean parallel,
                                                            final Predicate<PathWithAttributes> filter,
                                                            final FileVisitOption... fileVisitOptions) {
        Objects.requireNonNull(path);

        final Map<Path, BasicFileAttributes> pathToTypeMap = new ConcurrentHashMap<>();
        // Stateful predicate to hold onto the file type
        final BiPredicate<Path, BasicFileAttributes> predicate = (aPath, basicFileAttributes) -> {
            pathToTypeMap.put(aPath, basicFileAttributes);
            return true;
        };

        try (final Stream<Path> stream = Files.find(path, Integer.MAX_VALUE, predicate, fileVisitOptions)) {
            final Stream<Path> stream2 = parallel
                    ? stream.parallel()
                    : stream;

            final Stream<PathWithAttributes> fileWithAttributesStream = stream2
                    .map(aPath -> {
                        // Remove it from the map now we have mapped it
                        final BasicFileAttributes attributes = Objects.requireNonNull(pathToTypeMap.remove(aPath));
                        return new PathWithAttributes(aPath, attributes);
                    });
            if (filter != null) {
                return fileWithAttributesStream.filter(filter)
                        .toList();
            } else {
                return fileWithAttributesStream.toList();
            }
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * List the direct subdirectories of parent.
     */
    public static List<Path> listChildDirs(final Path parent) {
        return listChildPaths(parent, Files::isDirectory);
    }

    /**
     * List the regular files that are a direct child of parent.
     */
    public static List<Path> listChildFiles(final Path parent) {
        return listChildPaths(parent, Files::isRegularFile);
    }

    /**
     * List the direct child paths in parent that match pathPredicate.
     */
    public static List<Path> listChildPaths(final Path parent) {
        return listChildPaths(parent, null);
    }

    /**
     * List the direct child paths in parent that match pathPredicate.
     */
    public static List<Path> listChildPaths(final Path parent,
                                            final Predicate<Path> pathPredicate) {
        if (parent == null) {
            return Collections.emptyList();
        } else {
            try {
                try (final Stream<Path> pathStream = Files.list(parent)) {
                    if (pathPredicate != null) {
                        return pathStream.filter(pathPredicate)
                                .toList();
                    } else {
                        return pathStream.toList();
                    }
                }
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /**
     * For each subdirectory that is a direct child of parent, call childConsumer.
     */
    public static void forEachChildDir(final Path parent,
                                       final Consumer<Path> childConsumer) {
        forEachChild(parent, Files::isDirectory, childConsumer);
    }

    /**
     * For each regular file that is a direct child of parent, call childConsumer.
     */
    public static void forEachChildFile(final Path parent,
                                        final Consumer<Path> childConsumer) {
        forEachChild(parent, Files::isRegularFile, childConsumer);
    }

    /**
     * For each path (of any type) that is a direct child of parent, call childConsumer.
     */
    public static void forEachChild(final Path parent,
                                    final Consumer<Path> childConsumer) {
        forEachChild(parent, null, childConsumer);
    }

    /**
     * For each path (of any type) that is a direct child of parent, call childConsumer if it
     * matches pathPredicate.
     */
    public static void forEachChild(final Path parent,
                                    final Predicate<Path> pathPredicate,
                                    final Consumer<Path> childConsumer) {
        if (parent != null) {
            Objects.requireNonNull(childConsumer);
            try {
                try (final Stream<Path> pathStream = Files.list(parent)) {
                    if (pathPredicate != null) {
                        pathStream.filter(pathPredicate)
                                .forEach(childConsumer);
                    } else {
                        pathStream.forEach(childConsumer);
                    }
                }
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
