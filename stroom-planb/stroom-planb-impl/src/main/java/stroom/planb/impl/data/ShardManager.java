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
import stroom.docstore.api.DocumentActionHandler;
import stroom.docstore.api.DocumentNotFoundException;
import stroom.docstore.api.DocumentTypeName;
import stroom.node.api.NodeInfo;
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.PlanBDocCache;
import stroom.planb.impl.PlanBDocStore;
import stroom.planb.impl.data.SnapshotShard.DbFactory;
import stroom.planb.impl.db.Db;
import stroom.planb.impl.db.PlanBDb;
import stroom.planb.impl.db.ShardKeyRouter;
import stroom.planb.impl.db.StatePaths;
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

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Function;

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
                        final ExecutorProvider executorProvider,
                        final Provider<Map<DocumentTypeName, DocumentActionHandler>> documentActionHandlersProvider) {
        this.byteBuffers = byteBuffers;
        this.byteBufferFactory = byteBufferFactory;
        this.planBDocCache = planBDocCache;
        this.planBDocStore = planBDocStore;
        this.documentActionHandlersProvider = documentActionHandlersProvider;
        this.nodeInfo = nodeInfo;
        this.configProvider = configProvider;
        this.statePaths = statePaths;
        this.fileTransferClient = fileTransferClient;
        this.taskContextFactory = taskContextFactory;
        this.executor = executorProvider.get();

        // Delete any existing snapshots that might have been left behind from the last use of Stroom.
        FileUtil.deleteDir(statePaths.getSnapshotDir());
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
                final Runnable runnable = taskContextFactory
                        .childContext(parentTaskContext, "Maintain shard: " + doc.getName(), taskContext -> {
                            try {
                                try {
                                    final PlanBDocument loaded = planBDocCache.get(doc.getName());
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
        shardMap.forEach((uuid, shard) -> {
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
        } catch (final SnapshotShard.ShardClosedException e) {
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
        } catch (final SnapshotShard.ShardClosedException e) {
            LOGGER.debug(() -> "Shard was evicted, retrying with fresh shard for: " + mapName + "_" + shardIndex);
            final Shard shard = getShardForMapNameAndShard(mapName, shardIndex);
            return shard.get(function);
        } catch (final RuntimeException e) {
            LOGGER.error(() -> LogUtil.message("Error getting shard for map: {} shard: {} {}",
                    mapName, shardIndex, e.getMessage()), e);
            throw e;
        }
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
                    statePaths,
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
                    statePaths,
                    doc,
                    shardIndex);
        }
        return new RestStoreShard(
                byteBuffers,
                byteBufferFactory,
                configProvider,
                statePaths,
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
