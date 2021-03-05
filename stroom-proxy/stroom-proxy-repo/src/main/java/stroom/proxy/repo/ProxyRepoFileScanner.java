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

import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.io.AbstractFileVisitor;
import stroom.util.io.FileUtil;
import stroom.util.logging.LogExecutionTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.Optional;
import java.util.function.Consumer;
import javax.inject.Inject;

/**
 * Class that reads a nested directory tree of stroom zip files.
 */
public final class ProxyRepoFileScanner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyRepoFileScanner.class);

    private final TaskContextFactory taskContextFactory;

    private final ProxyRepoSources proxyRepoSources;
    private final Path repoPath;
    private final String repoDir;

    private volatile long lastScanTimeMs = -1;

    @Inject
    public ProxyRepoFileScanner(final TaskContextFactory taskContextFactory,
                                final ProxyRepoConfig proxyRepoConfig,
                                final ProxyRepoSources proxyRepoSources) {
        this.taskContextFactory = taskContextFactory;
        this.proxyRepoSources = proxyRepoSources;
        repoPath = Paths.get(proxyRepoConfig.getRepoDir());
        repoDir = FileUtil.getCanonicalPath(repoPath);
    }

    /**
     * Scan a proxy zip repository,
     */
    public void scan() {
        final Consumer<TaskContext> consumer = taskContext -> {
            final LogExecutionTime logExecutionTime = new LogExecutionTime();
            LOGGER.info("Started");

            try {
                if (Files.isDirectory(repoPath)) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Scanning " + repoDir);
                    }

                    // Discover and store locations of each zip file.
                    final long startTimeMs = System.currentTimeMillis();
                    scanZipFiles(taskContext);
                    lastScanTimeMs = startTimeMs;

                    LOGGER.debug("Completed");

                } else {
                    LOGGER.debug("Repo dir " + repoDir + " not found");
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
            Files.walkFileTree(repoPath,
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

                            // Only process zip repo files
                            final String fileName = file.getFileName().toString();
                            if (fileName.endsWith(ProxyRepo.ZIP_EXTENSION)) {

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

                                    final Path relativePath = repoPath.relativize(file);
                                    final String relativePathString = relativePath.toString();

                                    // See if we already know about this source.
                                    final Optional<Long> optionalSourceId =
                                            proxyRepoSources.getSourceId(relativePathString);
                                    if (optionalSourceId.isEmpty()) {
                                        // This is an unrecorded source so add it.
                                        proxyRepoSources.addSource(relativePathString, lastModified);
                                    }
                                }
                            }

                            return FileVisitResult.CONTINUE;
                        }
                    });
        } catch (final IOException | RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }
}
