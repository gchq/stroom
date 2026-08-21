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

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.node.api.NodeInfo;
import stroom.planb.impl.PlanBConstants;
import stroom.planb.impl.PlanBPaths;
import stroom.planb.impl.db.Db;
import stroom.planb.impl.db.PlanBDb;
import stroom.planb.shared.PlanBDocument;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Handles the crash-safe publish protocol for writing a merged shard back to
 * the shared file store.
 *
 * <p>For a live shard ({@link #push}) the sequence is:
 * <ol>
 *   <li>Copy the local shard files into a fresh {@code .tmp_} directory.</li>
 *   <li>Write the {@code .version} marker into that directory so it swaps in atomically with the data;
 *       a reader observing {@code .version} can therefore trust the shard is fully written.</li>
 *   <li>Atomic rename-swap: live to {@code .old_}, temp to live, delete old.</li>
 * </ol>
 *
 * <p>{@link #recoverOrphaned} undoes the partial state left by an interrupted
 * push and must be called at the start of each lock cycle, before the shard is
 * opened, so that {@code syncFromSharedStoreIfRequired} sees a consistent
 * directory. Note it only ever scans the directory it is handed, which is the tree a store type
 * publishes whole shards to — never {@code archive/}, which is why {@link #pushArchive} uses a
 * different, recovery-free protocol.
 *
 * <p>Two invariants hold for everything here: no LMDB env is ever opened on the shared mount (data is
 * only copied to and from it, with all LMDB work done locally), and an archive bucket dir is never
 * renamed away, so a bucket can never transiently disappear from queries.
 */
@Singleton
public class SharedFileStorePublisher {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SharedFileStorePublisher.class);

    private final NodeInfo nodeInfo;
    private final ByteBuffers byteBuffers;
    private final ByteBufferFactory byteBufferFactory;
    private final Path archiveLocalDir;

    @Inject
    public SharedFileStorePublisher(final NodeInfo nodeInfo,
                                    final ByteBuffers byteBuffers,
                                    final ByteBufferFactory byteBufferFactory,
                                    final PlanBPaths planBPaths) {
        this.nodeInfo = nodeInfo;
        this.byteBuffers = byteBuffers;
        this.byteBufferFactory = byteBufferFactory;

        // Clear any local archive dirs left by a previous JVM crash; each push removes its own on the way
        // out, so anything present at startup is dead. Mirrors MergeProcessor's treatment of
        // mergingDir/unzipDir.
        archiveLocalDir = planBPaths.getArchiveLocalDir();
        FileUtil.ensureDirExists(archiveLocalDir);
        if (!FileUtil.deleteContents(archiveLocalDir)) {
            throw new RuntimeException(
                    "Unable to delete contents of: " + FileUtil.getCanonicalPath(archiveLocalDir));
        }
    }

    /**
     * Copies the local shard into {@code sharedDocDir/<shardIndex>} using an atomic rename-swap,
     * carrying forward any operational (non-system) files from the existing shared shard directory.
     *
     * <p>Takes the destination rather than deriving it, as {@link #recoverOrphaned} does, so which
     * tree a store type publishes a whole shard to stays that store type's business.
     */
    public void push(final Path localShardDir,
              final Path sharedDocDir,
              final int shardIndex) throws IOException {
        final Path sharedShardDir = sharedDocDir.resolve(PlanBConstants.formatShardIndex(shardIndex));

        // Named from the canonical dir's own file name, not from shardIndex — recoverOrphaned matches on
        // the same derivation, and formatting the index independently at each site is how the two came to
        // disagree before.
        final Path sharedTempDir = sharedShardDir.resolveSibling(
                PlanBConstants.TMP_DIR_PREFIX + sharedShardDir.getFileName() + "_"
                        + System.currentTimeMillis() + "_" + UUID.randomUUID());
        Files.createDirectories(sharedTempDir);

        // Carry forward operational files that are not regenerated by the push.
        if (Files.exists(sharedShardDir)) {
            try (final Stream<Path> existing = Files.list(sharedShardDir)) {
                existing.filter(Files::isRegularFile)
                        .filter(p -> !isSystemFile(p.getFileName().toString()))
                        .forEach(p -> {
                            try {
                                Files.copy(p, sharedTempDir.resolve(p.getFileName()),
                                        StandardCopyOption.REPLACE_EXISTING);
                            } catch (final IOException e) {
                                LOGGER.warn("Could not carry forward operational file {} during push: {}",
                                        p, e.getMessage());
                            }
                        });
            }
        }

        // Copy the LMDB data file from the local shard.
        copyIfExists(localShardDir.resolve(PlanBConstants.DATA_FILE_NAME),
                sharedTempDir.resolve(PlanBConstants.DATA_FILE_NAME));

        // Write the version marker INTO the temp dir so it swaps into the live shard atomically with
        // data.mdb (as pushArchive does). Its presence then already implies a fully written shard, so
        // there is no post-swap window in which the live shard exists without a .version for a lock-free
        // reader to trip over, and no separate .complete sentinel is needed here (nothing reads one
        // in the tree whole shards are published to).
        final String newVersion = Instant.now().toEpochMilli() + "_" + nodeInfo.getThisNodeName();
        Files.writeString(sharedTempDir.resolve(PlanBConstants.VERSION_FILE_NAME), newVersion);

        // Atomic rename-swap: live -> old, temp -> live, delete old.
        pushDir(sharedTempDir, sharedShardDir);
    }

    /**
     * Pushes a locally-produced archive shard to the shared file store. Unlike {@link #push} this does
     * <em>not</em> use the {@link #pushDir} rename-swap; see below for why.
     *
     * <p>If an archive shard already exists for this date (repeated archival runs,
     * or late-arriving data), the new batch is <em>merged</em> into the existing
     * bucket at the LMDB level. A raw file copy cannot merge two LMDB files — it
     * would overwrite the existing bucket and silently drop everything archived by
     * earlier runs for the same date, which is data loss.
     *
     * <p><b>No LMDB env is ever opened on the shared mount.</b> To merge, the existing bucket's
     * {@code data.mdb} is copied <em>down</em> to a local dir, the merge runs against that
     * local copy, and only the finished file is copied back up. The shared store therefore sees
     * whole-file copies and renames only.
     *
     * <p><b>The bucket dir is never renamed away.</b> Publication copies the finished file up under a
     * unique {@link PlanBConstants#DATA_TMP_FILE_NAME}-prefixed name and renames it to {@code data.mdb}
     * <em>within</em> the live bucket dir. Unlike {@link #pushDir}'s live→{@code .old_}→temp→live swap
     * there is no instant
     * at which the bucket is absent — which matters because {@link #recoverOrphaned} is never called
     * for the {@code archive/} tree, so an interrupted swap here would leave the bucket's
     * {@code .old_} copy orphaned and the whole date bucket permanently invisible to queries.
     * The worst case here is instead a new {@code data.mdb} with an older {@code .version}, which
     * readers already tolerate: they re-sync when the version later changes.
     */
    public void pushArchive(final PlanBDocument doc,
                     final int shardIndex,
                     final StagedArchive archiveShard) throws IOException {
        final Path archiveShardDir = Path.of(doc.getSharedPath())
                .resolve(PlanBConstants.ARCHIVE_DIR_NAME)
                .resolve(doc.getUuid())
                .resolve(PlanBConstants.formatShardIndex(shardIndex))
                .resolve(archiveShard.dateLabel());

        final String uid = System.currentTimeMillis() + "_" + UUID.randomUUID();
        final Path localDir = archiveLocalDir.resolve(
                "merge_" + doc.getUuid() + "_" + PlanBConstants.formatShardIndex(shardIndex)
                        + "_" + archiveShard.dateLabel() + "_" + uid);
        Files.createDirectories(localDir);

        try {
            // Throw rather than return: the staged records have already been deleted from the local shard,
            // so reporting this bucket as pushed would lose them. Failing here makes mergeShard skip the
            // publish and discard that shard, leaving the shared store's copy to be retried next cycle.
            if (!Files.exists(archiveShard.localDir().resolve(PlanBConstants.DATA_FILE_NAME))) {
                throw new IOException("Staged archive " + archiveShard.localDir()
                                      + " has no data file, so its records cannot be pushed");
            }

            final Path existingData = archiveShardDir.resolve(PlanBConstants.DATA_FILE_NAME);
            final Path localData = localDir.resolve(PlanBConstants.DATA_FILE_NAME);
            if (Files.exists(archiveShardDir.resolve(PlanBConstants.VERSION_FILE_NAME))
                    && Files.exists(existingData)) {
                // Seed the local env with the existing bucket, copied DOWN, so the new batch unions
                // with it rather than replacing it.
                LOGGER.info("Merging new archive batch into existing archive shard {}", archiveShardDir);
                Files.copy(existingData, localData, StandardCopyOption.REPLACE_EXISTING);
            }

            // Always merge, even into an empty local env for a brand-new bucket, rather than copying the
            // staged batch up verbatim. The batch carries raw records only — anything the store derives
            // from them, including its sort indexes, is not carried — so publishing it as-is would leave a
            // bucket no query could search, and without the index DBIs a read-only query open needs.
            try (final Db<?, ?> db = PlanBDb.open(
                    doc, localDir, byteBuffers, byteBufferFactory, false, true)) {
                db.merge(archiveShard.localDir());
                // Lets the bucket rebuild its own derived state from the records it now holds, rather
                // than inheriting whatever the batch happened to carry. merge() maintains the per-record
                // stats this needs and queues every record it touched.
                db.mergeComplete();
            }
            // Keep the archive layout as data.mdb + .version only: drop the lock file LMDB created
            // locally during the merge (it is recreated on the next open).
            Files.deleteIfExists(localDir.resolve(PlanBConstants.LOCK_FILE_NAME));

            publishBucketData(localData, archiveShardDir, uid);
        } finally {
            // Swallow cleanup failures so they cannot mask an in-flight exception from the push. A
            // leftover local dir is harmless: the next startup clears the whole local archive root.
            try {
                FileUtil.deleteDir(localDir);
            } catch (final Exception e) {
                LOGGER.warn("Failed to clean up local archive dir {}: {}", localDir, e.getMessage());
            }
        }
    }

    // Copies up under a temp name, renames within the live bucket dir, then bumps .version — so the dir
    // is never renamed away and the bucket cannot transiently vanish.
    //
    // .version goes last because ArchiveShardLocator treats its presence as "bucket complete", so a bucket
    // stays invisible until its data is in place; writing it first would advertise data that never arrived.
    // Not covered: a crash between the two on a bucket's first push leaves data.mdb unversioned, so it is
    // invisible and the next run overwrites it rather than merging into it.
    private static void publishBucketData(final Path localData,
                                          final Path archiveShardDir,
                                          final String version) throws IOException {
        // As in pushArchive: the rows this would have carried are already gone locally, so a silent return
        // would drop them. Fail so the merged shard is not published and the next cycle retries.
        if (!Files.exists(localData)) {
            throw new IOException("No data file to publish for archive shard " + archiveShardDir);
        }
        Files.createDirectories(archiveShardDir);
        deleteOrphanedTempData(archiveShardDir);

        // Unique per push: the merge cluster lock already serialises pushes to a given bucket, but a
        // collision here would corrupt the published file rather than merely retry.
        final Path tmpData = archiveShardDir.resolve(PlanBConstants.DATA_TMP_FILE_NAME + "_" + version);
        try {
            Files.copy(localData, tmpData, StandardCopyOption.REPLACE_EXISTING);
            moveWithAtomicFallback(tmpData, archiveShardDir.resolve(PlanBConstants.DATA_FILE_NAME));
            Files.writeString(archiveShardDir.resolve(PlanBConstants.VERSION_FILE_NAME), version);
        } finally {
            // A failed copy/rename must not leave a partial temp file behind.
            Files.deleteIfExists(tmpData);
        }
    }

    // A JVM kill between the copy up and the rename orphans a bucket-sized temp file here, and nothing
    // else would ever clean it: recoverOrphaned is never called for the archive/ tree, and
    // SharedFileStoreCleaner sweeps only holding/ and processing/. Sweeping on the next push keeps
    // that self-healing.
    private static void deleteOrphanedTempData(final Path archiveShardDir) throws IOException {
        try (final Stream<Path> files = Files.list(archiveShardDir)) {
            files.filter(p -> p.getFileName().toString().startsWith(PlanBConstants.DATA_TMP_FILE_NAME))
                    .forEach(p -> {
                        LOGGER.warn("Deleting orphaned archive temp data file: {}", p);
                        try {
                            Files.deleteIfExists(p);
                        } catch (final IOException e) {
                            LOGGER.warn("Could not delete orphaned archive temp data file {}: {}",
                                    p, e.getMessage());
                        }
                    });
        }
    }

    /**
     * Crash-safe rename-swap: atomically swaps the pre-populated {@code localDir}
     * into {@code sharedTargetDir}.
     */
    private void pushDir(final Path localDir, final Path sharedTargetDir) throws IOException {
        final String uid = System.currentTimeMillis() + "_" + UUID.randomUUID();
        final Path oldDir = sharedTargetDir.resolveSibling(
                PlanBConstants.OLD_DIR_PREFIX + sharedTargetDir.getFileName() + "_" + uid);
        if (Files.exists(sharedTargetDir)) {
            moveWithAtomicFallback(sharedTargetDir, oldDir);
        }
        moveWithAtomicFallback(localDir, sharedTargetDir);
        if (Files.exists(oldDir)) {
            FileUtil.deleteDir(oldDir);
        }
    }

    /**
     * Recovers from an interrupted {@link #push} by scanning the shard parent
     * directory for orphaned {@code .tmp_} and {@code .old_} directories.
     *
     * <p>Both markers carry the canonical shard dir's own zero-padded name, e.g. {@code .old_0001_*} for
     * shard 1, so the prefixes are built from that name rather than from {@code shardIndex}.
     *
     * <ul>
     *   <li>{@code .tmp_0001_*} -- partial push; canonical shard untouched. Delete.</li>
     *   <li>{@code .old_0001_*} with canonical dir present -- push completed. Delete old.</li>
     *   <li>{@code .old_0001_*} with canonical dir absent -- push failed mid-swap.
     *       Restore the old dir as the canonical shard.</li>
     * </ul>
     *
     * Must be called inside the shard cluster lock, before the shard is opened.
     */
    public void recoverOrphaned(final Path sharedHoldingDocDir, final int shardIndex) {
        if (!Files.exists(sharedHoldingDocDir)) {
            return;
        }
        final Path canonicalShardDir = sharedHoldingDocDir.resolve(
                PlanBConstants.formatShardIndex(shardIndex));
        final String tmpPrefix = PlanBConstants.TMP_DIR_PREFIX + canonicalShardDir.getFileName() + "_";
        final String oldPrefix = PlanBConstants.OLD_DIR_PREFIX + canonicalShardDir.getFileName() + "_";

        try (final Stream<Path> siblings = Files.list(sharedHoldingDocDir)) {
            siblings.forEach(sibling -> {
                final String name = sibling.getFileName().toString();
                if (name.startsWith(tmpPrefix)) {
                    LOGGER.warn("Deleting orphaned push temp dir: {}", sibling);
                    FileUtil.deleteDir(sibling);
                } else if (name.startsWith(oldPrefix)) {
                    if (Files.exists(canonicalShardDir)) {
                        LOGGER.warn("Deleting orphaned push old dir (push completed): {}", sibling);
                        FileUtil.deleteDir(sibling);
                    } else {
                        LOGGER.warn("Restoring shard from orphaned push old dir: {} -> {}",
                                sibling, canonicalShardDir);
                        try {
                            moveWithAtomicFallback(sibling, canonicalShardDir);
                        } catch (final IOException e) {
                            LOGGER.error("Failed to restore shard from orphaned old dir: {}", sibling, e);
                        }
                    }
                }
            });
        } catch (final IOException e) {
            LOGGER.error("Error scanning for orphaned push dirs in {}", sharedHoldingDocDir, e);
        }
    }

    private static void copyIfExists(final Path src, final Path dst) throws IOException {
        if (Files.exists(src)) {
            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void moveWithAtomicFallback(final Path src, final Path dst) throws IOException {
        try {
            Files.move(src, dst, StandardCopyOption.ATOMIC_MOVE);
        } catch (final AtomicMoveNotSupportedException e) {
            LOGGER.warn("Atomic move not supported, falling back to REPLACE_EXISTING: {} -> {}", src, dst);
            Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Returns {@code true} for files that are always regenerated during a push
     * and must not be carried forward from the previous shard directory.
     */
    private static boolean isSystemFile(final String name) {
        return name.equals(PlanBConstants.DATA_FILE_NAME)
                || name.equals(PlanBConstants.LOCK_FILE_NAME)
                || name.equals(PlanBConstants.VERSION_FILE_NAME);
    }
}
