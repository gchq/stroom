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
import stroom.meta.api.StandardHeaderArguments;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.io.AbstractFileVisitor;
import stroom.util.io.FileUtil;
import stroom.util.logging.LogExecutionTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;

/**
 * Class that reads a nested directory tree of stroom zip files.
 */
public final class ProxyRepoFileScanner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyRepoFileScanner.class);

    private final TaskContextFactory taskContextFactory;

    private final RepoSources proxyRepoSources;
    private final ProgressLog progressLog;
    private final Path repoDir;
    private final String repoPath;

    private volatile long lastScanTimeMs = -1;

    @Inject
    public ProxyRepoFileScanner(final TaskContextFactory taskContextFactory,
                                final RepoSources proxyRepoSources,
                                final ProgressLog progressLog,
                                final RepoDirProvider repoDirProvider) {
        this.taskContextFactory = taskContextFactory;
        this.proxyRepoSources = proxyRepoSources;
        this.progressLog = progressLog;
        repoDir = repoDirProvider.get();
        repoPath = FileUtil.getCanonicalPath(repoDir);
    }

    /**
     * Scan a proxy zip repository,
     */
    public void scan() {
        scan(false);
    }

    /**
     * Scan a proxy zip repository,
     */
    public void scan(final boolean sorted) {
        final Consumer<TaskContext> consumer = taskContext -> {
            final LogExecutionTime logExecutionTime = new LogExecutionTime();
            LOGGER.info("Started");

            try {
                if (Files.isDirectory(repoDir)) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Scanning " + repoPath);
                    }

                    // Discover and store locations of each zip file.
                    final long startTimeMs = System.currentTimeMillis();
                    if (sorted) {
                        scanZipFilesSorted(taskContext);
                    } else {
                        scanZipFiles(taskContext);
                    }
                    lastScanTimeMs = startTimeMs;

                    LOGGER.debug("Completed");

                } else {
                    LOGGER.debug("Repo dir " + repoPath + " not found");
                }
            } catch (final Exception e) {
                LOGGER.error(e.getMessage(), e);
            }

            LOGGER.info("Completed in {}", logExecutionTime);
        };
        final Runnable runnable = taskContextFactory.context("Proxy Repository Scanner", consumer);
        runnable.run();
    }

    private void scanZipFiles(final TaskContext taskContext) {
        try {
            Files.walkFileTree(repoDir,
                    EnumSet.of(FileVisitOption.FOLLOW_LINKS),
                    Integer.MAX_VALUE,
                    new AbstractFileVisitor() {
                        @Override
                        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                            return super.preVisitDirectory(dir, attrs);
                        }

                        @Override
                        public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
                            taskContext.info(() -> FileUtil.getCanonicalPath(file));

                            if (Thread.currentThread().isInterrupted()) {
                                return FileVisitResult.TERMINATE;
                            }

                            // Add file.
                            try {
                                addFile(file);
                            } catch (final RuntimeException e) {
                                LOGGER.error(e.getMessage(), e);
                            }

                            return FileVisitResult.CONTINUE;
                        }
                    });
        } catch (final IOException | RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private void addFile(final Path file) {
        // Only process zip repo files
        final String fileName = file.getFileName().toString();
        if (fileName.endsWith(ProxyRepoFileNames.ZIP_EXTENSION)) {
            // Don't try to add files that are older than the last time we scanned.
            boolean add = true;
            long lastModified = -1;
            if (lastScanTimeMs != -1) {
                try {
                    lastModified = Files.getLastModifiedTime(file).toMillis();
                    if (lastModified < lastScanTimeMs) {
                        add = false;
                    }
                } catch (final IOException e) {
                    LOGGER.error(e.getMessage(), e);
                }
            }

            if (add) {
                if (lastModified == -1) {
                    lastModified = System.currentTimeMillis();
                }

                progressLog.increment("ProxyRepoFileScanner - addFile");

                final Path relativePath = repoDir.relativize(file);
                final String relativePathString = relativePath.toString();

                // See if we already know about this source.
                final boolean exists = proxyRepoSources.sourceExists(relativePathString);
                if (!exists) {
                    // Read meta.
                    final String metaFileName = ProxyRepoFileNames.getMeta(file.getFileName().toString());
                    final Path metaFile = file.getParent().resolve(metaFileName);
                    if (!Files.isRegularFile(metaFile)) {
                        throw new RuntimeException("Unable to find proxy repo meta file: " + metaFileName);
                    }

                    final AttributeMap attributeMap = new AttributeMap();
                    try (final InputStream inputStream = Files.newInputStream(metaFile)) {
                        AttributeMapUtil.read(inputStream, attributeMap);

                        final String feedName = attributeMap.get(StandardHeaderArguments.FEED);
                        final String typeName = attributeMap.get(StandardHeaderArguments.TYPE);

                        // This is an unrecorded source so add it.
                        proxyRepoSources.addSource(relativePathString, feedName, typeName, lastModified, attributeMap);

                    } catch (final IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            }
        }
    }

    private void scanZipFilesSorted(final TaskContext taskContext) {
        scanDir(repoDir, taskContext);
    }

    private void scanDir(final Path dir, final TaskContext taskContext) {
        final List<Path> list = getSortedPathList(dir);
        list.forEach(file -> {
            taskContext.info(() -> FileUtil.getCanonicalPath(file));

            if (Files.isDirectory(file)) {
                scanDir(file, taskContext);
            } else {
                addFile(file);
            }
        });
    }

    private List<Path> getSortedPathList(final Path dir) {
        try (final Stream<Path> stream = Files.list(dir)) {
            return stream
                    .sorted((o1, o2) -> {
                        final boolean o1IsDir = Files.isDirectory(o1);
                        final boolean o2IsDir = Files.isDirectory(o2);
                        if (o1IsDir) {
                            if (o2IsDir) {
                                return o1.getFileName().toString().compareTo(o2.getFileName().toString());
                            }
                            return 1;
                        }
                        if (o2IsDir) {
                            return -1;
                        }
                        return o1.getFileName().toString().compareTo(o2.getFileName().toString());
                    })
                    .collect(Collectors.toList());
        } catch (final IOException | RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }
        return Collections.emptyList();
    }
}
