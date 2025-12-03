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

package stroom.importexport.impl;

import stroom.util.io.AbstractFileVisitor;
import stroom.util.io.FileUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

public class ContentMigration {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContentMigration.class);

    // UUID structure 8-4-4-4-12
    private static final String UUID_REGEX = "[a-z0-9]{8}-[a-z0-9]{4}-[a-z0-9]{4}-[a-z0-9]{4}-[a-z0-9]{12}";

    public void migrate(final Path path) {
        // Migrate node files.
        final boolean hasNodes = migrateNodeFiles(path);

        if (!hasNodes) {
            // If we don't have node files then this is legacy content.
            processLegacyContent(path);
        }
    }

    private boolean migrateNodeFiles(final Path path) {
        final AtomicBoolean hasNodes = new AtomicBoolean();
        migrateNodeFiles(path, hasNodes);
        return hasNodes.get();
    }

    private void migrateNodeFiles(final Path dir, final AtomicBoolean hasNodes) {
        try {
            Files.walkFileTree(dir,
                    EnumSet.of(FileVisitOption.FOLLOW_LINKS),
                    Integer.MAX_VALUE,
                    new AbstractFileVisitor() {
                        @Override
                        public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
                            try {
                                final String fileName = file.getFileName().toString();
                                if (fileName.endsWith(".node")) {
                                    hasNodes.set(true);
                                    final String fileStem = fileName.substring(0, fileName.lastIndexOf("."));
                                    final Properties properties = PropertiesSerialiser.read(Files.newInputStream(file));

                                    final String name = properties.getProperty("name");
                                    final String type = properties.getProperty("type");
                                    final String uuid = properties.getProperty("uuid");

                                    if (name != null && type != null && uuid != null) {
                                        final String newFileStem = createFilePrefix(name, type, uuid);
                                        if (!fileStem.equals(newFileStem)) {
                                            renameFiles(file.getParent(), fileStem, newFileStem);
                                        }
                                    } else {
                                        LOGGER.error("Bad properties file " + fileName);
                                    }
                                }
                            } catch (final RuntimeException | IOException e) {
                                LOGGER.error(e.getMessage(), e);
                            }
                            return super.visitFile(file, attrs);
                        }
                    });
        } catch (final IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private void processLegacyContent(final Path dir) {
        // If we don't have node files then this is legacy content.
        try {
            Files.walkFileTree(dir,
                    EnumSet.of(FileVisitOption.FOLLOW_LINKS),
                    Integer.MAX_VALUE,
                    new AbstractFileVisitor() {
                        @Override
                        public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
                            try {
                                final String fileName = file.getFileName().toString();
                                if (fileName.matches("([^.]*\\.){2,}xml") && fileName.matches(
                                        ".*\\.[A-Z][A-Za-z]+\\.xml$")) {
                                    process(dir, file);
                                }
                            } catch (final RuntimeException e) {
                                LOGGER.error(e.getMessage(), e);
                            }
                            return super.visitFile(file, attrs);
                        }
                    });
        } catch (final IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private void renameFiles(final Path dir, final String fileStem, final String newFileStem) {
        try (final DirectoryStream<Path> stream = Files.newDirectoryStream(dir, fileStem + ".*")) {
            stream.forEach(file -> {
                try {
                    final String newFileName = file.getFileName().toString().replaceAll(fileStem + ".",
                            newFileStem + ".");
                    LOGGER.info("Renaming file: '" + file.getFileName().toString() + "' to '" + newFileName);
                    Files.move(file, file.getParent().resolve(newFileName));
                } catch (final IOException e) {
                    LOGGER.error(e.getMessage(), e);
                }
            });
        } catch (final IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private void process(final Path dir, final Path path) {
        try {
            LOGGER.info("Processing: " + FileUtil.getCanonicalPath(path));

            final String fileName = path.getFileName().toString();
            final String fileStem = fileName.substring(0, fileName.lastIndexOf("."));
            final String type = fileStem.substring(fileStem.lastIndexOf(".") + 1);

            // Get path elements
            final List<String> pathElements = new ArrayList<>();
            Path parent = path.getParent();
            while (parent != null &&
                    parent.getFileName() != null &&
                    !dir.equals(parent) &&
                    !parent.getFileName().toString().equalsIgnoreCase("stroomContent")) {
                pathElements.add(0, parent.getFileName().toString());
                parent = parent.getParent();
            }

            final String xml = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            final String uuid = getValue(xml, "uuid");
            final String name = getValue(xml, "name");

            final Properties properties = new Properties();

            if (uuid == null) {
                throw new IllegalArgumentException("Unable to process " + path.toString() + " no UUID found.");
            }
            properties.setProperty("uuid", uuid);

            if (type == null) {
                throw new IllegalArgumentException("Unable to process " + path.toString()
                        + " docref type not defined.");
            }
            properties.setProperty("type", type);

            properties.setProperty("name", (name != null) ? name : ("Unnamed " + type));
            properties.setProperty("path", String.join("/", pathElements));

            final String newFileStem = createFilePrefix(name, type, uuid);
            final OutputStream outputStream = Files.newOutputStream(path.getParent().resolve(newFileStem + ".node"));
            PropertiesSerialiser.write(properties, outputStream);

            // Rename related files.
            try {
                Files.walkFileTree(path.getParent(),
                        EnumSet.of(FileVisitOption.FOLLOW_LINKS),
                        Integer.MAX_VALUE,
                        new AbstractFileVisitor() {
                            @Override
                            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
                                try {
                                    final String fileName = file.getFileName().toString();
                                    if (fileName.startsWith(fileStem + ".") && !fileName.startsWith(newFileStem)) {
                                        final String extension = fileName.substring(fileStem.length());
                                        final String newFileName = newFileStem + extension;
                                        Files.move(file, file.getParent().resolve(newFileName));
                                    }
                                } catch (final RuntimeException | IOException e) {
                                    LOGGER.error(e.getMessage(), e);
                                }

                                return super.visitFile(file, attrs);
                            }
                        });
            } catch (final IOException e) {
                LOGGER.error(e.getMessage(), e);
            }

        } catch (final IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private String getValue(final String xml, final String tag) {
        final int start = xml.indexOf("<" + tag + ">");
        final int end = xml.indexOf("</" + tag + ">");
        if (start != -1 && end != -1) {
            return xml.substring(start + 6, end);
        }
        return null;
    }

    private String createFilePrefix(final String name, final String type, final String uuid) {
        return ImportExportFileNameUtil.toSafeFileName(name, 100) + "." + type + "." + uuid;
    }
}
