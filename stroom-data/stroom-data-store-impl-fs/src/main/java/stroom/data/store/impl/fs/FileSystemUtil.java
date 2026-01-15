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

package stroom.data.store.impl.fs;

import stroom.util.io.FileUtil;
import stroom.util.io.FileUtilException;

import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.Collection;

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
final class FileSystemUtil {

    private static final int MKDIR_RETRY_COUNT = 2;
    private static final int MKDIR_RETRY_SLEEP_MS = 100;

    /**
     * We use this rather than the File.separator as we need to be standard
     * across Windows and UNIX.
     */
    static final char SEPERATOR_CHAR = '/';
    /**
     * How big our buffers are. This should always be a multiple of 8.
     */
    static final int STREAM_BUFFER_SIZE = 1024 * 100;
    /**
     * The store root.
     */
    private static final String STORE_NAME = "store";

    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemUtil.class);

    private FileSystemUtil() {
        // NA
    }

    /**
     * Create a root path.
     */
    static Path createFileTypeRoot(final String volumePath) {
        //        if (streamType != null) {
//            builder.append(SEPERATOR_CHAR);
//            builder.append(streamType.toString());
//        }
        final String path = volumePath +
                SEPERATOR_CHAR +
                STORE_NAME;
        return Paths.get(path);
    }

    static String encodeFileName(final String fileName) {
        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i < fileName.length(); i++) {
            final char c = fileName.charAt(i);
            if (Character.isLetter(c) || Character.isDigit(c) || c == '.' || c == ' ' || c == '-' || c == '_') {
                builder.append(c);
            } else {
                builder.append("#");
                builder.append(Strings.padStart(Integer.toHexString(c), 3, '0'));
            }
        }
        return builder.toString();
    }

    static String decodeFileName(final String fileName) {
        final StringBuilder builder = new StringBuilder();
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

    static boolean deleteAnyPath(final Collection<Path> files) {
        boolean ok = true;
        for (final Path file : files) {
            if (!deleteAnyPath(file)) {
                ok = false;
            }
        }
        return ok;
    }

    static boolean deleteAnyPath(final Path file) {
        boolean ok = true;
        if (Files.isRegularFile(file)) {
            try {
                Files.delete(file);
            } catch (final IOException e) {
                ok = false;
            }
        } else {
            if (Files.isDirectory(file)) {
                ok &= FileUtil.deleteDir(file);
            }
        }
        return ok;
    }

    static boolean isAllFile(final Collection<Path> files) {
        boolean allFiles = true;
        for (final Path file : files) {
            allFiles &= Files.isRegularFile(file);
        }
        return allFiles;
    }

    static boolean isAllParentDirectoryExist(final Collection<Path> files) {
        boolean allDirs = true;
        for (final Path file : files) {
            allDirs &= Files.isDirectory(file.getParent());
        }
        return allDirs;
    }

    static boolean updateLastModified(final Collection<Path> files, final long lastModified) {
        boolean allOk = true;
        for (final Path file : files) {
            try {
                Files.setLastModifiedTime(file, FileTime.fromMillis(lastModified));
            } catch (final IOException e) {
                allOk = false;
            }
        }
        return allOk;
    }

//    public static boolean deleteDirectory(final Path path) {
//        if (deleteContents(path)) {
//            try {
//                Files.deleteIfExists(path);
//                LOGGER.debug("Deleted file " + path);
//                return true;
//            } catch (final IOException e) {
//                LOGGER.error("Failed to delete file " + path);
//                return false;
//            }
//        } else {
//            LOGGER.error("Failed to delete file " + path);
//        }
//        return false;
//    }
//
//    public static boolean deleteContents(final Path path) {
//        final AtomicBoolean success = new AtomicBoolean(true);
//
//        try {
//            if (Files.isDirectory(path)) {
//                try (final Stream<Path> stream = Files.xwalk(path)) {
//                    stream.sorted(Comparator.reverseOrder()).forEach(p -> {
//                        if (!p.equals(path)) {
//                            try {
//                                Files.delete(p);
//                                LOGGER.debug("Deleted file " + p);
//                            } catch (final IOException e) {
//                                LOGGER.error("Failed to delete file " + p);
//                                success.set(false);
//                            }
//                        }
//                    });
//                }
//            }
//        } catch (final IOException e) {
//            LOGGER.error("Failed to delete file " + path);
//            success.set(false);
//        }
//
//        return success.get();
//    }

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

    private static boolean doMkdirs(final Path superDir, final Path dir, int retry) {
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

            try {
                // Someone could have made it in the * CONCURRENT PROBLEM AREA *
                if (!Files.isDirectory(dir)) {
                    if (retry > 0) {
                        retry = retry - 1;
                        LOGGER.warn("doMkdirs() - Sleep and Retry due to unable to create " + FileUtil.getCanonicalPath(
                                dir));
                        Thread.sleep(MKDIR_RETRY_SLEEP_MS);
                        return doMkdirs(superDir, dir, retry);
                    } else {
                        return false;
                    }
                }
            } catch (final InterruptedException e) {
                LOGGER.error(e.getMessage(), e);

                // Continue to interrupt this thread.
                Thread.currentThread().interrupt();
            }
        }
        return true;
    }
}
