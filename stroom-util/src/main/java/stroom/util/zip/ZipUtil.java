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
import stroom.util.io.AbstractFileVisitor;
import stroom.util.io.CloseableUtil;
import stroom.util.io.StreamUtil;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.List;
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

    public static void zip(final Path zipFile, final Path dir) throws IOException {
        zip(zipFile, dir, null, null);
    }

    public static void zip(final Path zipFile, final Path dir, final Pattern includePattern,
                           final Pattern excludePattern) throws IOException {
        final ZipOutputStream zipStream = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(zipFile)));

        zip(dir, zipStream, includePattern, excludePattern);

        zipStream.flush();
        zipStream.close();
    }

    private static void zip(final Path parent, final ZipOutputStream zip,
                            final Pattern includePattern, final Pattern excludePattern) {
        try {
            Files.walkFileTree(parent, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new AbstractFileVisitor() {
                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
                    try {
                        final String fullPath = parent.relativize(file).toString();
                        if ((includePattern == null || includePattern.matcher(fullPath).matches()) &&
                                (excludePattern == null || !excludePattern.matcher(fullPath).matches())) {
                            putEntry(zip, file, fullPath);
                        }
                    } catch (final IOException e) {
                        LOGGER.error(e.getMessage(), e);
                    }
                    return super.visitFile(file, attrs);
                }
            });
        } catch (final NotDirectoryException e) {
            // Ignore.
        } catch (final IOException e) {
            LOGGER.debug(e.getMessage(), e);
        }
    }

    private static void putEntry(final ZipOutputStream zipOutputStream, final Path file, final String name)
            throws IOException {
        LOGGER.debug("zip() - Putting entry {}", name);
        final ZipEntry zipEntry = new ZipEntry(name);
        zipOutputStream.putNextEntry(zipEntry);
        InputStream is = null;
        try {
            is = new BufferedInputStream(Files.newInputStream(file));
            StreamUtil.streamToStream(is, zipOutputStream, false);
        } finally {
            CloseableUtil.close(is);
        }
        zipOutputStream.closeEntry();
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

    public static List<String> pathList(final Path zipFile) throws IOException {
        final List<String> pathList = new ArrayList<>();
        ZipFile zipFile2 = null;
        try {
            zipFile2 = new ZipFile(zipFile.toFile());

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
}
