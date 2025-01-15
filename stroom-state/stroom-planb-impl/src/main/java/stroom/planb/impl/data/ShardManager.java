package stroom.planb.impl.data;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.node.api.NodeInfo;
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.PlanBDocCache;
import stroom.planb.impl.db.AbstractLmdb;
import stroom.planb.impl.db.RangedStateDb;
import stroom.planb.impl.db.SessionDb;
import stroom.planb.impl.db.StateDb;
import stroom.planb.impl.db.StatePaths;
import stroom.planb.impl.db.TemporalRangedStateDb;
import stroom.planb.impl.db.TemporalStateDb;
import stroom.planb.shared.PlanBDoc;
import stroom.util.NullSafe;
import stroom.util.date.DateUtil;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.zip.ZipUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

@Singleton
public class ShardManager {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ShardManager.class);

    public static final String CLEANUP_TASK_NAME = "Plan B Cleanup";

    private static final String DATA_FILE_NAME = "data.mdb";
    private static final String LOCK_FILE_NAME = "lock.mdb";

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

    public void zip(final String mapName, final OutputStream outputStream) throws IOException {
        final Shard shard = getShard(mapName);
        shard.zip(outputStream);
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


    private static class LocalShard implements Shard {

        private final ByteBufferFactory byteBufferFactory;
        private final PlanBDocCache planBDocCache;
        private final Provider<PlanBConfig> configProvider;
        private final StatePaths statePaths;
        private final String mapName;

        private final ReentrantLock lock = new ReentrantLock();

        private volatile PlanBDoc doc;
        private final AtomicInteger useCount = new AtomicInteger();
        private volatile AbstractLmdb<?, ?> db;
        private volatile boolean open;
        private volatile Instant openTime;
        private volatile Instant lastAccessTime;

        public LocalShard(final ByteBufferFactory byteBufferFactory,
                          final PlanBDocCache planBDocCache,
                          final Provider<PlanBConfig> configProvider,
                          final StatePaths statePaths,
                          final String mapName) {
            this.byteBufferFactory = byteBufferFactory;
            this.planBDocCache = planBDocCache;
            this.configProvider = configProvider;
            this.statePaths = statePaths;
            this.mapName = mapName;
        }

        private void incrementUseCount() {
            lock.lock();
            try {
                // Open if needed.
                if (!open) {
                    open();
                    open = true;
                    openTime = Instant.now();
                }

                final int count = useCount.incrementAndGet();
                if (count <= 0) {
                    throw new RuntimeException("Unexpected count");
                }

                lastAccessTime = Instant.now();

            } finally {
                lock.unlock();
            }
        }

        private void decrementUseCount() {
            lock.lock();
            try {
                final int count = useCount.decrementAndGet();
                if (count < 0) {
                    throw new RuntimeException("Unexpected count");
                }
                cleanup();
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void merge(final Path sourceDir) {
            boolean success = false;

            // See if we can just merge by moving the file.
            lock.lock();
            try {
                // Get shard dir.
                final PlanBDoc doc = getDoc();
                final Path targetDir = statePaths.getShardDir().resolve(doc.getName());

                // If we don't already have the shard dir then just move the source to the target.
                if (!Files.isDirectory(targetDir)) {
                    try {
                        success = true;
                        Files.createDirectories(statePaths.getShardDir());
                        Files.move(sourceDir, targetDir);
                    } catch (final IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            } finally {
                lock.unlock();
            }

            // If the file already existed then we must open the DB and merge with LMDB.
            if (!success) {
                incrementUseCount();
                try {
                    db.merge(sourceDir);
                } finally {
                    decrementUseCount();
                }
            }
        }

        @Override
        public void zip(final OutputStream outputStream) {
            boolean success = false;

            // If the DB is not open then we can just create the zip from the dir.
            lock.lock();
            try {
                if (!open) {
                    success = true;
                    createZip(outputStream);
                }
            } finally {
                lock.unlock();
            }

            // If the DB was open then we will need to lock the DB and zip the dir.
            if (!success) {
                incrementUseCount();
                try {
                    db.lock(() -> createZip(outputStream));
                } finally {
                    decrementUseCount();
                }
            }
        }

        private void createZip(final OutputStream outputStream) {
            // Get shard dir.
            final Path dbDir = statePaths.getShardDir().resolve(mapName);
            if (!Files.exists(dbDir)) {
                throw new RuntimeException("Shard not found");
            }
            final Path lmdbDataFile = dbDir.resolve(DATA_FILE_NAME);
            if (!Files.exists(lmdbDataFile)) {
                throw new RuntimeException("LMDB data file not found");
            }

            try (final ZipArchiveOutputStream zipOutputStream =
                    ZipUtil.createOutputStream(new BufferedOutputStream(outputStream))) {
                ZipUtil.zip(dbDir, zipOutputStream);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public <R> R get(final Function<AbstractLmdb<?, ?>, R> function) {
            incrementUseCount();
            try {
                return function.apply(db);
            } finally {
                decrementUseCount();
            }
        }

        @Override
        public void cleanup() {
            lock.lock();
            try {
                if (useCount.get() == 0) {
                    if (open && isIdle()) {
                        db.close();
                        open = false;
                        openTime = null;
                    }
                }
            } finally {
                lock.unlock();
            }
        }

        private boolean isIdle() {
            return lastAccessTime.isBefore(Instant.now().minus(
                    configProvider.get().getMinTimeToKeepEnvOpen().getDuration()));
        }

        private PlanBDoc getDoc() {
            if (doc == null) {
                doc = planBDocCache.get(mapName);
                if (doc == null) {
                    LOGGER.warn(() -> "No PlanB doc found for '" + mapName + "'");
                    throw new RuntimeException("No PlanB doc found for '" + mapName + "'");
                }
            }
            return doc;
        }

        private void open() {
            final PlanBDoc doc = getDoc();
            final String mapName = doc.getName();

            final Path shardDir = statePaths.getShardDir().resolve(mapName);
            if (Files.exists(shardDir)) {
                LOGGER.info(() -> "Found local shard for '" + mapName + "'");
                db = openDb(doc, shardDir);


            } else {
                // If this node is supposed to be a node that stores shards, but it doesn't have it, then error.
                final String message = "Local Plan B shard not found for '" +
                                       mapName +
                                       "'";
                LOGGER.error(() -> message);
                throw new RuntimeException(message);
            }
        }

        private AbstractLmdb<?, ?> openDb(final PlanBDoc doc,
                                          final Path targetPath) {
            switch (doc.getStateType()) {
                case STATE -> {
                    return new StateDb(targetPath, byteBufferFactory, true, false);
                }
                case TEMPORAL_STATE -> {
                    return new TemporalStateDb(targetPath, byteBufferFactory, true, false);
                }
                case RANGED_STATE -> {
                    return new RangedStateDb(targetPath, byteBufferFactory, true, false);
                }
                case TEMPORAL_RANGED_STATE -> {
                    return new TemporalRangedStateDb(targetPath, byteBufferFactory, true, false);
                }
                case SESSION -> {
                    return new SessionDb(targetPath, byteBufferFactory, true, false);
                }
                default -> throw new RuntimeException("Unexpected state type: " + doc.getStateType());
            }
        }
    }

    private static class SnapshotShard implements Shard {

        private final ByteBufferFactory byteBufferFactory;
        private final PlanBDocCache planBDocCache;
        private final Provider<PlanBConfig> configProvider;
        private final StatePaths statePaths;
        private final FileTransferClient fileTransferClient;
        private final String mapName;
        private final Instant createTime;

        private final ReentrantLock lock = new ReentrantLock();

        private volatile SnapshotInstance snapshotInstance;

        public SnapshotShard(final ByteBufferFactory byteBufferFactory,
                             final PlanBDocCache planBDocCache,
                             final Provider<PlanBConfig> configProvider,
                             final StatePaths statePaths,
                             final FileTransferClient fileTransferClient,
                             final String mapName) {
            this.byteBufferFactory = byteBufferFactory;
            this.planBDocCache = planBDocCache;
            this.configProvider = configProvider;
            this.statePaths = statePaths;
            this.fileTransferClient = fileTransferClient;
            this.mapName = mapName;
            this.createTime = Instant.now();
        }

        private SnapshotInstance getDBInstance() {
            SnapshotInstance instance;
            lock.lock();
            try {
                instance = snapshotInstance;
                if (instance == null) {
                    instance = new SnapshotInstance(
                            byteBufferFactory,
                            planBDocCache,
                            configProvider,
                            statePaths,
                            fileTransferClient,
                            mapName,
                            createTime);

                } else if (instance.isOldSnapshot()) {
                    instance.destroy();
                    instance = new SnapshotInstance(
                            byteBufferFactory,
                            planBDocCache,
                            configProvider,
                            statePaths,
                            fileTransferClient,
                            mapName,
                            createTime);

                } else if (instance.destroy) {
                    instance = new SnapshotInstance(
                            byteBufferFactory,
                            planBDocCache,
                            configProvider,
                            statePaths,
                            fileTransferClient,
                            mapName,
                            createTime);
                }

                snapshotInstance = instance;
            } finally {
                lock.unlock();
            }
            return instance;
        }

        @Override
        public void merge(final Path sourceDir) {
            throw new RuntimeException("Merge is not supported on snapshots");
        }

        @Override
        public void zip(final OutputStream outputStream) {
            throw new RuntimeException("Zip is not supported on snapshots");
        }

        @Override
        public <R> R get(final Function<AbstractLmdb<?, ?>, R> function) {
            R result = null;

            boolean success = false;
            while (!success) {
                try {
                    success = true;
                    final SnapshotInstance instance = getDBInstance();
                    result = instance.get(function);
                } catch (final DestroyedException e) {
                    LOGGER.debug(e::getMessage, e);
                    success = false;
                }
            }

            return result;
        }

        @Override
        public void cleanup() {
            getDBInstance().cleanup();
        }
    }

    private static class SnapshotInstance {

        private final ByteBufferFactory byteBufferFactory;
        private final PlanBDocCache planBDocCache;
        private final Provider<PlanBConfig> configProvider;
        private final StatePaths statePaths;
        private final FileTransferClient fileTransferClient;
        private final String mapName;
        private final Instant createTime;

        private final ReentrantLock lock = new ReentrantLock();

        private volatile PlanBDoc doc;
        private final AtomicInteger useCount = new AtomicInteger();
        private volatile AbstractLmdb<?, ?> db;
        private volatile boolean open;
        private volatile Instant openTime;
        private volatile Instant lastAccessTime;
        private volatile boolean destroy;

        public SnapshotInstance(final ByteBufferFactory byteBufferFactory,
                                final PlanBDocCache planBDocCache,
                                final Provider<PlanBConfig> configProvider,
                                final StatePaths statePaths,
                                final FileTransferClient fileTransferClient,
                                final String mapName,
                                final Instant createTime) {
            this.byteBufferFactory = byteBufferFactory;
            this.planBDocCache = planBDocCache;
            this.configProvider = configProvider;
            this.statePaths = statePaths;
            this.fileTransferClient = fileTransferClient;
            this.mapName = mapName;
            this.createTime = createTime;
        }

        private void incrementUseCount() throws DestroyedException {
            lock.lock();
            try {
                if (destroy) {
                    throw new DestroyedException();
                }

                // Open if needed.
                if (!open) {
                    open();
                    open = true;
                    openTime = Instant.now();
                }

                final int count = useCount.incrementAndGet();
                if (count <= 0) {
                    throw new RuntimeException("Unexpected count");
                }

                lastAccessTime = Instant.now();

            } finally {
                lock.unlock();
            }
        }

        private void decrementUseCount() {
            lock.lock();
            try {
                final int count = useCount.decrementAndGet();
                if (count < 0) {
                    throw new RuntimeException("Unexpected count");
                }
                cleanup();
            } finally {
                lock.unlock();
            }
        }

        public <R> R get(final Function<AbstractLmdb<?, ?>, R> function) throws DestroyedException {
            incrementUseCount();
            try {
                return function.apply(db);
            } finally {
                decrementUseCount();
            }
        }

        public void destroy() {
            lock.lock();
            try {
                destroy = true;
                cleanup();
            } finally {
                lock.unlock();
            }
        }


        private void cleanup() {
            lock.lock();
            try {
                if (useCount.get() == 0) {
                    if (open && (destroy || isIdle())) {
                        db.close();
                        open = false;
                        openTime = null;
                    }

                    if (!open && destroy) {
                        // Delete if this is an old snapshot, i.e. readonly.
                        try {
                            LOGGER.info(() -> "Deleting snapshot for '" + getDoc().getName() + "'");
                            db.delete();
                        } catch (final Exception e) {
                            LOGGER.error(e::getMessage, e);
                        }
                    }
                }
            } finally {
                lock.unlock();
            }
        }

        private boolean isIdle() {
            return lastAccessTime.isBefore(Instant.now().minus(
                    configProvider.get().getMinTimeToKeepEnvOpen().getDuration()));
        }

        public boolean isOldSnapshot() {
            return createTime.isBefore(Instant.now().minus(
                    configProvider.get().getMinTimeToKeepSnapshots().getDuration()));
        }

        private PlanBDoc getDoc() {
            if (doc == null) {
                doc = planBDocCache.get(mapName);
                if (doc == null) {
                    LOGGER.warn(() -> "No PlanB doc found for '" + mapName + "'");
                    throw new RuntimeException("No PlanB doc found for '" + mapName + "'");
                }
            }
            return doc;
        }

        private void open() {
            final PlanBDoc doc = getDoc();
            final String mapName = doc.getName();

            // See if we have a snapshot.
            final Path snapshotDir = statePaths
                    .getSnapshotDir()
                    .resolve(mapName)
                    .resolve(DateUtil.createFileDateTimeString(createTime));

            final Path lmdbDataFile = snapshotDir.resolve(DATA_FILE_NAME);
            if (Files.exists(snapshotDir) && !Files.exists(lmdbDataFile)) {
                LOGGER.info(() -> "Found local snapshot for '" + mapName + "'");
                db = openDb(doc, snapshotDir);

            } else {
                // Go and get a snapshot.
                final SnapshotRequest request = new SnapshotRequest(mapName, 0L);
                for (final String node : configProvider.get().getNodeList()) {
                    try {
                        LOGGER.info(() -> "Fetching shard for '" + mapName + "'");

                        // Create dir.
                        Files.createDirectories(snapshotDir);

                        fileTransferClient.fetchSnapshot(node, request, snapshotDir);
                        db = openDb(doc, snapshotDir);

                    } catch (final Exception e) {
                        LOGGER.warn(e::getMessage, e);
                    }
                }
            }

            throw new RuntimeException("Unable to get snapshot shard for '" + mapName + "'");
        }

        private AbstractLmdb<?, ?> openDb(final PlanBDoc doc,
                                          final Path targetPath) {
            switch (doc.getStateType()) {
                case STATE -> {
                    return new StateDb(targetPath, byteBufferFactory, false, true);
                }
                case TEMPORAL_STATE -> {
                    return new TemporalStateDb(targetPath, byteBufferFactory, false, true);
                }
                case RANGED_STATE -> {
                    return new RangedStateDb(targetPath, byteBufferFactory, false, true);
                }
                case TEMPORAL_RANGED_STATE -> {
                    return new TemporalRangedStateDb(targetPath, byteBufferFactory, false, true);
                }
                case SESSION -> {
                    return new SessionDb(targetPath, byteBufferFactory, false, true);
                }
                default -> throw new RuntimeException("Unexpected state type: " + doc.getStateType());
            }
        }
    }

    private interface Shard {

        void merge(Path sourceDir);

        void zip(OutputStream outputStream);

        <R> R get(Function<AbstractLmdb<?, ?>, R> function);

        void cleanup();
    }

    private static class DestroyedException extends Exception {

    }
}
