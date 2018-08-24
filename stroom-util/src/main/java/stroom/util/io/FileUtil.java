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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class FileUtil {
    public static final int MKDIR_RETRY_COUNT = 2;
    public static final int MKDIR_RETRY_SLEEP_MS = 100;
    private static final Logger LOGGER = LoggerFactory.getLogger(FileUtil.class);
    /**
     * JVM wide temp dir
     */
    private volatile static Path tempDir = null;

    private FileUtil() {
        // Utility.
    }

    private static String getTempPath() {
        return System.getProperty("java.io.tmpdir");
    }

    private static Path getInitialTempDir() {
        final String pathString = getTempPath();
        if (pathString == null) {
            throw new RuntimeException("No temp path is specified");
        }

        final Path path = Paths.get(pathString);
        if (!Files.exists(path)) {
            try {
                Files.createDirectory(path);
            } catch (IOException e) {
                LOGGER.error(MarkerFactory.getMarker("FATAL"), "Unable to create temp directory.", e);
                throw new RuntimeException("Unable to create temp directory", e);
            }
        }

        return path;
    }

    public static Path getTempDir() {
        if (tempDir == null) {
            synchronized (FileUtil.class) {
                if (tempDir == null) {
                    tempDir = getInitialTempDir();
                }
            }
        }

        return tempDir;
    }

    public static void setTempDir(final Path tempDir) {
        FileUtil.tempDir = tempDir;
    }

    //    public static void useDevTempDir() {
//        try {
//            final Path tempDir = getTempDir();
//
//            final Path devDir = tempDir.resolve("dev");
//            Files.createDirectories(devDir);
//
//            final String path = FileUtil.getCanonicalPath(devDir);
//
//            // Redirect the temp dir for dev.
//
//            StroomProperties.setOverrideProperty(StroomProperties.STROOM_TEMP, path, StroomProperties.Source.USER_CONF);
//            // Also set the temp dir as a system property as EclipseDevMode
//            // starts a new JVM and will forget this property otherwise.
//            System.setProperty(StroomProperties.STROOM_TEMP, path);
//
//            LOGGER.info("Using temp dir '" + path + "'");
//
//            forgetTempDir();
//
//        } catch (final IOException e) {
//            throw new UncheckedIOException(e);
//        }
//    }

    public static void forgetTempDir() {
        synchronized (FileUtil.class) {
            tempDir = null;
        }
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
                throw new FileUtilException("Path is directory not file \"" + FileUtil.getCanonicalPath(file) + "\"");
            }

            try {
                Files.deleteIfExists(file);
            } catch (final IOException e) {
                throw new FileUtilException("Unable to delete \"" + FileUtil.getCanonicalPath(file) + "\"");
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

    private static void recursiveDelete(final Path path, final AtomicBoolean success) {
        try {
            Files.walkFileTree(path, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new AbstractFileVisitor() {
                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
                    delete(file, success);
                    return super.visitFile(file, attrs);
                }

                @Override
                public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) {
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
     * Similar to the unix touch cammand. Sets the last modified time to now if the file
     * exists else, creates the file
     *
     * @throws IOException
     */
    public static void touch(Path file) throws IOException {
        Objects.requireNonNull(file, "file is null");
        if (Files.exists(file)) {
            if (!Files.isRegularFile(file)) {
                throw new RuntimeException(String.format("File %s is not a regular file", FileUtil.getCanonicalPath(file)));
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
                    "Unable to rename file \"" + FileUtil.getCanonicalPath(src) + "\" to \"" + FileUtil.getCanonicalPath(dest) + "\"");
        }
    }

    public static void setLastModified(final Path file, final long time) throws IOException {
        Files.setLastModifiedTime(file, FileTime.fromMillis(time));
    }

    public static void addFilePermision(final Path path, final PosixFilePermission... posixFilePermission) throws IOException {
        final Set<PosixFilePermission> filePermissions = Files.getPosixFilePermissions(path);
        final Set<PosixFilePermission> newPermissions = new HashSet<>(filePermissions);
        newPermissions.addAll(Arrays.asList(posixFilePermission));
        Files.setPosixFilePermissions(path, newPermissions);
    }

    public static void removeFilePermision(final Path path, final PosixFilePermission... posixFilePermission) throws IOException {
        final Set<PosixFilePermission> filePermissions = Files.getPosixFilePermissions(path);
        final Set<PosixFilePermission> newPermissions = new HashSet<>(filePermissions);
        newPermissions.removeAll(Arrays.asList(posixFilePermission));
        Files.setPosixFilePermissions(path, newPermissions);
    }

    public static String getCanonicalPath(final Path file) {
        return file.toAbsolutePath().normalize().toString();
    }
}
