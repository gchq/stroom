package stroom.planb.impl.data;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentNotFoundException;
import stroom.node.api.NodeInfo;
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.PlanBDocCache;
import stroom.planb.impl.PlanBDocStore;
import stroom.planb.impl.db.AbstractDb;
import stroom.planb.impl.db.StatePaths;
import stroom.planb.shared.PlanBDoc;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
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

    private final ByteBufferFactory byteBufferFactory;
    private final PlanBDocCache planBDocCache;
    private final PlanBDocStore planBDocStore;
    private final Map<String, Shard> shardMap = new ConcurrentHashMap<>();
    private final NodeInfo nodeInfo;
    private final Provider<PlanBConfig> configProvider;
    private final StatePaths statePaths;
    private final FileTransferClient fileTransferClient;

    @Inject
    public ShardManager(final ByteBufferFactory byteBufferFactory,
                        final PlanBDocCache planBDocCache,
                        final PlanBDocStore planBDocStore,
                        final NodeInfo nodeInfo,
                        final Provider<PlanBConfig> configProvider,
                        final StatePaths statePaths,
                        final FileTransferClient fileTransferClient) {
        this.byteBufferFactory = byteBufferFactory;
        this.planBDocCache = planBDocCache;
        this.planBDocStore = planBDocStore;
        this.nodeInfo = nodeInfo;
        this.configProvider = configProvider;
        this.statePaths = statePaths;
        this.fileTransferClient = fileTransferClient;

        // Delete any existing snapshots that might have been left behind from the last use of Stroom.
        FileUtil.deleteDir(statePaths.getSnapshotDir());
    }

    private boolean isSnapshotNode() {
        final List<String> nodes = NullSafe.list(configProvider.get().getNodeList());
        // If we have no node info or no nodes are configured then treat this as a shard writer node and not a
        // snapshot node.
        return nodeInfo != null && !nodes.isEmpty() && !nodes.contains(nodeInfo.getThisNodeName());
    }

    public void condenseAll() {
        shardMap.values().forEach(shard -> {
            try {
                final PlanBDoc doc = shard.getDoc();
                try {
                    final PlanBDoc loaded = planBDocStore.readDocument(doc.asDocRef());
                    // If we can't get the doc then we must have deleted it so delete the shard.
                    if (loaded == null) {
                        shard.delete();
                        shardMap.remove(shard.getDoc().getUuid());
                    } else {
                        shard.condense(loaded);
                    }
                } catch (final DocumentNotFoundException e) {
                    LOGGER.debug(e::getMessage, e);
                    // If we can't get the doc then we must have deleted it so delete the shard.
                    shard.delete();
                    shardMap.remove(shard.getDoc().getUuid());
                }
            } catch (final Exception e) {
                LOGGER.error(e::getMessage, e);
            }
        });
    }

    public void checkSnapshotStatus(final SnapshotRequest request) {
        final Shard shard = getShardForDocUuid(request.getPlanBDocRef().getUuid());
        shard.checkSnapshotStatus(request);
    }

    public void createSnapshots() {
        final List<CompletableFuture<Void>> futures = new ArrayList<>();
        shardMap.values().forEach(shard -> futures.add(CompletableFuture.runAsync(shard::createSnapshot)));
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    public void fetchSnapshot(final SnapshotRequest request, final OutputStream outputStream) {
        final Shard shard = getShardForDocUuid(request.getPlanBDocRef().getUuid());
        if (shard instanceof final StoreShard storeShard) {
            try {
                final Path path = storeShard.getSnapshotZip();
                if (Files.exists(path)) {
                    StreamUtil.streamToStream(Files.newInputStream(path), outputStream);
                }
            } catch (final Exception e) {
                LOGGER.error(e::getMessage, e);
            }
        }
    }

    public <R> R get(final String mapName, final Function<AbstractDb<?, ?>, R> function) {
        final Shard shard = getShardForMapName(mapName);
        return shard.get(function);
    }

    public void cleanup() {
        shardMap.values().forEach(Shard::cleanup);
    }

    private Shard getShardForMapName(final String mapName) {
        final PlanBDoc doc = planBDocCache.get(mapName);
        if (doc == null) {
            LOGGER.warn(() -> "No PlanB doc found for '" + mapName + "'");
            throw new RuntimeException("No PlanB doc found for '" + mapName + "'");
        }
        return shardMap.computeIfAbsent(doc.getUuid(), k -> createShard(doc));
    }

    public Shard getShardForDocUuid(final String docUuid) {
        return shardMap.computeIfAbsent(docUuid, k -> {
            final PlanBDoc doc = planBDocStore.readDocument(DocRef.builder().type(PlanBDoc.TYPE).uuid(k).build());
            if (doc == null) {
                LOGGER.warn(() -> "No PlanB doc found for UUID '" + docUuid + "'");
                throw new RuntimeException("No PlanB doc found for UUID '" + docUuid + "'");
            }
            return createShard(doc);
        });
    }

    private Shard createShard(final PlanBDoc doc) {
        if (isSnapshotNode()) {
            return new SnapshotShard(byteBufferFactory,
                    configProvider,
                    statePaths,
                    fileTransferClient,
                    doc);
        }
        return new StoreShard(
                byteBufferFactory,
                configProvider,
                statePaths,
                doc);
    }
}
