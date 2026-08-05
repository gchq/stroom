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

package stroom.planb.impl.data;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentNotFoundException;
import stroom.node.api.NodeInfo;
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.PlanBDocCache;
import stroom.planb.impl.PlanBDocStore;
import stroom.planb.impl.dao.Db;
import stroom.planb.impl.dao.PlanBDb;
import stroom.planb.impl.dao.StatePaths;
import stroom.planb.impl.data.SnapshotShard.DbFactory;
import stroom.planb.shared.PlanBDoc;
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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    private final ByteBuffers byteBuffers;
    private final ByteBufferFactory byteBufferFactory;
    private final PlanBDocCache planBDocCache;
    private final PlanBDocStore planBDocStore;
    private final Map<String, Shard> shardMap = new ConcurrentHashMap<>();
    // Serialises creation of a given shard without holding a lock on the shard map while we do it.
    private final StripedLock creationLocks = new StripedLock();
    private final NodeInfo nodeInfo;
    private final Provider<PlanBConfig> configProvider;
    private final StatePaths statePaths;
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
                        final StatePaths statePaths,
                        final FileTransferClient fileTransferClient,
                        final TaskContextFactory taskContextFactory,
                        final ExecutorProvider executorProvider) {
        this.byteBuffers = byteBuffers;
        this.byteBufferFactory = byteBufferFactory;
        this.planBDocCache = planBDocCache;
        this.planBDocStore = planBDocStore;
        this.nodeInfo = nodeInfo;
        this.configProvider = configProvider;
        this.statePaths = statePaths;
        this.fileTransferClient = fileTransferClient;
        this.taskContextFactory = taskContextFactory;
        this.executor = executorProvider.get();

        // Delete any snapshots fetched from other nodes that might have been left behind from the last use of
        // Stroom.
        deleteFetchedSnapshots(statePaths.getSnapshotDir());
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
                final PlanBDoc doc = shard.getDoc();
                final Runnable runnable = taskContextFactory
                        .childContext(parentTaskContext, "Maintain shard: " + doc.getName(), taskContext -> {
                            try {
                                try {
                                    final PlanBDoc loaded = planBDocStore.readDocument(doc.asDocRef());
                                    // If we can't get the doc then we must have deleted it so delete the shard.
                                    if (loaded == null) {
                                        taskContext.info(() -> "Deleting shard");
                                        if (shard.delete()) {
                                            shardMap.remove(shard.getDoc().getUuid());
                                        }
                                    } else {
                                        long total = 0;
                                        taskContext.info(() -> "Condensing data");
                                        total += shard.condense(loaded);
                                        taskContext.info(() -> "Deleting old data");
                                        total += shard.deleteOldData(loaded);
                                        if (total > 0) {
                                            // If we removed data then compact the shard.
                                            taskContext.info(() -> "Compacting shard");
                                            shard.compact();
                                        }
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
                                count, NullSafe.get(shard.getDoc(), PlanBDoc::getName)));
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
                    final PlanBDoc doc = shard.getDoc();
                    try {
                        final PlanBDoc loaded = planBDocStore.readDocument(doc.asDocRef());
                        // If we can't get the doc then we must have deleted it so delete the shard.
                        if (loaded == null) {
                            if (shard.delete()) {
                                shardMap.remove(shard.getDoc().getUuid());
                            }
                        } else {
                            // If we removed data then compact the shard.
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
                    LOGGER.error(e::getMessage, e);
                }
            });
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
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

            // Lets the shard reject the request, e.g. because the client already has the latest snapshot, or
            // because no snapshot has been created yet.
            shard.checkSnapshotStatus(request);

            if (!(shard instanceof final StoreShard storeShard)) {
                throw new SnapshotNotFoundException(LogUtil.message(
                        "This node does not store the shard for {} so cannot supply a snapshot",
                        request.getPlanBDocRef()));
            }

            final Path path = storeShard.getSnapshotZip();
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

    public void createSnapshots() {
        try {
            final List<CompletableFuture<Void>> futures = new ArrayList<>();
            shardMap.values().forEach(shard ->
                    futures.add(CompletableFuture.runAsync(shard::createSnapshot, executor)));
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }
    }

    public void cleanup() {
        shardMap.forEach((uuid, shard) -> {
            try {
                boolean docDeleted;

                // Check if the doc has been deleted.
                try {
                    final PlanBDoc loaded = planBDocStore.readDocument(
                            DocRef.builder().type(PlanBDoc.TYPE).uuid(uuid).build());
                    docDeleted = loaded == null;
                } catch (final DocumentNotFoundException e) {
                    LOGGER.debug(e::getMessage, e);
                    docDeleted = true;
                }

                if (docDeleted) {
                    // Doc deleted — could be StoreShard whose delete() may fail if readers
                    // are active. Keep in map for retry on next cycle if delete fails.
                    if (shard.delete()) {
                        shardMap.remove(uuid);
                    }
                } else if (shard.isIdle()) {
                    // Idle eviction — only SnapshotShard reaches here (StoreShard.isIdle()
                    // always returns false). Remove from map first to prevent a zombie shard
                    // window where a concurrent reader gets a deleted shard from computeIfAbsent.
                    shardMap.remove(uuid);
                    shard.delete();
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
        } catch (final SnapshotShard.ShardClosedException e) {
            // The shard was evicted by cleanup between our lookup and use.
            // Retry once — we will create a fresh shard.
            LOGGER.debug(() -> "Shard was evicted, retrying with fresh shard for: " + mapName);
            final Shard shard = getShardForMapName(mapName);
            return shard.get(function);
        } catch (final RuntimeException e) {
            LOGGER.error(() -> LogUtil.message("Error getting shard for map: {} {}", mapName, e.getMessage()), e);
            throw e;
        }
    }

    public Shard getShardForMapName(final String mapName) {
        final PlanBDoc doc = planBDocCache.get(mapName);
        if (doc == null) {
            LOGGER.warn(() -> "No PlanB doc found for '" + mapName + "'");
            throw new RuntimeException("No PlanB doc found for '" + mapName + "'");
        }
        return getOrCreateShard(doc.getUuid(), () -> createShard(doc));
    }

    public Shard getShardForDocUuid(final String docUuid) throws DocumentNotFoundException {
        return getOrCreateShard(docUuid, () -> {
            final PlanBDoc doc = planBDocStore.readDocument(
                    DocRef.builder().type(PlanBDoc.TYPE).uuid(docUuid).build());
            if (doc == null) {
                LOGGER.warn(() -> "No PlanB doc found for UUID '" + docUuid + "'");
                throw new DocumentNotFoundException(DocRef.builder().type(PlanBDoc.TYPE).uuid(docUuid).build());
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
     * race and discarded the loser we would have to release the discarded shard, and releasing a
     * {@link StoreShard} means calling delete() on it, which deletes the shard data itself.
     */
    private Shard getOrCreateShard(final String docUuid, final Supplier<Shard> creator) {
        final Shard existing = shardMap.get(docUuid);
        if (existing != null) {
            return existing;
        }

        final Lock lock = creationLocks.getLockForKey(docUuid);
        lock.lock();
        try {
            // Another thread may have created it while we were waiting for the lock.
            final Shard created = shardMap.get(docUuid);
            if (created != null) {
                return created;
            }

            final Shard shard = creator.get();
            shardMap.put(docUuid, shard);
            return shard;
        } finally {
            lock.unlock();
        }
    }

    private Shard createShard(final PlanBDoc doc) {
        if (isSnapshotNode()) {
            return new SnapshotShard(
                    byteBuffers,
                    byteBufferFactory,
                    configProvider,
                    statePaths,
                    fileTransferClient,
                    doc,
                    DB_FACTORY,
                    executor);
        }
        return new StoreShard(
                byteBuffers,
                byteBufferFactory,
                configProvider,
                statePaths,
                doc);
    }
}
