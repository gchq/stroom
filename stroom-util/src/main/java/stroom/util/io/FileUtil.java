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
import stroom.util.config.StroomProperties;
import stroom.util.thread.ThreadUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class FileUtil {
    public static final int MKDIR_RETRY_COUNT = 2;
    public static final int MKDIR_RETRY_SLEEP_MS = 100;
    private static final Logger LOGGER = LoggerFactory.getLogger(FileUtil.class);
    /**
     * JVM wide temp dir
     */
    private volatile static File tempDir = null;

    private FileUtil() {
        // Utility.
    }

    public static File getInitialTempDir() {
        final String pathString = StroomProperties.getProperty(StroomProperties.STROOM_TEMP);
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

        return path.toFile();
    }

    public static File getTempDir() {
        if (tempDir == null) {
            synchronized (FileUtil.class) {
                if (tempDir == null) {
                    tempDir = getInitialTempDir();
                }
            }
        }

        return tempDir;
    }

    public static void useDevTempDir() {
        try {
            final File tempDir = getTempDir();

            final File devDir = new File(tempDir, "dev");
            mkdirs(devDir);

            final String path = devDir.getCanonicalPath();

            // Redirect the temp dir for dev.

            StroomProperties.setOverrideProperty(StroomProperties.STROOM_TEMP, path, StroomProperties.Source.USER_CONF);
            // Also set the temp dir as a system property as EclipseDevMode
            // starts a new JVM and will forget this property otherwise.
            System.setProperty(StroomProperties.STROOM_TEMP, path);

            LOGGER.info("Using temp dir '" + path + "'");

            forgetTempDir();

        } catch (final IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public static void forgetTempDir() throws IOException {
        synchronized (FileUtil.class) {
            tempDir = null;
        }
    }

    public static void createNewFile(final File file) throws IOException {
        if (!file.createNewFile()) {
            throw new FileUtilException("Unable to create new file: " + file.getAbsolutePath());
        }
    }

    public static void deleteFile(final File file) {
        if (file.exists()) {
            if (!file.isFile()) {
                throw new FileUtilException("Path is directory not file \"" + file.getAbsolutePath() + "\"");
            }

            if (!file.delete()) {
                throw new FileUtilException("Unable to delete \"" + file.getAbsolutePath() + "\"");
            }
        }
    }

    public static void deleteDir(final File file) {
        if (file.exists()) {
            if (!file.delete()) {
                throw new FileUtilException("Unable to delete \"" + file.getAbsolutePath() + "\"");
            }
        }
    }

    public static void forceDelete(final Path path) {
        forceDelete(path.toFile());
    }

    public static void forceDelete(final File file) {
        if (file.exists()) {
            if (file.isDirectory()) {
                for (final File f : file.listFiles()) {
                    forceDelete(f);
                }
            }

            try {
                if (!file.delete()) {
                    file.deleteOnExit();
                }
            } catch (final Exception e) {
            }
        }
    }

    public static void mkdirs(final File dir) {
        if (!dir.isDirectory()) {
            if (!doMkdirs(null, dir, MKDIR_RETRY_COUNT)) {
                throw new FileUtilException("Unable to make directory: " + dir.getAbsolutePath());
            }

            if (!dir.isDirectory()) {
                throw new FileUtilException("Directory not found: " + dir.getAbsolutePath());
            }
        }
    }

    public static boolean doMkdirs(final File superDir, final File dir, int retry) {
        // Make sure the parent exists first
        final File parentDir = dir.getParentFile();
        if (parentDir != null && !parentDir.isDirectory()) {
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
        if (!dir.isDirectory()) {
            // * CONCURRENT PROBLEM AREA *
            if (!dir.mkdir()) {
                // Someone could have made it in the * CONCURRENT PROBLEM AREA *
                if (!dir.isDirectory()) {
                    if (retry > 0) {
                        retry = retry - 1;
                        LOGGER.warn("doMkdirs() - Sleep and Retry due to unable to create " + dir.getAbsolutePath());
                        ThreadUtil.sleep(MKDIR_RETRY_SLEEP_MS);
                        return doMkdirs(superDir, dir, retry);
                    } else {
                        return false;
                    }
                }
            }
        }
        return true;

    }

    public static void rename(final File src, final File dest) {
        if (!src.renameTo(dest)) {
            throw new FileUtilException(
                    "Unable to rename file \"" + src.getAbsolutePath() + "\" to \"" + dest.getAbsolutePath() + "\"");
        }
    }

    public static void setLastModified(final File file, final long time) throws IOException {
        if (!file.setLastModified(time)) {
            throw new FileUtilException("Unable to set last modified on file: " + file.getAbsolutePath());
        }
    }

    public static String getCanonicalPath(final File file) {
        try {
            return file.getCanonicalPath();
        } catch (final IOException e) {
        }

        return file.getAbsolutePath();
    }

    public static String getCanonicalPath(final Path file) {
        return file.toAbsolutePath().normalize().toString();
    }
}
