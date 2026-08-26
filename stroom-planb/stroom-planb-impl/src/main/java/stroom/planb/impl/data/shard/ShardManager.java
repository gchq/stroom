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

package stroom.planb.impl.data.shard;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentActionHandler;
import stroom.docstore.api.DocumentNotFoundException;
import stroom.docstore.api.DocumentTypeName;
import stroom.node.api.NodeInfo;
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.PlanBConstants;
import stroom.planb.impl.PlanBDocCache;
import stroom.planb.impl.PlanBDocStore;
import stroom.planb.impl.PlanBPaths;
import stroom.planb.impl.dao.Db;
import stroom.planb.impl.dao.PlanBDb;
import stroom.planb.impl.data.SnapshotNotFoundException;
import stroom.planb.impl.data.archive.ArchiveShardRef;
import stroom.planb.impl.data.shard.SnapshotShard.DbFactory;
import stroom.planb.impl.fs.ArchiveStoreShard;
import stroom.planb.impl.rest.FileTransferClient;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.PlanBDocument;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.concurrent.StripedLock;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.NotImplementedException;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.Lock;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Singleton
public class ShardManager {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ShardManager.class);

    public static final String SNAPSHOT_CREATOR_TASK_NAME = "Plan B Snapshot Creator";
    public static final String SNAPSHOT_CLEANUP_TASK_NAME = "Plan B Snapshot Cleanup";

    private static final DbFactory DB_FACTORY = PlanBDb::open;
    private static final String CLOSED_MESSAGE = "Plan B shard manager is closed";

    private final ByteBuffers byteBuffers;
    private final ByteBufferFactory byteBufferFactory;
    private final PlanBDocCache planBDocCache;
    private final PlanBDocStore planBDocStore;
    private final Provider<Map<DocumentTypeName, DocumentActionHandler>> documentActionHandlersProvider;
    private final Map<String, Shard> shardMap = new ConcurrentHashMap<>();
    // Set permanently by closeAll(); stops any new shard (and so any new env) being created.
    private volatile boolean closed;
    // Serialises creation of a given shard without holding a lock on the shard map while we do it.
    private final StripedLock creationLocks = new StripedLock();
    // Cached read-only local copies of shared-store archive buckets, keyed uuid_<idx>_<dateLabel>.
    private final Map<String, Shard> archiveShardMap = new ConcurrentHashMap<>();
    private final NodeInfo nodeInfo;
    private final Provider<PlanBConfig> configProvider;
    private final PlanBPaths planBPaths;
    private final FileTransferClient fileTransferClient;
    private final TaskContextFactory taskContextFactory;
    private final Executor executor;

    @Inject
    public ShardManager(final ByteBuffers byteBuffers,
                        final ByteBufferFactory byteBufferFactory,
                        final PlanBDocCache planBDocCache,
                        final PlanBDocStore planBDocStore,
                        final NodeInfo nodeInfo,
                        final Provider<PlanBConfig> configProvider,
                        final PlanBPaths planBPaths,
                        final FileTransferClient fileTransferClient,
                        final TaskContextFactory taskContextFactory,
                        final ExecutorProvider executorProvider,
                        final Provider<Map<DocumentTypeName, DocumentActionHandler>> documentActionHandlersProvider) {
        this.byteBuffers = byteBuffers;
        this.byteBufferFactory = byteBufferFactory;
        this.planBDocCache = planBDocCache;
        this.planBDocStore = planBDocStore;
        this.documentActionHandlersProvider = documentActionHandlersProvider;
        this.nodeInfo = nodeInfo;
        this.configProvider = configProvider;
        this.planBPaths = planBPaths;
        this.fileTransferClient = fileTransferClient;
        this.taskContextFactory = taskContextFactory;
        this.executor = executorProvider.get();

        // Delete any snapshots fetched from other nodes that might have been left behind from the last use of
        // Stroom.
        deleteFetchedSnapshots(planBPaths.getSnapshotDir());

        // Reap any shared-file-store shard generation dirs left behind by a previous run (nothing is
        // serving yet, so all are orphans); they are re-synced from the shared store on next access.
        sweepOrphanGenerationDirs(true);
    }

    /**
     * Delete snapshots that this node previously fetched from the nodes that store the shards.
     * <p>
     * Fetched snapshots are unpacked by {@link SnapshotShard} into a dir per fetch,
     * i.e. {@code snapshots/<doc uuid>/<fetch time>/}, so only sub dirs are deleted here. A node that stores
     * shards publishes its snapshot as a file, i.e. {@code snapshots/<doc uuid>/snapshot.zip}, and deleting that
     * would leave the shard with nothing to serve until a new snapshot was created, which may never happen if the
     * shard receives no further writes. See gh-5689.
     */
    // Package private for testing.
    static void deleteFetchedSnapshots(final Path snapshotDir) {
        if (!Files.isDirectory(snapshotDir)) {
            return;
        }

        try (final Stream<Path> docDirs = Files.list(snapshotDir)) {
            docDirs.filter(Files::isDirectory).forEach(docDir -> {
                try (final Stream<Path> fetchDirs = Files.list(docDir)) {
                    fetchDirs.filter(Files::isDirectory).forEach(FileUtil::deleteDir);
                } catch (final IOException e) {
                    LOGGER.error(() -> LogUtil.message("Error deleting fetched snapshots in '{}': {}",
                            FileUtil.getCanonicalPath(docDir), e.getMessage()), e);
                }
            });
        } catch (final IOException e) {
            LOGGER.error(() -> LogUtil.message("Error listing snapshot dir '{}': {}",
                    FileUtil.getCanonicalPath(snapshotDir), e.getMessage()), e);
        }
    }

    public boolean isSnapshotNode() {
        try {
            final List<String> nodes = NullSafe.list(configProvider.get().getNodeList());
            // If we have no node info or no nodes are configured then treat this as a shard writer node and not a
            // snapshot node.
            return nodeInfo != null && !nodes.isEmpty() && !nodes.contains(nodeInfo.getThisNodeName());
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }
    }

    public void condenseAll(final TaskContext parentTaskContext) {
        try {
            final List<CompletableFuture<Void>> futures = new ArrayList<>();
            shardMap.values().forEach(shard -> {
                final PlanBDocument doc = shard.getDoc();
                // Never taken today: createShard rejects a shared-file-store doc, so shardMap cannot
                // hold one. Those shards are maintained by the merge cycle under a cluster lock.
                if (doc.getSharedPath() != null && doc.getShardCount() > 0) {
                    return;
                }
                final Runnable runnable = taskContextFactory
                        .childContext(parentTaskContext, "Maintain shard: " + doc.getName(), taskContext -> {
                            try {
                                // By UUID, not name: a shard holds the doc it was created with, so a
                                // renamed doc would look deleted and have its shard destroyed.
                                final PlanBDocument loaded = readPlanBDoc(doc.getUuid());
                                if (loaded == null) {
                                    // The doc has been deleted, so delete the shard with it.
                                    if (shard.delete()) {
                                        // Two arg remove so we can only ever evict the shard we just
                                        // deleted, never a replacement published since we read it.
                                        shardMap.remove(doc.getUuid(), shard);
                                    }
                                } else {
                                    long total = 0;
                                    taskContext.info(() -> "Condensing data");
                                    total += shard.condense(loaded);
                                    taskContext.info(() -> "Deleting old data");
                                    total += shard.runRetention(loaded);
                                    if (total > 0) {
                                        // If we removed data then compact the shard.
                                        taskContext.info(() -> "Compacting shard");
                                        shard.compact();
                                    }
                                }
                            } catch (final Exception e) {
                                LOGGER.error(() -> LogUtil.message("Error condensing: {} {}",
                                        doc.getName(), e.getMessage()), e);
                            }
                        });

                futures.add(CompletableFuture.runAsync(runnable, executor));
            });
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }
    }

    /**
     * Delete old merge status records from additive store shards. Only shards whose doc passes the
     * quiescence test are pruned: a status record may only be deleted once no replayable copy of any merge
     * source can still exist for that doc. See docs/merge-idempotency-design.md.
     */
    public void deleteOldMergeStatus(final Predicate<String> docUuidQuiescent,
                                    final Instant deleteBefore) {
        shardMap.forEach((docUuid, shard) -> {
            try {
                if (docUuidQuiescent.test(docUuid)) {
                    final long count = shard.deleteOldMergeStatus(deleteBefore);
                    if (count > 0) {
                        LOGGER.info(() -> LogUtil.message("Deleted {} old merge status records for: {}",
                                count, NullSafe.get(shard.getDoc(), PlanBDocument::getName)));
                    }
                }
            } catch (final UncheckedInterruptedException e) {
                // We are being terminated, e.g. at shutdown. Stop rather than churn through the remaining
                // shards logging an error for each.
                LOGGER.debug(e::getMessage, e);
                throw e;
            } catch (final Exception e) {
                LOGGER.error(e::getMessage, e);
            }
        });
    }

    public void compactAll() {
        try {
            shardMap.values().forEach(shard -> {
                try {
                    final PlanBDocument doc = shard.getDoc();
                    // By UUID, not name — see condenseAll.
                    if (readPlanBDoc(doc.getUuid()) == null) {
                        // The doc has been deleted, so delete the shard with it.
                        if (shard.delete()) {
                            // See condenseAll.
                            shardMap.remove(doc.getUuid(), shard);
                        }
                    } else {
                        shard.compact();
                    }
                } catch (final Exception e) {
                    LOGGER.error(e::getMessage, e);
                }
            });
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }
    }

    public void checkSnapshotStatus(final SnapshotRequest request) {
        try {
            final Shard shard = getShardForDocUuid(request.getPlanBDocRef().getUuid());
            if (shard instanceof final SnapshotCapable snapshotCapable) {
                snapshotCapable.checkSnapshotStatus(request);
            }
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Debug checking snapshot status: {} {}",
                    request.getPlanBDocRef(), e.getMessage()), e);
            throw e;
        }
    }

    public void createSnapshots() {
        try {
            final List<CompletableFuture<Void>> futures = new ArrayList<>();
            shardMap.values().forEach(shard -> {
                if (shard instanceof final SnapshotCapable snapshotCapable) {
                    futures.add(CompletableFuture.runAsync(snapshotCapable::createSnapshot, executor));
                }
            });
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }
    }

    public void cleanup() {
        cleanupMap(shardMap);
        cleanupMap(archiveShardMap);

        // Reap generation dirs left by crashes / deferred deletes (no live owner).
        sweepOrphanGenerationDirs(false);

        deleteOrphanedDirs();
    }

    /**
     * Deletes shard and snapshot dirs that no shard in the map owns and whose doc has definitely
     * been deleted. The map driven loop above only reaches shards this process has OPENED, and a
     * shard whose doc has gone can never be opened again: nothing routes merges to it and no query
     * can resolve its name. Without this its dir is held for the life of the deployment.
     * <p>
     * Only flat {@code <uuid>} dirs are considered. Generation dirs, {@code <uuid>_<idx>}, belong to
     * {@link #sweepOrphanGenerationDirs}, which keys them on live map entries; reading their name as
     * a doc uuid here would find no doc and delete a live shard's local copy.
     */
    private void deleteOrphanedDirs() {
        deleteOrphanedDirs(planBPaths.getShardDir());
        // Same reasoning for a snapshot node, where the fetched copy is the only thing on disk.
        deleteOrphanedDirs(planBPaths.getSnapshotDir());
    }

    private void deleteOrphanedDirs(final Path parentDir) {
        if (!Files.isDirectory(parentDir)) {
            return;
        }
        try (final Stream<Path> stream = Files.list(parentDir)) {
            stream.filter(Files::isDirectory).forEach(this::deleteIfOrphaned);
        } catch (final IOException e) {
            LOGGER.error(() -> "Error listing " + FileUtil.getCanonicalPath(parentDir) +
                               " to find orphaned dirs: " + e.getMessage(), e);
        }
    }

    private void deleteIfOrphaned(final Path dir) {
        final String docUuid = dir.getFileName().toString();
        if (docUuid.indexOf('_') != -1) {
            return;
        }
        try {
            // An open shard is the map driven loop's job, as that closes the env before deleting
            // the dir. We must never unlink files that an env is still on.
            if (shardMap.containsKey(docUuid)) {
                return;
            }

            // Anything but a definite "not found" leaves the dir alone. A transient docstore
            // failure, or one we lack permission to read, must not be taken to mean the doc has
            // gone and destroy the data of a shard that is still live. readPlanBDoc returns null
            // only for a doc that is not there; any other failure throws and is caught below.
            if (readPlanBDoc(docUuid) != null) {
                return;
            }

            // The same stripe getOrCreateShard() creates under, so a shard cannot be opened on
            // these files while we are deleting them.
            final Lock lock = creationLocks.getLockForKey(docUuid);
            lock.lock();
            try {
                // A shard may have been opened between the check above and taking the lock.
                if (shardMap.containsKey(docUuid)) {
                    return;
                }
                LOGGER.info(() -> "Deleting orphaned Plan B dir for deleted doc " + docUuid + ": " +
                                  FileUtil.getCanonicalPath(dir));
                FileUtil.deleteDir(dir);
            } finally {
                lock.unlock();
            }
        } catch (final RuntimeException e) {
            // One bad dir must not stop the rest being reclaimed.
            LOGGER.error(() -> "Error deleting orphaned Plan B dir " + FileUtil.getCanonicalPath(dir) +
                               ": " + e.getMessage(), e);
        }
    }

    private void cleanupMap(final Map<String, Shard> map) {
        map.forEach((key, shard) -> {
            try {
                // Check if the doc has been deleted. By UUID, not name — see condenseAll.
                final boolean docDeleted = readPlanBDoc(shard.getDoc().getUuid()) == null;

                if (docDeleted) {
                    // Doc deleted — could be a StoreShard whose delete() may fail if readers
                    // are active. Keep in map for retry on next cycle if delete fails.
                    if (shard.delete()) {
                        // Two arg remove so we can only ever evict the shard we just deleted, never a
                        // replacement published since we read it.
                        map.remove(key, shard);
                    }
                } else if (shard.isIdle()) {
                    // Idle eviction (SnapshotShard, ArchiveStoreShard). Remove THIS
                    // exact instance first so the next get() creates a fresh one — for a shard using a
                    // generation dir that means a new dir, so the closing and new envs never share a
                    // lock.mdb (no robust-mutex SIGSEGV). evict() waits for in-flight readers then closes
                    // + deletes the local copy; a reader that grabbed this instance mid-eviction
                    // re-resolves a fresh one via the ShardClosedException retry in get().
                    if (map.remove(key, shard)) {
                        shard.evict();
                    }
                }
            } catch (final Exception e) {
                LOGGER.error(e::getMessage, e);
            }
        });
    }

    /**
     * Deletes {@code <identity>/<generation>} dirs that no live shard instance owns — crash /
     * failed-delete orphans. In practice that means the cached archive buckets under
     * {@code archive_cache/}: the {@code shards/} root holds only flat {@code shards/<uuid>} dirs
     * (RestStoreShard), whose local copy is the authoritative data and which the {@code data.mdb}
     * check below skips. Safe for a generation dir because its contents are always re-syncable from
     * the shared store. On {@code startup} everything non-live is reaped (nothing is serving yet);
     * otherwise only dirs older than {@code minTimeToKeepStoreShardEnv}, so a just-created instance
     * not yet visible in its map is not swept out from under a concurrent {@code get()}.
     */
    private void sweepOrphanGenerationDirs(final boolean startup) {
        // Both roots are swept the same way: archive_cache/ is where the <identity>/<generation>
        // layout is used, and shards/ is included so a stray generation dir there cannot accumulate.
        sweepGenerationDirs(planBPaths.getShardDir(), collectLiveGenerationDirs(shardMap), startup);
        sweepGenerationDirs(planBPaths.getArchiveCacheDir(), collectLiveGenerationDirs(archiveShardMap),
                startup);
    }

    private static Set<Path> collectLiveGenerationDirs(final Map<String, Shard> map) {
        final Set<Path> live = new HashSet<>();
        for (final Shard shard : map.values()) {
            if (shard instanceof final AbstractStoreShard storeShard) {
                live.add(storeShard.getShardDir().toAbsolutePath().normalize());
            }
        }
        return live;
    }

    private void sweepGenerationDirs(final Path root, final Set<Path> live, final boolean startup) {
        if (!Files.exists(root)) {
            return;
        }

        // Age guard only matters for the periodic sweep (avoid racing a just-created instance not yet
        // visible in the map); at startup nothing is serving so everything non-live is reaped.
        final Instant cutoff = startup
                ? null
                : Instant.now().minus(configProvider.get().getMinTimeToKeepStoreShardEnv().getDuration());

        try (final Stream<Path> identityDirs = Files.list(root)) {
            identityDirs.filter(Files::isDirectory).forEach(identityDir -> {
                // Flat data.mdb directly under the identity dir => RestStoreShard (authoritative) — skip.
                if (Files.exists(identityDir.resolve(PlanBConstants.DATA_FILE_NAME))) {
                    return;
                }
                try (final Stream<Path> genDirs = Files.list(identityDir)) {
                    genDirs.filter(Files::isDirectory).forEach(genDir -> {
                        if (live.contains(genDir.toAbsolutePath().normalize())) {
                            return;
                        }
                        if (!startup && !isOlderThan(genDir, cutoff)) {
                            return;
                        }
                        LOGGER.debug(() -> "Sweeping orphaned local shard generation dir: " + genDir);
                        FileUtil.deleteDir(genDir);
                    });
                } catch (final IOException e) {
                    LOGGER.error(() -> "Error sweeping generation dirs under " + identityDir
                            + ": " + e.getMessage(), e);
                }
                removeIfEmpty(identityDir, startup, cutoff);
            });
        } catch (final IOException e) {
            LOGGER.error(() -> "Error sweeping local shard dir " + root + ": " + e.getMessage(), e);
        }
    }

    private static void removeIfEmpty(final Path identityDir, final boolean startup, final Instant cutoff) {
        if (!startup && !isOlderThan(identityDir, cutoff)) {
            return;
        }
        try {
            Files.delete(identityDir);
        } catch (final DirectoryNotEmptyException | NoSuchFileException e) {
            // Still in use, or another sweep got there first.
        } catch (final IOException e) {
            LOGGER.debug(() -> "Could not remove empty identity dir " + identityDir + ": " + e.getMessage());
        }
    }

    private static boolean isOlderThan(final Path dir, final Instant cutoff) {
        try {
            return Files.getLastModifiedTime(dir).toInstant().isBefore(cutoff);
        } catch (final IOException e) {
            return false; // unknown age — don't sweep
        }
    }

    /**
     * Check that we can supply a snapshot for the request and open it ready for streaming.
     * <p>
     * The snapshot is opened here, rather than the path being resolved again once streaming has started, so that
     * everything that can fail does so while the response status can still reflect it. Holding the file open also
     * means a snapshot rotation can't take it away mid transfer. See gh-5689.
     *
     * @return The snapshot, which the caller must close.
     */
    public InputStream openSnapshot(final SnapshotRequest request) {
        try {
            final Shard shard = getShardForDocUuid(request.getPlanBDocRef().getUuid());

            // Only a shard whose local copy is the authoritative data can serve a snapshot. Checked before
            // checkSnapshotStatus because that only exists on SnapshotCapable.
            if (!(shard instanceof final SnapshotCapable capable)) {
                throw new SnapshotNotFoundException(LogUtil.message(
                        "This node does not store the shard for {} so cannot supply a snapshot",
                        request.getPlanBDocRef()));
            }

            // Lets the shard reject the request, e.g. because the client already has the latest snapshot, or
            // because no snapshot has been created yet.
            capable.checkSnapshotStatus(request);

            final Path path = capable.getSnapshotZip();
            try {
                return new BufferedInputStream(Files.newInputStream(path));
            } catch (final NoSuchFileException e) {
                throw new SnapshotNotFoundException(LogUtil.message(
                        "Snapshot for {} no longer exists at '{}'",
                        request.getPlanBDocRef(), FileUtil.getCanonicalPath(path)));
            } catch (final IOException e) {
                throw new UncheckedIOException(LogUtil.message(
                        "Error opening snapshot for {} at '{}': {}",
                        request.getPlanBDocRef(), FileUtil.getCanonicalPath(path), e.getMessage()), e);
            }
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Debug checking snapshot status: {} {}",
                    request.getPlanBDocRef(), e.getMessage()), e);
            throw e;
        }
    }

    /**
     * Close all shards, closing their LMDB environments. Store shard data remains on disk;
     * snapshot copies are discarded. Closes this manager permanently: no shard can be created
     * afterwards, so nothing can reopen an env on a dir whose env we have just closed.
     * <p>
     * Every env is closed, but not necessarily before this returns. A creation already in
     * flight when this runs may publish its shard after the sweep below has passed; that
     * creator then closes its own shard (see {@link #getOrCreateShard}), which may happen
     * after this method has returned. Callers that must know every env is shut before they
     * touch the files (e.g. deleting the parent dir) have to quiesce their own callers first.
     */
    public void closeAll() {
        // Set BEFORE sweeping, which is what makes this airtight against a creation that is
        // already in flight: such a creation publishes its shard to the map and then re-reads
        // this flag, so either we see its entry in the sweep below, or it sees this flag and
        // closes its own shard. See getOrCreateShard().
        closed = true;

        shardMap.forEach((uuid, shard) -> {
            try {
                // Remove before closing so no concurrent caller can obtain the closing shard.
                // Only close it if we were the one to remove it: a creator racing us may have
                // taken it out first, in which case closing it is its job, not ours.
                if (shardMap.remove(uuid, shard)) {
                    shard.close();
                }
            } catch (final Exception e) {
                LOGGER.error(e::getMessage, e);
            }
        });
    }

    public <R> R get(final String mapName, final Function<Db<?, ?>, R> function) {
        try {
            final Shard shard = getShardForMapName(mapName);
            return shard.get(function);
        } catch (final ShardClosedException e) {
            // The shard was closed between our lookup and use, by cleanup evicting it or by
            // closeAll(). Retrying builds a fresh shard in the first case; in the second it
            // throws "closed", which is the right outcome.
            LOGGER.debug(() -> "Shard was evicted, retrying with fresh shard for: " + mapName);
            final Shard shard = getShardForMapName(mapName);
            return shard.get(function);
        } catch (final RuntimeException e) {
            // Debug only as we rethrow, so the caller reports the failure, e.g. to the pipeline error receiver.
            // Logging it here as well just duplicates it into the stream processing error file. See gh-5705.
            LOGGER.debug(() -> LogUtil.message("Error getting shard for map: {} {}", mapName, e.getMessage()), e);
            throw e;
        }
    }

    /**
     * Read from a cached, read-only local copy of an archive bucket (copied down + version-checked +
     * idle-evicted), instead of copying the bucket to a temp dir per call. Keyed by
     * {@code uuid_<shardIndex>_<dateLabel>}. Mirrors the live {@link #get} retry so a read racing an
     * idle eviction re-resolves a fresh copy.
     */
    public <R> R getArchive(final PlanBDocument doc,
                            final int shardIndex,
                            final ArchiveShardRef ref,
                            final Function<Db<?, ?>, R> function) {
        try {
            return getArchiveShard(doc, shardIndex, ref).get(function);
        } catch (final ShardClosedException e) {
            LOGGER.debug(() -> "Archive shard was evicted, retrying with fresh shard for: "
                    + doc.getUuid() + "_" + shardIndex + "_" + ref.dateLabel());
            return getArchiveShard(doc, shardIndex, ref).get(function);
        }
    }

    private Shard getArchiveShard(final PlanBDocument doc,
                                  final int shardIndex,
                                  final ArchiveShardRef ref) {
        final String cacheKey = doc.getUuid() + "_" + shardIndex + "_" + ref.dateLabel();
        return archiveShardMap.computeIfAbsent(cacheKey, k ->
                new ArchiveStoreShard(byteBuffers, byteBufferFactory, configProvider, planBPaths,
                        doc, shardIndex, ref));
    }

    /**
     * Returns the {@link PlanBDocument} for the given map name from the doc
     * cache, or {@code null} if no document with that name is registered.
     */
    public PlanBDocument getDoc(final String mapName) {
        try {
            return planBDocCache.get(mapName);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "No PlanB doc found for map name '" + mapName + "': " + e.getMessage());
            return null;
        }
    }

    /**
     * @return The shard for a doc uuid if one is already open, without creating one. For callers
     * that must not open an env as a side effect of a read, e.g. an admin info listing. Note the
     * shard this returns may still have side effects of its own when used: a snapshot shard
     * refreshes its access time and may start a fetch.
     */
    public Optional<Shard> getExistingShard(final String docUuid) {
        return Optional.ofNullable(shardMap.get(docUuid));
    }

    public Shard getShardForMapName(final String mapName) {
        final PlanBDocument doc = planBDocCache.get(mapName);
        if (doc == null) {
            LOGGER.warn(() -> "No PlanB doc found for '" + mapName + "'");
            throw new RuntimeException("No PlanB doc found for '" + mapName + "'");
        }
        return getOrCreateShard(doc.getUuid(), () -> createShard(doc));
    }


    public Shard getShardForDocUuid(final String docUuid) throws DocumentNotFoundException {
        return getOrCreateShard(docUuid, () -> {
            final PlanBDocument doc = readPlanBDoc(docUuid);
            if (doc == null) {
                LOGGER.warn(() -> "No PlanB doc found for UUID '" + docUuid + "'");
                // A type is required — DocRef's constructor rejects a null one, and an NPE here would be
                // caught as an unexpected RuntimeException by callers that handle DocumentNotFoundException.
                throw new DocumentNotFoundException(DocRef.builder()
                        .type(PlanBDoc.TYPE)
                        .uuid(docUuid)
                        .build());
            }
            return createShard(doc);
        });
    }

    /**
     * Get the shard for a doc, creating it if we don't already have it.
     * <p>
     * Creating a shard is slow. On a snapshot node it fetches the snapshot from another node over HTTP, with no
     * client side timeout, and unzips it. This must not be done inside
     * {@link java.util.concurrent.ConcurrentHashMap#computeIfAbsent} because the mapping function runs while
     * holding the lock for the bin the key hashes to, so one slow fetch stalls lookups of unrelated shards that
     * happen to hash to the same bin. See gh-5689.
     * <p>
     * Creation instead happens outside the map, serialised by a striped lock so that concurrent callers for the
     * same shard wait rather than both building one. Serialising matters more than it might seem: if we let them
     * race and discarded the loser we would have to release the discarded shard, and releasing a store shard
     * means calling delete() on it, which deletes the shard data itself.
     */
    private Shard getOrCreateShard(final String docUuid, final Supplier<Shard> creator) {
        final Shard existing = shardMap.get(docUuid);
        if (existing != null) {
            return existing;
        }

        final Shard orphan;
        final Lock lock = creationLocks.getLockForKey(docUuid);
        lock.lock();
        try {
            // Another thread may have created it while we were waiting for the lock.
            final Shard created = shardMap.get(docUuid);
            if (created != null) {
                return created;
            }

            if (closed) {
                // Fast path only: don't open an env we would immediately have to close
                // again. The check that actually matters is the one after publishing below.
                throw new RuntimeException(CLOSED_MESSAGE);
            }

            final Shard shard = creator.get();
            shardMap.put(docUuid, shard);

            // closeAll() may have swept the map while we were opening the env above, which
            // would leave this shard and its open env in the map with nothing left to close
            // it. Re-read the flag AFTER publishing: closeAll() sets it before it sweeps, so
            // the two possible orderings are exhaustive — either it sees our entry and closes
            // it, or we see the flag here and take responsibility for closing it ourselves.
            if (!closed) {
                return shard;
            }
            orphan = shardMap.remove(docUuid, shard)
                    ? shard
                    : null;
        } finally {
            lock.unlock();
        }

        // Closed while we were creating. Closed outside the creation lock as close() waits
        // for in-flight readers, and this lock is striped, so holding it here would stall
        // creation for unrelated docs that hash to the same stripe.
        if (orphan != null) {
            try {
                orphan.close();
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
            }
        }
        throw new RuntimeException(CLOSED_MESSAGE);
    }

    private Shard createShard(final PlanBDocument doc) {
        if (isSnapshotNode() && (doc.getSharedPath() == null || doc.getShardCount() == 0)) {
            return new SnapshotShard(
                    byteBuffers,
                    byteBufferFactory,
                    configProvider,
                    planBPaths,
                    fileTransferClient,
                    doc,
                    DB_FACTORY,
                    executor);
        }

        if (doc.getSharedPath() != null && doc.getShardCount() > 0) {
            throw new NotImplementedException("Not yet implemented");
        }

        return new RestStoreShard(
                byteBuffers,
                byteBufferFactory,
                configProvider,
                planBPaths,
                doc);
    }

    private PlanBDocument readPlanBDoc(final DocRef docRef) {
        if (documentActionHandlersProvider != null && !documentActionHandlersProvider.get().isEmpty()) {
            final DocumentActionHandler<?> handler = documentActionHandlersProvider.get()
                    .get(new DocumentTypeName(docRef.getType()));
            if (handler != null) {
                try {
                    final Object loaded = handler.readDocument(docRef);
                    if (loaded instanceof PlanBDocument planBStoredDoc) {
                        return planBStoredDoc;
                    }
                } catch (final DocumentNotFoundException e) {
                    LOGGER.debug(() -> "Document not found: " + docRef, e);
                }
            }
        }
        if (planBDocStore != null) {
            try {
                return planBDocStore.readDocument(docRef);
            } catch (final DocumentNotFoundException e) {
                LOGGER.debug(() -> "Document not found: " + docRef, e);
            }
        }
        return null;
    }

    private PlanBDocument readPlanBDoc(final String uuid) {
        if (documentActionHandlersProvider != null) {
            for (final DocumentTypeName typeName : documentActionHandlersProvider.get().keySet()) {
                final PlanBDocument doc = readPlanBDoc(
                        DocRef.builder().type(typeName.toString()).uuid(uuid).build());
                if (doc != null) {
                    return doc;
                }
            }
        }
        if (planBDocStore != null) {
            try {
                return planBDocStore.readDocument(DocRef.builder()
                        .type(PlanBDoc.TYPE)
                        .uuid(uuid)
                        .build());
            } catch (final DocumentNotFoundException e) {
                LOGGER.debug(() -> "Document not found by UUID: " + uuid, e);
            }
        }
        return null;
    }
}
