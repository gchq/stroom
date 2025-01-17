package stroom.planb.impl.data;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.node.api.NodeInfo;
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.PlanBDocCache;
import stroom.planb.impl.db.AbstractLmdb;
import stroom.planb.impl.db.StatePaths;
import stroom.util.NullSafe;
import stroom.util.io.FileUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Singleton
public class ShardManager {

    public static final String CLEANUP_TASK_NAME = "Plan B Cleanup";

    private final ByteBufferFactory byteBufferFactory;
    private final PlanBDocCache planBDocCache;
    private final Map<String, Shard> shardMap = new ConcurrentHashMap<>();
    private final NodeInfo nodeInfo;
    private final Provider<PlanBConfig> configProvider;
    private final StatePaths statePaths;
    private final FileTransferClient fileTransferClient;

    @Inject
    public ShardManager(final ByteBufferFactory byteBufferFactory,
                        final PlanBDocCache planBDocCache,
                        final NodeInfo nodeInfo,
                        final Provider<PlanBConfig> configProvider,
                        final StatePaths statePaths,
                        final FileTransferClient fileTransferClient) {
        this.byteBufferFactory = byteBufferFactory;
        this.planBDocCache = planBDocCache;
        this.nodeInfo = nodeInfo;
        this.configProvider = configProvider;
        this.statePaths = statePaths;
        this.fileTransferClient = fileTransferClient;

        // Delete any existing shards that might have been left behind from the last use of Stroom.
        FileUtil.deleteDir(statePaths.getShardDir());
    }

    private boolean isSnapshotNode() {
        final List<String> nodes = NullSafe.list(configProvider.get().getNodeList());
        // If we have no node info or no nodes are configured then treat this as a shard writer node and not a
        // snapshot node.
        return nodeInfo != null && !nodes.isEmpty() && !nodes.contains(nodeInfo.getThisNodeName());
    }

    public void merge(final Path sourceDir) throws IOException {
        final String mapName = sourceDir.getFileName().toString();
        final Shard shard = getShard(mapName);
        shard.merge(sourceDir);
    }

    public void checkSnapshotStatus(final SnapshotRequest request) {
        final Shard shard = getShard(request.getMapName());
        shard.checkSnapshotStatus(request);
    }

    public void createSnapshot(final SnapshotRequest request, final OutputStream outputStream) {
        final Shard shard = getShard(request.getMapName());
        shard.createSnapshot(request, outputStream);
    }

    public <R> R get(final String mapName, final Function<AbstractLmdb<?, ?>, R> function) {
        final Shard shard = getShard(mapName);
        return shard.get(function);
    }

    public void cleanup() {
        shardMap.values().forEach(Shard::cleanup);
    }

    private Shard getShard(final String mapName) {
        return shardMap.computeIfAbsent(mapName, this::createShard);
    }

    private Shard createShard(final String mapName) {
        if (isSnapshotNode()) {
            return new SnapshotShard(byteBufferFactory,
                    planBDocCache,
                    configProvider,
                    statePaths,
                    fileTransferClient,
                    mapName);
        }
        return new LocalShard(
                byteBufferFactory,
                planBDocCache,
                configProvider,
                statePaths,
                mapName);
    }
}
