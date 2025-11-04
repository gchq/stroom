package stroom.planb.impl.data;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentNotFoundException;
import stroom.node.api.NodeInfo;
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.PlanBDocCache;
import stroom.planb.impl.PlanBDocStore;
import stroom.planb.impl.db.Db;
import stroom.planb.impl.db.StatePaths;
import stroom.planb.shared.PlanBDoc;
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
import java.util.function.Function;

@Singleton
public class ShardManager {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ShardManager.class);

    public static final String CLEANUP_TASK_NAME = "Plan B Cleanup";
    public static final String SNAPSHOT_CREATOR_TASK_NAME = "Plan B Snapshot Creator";

    private final ByteBuffers byteBuffers;
    private final ByteBufferFactory byteBufferFactory;
    private final PlanBDocCache planBDocCache;
    private final PlanBDocStore planBDocStore;
    private final Map<String, Shard> shardMap = new ConcurrentHashMap<>();
    private final NodeInfo nodeInfo;
    private final Provider<PlanBConfig> configProvider;
    private final StatePaths statePaths;
    private final FileTransferClient fileTransferClient;
    private final TaskContextFactory taskContextFactory;

    @Inject
    public ShardManager(final ByteBuffers byteBuffers,
                        final ByteBufferFactory byteBufferFactory,
                        final PlanBDocCache planBDocCache,
                        final PlanBDocStore planBDocStore,
                        final NodeInfo nodeInfo,
                        final Provider<PlanBConfig> configProvider,
                        final StatePaths statePaths,
                        final FileTransferClient fileTransferClient,
                        final TaskContextFactory taskContextFactory) {
        this.byteBuffers = byteBuffers;
        this.byteBufferFactory = byteBufferFactory;
        this.planBDocCache = planBDocCache;
        this.planBDocStore = planBDocStore;
        this.nodeInfo = nodeInfo;
        this.configProvider = configProvider;
        this.statePaths = statePaths;
        this.fileTransferClient = fileTransferClient;
        this.taskContextFactory = taskContextFactory;

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

                futures.add(CompletableFuture.runAsync(runnable));
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

    public void checkSnapshotStatus(final SnapshotRequest request) {
        try {
            final Shard shard = getShardForDocUuid(request.getPlanBDocRef().getUuid());
            shard.checkSnapshotStatus(request);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Debug checking snapshot status: {} {}",
                    request.getPlanBDocRef(), e.getMessage()), e);
            throw e;
        }
    }

    public void createSnapshots() {
        try {
            final List<CompletableFuture<Void>> futures = new ArrayList<>();
            shardMap.values().forEach(shard -> futures.add(CompletableFuture.runAsync(shard::createSnapshot)));
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }
    }

    public void fetchSnapshot(final SnapshotRequest request, final OutputStream outputStream) {
        try {
            final Shard shard = getShardForDocUuid(request.getPlanBDocRef().getUuid());
            if (shard instanceof final StoreShard storeShard) {
                try {
                    final Path path = storeShard.getSnapshotZip();
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
        } catch (final RuntimeException e) {
            LOGGER.error(() -> LogUtil.message("Error getting shard for map: {} {}", mapName, e.getMessage()), e);
            throw e;
        }
    }

    public void cleanup() {
        try {
            shardMap.values().forEach(Shard::cleanup);
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }
    }

    public Shard getShardForMapName(final String mapName) {
        final PlanBDoc doc = planBDocCache.get(mapName);
        if (doc == null) {
            LOGGER.warn(() -> "No PlanB doc found for '" + mapName + "'");
            throw new RuntimeException("No PlanB doc found for '" + mapName + "'");
        }
        return shardMap.computeIfAbsent(doc.getUuid(), k -> createShard(doc));
    }

    public Shard getShardForDocUuid(final String docUuid) throws DocumentNotFoundException {
        return shardMap.computeIfAbsent(docUuid, k -> {
            final PlanBDoc doc = planBDocStore.readDocument(DocRef.builder().type(PlanBDoc.TYPE).uuid(k).build());
            if (doc == null) {
                LOGGER.warn(() -> "No PlanB doc found for UUID '" + docUuid + "'");
                throw new DocumentNotFoundException(DocRef.builder().type(PlanBDoc.TYPE).uuid(docUuid).build());
            }
            return createShard(doc);
        });
    }

    private Shard createShard(final PlanBDoc doc) {
        if (isSnapshotNode()) {
            return new SnapshotShard(
                    byteBuffers,
                    byteBufferFactory,
                    configProvider,
                    statePaths,
                    fileTransferClient,
                    doc);
        }
        return new StoreShard(
                byteBuffers,
                byteBufferFactory,
                configProvider,
                statePaths,
                doc);
    }
}
