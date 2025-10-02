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

import stroom.util.io.AbstractFileVisitor;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LogUtil;

import org.apache.commons.compress.archivers.zip.Zip64Mode;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public final class ZipUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(ZipUtil.class);

    private ZipUtil() {
        // Utility class.
    }

    public static ZipArchiveOutputStream createOutputStream(final OutputStream outputStream) {
        final ZipArchiveOutputStream zipArchiveOutputStream = new ZipArchiveOutputStream(outputStream);
        zipArchiveOutputStream.setUseZip64(Zip64Mode.Always);
        return zipArchiveOutputStream;
    }

    public static void zip(final Path zipFile, final Path dir) throws IOException {
        zip(zipFile, dir, null, null);
    }

    public static void zip(final Path zipFile, final Path dir, final Pattern includePattern,
                           final Pattern excludePattern) throws IOException {
        try (final ZipArchiveOutputStream zipStream =
                createOutputStream(new BufferedOutputStream(Files.newOutputStream(zipFile)))) {
            zip(dir, zipStream, includePattern, excludePattern);
        }
    }

    private static void zip(final Path parent, final ZipArchiveOutputStream zip,
                            final Pattern includePattern, final Pattern excludePattern) {
        try {
            Files.walkFileTree(
                    parent,
                    EnumSet.of(FileVisitOption.FOLLOW_LINKS),
                    Integer.MAX_VALUE,
                    new AbstractFileVisitor() {
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

    private static void putEntry(final ZipArchiveOutputStream zipOutputStream, final Path file, final String name)
            throws IOException {
        LOGGER.debug("zip() - Putting entry {}", name);
        final ZipArchiveEntry zipEntry = new ZipArchiveEntry(name);
        try {
            zipOutputStream.putArchiveEntry(zipEntry);
            try (final InputStream is = new BufferedInputStream(Files.newInputStream(file))) {
                StreamUtil.streamToStream(is, zipOutputStream);
            }
        } finally {
            zipOutputStream.closeArchiveEntry();
        }
    }

    public static void unzip(final Path zipFile, final Path dir) throws IOException {
        Objects.requireNonNull(dir);
        if (Files.exists(dir) && !Files.isDirectory(dir)) {
            throw new IOException(LogUtil.message("'{}' is not a directory.", dir.toAbsolutePath()));
        }

        // TODO change to use ZipFile as ZipArchiveInputStream doesn't read the central
        //  directory. See the javadoc for ZipArchiveInputStream
        try (final ZipArchiveInputStream zip =
                new ZipArchiveInputStream(new BufferedInputStream(Files.newInputStream(zipFile)))) {
            ZipArchiveEntry zipEntry;
            while ((zipEntry = zip.getNextEntry()) != null) {
                // Get output file.
                final Path file = dir.resolve(zipEntry.getName());

                // Ensure that the resolved path is within the target directory
                if (!file.normalize().startsWith(dir.normalize())) {
                    throw new IOException(LogUtil.message(
                            "Zip entry '{}' (normalised: {}) would extract outside target directory '{}'. " +
                                    "Only relative paths inside the target directory are allowed.",
                            zipEntry.getName(),
                            file.normalize().toAbsolutePath(),
                            dir.normalize().toAbsolutePath()));
                }

                if (zipEntry.isDirectory()) {
                    // Make sure output directories exist.
                    Files.createDirectories(file);
                } else {
                    // Make sure output directories exist.
                    Files.createDirectories(file.getParent());

                    // Write file.
                    try (final OutputStream outputStream = Files.newOutputStream(file)) {
                        StreamUtil.streamToStream(zip, outputStream);
                    }
                }
            }
        }
    }

    public static List<String> pathList(final Path zipFile) throws IOException {
        final List<String> pathList = new ArrayList<>();
        // TODO change to use ZipFile as ZipArchiveInputStream doesn't read the central
        //  directory. See the javadoc for ZipArchiveInputStream
        try (final ZipArchiveInputStream zip =
                new ZipArchiveInputStream(new BufferedInputStream(Files.newInputStream(zipFile)))) {
            ZipArchiveEntry zipEntry;
            while ((zipEntry = zip.getNextEntry()) != null) {
                pathList.add(zipEntry.getName());
            }
        }
        return pathList;
    }
}
