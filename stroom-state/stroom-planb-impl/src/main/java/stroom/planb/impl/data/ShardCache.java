package stroom.planb.impl.data;

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.PlanBDocCache;
import stroom.planb.impl.io.StatePaths;
import stroom.planb.shared.PlanBDoc;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Singleton
class ShardCache {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ShardCache.class);

    private static final String CACHE_NAME = "PlanB Snapshot Cache";

    private final PlanBDocCache planBDocCache;
    private final Provider<PlanBConfig> configProvider;
    private final StatePaths statePaths;
    private final FileTransferClient fileTransferClient;
    private final LoadingStroomCache<String, Optional<Shard>> cache;

    @Inject
    public ShardCache(final PlanBDocCache planBDocCache,
                      final Provider<PlanBConfig> configProvider,
                      final StatePaths statePaths,
                      final FileTransferClient fileTransferClient,
                      final CacheManager cacheManager) {
        this.planBDocCache = planBDocCache;
        this.configProvider = configProvider;
        this.statePaths = statePaths;
        this.fileTransferClient = fileTransferClient;

        cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> configProvider.get().getSnapshotCache(),
                this::create,
                this::remove);
    }

    public Optional<Shard> get(final String mapName) {
        return cache.get(mapName);
    }

    private Optional<Shard> create(final String mapName) {
        try {
            LOGGER.info(() -> "Caching shard for '" + mapName + "'");
            final SnapshotRequest request = new SnapshotRequest(mapName, 0L);
            return Optional.ofNullable(getShard(request));
        } catch (final Exception e) {
            LOGGER.error(e::getMessage, e);
        }
        return Optional.empty();
    }

    private void remove(final String mapName,
                        final Optional<Shard> optional) {
        optional.ifPresent(shard -> {
            if (shard.snapshot()) {
                LOGGER.info(() -> "Deleting snapshot for '" + mapName + "'");
                FileUtil.deleteDir(shard.path());
            }
        });
    }

    private Shard getShard(final SnapshotRequest request) {
        final PlanBDoc doc = planBDocCache.get(request.getMapName());
        if (doc == null) {
            LOGGER.warn(() -> "No PlanB doc found for '" + request.getMapName() + "'");
            return null;
        }

        // See if we have it locally.
        final Path shardDir = statePaths.getShardDir().resolve(request.getMapName());
        if (Files.exists(shardDir)) {
            LOGGER.info(() -> "Found local shard for '" + request.getMapName() + "'");
            return new Shard(shardDir, false, doc);
        }

        // See if we have a snapshot.
        final Path snapshotDir = statePaths.getSnapshotDir().resolve(request.getMapName());
        if (Files.exists(snapshotDir)) {
            LOGGER.info(() -> "Found local snapshot for '" + request.getMapName() + "'");
            return new Shard(snapshotDir, true, doc);
        }

        // Go and get a snapshot.
        for (final String node : configProvider.get().getNodeList()) {
            try {
                fileTransferClient.fetchSnapshot(node, request, snapshotDir);
                return new Shard(snapshotDir, true, doc);
            } catch (final IOException e) {
                LOGGER.error(e::getMessage, e);
            }
        }

        return null;
    }

    public record Shard(Path path, boolean snapshot, PlanBDoc doc) {

    }
}
