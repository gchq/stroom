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

package stroom.data.store.impl.fs;

import stroom.task.api.TaskContextFactory;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

/**
 * Split out on its own to aid mocking in tests.
 */
class FsFileDeleter {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FsFileDeleter.class);

    private final TaskContextFactory taskContextFactory;

    @Inject
    FsFileDeleter(final TaskContextFactory taskContextFactory) {
        this.taskContextFactory = taskContextFactory;
    }

    /**
     * Deletes all files of the form '{@code <baseName>.*}' in directory {@code dir}.
     * Not recursive.
     * All IO errors are logged as errors and swallowed.
     *
     * @param metaId   The meta record the files belong to, used for logging.
     * @param dir      The dir to delete files from.
     * @param baseName The base of the filename, i.e. without extension.
     * @return False if any errors occurred deleting files. Missing files do not count as errors.
     */
    boolean deleteFilesByBaseName(final long metaId,
                                  final Path dir,
                                  final String baseName,
                                  final LongConsumer deleteCountConsumer) {
        Objects.requireNonNull(dir);
        Objects.requireNonNull(baseName);
        final String glob = baseName + ".*";
        final AtomicBoolean wasSuccessful = new AtomicBoolean(true);
        try (final DirectoryStream<Path> stream = Files.newDirectoryStream(dir, glob)) {
            stream.forEach(file -> {
                if (!Thread.currentThread().isInterrupted()) {
                    try {
                        final Supplier<String> msgSupplier = () -> "Deleting file: '"
                                + FileUtil.getCanonicalPath(file)
                                + "' for stream "
                                + metaId;
                        info(msgSupplier);

                        // Doesn't matter if it does not exist as that is the desired end state anyway
                        final boolean didDelete = Files.deleteIfExists(file);

                        if (deleteCountConsumer != null && didDelete) {
                            deleteCountConsumer.accept(1L);
                        }

                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("File '{}' {}",
                                    FileUtil.getCanonicalPath(file),
                                    (didDelete
                                            ? "deleted"
                                            : "doesn't exist"));
                        }
                    } catch (final IOException e) {
                        wasSuccessful.compareAndSet(true, false);
                        final String msg = "Error deleting file '" +
                                FileUtil.getCanonicalPath(file) +
                                "' for meta ID " +
                                metaId;
                        LOGGER.debug(msg, e);
                        LOGGER.error(msg + " - "
                                + e.getClass().getSimpleName() + " - " + e.getMessage()
                                + " (see DEBUG for stacktrace)");
                    }
                } else {
                    // We probably haven't finished so treat as failure.  Next run of the job can finish it.
                    // Although we keep looping it should only be a few files so will drop out quickly
                    // with a failure returned.
                    wasSuccessful.compareAndSet(true, false);
                    LOGGER.debug("Thread interrupted for stream: {}, dir: '{}', file: {}", metaId, dir, file);
                }
            });
        } catch (final IOException e) {
            wasSuccessful.compareAndSet(true, false);
            final String msg = "Error creating directory stream '" +
                    FileUtil.getCanonicalPath(dir) +
                    "' for stream " + metaId + ", glob=" + glob;
            LOGGER.debug(msg, e);
            LOGGER.error(msg + " - "
                    + e.getClass().getSimpleName() + " - " + e.getMessage()
                    + " (see DEBUG for stacktrace)");
        }
        return wasSuccessful.get();
    }

    /**
     * Attempts to delete directory {@code dir} if it is empty and all non-empty
     * ancestor directories excluding {@code root}. {@code dir} must be a descendant
     * of {@code root}. Also, it won't delete any directories whose last modified time is >=
     * oldFileTime.
     *
     * @param root
     * @param dir
     * @param oldFileTime
     * @return
     */
    boolean tryDeleteDir(final Path root,
                         final Path dir,
                         final long oldFileTime,
                         final LongConsumer deleteCountConsumer) {
        Objects.requireNonNull(root);
        Objects.requireNonNull(dir);

        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException(LogUtil.message("root '{}' is not a directory",
                    root.normalize().toAbsolutePath()));
        }
        boolean success = true;
        try {
            final Path canonicalRoot = root.toAbsolutePath().normalize();
            final Path canonicalDir = dir.toAbsolutePath().normalize();
            LOGGER.trace(() -> LogUtil.message(
                    "tryDelete() - canonicalRoot: '{}', canonicalDir: '{}', oldFileTime: {}",
                    canonicalRoot,
                    canonicalDir,
                    Instant.ofEpochMilli(oldFileTime)));

            // No idea why the length condition?
            if (canonicalRoot.toString().length() > 2 && canonicalDir.startsWith(canonicalRoot)) {
                if (Objects.equals(canonicalRoot, canonicalDir)) {
                    LOGGER.trace("tryDelete() - dir: '{}' is the same as root: '{}'", canonicalRoot, canonicalDir);
                    // We have reached root so silently drop out
                } else if (!Files.exists(dir)) {
                    // It is not there (which is what we want), so just carry on recursing
                    // with its parent
                    final boolean wasRecursionSuccess = tryDeleteDir(
                            root, dir.getParent(), oldFileTime, deleteCountConsumer);
                    if (!wasRecursionSuccess) {
                        success = false;
                    }
                } else if (!Files.isDirectory(dir)) {
                    throw new IllegalArgumentException(LogUtil.message("dir '{}' is not a directory", canonicalDir));
                } else if (!Files.isSameFile(root, dir)) { // handles symlinks
                    final long lastModified = Files.getLastModifiedTime(dir).toMillis();

                    if (lastModified < oldFileTime) {
                        // Delete the dir if it is empty
                        try {
                            try {
                                Files.delete(dir);
                                if (deleteCountConsumer != null) {
                                    deleteCountConsumer.accept(1L);
                                }
                                LOGGER.debug("tryDelete() - Successfully deleted empty directory {}", canonicalDir);
                            } catch (final NoSuchFileException e) {
                                LOGGER.debug("tryDelete() - Directory not found {}", canonicalDir);
                                // It is not there (which is what we want), so just carry on recursing
                                // with its parent
                            }

                            // Recurse backwards towards the root, using parent as next dir
                            final boolean wasRecursionSuccess = tryDeleteDir(
                                    root, dir.getParent(), oldFileTime, deleteCountConsumer);
                            if (!wasRecursionSuccess) {
                                success = false;
                            }
                        } catch (final DirectoryNotEmptyException e) {
                            LOGGER.debug("tryDelete() - Skipping non-empty dir {}", canonicalDir);
                            // This is an expected case, so swallow and drop out gracefully.
                            // Can't delete it so can't recurse.
                        } catch (final IOException e) {
                            // Some other reason for not being able to delete
                            success = false;
                            LOGGER.error(() -> LogUtil.message("tryDelete() - Failed to delete dir {} - {} - {}",
                                    canonicalDir,
                                    e.getClass().getSimpleName(),
                                    e.getMessage()));
                        }
                    } else {
                        LOGGER.debug(() ->
                                LogUtil.message(
                                        "tryDelete() - Skipping dir that was modified too recently " +
                                                "(lastModified: {} >= oldFileTime: {}) {}",
                                        Instant.ofEpochMilli(lastModified),
                                        Instant.ofEpochMilli(oldFileTime),
                                        canonicalDir));
                    }
                } else {
                    // reached the root so drop out quietly
                    LOGGER.trace("tryDelete() - dir: '{}' is the same directory as root: '{}'",
                            canonicalRoot, canonicalDir);
                }
            } else {
                // If it not then this would carry on deleting
                throw new IllegalArgumentException(LogUtil.message("dir '{}', must be an descendant of root '{}'",
                        canonicalDir, canonicalRoot));
            }
        } catch (final IOException e) {
            success = false;
            LOGGER.error("tryDelete() - Failed to delete dir {}", FileUtil.getCanonicalPath(dir), e);
        }
        return success;
    }

    private void info(final Supplier<String> message) {
        try {
            taskContextFactory.current().info(message);
            LOGGER.debug(message);
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }
    }
}
