package stroom.planb.impl.data;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.node.api.NodeInfo;
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.PlanBDocCache;
import stroom.planb.impl.io.AbstractLmdbReader;
import stroom.planb.impl.io.RangedStateReader;
import stroom.planb.impl.io.SessionReader;
import stroom.planb.impl.io.StatePaths;
import stroom.planb.impl.io.StateReader;
import stroom.planb.impl.io.TemporalRangedStateReader;
import stroom.planb.impl.io.TemporalStateReader;
import stroom.planb.shared.PlanBDoc;
import stroom.util.NullSafe;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.lmdbjava.Env.AlreadyClosedException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

@Singleton
public class ReaderCache {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ReaderCache.class);

    private static final String CACHE_NAME = "Plan B Reader Cache";

    private final LoadingStroomCache<String, Shard> cache;
    private final ByteBufferFactory byteBufferFactory;
    private final PlanBDocCache planBDocCache;
    private final Provider<PlanBConfig> configProvider;
    private final StatePaths statePaths;
    private final FileTransferClient fileTransferClient;
    private final NodeInfo nodeInfo;

    @Inject
    public ReaderCache(final Provider<PlanBConfig> configProvider,
                       final CacheManager cacheManager,
                       final ByteBufferFactory byteBufferFactory,
                       final PlanBDocCache planBDocCache,
                       final StatePaths statePaths,
                       final FileTransferClient fileTransferClient,
                       final NodeInfo nodeInfo) {
        this.byteBufferFactory = byteBufferFactory;
        this.planBDocCache = planBDocCache;
        this.configProvider = configProvider;
        this.statePaths = statePaths;
        this.fileTransferClient = fileTransferClient;
        this.nodeInfo = nodeInfo;

        cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> configProvider.get().getReaderCache(),
                this::create,
                this::destroy);
    }

    public <R> R get(final String mapName,
                     final Function<AbstractLmdbReader<?, ?>, R> function) {
        while (true) {
            final Shard shard = cache.get(mapName);
            try {
                return shard.get(function);
            } catch (final AlreadyClosedException e) {
                // Expected exception.
                LOGGER.debug(e::getMessage, e);
            }
        }
    }

    private Shard create(final String mapName) {
        return new Shard(
                mapName,
                statePaths,
                planBDocCache,
                configProvider,
                fileTransferClient,
                byteBufferFactory,
                nodeInfo);
    }

    private void destroy(final String mapName,
                         final Shard shard) {
        shard.close();
    }

    public static class Shard {

        private final String mapName;
        private Path path;
        private boolean snapshot;
        private final PlanBDoc doc;
        private final ByteBufferFactory byteBufferFactory;

        private RuntimeException exception;
        private AbstractLmdbReader<?, ?> currentReader;
        private final ReentrantLock readLock = new ReentrantLock();
        private final Condition readCondition = readLock.newCondition();
        private final AtomicInteger currentReadCount = new AtomicInteger();
        private volatile boolean closed;

        public Shard(final String mapName,
                     final StatePaths statePaths,
                     final PlanBDocCache planBDocCache,
                     final Provider<PlanBConfig> configProvider,
                     final FileTransferClient fileTransferClient,
                     final ByteBufferFactory byteBufferFactory,
                     final NodeInfo nodeInfo) {
            this.mapName = mapName;
            this.byteBufferFactory = byteBufferFactory;
            doc = planBDocCache.get(mapName);
            if (doc == null) {
                LOGGER.warn(() -> "No PlanB doc found for '" + mapName + "'");
                exception = new RuntimeException("No PlanB doc found for '" + mapName + "'");

            } else {
                final List<String> nodes = NullSafe.list(configProvider.get().getNodeList());
                final boolean isStoreNode = nodes.contains(nodeInfo.getThisNodeName());

                // See if we have it locally.
                final Path shardDir = statePaths.getShardDir().resolve(mapName);
                if (Files.exists(shardDir)) {
                    LOGGER.info(() -> "Found local shard for '" + mapName + "'");
                    this.path = shardDir;
                    this.snapshot = false;
                    this.currentReader = createReader();

                } else if (isStoreNode) {
                    // If this node is supposed to be a node that stores shards, but it doesn't have it, then error.
                    final String message = "Local Plan B shard not found for '" +
                                           mapName +
                                           "'";
                    LOGGER.error(() -> message);
                    exception = new RuntimeException(message);

                } else if (nodes.isEmpty()) {
                    final String message = "Local Plan B shard not found for '" +
                                           mapName +
                                           "' and no remote nodes are configured";
                    LOGGER.error(() -> message);
                    exception = new RuntimeException(message);

                } else {

                    // See if we have a snapshot.
                    final Path snapshotDir = statePaths.getSnapshotDir().resolve(mapName);
                    if (Files.exists(snapshotDir)) {
                        LOGGER.info(() -> "Found local snapshot for '" + mapName + "'");
                        this.path = snapshotDir;
                        this.snapshot = true;
                        this.currentReader = createReader();

                    } else {
                        // Go and get a snapshot.
                        final SnapshotRequest request = new SnapshotRequest(mapName, 0L);
                        for (final String node : configProvider.get().getNodeList()) {
                            try {
                                LOGGER.info(() -> "Fetching shard for '" + mapName + "'");
                                fileTransferClient.fetchSnapshot(node, request, snapshotDir);
                                this.path = snapshotDir;
                                this.snapshot = true;
                                this.currentReader = createReader();

                            } catch (final Exception e) {
                                LOGGER.error(e::getMessage, e);
                                exception = new RuntimeException(e);
                            }
                        }
                    }
                }
            }
        }

        public <R> R get(final Function<AbstractLmdbReader<?, ?>, R> function) {
            if (exception != null) {
                throw exception;
            }

            // Count up readers.
            readLock.lock();
            try {
                if (closed) {
                    throw new AlreadyClosedException();
                }
                currentReadCount.incrementAndGet();
            } finally {
                readLock.unlock();
            }

            try {
                return function.apply(currentReader);
            } finally {
                // Count down readers.
                readLock.lock();
                try {
                    currentReadCount.decrementAndGet();
                    readCondition.signalAll();
                } finally {
                    readLock.unlock();
                }
            }
        }

        private AbstractLmdbReader<?, ?> createReader() {
            switch (doc.getStateType()) {
                case STATE -> {
                    return new StateReader(path, byteBufferFactory);
                }
                case TEMPORAL_STATE -> {
                    return new TemporalStateReader(path, byteBufferFactory);
                }
                case RANGED_STATE -> {
                    return new RangedStateReader(path, byteBufferFactory);
                }
                case TEMPORAL_RANGED_STATE -> {
                    return new TemporalRangedStateReader(path, byteBufferFactory);
                }
                case SESSION -> {
                    return new SessionReader(path, byteBufferFactory);
                }
                default -> throw new RuntimeException("Unexpected state type: " + doc.getStateType());
            }
        }

        public void close() {
            try {
                // Don't allow close until we have nobody reading.
                readLock.lock();
                try {
                    // Make sure new reads will end up going and getting a new shard.
                    closed = true;

                    // Wait for all current reads to stop.
                    while (currentReadCount.get() > 0) {
                        readCondition.await();
                    }
                } finally {
                    readLock.unlock();
                }
            } catch (final InterruptedException e) {
                LOGGER.debug(e::getMessage, e);
                Thread.currentThread().interrupt();

            } finally {
                closeReader();
                if (snapshot) {
                    deleteSnapshot();
                }
            }
        }

        private void closeReader() {
            try {
                LOGGER.info(() -> "Closing reader for '" + mapName + "'");
                currentReader.close();
            } catch (final Exception e) {
                LOGGER.error(e::getMessage, e);
            }
        }

        private void deleteSnapshot() {
            try {
                LOGGER.info(() -> "Deleting snapshot for '" + mapName + "'");
                FileUtil.deleteDir(path);
            } catch (final Exception e) {
                LOGGER.error(e::getMessage, e);
            }
        }
    }
}
