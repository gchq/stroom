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

package stroom.streamstore.server.fs;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.node.shared.Volume;
import stroom.streamstore.shared.StreamType;
import stroom.util.io.FileUtil;
import stroom.util.io.FileUtilException;
import stroom.util.thread.ThreadUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Utility class to open a File based on it's meta data.
 * <p>
 * Stream's on the file system are stored in directories.
 * <p>
 * If the stream is compressed it ends with the "bgz" (block gzip compression)
 * which is our own random access file format. If the file is not compressed if
 * it ends with "dat" Raw data file (used for indexed child streams)
 * <p>
 * Child streams have an appended "."[type] extension and then .bgz or .dat.
 * <p>
 * A typical stream with 2 children could be: .../001/100/001=001002001.bgz
 * .../001/100/001=001002001.ctx.bgz .../001/100/001=001002001.idx.dat
 * <p>
 * Those children can have further child streams: .../001/100/001=001002001.bgz
 * .../001/100/001=001002001.ctx.bgz .../001/100/001=001002001.ctx.idx.dat
 * .../001/100/001=001002001.idx.dat
 * <p>
 * Any files ended in .lock are locked output streams that have not yet closed.
 */
public final class FileSystemUtil {
    public static final int MKDIR_RETRY_COUNT = 2;
    public static final int MKDIR_RETRY_SLEEP_MS = 100;

