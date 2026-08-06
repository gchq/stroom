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
import stroom.planb.impl.data.archive.ArchiveShardRef;
import stroom.planb.impl.data.shard.SnapshotShard.DbFactory;
import stroom.planb.impl.db.Db;
import stroom.planb.impl.db.PlanBDb;
import stroom.planb.impl.db.ShardKeyRouter;
import stroom.planb.impl.fs.ArchiveStoreShard;
import stroom.planb.impl.fs.SharedFileStoreShard;
import stroom.planb.impl.rest.FileTransferClient;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.PlanBDocument;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Stream;

@Singleton
public class ShardManager {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ShardManager.class);

    public static final String SNAPSHOT_CREATOR_TASK_NAME = "Plan B Snapshot Creator";
    public static final String SNAPSHOT_CLEANUP_TASK_NAME = "Plan B Snapshot Cleanup";

    private static final DbFactory DB_FACTORY = PlanBDb::open;

    private final ByteBuffers byteBuffers;
    private final ByteBufferFactory byteBufferFactory;
    private final PlanBDocCache planBDocCache;
    private final PlanBDocStore planBDocStore;
    private final Provider<Map<DocumentTypeName, DocumentActionHandler>> documentActionHandlersProvider;
    private final Map<String, Shard> shardMap = new ConcurrentHashMap<>();
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

        // Delete any existing snapshots that might have been left behind from the last use of Stroom.
        FileUtil.deleteDir(planBPaths.getSnapshotDir());

        // Reap any shared-file-store shard generation dirs left behind by a previous run (nothing is
        // serving yet, so all are orphans); they are re-synced from the shared store on next access.
        sweepOrphanGenerationDirs(true);
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
                // The merge cycle maintains shared-file-store shards, under a cluster lock.
                if (doc.getSharedPath() != null && doc.getShardCount() > 0) {
                    return;
                }
                final Runnable runnable = taskContextFactory
                        .childContext(parentTaskContext, "Maintain shard: " + doc.getName(), taskContext -> {
                            try {
                                try {
                                    final PlanBDocument loaded = planBDocCache.get(doc.getName());
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
                                } catch (final DocumentNotFoundException e) {
                                    LOGGER.debug(e::getMessage, e);
                                    // If we can't get the doc then we must have deleted it so delete the shard.
                                    if (shard.delete()) {
                                        shardMap.remove(shard.getDoc().getUuid());
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

    public void compactAll() {
        try {
            shardMap.values().forEach(shard -> {
                try {
                    final PlanBDocument doc = shard.getDoc();
                    try {
                        planBDocCache.get(doc.getName());
                        // Doc exists — compact the shard.
                        shard.compact();
                    } catch (final DocumentNotFoundException e) {
                        LOGGER.debug(e::getMessage, e);
                        // If we can't get the doc then we must have deleted it so delete the shard.
                        if (shard.delete()) {
                            shardMap.remove(shard.getDoc().getUuid());
                        }
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
    }

    private void cleanupMap(final Map<String, Shard> map) {
        map.forEach((key, shard) -> {
            try {
                boolean docDeleted;

                // Check if the doc has been deleted.
                try {
                    planBDocCache.get(shard.getDoc().getName());
                    docDeleted = false;
                } catch (final DocumentNotFoundException e) {
                    LOGGER.debug(e::getMessage, e);
                    docDeleted = true;
                }

                if (docDeleted) {
                    // Doc deleted — could be a StoreShard whose delete() may fail if readers
                    // are active. Keep in map for retry on next cycle if delete fails.
                    if (shard.delete()) {
                        map.remove(key);
                    }
                } else if (shard.isIdle()) {
                    // Idle eviction (SnapshotShard, SharedFileStoreShard, ArchiveStoreShard). Remove THIS
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
     * Deletes local shared-file-store shard generation dirs under {@code shards/<uuid>_<idx>/} that no
     * live {@link shardMap} instance owns — crash / failed-delete orphans. Safe because those copies
     * are always re-syncable from the shared store. Never touches flat {@code shards/<uuid>} dirs
     * (RestStoreShard) whose local copy is the authoritative data. On {@code startup} everything
     * non-live is reaped (nothing is serving yet); otherwise only dirs older than
     * {@code minTimeToKeepStoreShardEnv}, so a just-created instance not yet visible in the map is not
     * swept out from under a concurrent {@code get()}.
     */
    private void sweepOrphanGenerationDirs(final boolean startup) {
        // Live shards (shards/) and cached archive buckets (archive_cache/) both use the
        // <identity>/<generation> layout, so the same sweep handles both roots.
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
                        LOGGER.info(() -> "Sweeping orphaned local shard generation dir: " + genDir);
                        FileUtil.deleteDir(genDir);
                    });
                } catch (final IOException e) {
                    LOGGER.error(() -> "Error sweeping generation dirs under " + identityDir
                            + ": " + e.getMessage(), e);
                }
            });
        } catch (final IOException e) {
            LOGGER.error(() -> "Error sweeping local shard dir " + root + ": " + e.getMessage(), e);
        }
    }

    private static boolean isOlderThan(final Path dir, final Instant cutoff) {
        try {
            return Files.getLastModifiedTime(dir).toInstant().isBefore(cutoff);
        } catch (final IOException e) {
            return false; // unknown age — don't sweep
        }
    }

    public void fetchSnapshot(final SnapshotRequest request, final OutputStream outputStream) {
        try {
            final Shard shard = getShardForDocUuid(request.getPlanBDocRef().getUuid());
            if (shard instanceof final RestStoreShard restShard) {
                try {
                    final Path path = restShard.getSnapshotZip();
                    if (Files.exists(path)) {
                        StreamUtil.streamToStream(Files.newInputStream(path), outputStream);
                    }
                } catch (final Exception e) {
                    LOGGER.error(() -> LogUtil.message("Error fetching snapshot: {} {}",
                            request.getPlanBDocRef(), e.getMessage()), e);
                }
            }
        } catch (final RuntimeException e) {
            LOGGER.error(() -> LogUtil.message("Error fetching snapshot: {} {}",
                    request.getPlanBDocRef(), e.getMessage()), e);
            throw e;
        }
    }

    public <R> R get(final String mapName, final Function<Db<?, ?>, R> function) {
        try {
            final Shard shard = getShardForMapName(mapName);
            return shard.get(function);
        } catch (final ShardClosedException e) {
            // The shard was evicted by cleanup between our lookup and use.
            // Retry once — computeIfAbsent will create a fresh shard.
            LOGGER.debug(() -> "Shard was evicted, retrying with fresh shard for: " + mapName);
            final Shard shard = getShardForMapName(mapName);
            return shard.get(function);
        } catch (final RuntimeException e) {
            LOGGER.error(() -> LogUtil.message("Error getting shard for map: {} {}", mapName, e.getMessage()), e);
            throw e;
        }
    }

    public <R> R get(final String mapName, final String key, final Function<Db<?, ?>, R> function) {
        final PlanBDocument doc = planBDocCache.get(mapName);
        if (doc == null) {
            LOGGER.warn(() -> "No PlanB doc found for '" + mapName + "'");
            throw new RuntimeException("No PlanB doc found for '" + mapName + "'");
        }
        if (doc.getSharedPath() != null && doc.getShardCount() > 0) {
            final int shardIndex = ShardKeyRouter.computeShardIndex(key, doc.getShardCount());
            return get(mapName, shardIndex, function);
        } else {
            return get(mapName, function);
        }
    }

    public <R> R get(final String mapName, final int shardIndex, final Function<Db<?, ?>, R> function) {
        try {
            final Shard shard = getShardForMapNameAndShard(mapName, shardIndex);
            return shard.get(function);
        } catch (final ShardClosedException e) {
            LOGGER.debug(() -> "Shard was evicted, retrying with fresh shard for: " + mapName + "_" + shardIndex);
            final Shard shard = getShardForMapNameAndShard(mapName, shardIndex);
            return shard.get(function);
        } catch (final RuntimeException e) {
            LOGGER.error(() -> LogUtil.message("Error getting shard for map: {} shard: {} {}",
                    mapName, shardIndex, e.getMessage()), e);
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

    public Shard getShardForMapName(final String mapName) {
        return getShardForMapNameAndShard(mapName, -1);
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

    public Shard getShardForMapNameAndShard(final String mapName, final int shardIndex) {
        final PlanBDocument doc = planBDocCache.get(mapName);
        if (doc == null) {
            LOGGER.warn(() -> "No PlanB doc found for '" + mapName + "'");
            throw new RuntimeException("No PlanB doc found for '" + mapName + "'");
        }
        final String cacheKey = shardIndex >= 0 ? doc.getUuid() + "_" + shardIndex : doc.getUuid();
        return shardMap.computeIfAbsent(cacheKey, k -> createShard(doc, shardIndex));
    }


    public Shard getShardForDocUuid(final String docUuid) throws DocumentNotFoundException {
        return getShardForDocUuidAndShard(docUuid, -1);
    }

    public Shard getShardForDocUuidAndShard(final String docUuid,
                                            final int shardIndex) throws DocumentNotFoundException {
        final String cacheKey = shardIndex >= 0 ? docUuid + "_" + shardIndex : docUuid;
        return shardMap.computeIfAbsent(cacheKey, k -> {
            final PlanBDocument doc = readPlanBDoc(docUuid);
            if (doc == null) {
                LOGGER.warn(() -> "No PlanB doc found for UUID '" + docUuid + "'");
                throw new DocumentNotFoundException(DocRef.builder().uuid(docUuid).build());
            }
            return createShard(doc, shardIndex);
        });
    }

    private Shard createShard(final PlanBDocument doc, final int shardIndex) {
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
            return new SharedFileStoreShard(
                    byteBuffers,
                    byteBufferFactory,
                    configProvider,
                    planBPaths,
                    doc,
                    shardIndex);
        }
        return new RestStoreShard(
                byteBuffers,
                byteBufferFactory,
                configProvider,
                planBPaths,
                doc,
                shardIndex);
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
