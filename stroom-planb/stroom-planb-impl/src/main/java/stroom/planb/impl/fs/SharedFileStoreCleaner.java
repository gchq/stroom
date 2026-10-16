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
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Housekeeping job for PlanB-style shard directories on the shared filesystem.
 * Covers all document types that implement {@link SharedFileStoreDocStore}
 * (e.g. {@code PlanBDoc}, {@code TracesDoc}).
 *
 * <p>On each execution:</p>
 * <ol>
 *   <li><b>Orphan detection</b> — scans every {@link PlanBConstants#STAGE_DIR_NAMES} subdirectory of
 *       a shared root for UUID directories with no corresponding live document, and hands each such
 *       UUID to {@link SharedFileStoreTrash}. A grace period prevents
 *       newly-created shard directories from being incorrectly orphaned.</li>
 *   <li><b>Trash drain</b> — recursively deletes everything under
 *       {@code sharedPath/trash/}. Runs after orphan detection so orphans
 *       found in this pass are also drained immediately.</li>
 * </ol>
 *
 * <p>Orphan detection deletes data because a UUID is absent, so it fails closed: if any document store
 * cannot enumerate what is live, or a root's live set comes back empty, nothing is swept that cycle. An
 * incomplete list would otherwise read as a directory full of orphans.</p>
 *
 * <p>Safe to run concurrently across multiple cluster nodes. If a previous
 * execution is still running on this node the new trigger is skipped.</p>
 *
 * <p>On startup, {@link #startup()} removes orphaned batch directories whose name ends
 * {@code .tmp} from the shared {@code processing/} area — the half-written state
 * {@link SharedFileStoreWriter#copyToSharedStore} leaves if the JVM dies before its rename. Only
 * directories older than {@link #ORPHANED_TMP_AGE} are deleted, to avoid racing with a
 * concurrently-starting peer node whose write may have just created one. The {@code .tmp_}-prefixed
 * dirs left by a whole-shard push are a different marker, recovered by
 * {@link SharedFileStorePublisher#recoverOrphaned} instead.
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
     * Minimum age of a {@code .tmp}-suffixed batch directory in the shared store's
     * {@code processing/} area before it is considered an orphan left by a
     * previous JVM crash and eligible for deletion at startup.
     */
    static final Duration ORPHANED_TMP_AGE = Duration.ofMinutes(5);

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
     * Startup lifecycle hook — removes orphaned {@code .tmp}-suffixed batch directories from
     * the shared filesystem's {@code processing/} area.
     *
     * <p>Only directories older than {@link #ORPHANED_TMP_AGE} are removed.
     * This avoids a race with a concurrently-starting peer node whose write
     * may have just created one.
     */
    public void startup() {
        LOGGER.debug("Starting PlanB shared filesystem tmp cleanup");
        final Instant cutoff = Instant.now().minus(ORPHANED_TMP_AGE);

        // Only the set of roots is used here, not document liveness, so a failed enumeration costs a
        // boot's worth of cleanup rather than risking data: a .tmp batch dir carries no .version marker,
        // and SharedFileStoreMergeProcessor.collectBatchDirs skips any batch without one.
        final Optional<Map<Path, Set<String>>> liveUuidsBySharedPath = collectLiveUuids();
        liveUuidsBySharedPath.ifPresent(map -> map.keySet()
                .forEach(sharedRoot -> cleanupOrphanedTmpDirs(sharedRoot, cutoff)));

        LOGGER.debug("Completed PlanB shared filesystem tmp cleanup");
    }

    // Live UUIDs of every registered document store, grouped by shared root. Empty means a store failed
    // to enumerate, so the result cannot be treated as a complete list of live documents.
    private Optional<Map<Path, Set<String>>> collectLiveUuids() {
        final Map<Path, Set<String>> liveUuidsBySharedPath = new HashMap<>();
        for (final SharedFileStoreDocStore source : dataSources) {
            final Map<Path, Set<String>> sourceData;
            try {
                sourceData = source.getLiveSharedPathData();
            } catch (final Exception e) {
                LOGGER.error(() -> "Could not list live documents from "
                                   + source.getClass().getSimpleName()
                                   + ", so this run cannot tell a live document from an orphan and is "
                                   + "abandoned: " + e.getMessage(), e);
                return Optional.empty();
            }
            sourceData.forEach((sharedPath, uuids) ->
                    liveUuidsBySharedPath
                            .computeIfAbsent(sharedPath, k -> new HashSet<>())
                            .addAll(uuids));
        }
        return Optional.of(liveUuidsBySharedPath);
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
                                        LOGGER.debug("Deleting orphaned tmp directory: {}", p);
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
        LOGGER.debug("Starting PlanB shard housekeeping");

        final Optional<Map<Path, Set<String>>> liveUuidsBySharedPath = collectLiveUuids();
        if (liveUuidsBySharedPath.isEmpty()) {
            return;
        }
        if (liveUuidsBySharedPath.get().isEmpty()) {
            LOGGER.debug("No doc configs with a shared path — nothing to housekeep");
            return;
        }

        for (final Map.Entry<Path, Set<String>> entry : liveUuidsBySharedPath.get().entrySet()) {
            housekeepSharedPath(entry.getKey(), entry.getValue());
        }

        LOGGER.debug("Completed PlanB shard housekeeping");
    }

    private void housekeepSharedPath(final Path sharedRoot, final Set<String> liveUuids) {
        LOGGER.debug(() -> "Housekeeping shared path: " + sharedRoot);

        if (liveUuids.isEmpty()) {
            // A root is only in the map because some document reported it, so an empty set means the
            // enumeration lost them. Sweeping now would read every UUID directory under this root as an
            // orphan and trash live data.
            LOGGER.warn(() -> "No live documents reported for " + sharedRoot
                              + ", so orphan detection is skipped for it this run");
        } else {
            // Before the drain, so orphans found now are drained in the same pass.
            findOrphanUuids(sharedRoot, liveUuids)
                    .forEach(uuid -> SharedFileStoreTrash.trashDoc(sharedRoot, uuid));
        }

        drainTrash(sharedRoot);
    }

    // UUIDs with data under sharedRoot that are absent from liveUuids and whose every stage dir predates
    // the grace period. Checked across all of a document's stage dirs, not each alone: a dir touched
    // recently means something is still writing the document, so it keeps all of its data or none.
    private Set<String> findOrphanUuids(final Path sharedRoot, final Set<String> liveUuids) {
        final Instant cutoff = Instant.now().minus(ORPHAN_GRACE_PERIOD);
        final Map<String, Boolean> candidates = new HashMap<>();

        for (final String stage : PlanBConstants.STAGE_DIR_NAMES) {
            final Path scanDir = sharedRoot.resolve(stage);
            if (!Files.isDirectory(scanDir)) {
                continue;
            }
            try (final Stream<Path> stream = Files.list(scanDir)) {
                stream.filter(Files::isDirectory).forEach(uuidDir -> {
                    final String uuid = uuidDir.getFileName().toString();
                    if (liveUuids.contains(uuid)) {
                        return;
                    }
                    candidates.merge(uuid, isOlderThan(uuidDir, cutoff), Boolean::logicalAnd);
                });
            } catch (final IOException e) {
                LOGGER.error(() -> "Error scanning " + scanDir + " for orphans: " + e.getMessage(), e);
            }
        }

        candidates.forEach((uuid, expired) -> {
            if (!expired) {
                LOGGER.debug(() -> "Skipping recently-modified potential orphan "
                                   + "(within grace period): " + uuid);
            }
        });

        return candidates.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
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
                LOGGER.debug("Draining trash entry: {}", entry);
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
