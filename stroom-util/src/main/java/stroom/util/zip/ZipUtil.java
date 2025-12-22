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

package stroom.util.zip;

import stroom.util.io.AbstractFileVisitor;
import stroom.util.io.ByteSize;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LogUtil;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.examples.Expander;
import org.apache.commons.compress.archivers.zip.Zip64Mode;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
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
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

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

    /**
     * Unzips zipFile into targetDir. targetDir will be created if it doesn't exist.
     *
     * @param zipFile   The ZIP file to unzip.
     * @param targetDir The target directory to unzip into.
     * @throws IOException
     */
    public static void unzip(final Path zipFile, final Path targetDir) throws IOException {
        Objects.requireNonNull(targetDir);
        if (Files.exists(targetDir) && !Files.isDirectory(targetDir)) {
            throw new IOException(LogUtil.message("'{}' is not a directory.", targetDir.toAbsolutePath()));
        }
        try (ZipFile zipArchive = createZipFile(zipFile)) {
            // This will check zip entry paths are not outside the targetDir
            new Expander().expand(zipArchive, targetDir);
        }
    }

    /**
     * Unzipping from an {@link InputStream} means you may unzip entries that are not in the
     * ZIP central directory. See ZipArchiveInputStream javadoc.
     *
     * @param inputStream The input stream to unzip.
     * @param targetDir   The target directory to unzip into.
     * @throws IOException
     */
    @Deprecated
    public static void unzip(final InputStream inputStream, final Path targetDir) throws IOException {
        Objects.requireNonNull(inputStream);
        Objects.requireNonNull(targetDir);
        if (Files.exists(targetDir) && !Files.isDirectory(targetDir)) {
            throw new IOException(LogUtil.message("'{}' is not a directory.", targetDir.toAbsolutePath()));
        }

        try (final ZipArchiveInputStream zip = new ZipArchiveInputStream(new BufferedInputStream(inputStream))) {
            ZipArchiveEntry zipEntry;
            while ((zipEntry = zip.getNextEntry()) != null) {
                // Get output file. This will throw if the entry is outside targetDir
                final Path file = zipEntry.resolveIn(targetDir);

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

    /**
     * List the paths ({@link ZipArchiveEntry#getName()}) in the ZIP.
     */
    public static List<String> pathList(final Path zipFile) throws IOException {
        return pathList(zipFile, true);
    }

    /**
     * List the paths ({@link ZipArchiveEntry#getName()}) in the ZIP.
     *
     * @param zipFilePath   The zip file to list.
     * @param validatePaths If true will throw an exception for any zip entries that have an
     *                      absolute path or a path that would be outside a target directory,
     *                      e.g. '../foo.txt'.
     * @return The list of paths.
     * @throws IOException
     */
    public static List<String> pathList(final Path zipFilePath, final boolean validatePaths) throws IOException {
        Objects.requireNonNull(zipFilePath);
        final List<String> pathList = new ArrayList<>();
        try (ZipFile zipFile = createZipFile(zipFilePath)) {
            final Iterator<ZipArchiveEntry> iterator = zipFile.getEntries().asIterator();
            while (iterator.hasNext()) {
                final ZipArchiveEntry zipEntry = iterator.next();
                if (validatePaths && !isSafeZipPath(Path.of(zipEntry.getName()))) {
                    throw new IOException(LogUtil.message(
                            "Zip entry '{}' would extract outside of a target directory.",
                            zipEntry.getName()));
                }
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
        if (!Files.isRegularFile(zipFilePath)) {
            throw new UncheckedIOException(new IOException(
                    LogUtil.message("ZIP file '{}' does not exist or is not a file", zipFilePath.toAbsolutePath())));
        }
        if (!Files.isReadable(zipFilePath)) {
            throw new UncheckedIOException(new IOException(
                    LogUtil.message("ZIP file '{}' is not readable", zipFilePath.toAbsolutePath())));
        }
        try {
            // Not clear if we should be using setSeekableByteChannel or setFile ??
            return ZipFile.builder()
                    .setSeekableByteChannel(Files.newByteChannel(zipFilePath))
                    .get();
        } catch (final IOException e) {
            // TODO change e.getMessage() => LogUtil.exceptionMessage(e) in later versions
            throw new UncheckedIOException(LogUtil.message(
                    "Error creating ZipFile object for zipFilePath {}: {}",
                    zipFilePath, e.getMessage()), e);
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
    public static void forEachEntry(final Path zipFile,
                                    final BiConsumer<ZipFile, ZipArchiveEntry> entryConsumer) {
        Objects.requireNonNull(entryConsumer);
        try (final ZipFile zipArchive = createZipFile(zipFile)) {
            final Enumeration<ZipArchiveEntry> entries = zipArchive.getEntries();
            ZipArchiveEntry zipEntry = entries.hasMoreElements()
                    ? entries.nextElement()
                    : null;
            while (zipEntry != null && zipArchive.canReadEntryData(zipEntry)) {
                entryConsumer.accept(zipArchive, zipEntry);
                zipEntry = entries.hasMoreElements()
                        ? entries.nextElement()
                        : null;
            }
        } catch (final IOException e) {
            // TODO change e.getMessage() => LogUtil.exceptionMessage(e) in later versions
            throw new UncheckedIOException(LogUtil.message(
                    "Error iterating over entries in zipFilePath {}: {}",
                    zipFile, e.getMessage()), e);
        }
    }

    /**
     * Tests if a zip entry path is safe to use, i.e. when path is unzipped into
     * any directory, it does not resolve to a path that is outside that directory.
     *
     * @param path The path to test, relative or absolute.
     * @return True if the path is safe.
     */
    public static boolean isSafeZipPath(final Path path) {
        Objects.requireNonNull(path);
        final Path normalisedPath = path.normalize();

        // We don't have a destination dir, so need to construct a base path to
        // test normalisedPath against that has at least as many parts,
        // e.g. if normalisedPath is ../../../foo basePath needs to be /0/1/2/3
        // to give a fullPath of /0/foo when combined, which does not start
        // with /0/1/2/3
        final long partCount = StreamSupport.stream(normalisedPath.spliterator(), false)
                .count();
        final StringBuilder stringBuilder = new StringBuilder();
        for (long i = 0; i < partCount; i++) {
            stringBuilder.append(File.separatorChar)
                    .append(i);
        }
        final Path basePath = Path.of(stringBuilder.toString());
        final Path fullPath = basePath.resolve(normalisedPath)
                .normalize();

        LOGGER.trace("path: {}, normalisedPath: {}, basePath: {}, fullPath: {}",
                path, normalisedPath, basePath, fullPath);

        return fullPath.startsWith(basePath.normalize());
    }

    /**
     * Tests if a zip entry path is safe to use, i.e. it does not resolve to a path
     * that is outside destDir.
     * <p>
     * Consider using {@link ZipArchiveEntry#resolveIn(Path)} which does the same check.
     * </p>
     *
     * @param path    The path to test, relative or absolute.
     * @param destDir The directory that path will be resolved against.
     * @return True if the path is safe.
     */
    public static boolean isSafeZipPath(final Path path, final Path destDir) {
        Objects.requireNonNull(path);
        Objects.requireNonNull(destDir);
        final Path normalisedPath = path.normalize();
        final Path fullPath = destDir.resolve(normalisedPath)
                .normalize();

        LOGGER.debug("path: {}, destDir: {}, normalisedPath: {}, fullPath: {}",
                path, destDir, normalisedPath, fullPath);

        return fullPath.startsWith(destDir.normalize());
    }
}
