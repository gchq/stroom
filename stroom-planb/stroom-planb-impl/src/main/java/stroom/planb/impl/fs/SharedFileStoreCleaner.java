/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.planb.impl.fs;

import stroom.planb.impl.PlanBConstants;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Housekeeping job for PlanB-style shard directories on the shared filesystem.
 * Covers all document types that implement {@link SharedFileStoreDocStore}
 * (e.g. {@code PlanBDoc}, {@code TracesDoc}).
 *
 * <p>On each execution:</p>
 * <ol>
 *   <li><b>Orphan detection</b> — scans {@code sharedPath/shards/} and
 *       {@code sharedPath/processing/} for UUID directories with no
 *       corresponding live document, and renames them into
 *       {@code sharedPath/trash/} (atomic, fast). A grace period prevents
 *       newly-created shard directories from being incorrectly orphaned.</li>
 *   <li><b>Trash drain</b> — recursively deletes everything under
 *       {@code sharedPath/trash/}. Runs after orphan detection so orphans
 *       found in this pass are also drained immediately.</li>
 * </ol>
 *
 * <p>Safe to run concurrently across multiple cluster nodes. If a previous
 * execution is still running on this node the new trigger is skipped.</p>
 *
 * <p>On startup, {@link #startup()} removes orphaned {@code .tmp_} directories
 * from the shared {@code processing/} area that were left by a previous JVM
 * crash. Only directories older than {@link #ORPHANED_TMP_AGE} are deleted, to
 * avoid racing with a concurrently-starting peer node whose push may have just
 * created a {@code .tmp_} directory.
 */
@Singleton
public class SharedFileStoreCleaner {

    private static final LambdaLogger LOGGER =
            LambdaLoggerFactory.getLogger(SharedFileStoreCleaner.class);

    /**
     * Shard directories modified more recently than this threshold are not
     * considered orphans, protecting against the race between shard-directory
     * creation and doc-store write during new document creation.
     */
    static final Duration ORPHAN_GRACE_PERIOD = Duration.ofHours(1);

    /**
     * Minimum age of a {@code .tmp_} directory in the shared store's
     * {@code processing/} area before it is considered an orphan left by a
     * previous JVM crash and eligible for deletion at startup.
     */
    static final Duration ORPHANED_TMP_AGE = Duration.ofMinutes(5);

    private static final List<String> SHARD_SUBDIRS =
            List.of(PlanBConstants.SHARDS_DIR_NAME, PlanBConstants.PROCESSING_DIR_NAME);

    private final Set<SharedFileStoreDocStore> dataSources;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Inject
    public SharedFileStoreCleaner(final Set<SharedFileStoreDocStore> dataSources) {
        this.dataSources = dataSources;
    }

    public void exec() {
        if (!running.compareAndSet(false, true)) {
            LOGGER.debug("PlanB shard housekeeping already running on this node — skipping trigger");
            return;
        }
        try {
            housekeep();
        } finally {
            running.set(false);
        }
    }

    /**
     * Startup lifecycle hook — removes orphaned {@code .tmp_} directories from
     * the shared filesystem's {@code processing/} area.
     *
     * <p>Only directories older than {@link #ORPHANED_TMP_AGE} are removed.
     * This avoids a race with a concurrently-starting peer node whose push
     * operation may have just created a {@code .tmp_} directory.
     */
    public void startup() {
        LOGGER.info("Starting PlanB shared filesystem tmp cleanup");
        final Instant cutoff = Instant.now().minus(ORPHANED_TMP_AGE);

        final Map<Path, Set<String>> liveUuidsBySharedPath = new HashMap<>();
        for (final SharedFileStoreDocStore source : dataSources) {
            source.getLiveSharedPathData().forEach((sharedPath, uuids) ->
                    liveUuidsBySharedPath
                            .computeIfAbsent(sharedPath, k -> new java.util.HashSet<>())
                            .addAll(uuids));
        }

        for (final Path sharedRoot : liveUuidsBySharedPath.keySet()) {
            cleanupOrphanedTmpDirs(sharedRoot, cutoff);
        }

        LOGGER.info("Completed PlanB shared filesystem tmp cleanup");
    }

    private void cleanupOrphanedTmpDirs(final Path sharedRoot, final Instant cutoff) {
        final Path processingDir = sharedRoot.resolve(PlanBConstants.PROCESSING_DIR_NAME);
        if (!Files.isDirectory(processingDir)) {
            return;
        }
        try (final Stream<Path> docStream = Files.list(processingDir)) {
            docStream.filter(Files::isDirectory).forEach(docDir -> {
                try (final Stream<Path> shardStream = Files.list(docDir)) {
                    shardStream.filter(Files::isDirectory).forEach(shardDir -> {
                        try (final Stream<Path> batchStream = Files.list(shardDir)) {
                            batchStream
                                    .filter(p -> p.getFileName().toString()
                                            .endsWith(PlanBConstants.TMP_DIR_SUFFIX))
                                    .filter(p -> isOlderThan(p, cutoff))
                                    .forEach(p -> {
                                        LOGGER.info("Deleting orphaned tmp directory: {}", p);
                                        FileUtil.deleteDir(p);
                                    });
                        } catch (final IOException e) {
                            LOGGER.warn("Failed to list batch dirs in {}: {}", shardDir, e.getMessage());
                        }
                    });
                } catch (final IOException e) {
                    LOGGER.warn("Failed to list shard dirs in {}: {}", docDir, e.getMessage());
                }
            });
        } catch (final IOException e) {
            LOGGER.error("Error cleaning up tmp dirs in {}: {}", processingDir, e.getMessage(), e);
        }
    }

    private void housekeep() {
        LOGGER.info("Starting PlanB shard housekeeping");

        // Merge live UUID sets across all registered doc-type stores,
        // grouping by shared filesystem root path.
        final Map<Path, Set<String>> liveUuidsBySharedPath = new HashMap<>();
        for (final SharedFileStoreDocStore source : dataSources) {
            source.getLiveSharedPathData().forEach((sharedPath, uuids) ->
                    liveUuidsBySharedPath
                            .computeIfAbsent(sharedPath, k -> new java.util.HashSet<>())
                            .addAll(uuids));
        }

        if (liveUuidsBySharedPath.isEmpty()) {
            LOGGER.debug("No doc configs with a shared path — nothing to housekeep");
            return;
        }

        for (final Map.Entry<Path, Set<String>> entry : liveUuidsBySharedPath.entrySet()) {
            housekeepSharedPath(entry.getKey(), entry.getValue());
        }

        LOGGER.info("Completed PlanB shard housekeeping");
    }

    private void housekeepSharedPath(final Path sharedRoot, final Set<String> liveUuids) {
        LOGGER.debug(() -> "Housekeeping shared path: " + sharedRoot);

        // Step 1: orphan detection — before drain so newly-detected orphans
        // are included in the same drain pass.
        for (final String subdir : SHARD_SUBDIRS) {
            detectOrphans(sharedRoot, subdir, liveUuids);
        }

        // Step 2: drain trash.
        drainTrash(sharedRoot);
    }

    /**
     * Scans {@code sharedRoot/<subdir>/} for UUID directories absent from
     * {@code liveUuids} whose last-modified time is older than
     * {@link #ORPHAN_GRACE_PERIOD}, and renames them into
     * {@code sharedRoot/trash/}.
     */
    private void detectOrphans(final Path sharedRoot,
                                final String subdir,
                                final Set<String> liveUuids) {
        final Path scanDir = sharedRoot.resolve(subdir);
        if (!Files.isDirectory(scanDir)) {
            return;
        }

        final Instant cutoff = Instant.now().minus(ORPHAN_GRACE_PERIOD);

        try (final var stream = Files.list(scanDir)) {
            stream.forEach(uuidDir -> {
                final String uuid = uuidDir.getFileName().toString();
                if (liveUuids.contains(uuid)) {
                    return;
                }
                if (!isOlderThan(uuidDir, cutoff)) {
                    LOGGER.debug(() -> "Skipping recently-modified potential orphan "
                            + "(within grace period): " + uuidDir);
                    return;
                }
                trashDir(sharedRoot, uuidDir, uuid, subdir);
            });
        } catch (final IOException e) {
            LOGGER.error(() -> "Error scanning " + scanDir + " for orphans: " + e.getMessage(), e);
        }
    }

    private void trashDir(final Path sharedRoot,
                          final Path src,
                          final String uuid,
                          final String subdir) {
        final String trashEntryName = uuid + "-" + System.currentTimeMillis();
        final Path dest = sharedRoot
                .resolve(PlanBConstants.TRASH_DIR_NAME)
                .resolve(trashEntryName)
                .resolve(subdir);
        try {
            Files.createDirectories(dest.getParent());
            Files.move(src, dest, StandardCopyOption.ATOMIC_MOVE);
            LOGGER.info("Moved orphaned shard directory to trash: {} -> {}", src, dest);
        } catch (final NoSuchFileException e) {
            // Another node already moved it — safe to ignore.
            LOGGER.debug(() -> "Orphan already moved by another node: " + src);
        } catch (final IOException e) {
            LOGGER.warn(() -> "Could not move orphan to trash: " + src + " — " + e.getMessage());
        }
    }

    /**
     * Recursively deletes all entries under {@code sharedRoot/trash/}.
     */
    private void drainTrash(final Path sharedRoot) {
        final Path trashDir = sharedRoot.resolve(PlanBConstants.TRASH_DIR_NAME);
        if (!Files.isDirectory(trashDir)) {
            return;
        }

        try (final var stream = Files.list(trashDir)) {
            stream.forEach(entry -> {
                LOGGER.info("Draining trash entry: {}", entry);
                if (!FileUtil.deleteDir(entry)) {
                    LOGGER.warn(() -> "Could not fully delete trash entry: " + entry);
                }
            });
        } catch (final IOException e) {
            LOGGER.error(() -> "Error listing trash directory " + trashDir + ": " + e.getMessage(), e);
        }
    }

    private boolean isOlderThan(final Path path, final Instant cutoff) {
        try {
            final FileTime lastModified = Files.getLastModifiedTime(path);
            return lastModified.toInstant().isBefore(cutoff);
        } catch (final IOException e) {
            // Can't read timestamp — err on the side of caution and do not orphan.
            LOGGER.debug(() -> "Could not read last-modified time for "
                    + path + " — treating as recent");
            return false;
        }
    }
}
