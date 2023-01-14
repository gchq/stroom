/*
 * Copyright 2017 Crown Copyright
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

package stroom.proxy.repo;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.proxy.repo.store.Entries;
import stroom.proxy.repo.store.FileSet;
import stroom.proxy.repo.store.SequentialFileStore;
import stroom.util.io.AbstractFileVisitor;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LogExecutionTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Class that reads a nested directory tree of stroom zip files.
 */
public final class FileScanner {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileScanner.class);

    private final Path sourceDir;
    private final SequentialFileStore targetStore;

    public FileScanner(final Path sourceDir,
                       final SequentialFileStore targetStore) {
        this.sourceDir = sourceDir;
        this.targetStore = targetStore;
    }

    public Path getSourceDir() {
        return sourceDir;
    }

    /**
     * Scan a proxy zip repository,
     */
    public void scan() {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        LOGGER.info("Started");

        try {
            if (Files.isDirectory(sourceDir)) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Scanning " + sourceDir);
                }

                scanZipFiles();

                LOGGER.debug("Completed");

            } else {
                LOGGER.debug("Repo dir " + sourceDir + " not found");
            }
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
        }

        LOGGER.info("Completed in {}", logExecutionTime);
    }

    private void scanZipFiles() {
        try {
            Files.walkFileTree(sourceDir,
                    EnumSet.of(FileVisitOption.FOLLOW_LINKS),
                    Integer.MAX_VALUE,
                    new AbstractFileVisitor() {
                        @Override
                        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                            if (Thread.currentThread().isInterrupted()) {
                                return FileVisitResult.TERMINATE;
                            }
                            return super.preVisitDirectory(dir, attrs);
                        }

                        @Override
                        public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) {
                            if (Thread.currentThread().isInterrupted()) {
                                return FileVisitResult.TERMINATE;
                            }

                            // Delete dirs.
                            if (!sourceDir.equals(dir)) {
                                try {
                                    Files.deleteIfExists(dir);
                                } catch (final IOException e) {
                                    LOGGER.trace(e.getMessage(), e);
                                }
                            }
                            return super.postVisitDirectory(dir, exc);
                        }

                        @Override
                        public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
                            if (Thread.currentThread().isInterrupted()) {
                                return FileVisitResult.TERMINATE;
                            }

                            // Add file.
                            try {
                                addFile(file);
                            } catch (final IOException e) {
                                LOGGER.error(e.getMessage(), e);
                            }

                            return FileVisitResult.CONTINUE;
                        }
                    });
        } catch (final IOException | RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private void addFile(final Path file) throws IOException {
        // Only process zip repo files
        final String fileName = file.getFileName().toString();

        // Meta is moved last so use that to determine if we can consume this stream.
        if (fileName.endsWith(FileSet.META_EXTENSION)) {
            final Path parent = file.getParent();
            final Path metaFile = file;

            // Get zip file.
            String stem = fileName;
            int index = fileName.lastIndexOf(FileSet.META_EXTENSION);
            if (index != -1) {
                stem = fileName.substring(0, index);
            }
            final Path zipFile = parent.resolve(stem + FileSet.ZIP_EXTENSION);

            if (Files.isRegularFile(zipFile)) {
                final AttributeMap attributeMap = new AttributeMap();
                try (final InputStream inputStream = Files.newInputStream(metaFile)) {
                    AttributeMapUtil.read(inputStream, attributeMap);

                    try (final ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(zipFile))) {
                        try (final Entries entries = targetStore.getEntries(attributeMap)) {
                            ZipEntry zipEntry = zipInputStream.getNextEntry();
                            while (zipEntry != null) {
                                try (final OutputStream outputStream = entries.addEntry(zipEntry.getName())) {
                                    StreamUtil.streamToStream(zipInputStream, outputStream);
                                }
                                zipEntry = zipInputStream.getNextEntry();
                            }
                        }
                    }
                }

                // Delete files.
                Files.deleteIfExists(zipFile);
                Files.deleteIfExists(metaFile);
            }
        }
    }
}