    /**
     * We use this rather than the File.separator as we need to be standard
     * across Windows and UNIX.
     */
    public static final char SEPERATOR_CHAR = '/';
    public static final char FILE_SEPERATOR_CHAR = '=';
    /**
     * Extension used for locking.
     */
    public static final String LOCK_EXTENSION = ".lock";
    /**
     * How big our buffers are. This should always be a multiple of 8.
     */
    public static final int STREAM_BUFFER_SIZE = 1024 * 100;
    /**
     * The store root.
     */
    public static final String STORE_NAME = "store";

    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemUtil.class);

    private FileSystemUtil() {
        // NA
    }

    /**
     * Create a root path.
     */
    public static Path createFileTypeRoot(final Volume volume) {
        return createFileTypeRoot(volume, null);
    }

    /**
     * Create a root path.
     */
    public static Path createFileTypeRoot(final Volume volume, final StreamType streamType) {
        StringBuilder builder = new StringBuilder();
        builder.append(volume.getPath());
        builder.append(SEPERATOR_CHAR);
        builder.append(STORE_NAME);
        if (streamType != null) {
            builder.append(SEPERATOR_CHAR);
            builder.append(streamType.toString());
        }
        return Paths.get(builder.toString());
    }

    public static String encodeFileName(String fileName) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < fileName.length(); i++) {
            char c = fileName.charAt(i);
            if (Character.isLetter(c) || Character.isDigit(c) || c == '.' || c == ' ' || c == '-' || c == '_') {
                builder.append(c);
            } else {
                builder.append("#");
                builder.append(StringUtils.leftPad(Integer.toHexString(c), 3, '0'));
            }
        }
        return builder.toString();
    }

    public static String decodeFileName(String fileName) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < fileName.length(); i++) {
            char c = fileName.charAt(i);
            if (c == '#') {
                c = (char) Integer.decode("0x" + fileName.substring(i + 1, i + 4)).intValue();
                builder.append(c);
                i = i + 3;
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    public static boolean deleteAnyFile(final Collection<File> files) {
        boolean ok = true;
        for (File file : files) {
            if (file.isFile()) {
                ok &= file.delete();
            } else {
                if (file.isDirectory()) {
                    ok &= deleteDirectory(file);
                }
            }
        }
        return ok;
    }

    public static boolean deleteAnyPath(final Collection<Path> files) {
        boolean ok = true;
        for (Path file : files) {
            if (Files.isRegularFile(file)) {
                try {
                    Files.delete(file);
                } catch (final IOException e) {
                    ok = false;
                }
            } else {
                if (Files.isDirectory(file)) {
                    ok &= deleteDirectory(file);
                }
            }
        }
        return ok;
    }

    public static boolean isAllFile(final Collection<Path> files) {
        boolean allFiles = true;
        for (Path file : files) {
            allFiles &= Files.isRegularFile(file);
        }
        return allFiles;
    }

//    public static boolean isAllFile(final Collection<File> files) {
//        boolean allFiles = true;
//        for (File file : files) {
//            allFiles &= file.isFile();
//        }
//        return allFiles;
//    }

    public static boolean isAllParentDirectoryExist(final Collection<Path> files) {
        boolean allDirs = true;
        for (Path file : files) {
            allDirs &= Files.isDirectory(file.getParent());
        }
        return allDirs;
    }

//    public static boolean updateLastModified(final Collection<File> files, final long lastModified) {
//        boolean allOk = true;
//        for (File file : files) {
//            allOk &= file.setLastModified(lastModified);
//        }
//        return allOk;
//    }

    public static boolean updateLastModified(final Collection<Path> files, final long lastModified) {
        boolean allOk = true;
        for (Path file : files) {
            try {
                Files.setLastModifiedTime(file, FileTime.fromMillis(lastModified));
            } catch (final IOException e) {
                allOk = false;
            }
        }
        return allOk;
    }

    public static boolean deleteDirectory(final File path) {
        boolean success = true;
        if (path != null && path.isDirectory()) {
            if (deleteContents(path)) {
                final boolean deleteDir = path.delete();
                if (!deleteDir) {
                    LOGGER.error("Failed to delete file " + path);
                    success = false;
                } else {
                    LOGGER.debug("Deleted file " + path);
                }
            } else {
                LOGGER.error("Failed to delete file " + path);
                success = false;
            }
        }
        return success;
    }



    public static boolean deleteContents(final File path) {
        boolean allOk = true;
        File[] kids = path.listFiles();
        if (kids != null) {
            for (File kid : kids) {
                if (kid.isFile()) {
                    if (!kid.delete()) {
                        LOGGER.error("Failed to delete file " + kid);
                        allOk = false;
                    } else if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Deleted file " + kid);
                    }
                } else {
                    boolean kidsDeleted = deleteContents(kid);
                    if (kidsDeleted) {
                        if (!kid.delete()) {
                            LOGGER.error("Failed to delete file " + kid);
                            allOk = false;
                        } else if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("Deleted directory " + kid);
                        }
                    } else {
                        allOk = false;
                    }
                }
            }
        }
        return allOk;
    }

    public static boolean deleteDirectory(final Path path) {
        if (deleteContents(path)) {
            try {
                Files.deleteIfExists(path);
                LOGGER.debug("Deleted file " + path);
                return true;
            } catch (final IOException e) {
                LOGGER.error("Failed to delete file " + path);
                return false;
            }
        } else {
            LOGGER.error("Failed to delete file " + path);
        }
        return false;
    }

    public static boolean deleteContents(final Path path) {
        final AtomicBoolean success = new AtomicBoolean(true);

        try {
            if (Files.isDirectory(path)) {
                try (final Stream<Path> stream = Files.walk(path)) {
                    stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                        if (!p.equals(path)) {
                            try {
                                Files.delete(p);
                                LOGGER.debug("Deleted file " + p);
                            } catch (final IOException e) {
                                LOGGER.error("Failed to delete file " + p);
                                success.set(false);
                            }
                        }
                    });
                }
            }
        } catch (final IOException e) {
            LOGGER.error("Failed to delete file " + path);
            success.set(false);
        }

        return success.get();
    }

    /**
     * <p>
     * Utility method to create directories one by one (rather than call Java
     * API mkdirs). We do this to ensure if we fail to make one we check that it
     * has not been created between that last time we checked.
     * </p>
     * <p>
     * <p>
     * WE ASSUME here that mkdir is ATOMIC .... which it is.
     * </p>
     */
    public static boolean mkdirs(final File superDir, final File dir) {
        return doMkdirs(toPath(superDir), toPath(dir), FileUtil.MKDIR_RETRY_COUNT);
    }

    private static Path toPath(final File file) {
        if (file == null) {
            return null;
        }
        return file.toPath();
    }

    public static boolean mkdirs(final Path superDir, final Path dir) {
        return doMkdirs(superDir, dir, FileUtil.MKDIR_RETRY_COUNT);
    }

    public static void mkdirs(final Path dir) {
        if (!Files.isDirectory(dir)) {
            if (!doMkdirs(null, dir, MKDIR_RETRY_COUNT)) {
                throw new FileUtilException("Unable to make directory: " + FileUtil.getCanonicalPath(dir));
            }

            if (!Files.isDirectory(dir)) {
                throw new FileUtilException("Directory not found: " + FileUtil.getCanonicalPath(dir));
            }
        }
    }

    public static boolean doMkdirs(final Path superDir, final Path dir, int retry) {
        // Make sure the parent exists first
        final Path parentDir = dir.getParent();
        if (parentDir != null && !Files.isDirectory(parentDir)) {
            if (superDir != null && superDir.equals(parentDir)) {
                // Unable to make parent as it is the super dir
                return false;

            }
            if (!doMkdirs(superDir, parentDir, retry)) {
                // Unable to make parent :(
                return false;
            }
        }
        // No Make us
        if (!Files.isDirectory(dir)) {
            // * CONCURRENT PROBLEM AREA *
            try {
                Files.createDirectory(dir);
            } catch (final IOException e) {
                LOGGER.debug("Failed to make dir " + e.getMessage());
            }

            // Someone could have made it in the * CONCURRENT PROBLEM AREA *
            if (!Files.isDirectory(dir)) {
                if (retry > 0) {
                    retry = retry - 1;
                    LOGGER.warn("doMkdirs() - Sleep and Retry due to unable to create " + FileUtil.getCanonicalPath(dir));
                    ThreadUtil.sleep(MKDIR_RETRY_SLEEP_MS);
                    return doMkdirs(superDir, dir, retry);
                } else {
                    return false;
                }
            }
        }
        return true;

    }
}
