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

package stroom.util.zip;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.io.CloseableUtil;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class ZipUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(ZipUtil.class);

    private ZipUtil() {
        // Utility class.
    }

    public static Path workingZipDir(final Path zipFile) {
        final String name = zipFile.getFileName().toString();
        return zipFile.resolveSibling(name.substring(0, name.length() - ".zip".length()));
    }

    /**
     * Given a zip file /home/user/zippy.zip give a directory to explode to
     * /home/user/zippy
     */
    public static File workingZipDir(final File zipFile) {
        final int zipExtensionLen = ".zip".length();
        final String zipPath = zipFile.getAbsolutePath();
        return new File(zipPath.substring(0, zipPath.length() - zipExtensionLen));
    }

    public static void zip(final File zipFile, final File dir) throws IOException {
        zip(zipFile, dir, null, null);
    }

    public static void zip(final File zipFile, final File dir, final Pattern includePattern,
                           final Pattern excludePattern) throws IOException {
        final ZipOutputStream zipStream = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)));

        zip(dir, "", zipStream, includePattern, excludePattern);

        zipStream.flush();
        zipStream.close();
    }

    private static void zip(final File parent, final String path, final ZipOutputStream zip,
                            final Pattern includePattern, final Pattern excludePattern) throws IOException {
        final File[] files = parent.listFiles();
        Arrays.sort(files);

        for (final File file : files) {
            final String fullPath = path + file.getName();
            if (file.isDirectory()) {
                zip(file, fullPath + "/", zip, includePattern, excludePattern);
            } else {
                if (includePattern != null && !includePattern.matcher(fullPath).matches()) {
                    continue;
                }
                if (excludePattern != null && excludePattern.matcher(fullPath).matches()) {
                    continue;
                }

                putEntry(zip, file, fullPath);
            }
        }
    }

    private static void putEntry(final ZipOutputStream zipOutputStream, final File file, final String name)
            throws IOException {
        LOGGER.debug("zip() - Putting entry {}", name);
        final ZipEntry zipEntry = new ZipEntry(name);
        zipOutputStream.putNextEntry(zipEntry);
        InputStream is = null;
        try {
            is = new BufferedInputStream(new FileInputStream(file));
            StreamUtil.streamToStream(is, zipOutputStream, false);
        } finally {
            CloseableUtil.close(is);
        }
        zipOutputStream.closeEntry();
    }

    public static void unzip(final File zipFile, final File dir) throws IOException {
        final ZipInputStream zip = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)));

        ZipEntry zipEntry = null;
        while ((zipEntry = zip.getNextEntry()) != null) {
            // Get output file.
            final File file = new File(dir, zipEntry.getName());

            if (zipEntry.isDirectory()) {
                // Make sure output directories exist.
                FileUtil.mkdirs(file);
            } else {
                // Make sure output directories exist.
                FileUtil.mkdirs(file.getParentFile());

                // Write file.
                FileOutputStream fileStream = null;
                try {
                    fileStream = new FileOutputStream(file);
                    StreamUtil.streamToStream(zip, fileStream, false);
                } finally {
                    CloseableUtil.close(fileStream);
                }
            }

            zip.closeEntry();
        }

        zip.close();
    }

    public static void unzip(final Path zipFile, final Path dir) throws IOException {
        try (final ZipInputStream zip = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zipFile)))) {
            ZipEntry zipEntry;
            while ((zipEntry = zip.getNextEntry()) != null) {
                try {
                    // Get output file.
                    final Path file = dir.resolve(zipEntry.getName());

                    if (zipEntry.isDirectory()) {
                        // Make sure output directories exist.
                        Files.createDirectories(file);
                    } else {
                        // Make sure output directories exist.
                        Files.createDirectories(file.getParent());

                        // Write file.
                        try (final OutputStream outputStream = Files.newOutputStream(file)) {
                            StreamUtil.streamToStream(zip, outputStream, false);
                        }
                    }
                } finally {
                    zip.closeEntry();
                }
            }
        }
    }

    public static List<String> pathList(final File zipFile) throws IOException {
        final List<String> pathList = new ArrayList<>();
        ZipFile zipFile2 = null;
        try {
            zipFile2 = new ZipFile(zipFile);

            final Enumeration<? extends ZipEntry> zipEnumeration = zipFile2.entries();

            while (zipEnumeration.hasMoreElements()) {
                final ZipEntry zipEntry = zipEnumeration.nextElement();
                pathList.add(zipEntry.getName());
            }
        } finally {
            CloseableUtil.close(zipFile2);
        }

        return pathList;
    }

    public static Map<String, Long> pathSize(final File zipFile) throws IOException {
        final Map<String, Long> pathMap = new HashMap<>();
        ZipFile zipFile2 = null;
        try {
            zipFile2 = new ZipFile(zipFile);

            final Enumeration<? extends ZipEntry> zipEnumeration = zipFile2.entries();

            while (zipEnumeration.hasMoreElements()) {
                final ZipEntry zipEntry = zipEnumeration.nextElement();
                pathMap.put(zipEntry.getName(), zipEntry.getSize());
            }
        } finally {
            CloseableUtil.close(zipFile2);
        }

        return pathMap;
    }
}
