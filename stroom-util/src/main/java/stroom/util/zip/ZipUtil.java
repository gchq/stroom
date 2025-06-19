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
import stroom.util.io.ByteSize;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LogUtil;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.zip.Zip64Mode;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
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
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public final class ZipUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(ZipUtil.class);

    private ZipUtil() {
        // Utility class.
    }

    public static ZipArchiveOutputStream createOutputStream(final OutputStream outputStream) {
        final ZipArchiveOutputStream zipArchiveOutputStream = new ZipArchiveOutputStream(outputStream);
        zipArchiveOutputStream.setUseZip64(Zip64Mode.AlwaysWithCompatibility);
        return zipArchiveOutputStream;
    }

    public static void zip(final Path zipFile, final Path dir) throws IOException {
        final Predicate<Path> filePredicate = path -> !path.equals(zipFile);
        zip(zipFile, dir, filePredicate, name -> true);
    }

    public static Predicate<String> createIncludeExcludeEntryPredicate(final Pattern includePattern,
                                                                       final Pattern excludePattern) {
        if (includePattern == null && excludePattern == null) {
            return name -> true;
        }
        Predicate<String> include = null;
        Predicate<String> exclude = null;
        if (includePattern != null) {
            include = name -> includePattern.matcher(name).matches();
        }
        if (excludePattern != null) {
            exclude = name -> !excludePattern.matcher(name).matches();
        }
        if (include != null && exclude != null) {
            return include.and(exclude);
        } else if (include != null) {
            return include;
        }
        return exclude;
    }

    public static void zip(final Path zipFile,
                           final Path dir,
                           final Predicate<Path> filePredicate,
                           final Predicate<String> entryPredicate) throws IOException {
        try (final ZipArchiveOutputStream zipOutputStream =
                createOutputStream(new BufferedOutputStream(Files.newOutputStream(zipFile)))) {
            zip(dir, zipOutputStream, filePredicate, entryPredicate);
        }
    }

    public static void zip(final Path dir,
                           final ZipArchiveOutputStream zipOutputStream) {
        zip(dir, zipOutputStream, path -> true, name -> true);
    }

    private static void zip(final Path dir,
                            final ZipArchiveOutputStream zipOutputStream,
                            final Predicate<Path> filePredicate,
                            final Predicate<String> entryPredicate) {
        try {
            Files.walkFileTree(
                    dir,
                    EnumSet.of(FileVisitOption.FOLLOW_LINKS),
                    Integer.MAX_VALUE,
                    new AbstractFileVisitor() {
                        @Override
                        public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
                            try {
                                // Make sure we don't include the destination zip file in the zip file.
                                if (filePredicate.test(file)) {
                                    final String name = dir.relativize(file).toString();
                                    if (entryPredicate.test(name)) {
                                        putEntry(zipOutputStream, file, name);
                                    }
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
        try {
            zipOutputStream.putArchiveEntry(new ZipArchiveEntry(name));
            try (final InputStream is = new BufferedInputStream(Files.newInputStream(file))) {
                StreamUtil.streamToStream(is, zipOutputStream);
            }
        } finally {
            zipOutputStream.closeArchiveEntry();
        }
    }

    public static void unzip(final Path zipFile, final Path dir) throws IOException {
        unzip(Files.newInputStream(zipFile), dir);
    }

    public static void unzip(final InputStream inputStream, final Path dir) throws IOException {
        try (final ZipArchiveInputStream zip =
                new ZipArchiveInputStream(new BufferedInputStream(inputStream))) {
            ZipArchiveEntry zipEntry;
            while ((zipEntry = zip.getNextEntry()) != null) {
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
                        StreamUtil.streamToStream(zip, outputStream);
                    }
                }
            }
        }
    }

    public static List<String> pathList(final Path zipFilePath) throws IOException {
        final List<String> pathList = new ArrayList<>();
        try (final ZipArchiveInputStream zip =
                new ZipArchiveInputStream(new BufferedInputStream(Files.newInputStream(zipFilePath)))) {
            ZipArchiveEntry zipEntry;
            while ((zipEntry = zip.getNextEntry()) != null) {
                pathList.add(zipEntry.getName());
            }
        }
        return pathList;
    }

    /**
     * Get the uncompressed size of the entry, if known.
     */
    public static Optional<ByteSize> getEntryUncompressedSize(final ArchiveEntry archiveEntry) {
        return Optional.ofNullable(archiveEntry)
                .map(ArchiveEntry::getSize)
                .filter(size -> size != ArchiveEntry.SIZE_UNKNOWN)
                .map(ByteSize::ofBytes);
    }

    /**
     * @return True if the uncompressed size of the entry is known.
     */
    public static boolean hasKnownUncompressedSize(final ArchiveEntry archiveEntry) {
        if (archiveEntry == null) {
            return false;
        } else {
            return archiveEntry.getSize() != ArchiveEntry.SIZE_UNKNOWN;
        }
    }

    public static ZipFile createZipFile(final Path zipFilePath) {
        try {
            // Not clear if we should be using setSeekableByteChannel or setFile ??
            return ZipFile.builder()
                    .setSeekableByteChannel(Files.newByteChannel(zipFilePath))
                    .get();
        } catch (final IOException e) {
            throw new UncheckedIOException(LogUtil.message(
                    "Error creating ZipFile object for zipFilePath {}: {}",
                    zipFilePath, LogUtil.exceptionMessage(e)), e);
        }
    }

    public static String getEntryContent(final ZipFile zipFile,
                                         final ZipArchiveEntry entry) {
        try (final InputStream inputStream = zipFile.getInputStream(entry)) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Iterate over each entry in the zip file.
     */
    public static void forEachEntry(final Path zipFilePath,
                                    final BiConsumer<ZipFile, ZipArchiveEntry> entryConsumer) {
        Objects.requireNonNull(entryConsumer);
        try (final ZipFile zipFile = createZipFile(zipFilePath)) {
            zipFile.getEntries()
                    .asIterator()
                    .forEachRemaining(entry ->
                            entryConsumer.accept(zipFile, entry));
        } catch (final IOException e) {
            throw new UncheckedIOException(LogUtil.message(
                    "Error iterating over entries in zipFilePath {}: {}",
                    zipFilePath, LogUtil.exceptionMessage(e)), e);
        }
    }
}
