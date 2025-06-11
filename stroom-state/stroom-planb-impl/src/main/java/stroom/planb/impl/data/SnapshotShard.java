package stroom.planb.impl.data;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.db.AbstractDb;
import stroom.planb.impl.db.PlanBDb;
import stroom.planb.impl.db.StatePaths;
import stroom.planb.shared.PlanBDoc;
import stroom.util.date.DateUtil;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Provider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

class SnapshotShard implements Shard {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SnapshotShard.class);

    private final ByteBufferFactory byteBufferFactory;
    private final Provider<PlanBConfig> configProvider;
    private final StatePaths statePaths;
    private final FileTransferClient fileTransferClient;
    private final PlanBDoc doc;

    private final ReentrantLock lock = new ReentrantLock();

    private volatile SnapshotInstance snapshotInstance;

    public SnapshotShard(final ByteBufferFactory byteBufferFactory,
                         final Provider<PlanBConfig> configProvider,
                         final StatePaths statePaths,
                         final FileTransferClient fileTransferClient,
                         final PlanBDoc doc) {
        this.byteBufferFactory = byteBufferFactory;
        this.configProvider = configProvider;
        this.statePaths = statePaths;
        this.fileTransferClient = fileTransferClient;
        this.doc = doc;

        snapshotInstance = new SnapshotInstance(
                byteBufferFactory,
                configProvider,
                statePaths,
                fileTransferClient,
                doc,
                Instant.now(),
                null);
    }

    private SnapshotInstance getDBInstance() {
        final SnapshotInstance instance = snapshotInstance;

        // If the current instance has expired then asynchronously try to get a new shapshot.
        if (instance.getExpiryTime().isBefore(Instant.now())) {
            CompletableFuture.runAsync(() -> {
                lock.lock();
                try {
                    // Check again if the current instance has expired then asynchronously try to get a new
                    // shapshot.
                    final SnapshotInstance currentInstance = snapshotInstance;
                    if (currentInstance.getExpiryTime().isBefore(Instant.now())) {
                        final SnapshotInstance newInstance = new SnapshotInstance(
                                byteBufferFactory,
                                configProvider,
                                statePaths,
                                fileTransferClient,
                                doc,
                                Instant.now(),
                                currentInstance.getCurrentSnapshotTime());

                        // If the new shapshot had problems fetching then keep using the current one and extend
                        // it's expiry time so we don't keep fetching.
                        if (newInstance.hasFetchException()) {
                            // Extend the expiry time of the current instance so we don't just keep infinitely retrying
                            // to update this snapshot.
                            currentInstance.extendExpiry(
                                    configProvider.get().getSnapshotRetryFetchInterval().getDuration());

                        } else {
                            // Switch the instance and destroy the previous instance.
                            snapshotInstance = newInstance;
                            currentInstance.destroy();
                        }
                    }
                } finally {
                    lock.unlock();
                }
            });
        }

        return instance;
    }

    @Override
    public void merge(final Path sourceDir) {
        throw new RuntimeException("Merge is not supported on snapshots");
    }

    @Override
    public void condense(final PlanBDoc doc) {
        // Condense is not supported on snapshots
    }

    @Override
    public void checkSnapshotStatus(final SnapshotRequest request) {
    }

    @Override
    public void createSnapshot() {
    }

    @Override
    public <R> R get(final Function<AbstractDb<?, ?>, R> function) {
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

    @Override
    public void delete() {
        getDBInstance().destroy();
    }

    private static class SnapshotInstance {

        private final ByteBufferFactory byteBufferFactory;
        private final Provider<PlanBConfig> configProvider;
        private final PlanBDoc doc;
        private final Path dbDir;
        private final RuntimeException fetchException;
        private final ReentrantLock lock = new ReentrantLock();
        private final AtomicInteger useCount = new AtomicInteger();
        private final Instant currentSnapshotTime;

        private volatile AbstractDb<?, ?> db;
        private volatile boolean open;
        private volatile Instant lastAccessTime;
        private volatile Instant expiryTime;
        private volatile boolean destroy;

        public SnapshotInstance(final ByteBufferFactory byteBufferFactory,
                                final Provider<PlanBConfig> configProvider,
                                final StatePaths statePaths,
                                final FileTransferClient fileTransferClient,
                                final PlanBDoc doc,
                                final Instant createTime,
                                final Instant previousSnapshotTime) {
            Instant currentSnapshotTime = null;
            Instant expiryTime = null;
            Path dbDir = null;
            RuntimeException fetchException = null;

            try {
                // Get the snapshot dir.
                dbDir = statePaths
                        .getSnapshotDir()
                        .resolve(doc.getUuid())
                        .resolve(DateUtil.createFileDateTimeString(createTime));

                // Create dir.
                Files.createDirectories(dbDir);

                // Go and get a snapshot.
                boolean fetchComplete = false;
                final SnapshotRequest request = new SnapshotRequest(
                        doc.asDocRef(),
                        0L,
                        NullSafe.get(previousSnapshotTime, Instant::toEpochMilli));
                for (final String node : configProvider.get().getNodeList()) {
                    LOGGER.info(() -> "Fetching shard for '" + doc + "'");

                    // Fetch snapshot.
                    currentSnapshotTime = fileTransferClient.fetchSnapshot(node, request, dbDir);
                    // Remember that we successfully fetched.
                    fetchComplete = true;
                    // Determine how long we will keep this snapshot.
                    expiryTime = createTime.plus(configProvider.get().getMinTimeToKeepSnapshots().getDuration());
                    // Exit for loop.
                    break;
                }

                if (!fetchComplete) {
                    throw new RuntimeException("Unable to get snapshot shard for '" + doc + "'");
                }
            } catch (final Exception e) {
                LOGGER.debug(e::getMessage, e);
                fetchException = new RuntimeException(e);
                // If we have an exception then we will want to retry getting a snapshot so expire soon.
                expiryTime = createTime.plus(configProvider.get().getSnapshotRetryFetchInterval());
            }

            this.byteBufferFactory = byteBufferFactory;
            this.configProvider = configProvider;
            this.currentSnapshotTime = currentSnapshotTime;
            this.expiryTime = expiryTime;
            this.doc = doc;
            this.dbDir = dbDir;
            this.fetchException = fetchException;
        }

        public boolean hasFetchException() {
            return fetchException != null;
        }

        public Instant getCurrentSnapshotTime() {
            return currentSnapshotTime;
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

        public <R> R get(final Function<AbstractDb<?, ?>, R> function) throws DestroyedException {
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
                        db = null;
                        open = false;
                    }

                    if (!open && destroy) {
                        // Delete if this is an old snapshot.
                        try {
                            LOGGER.info(() -> "Deleting snapshot for '" + doc + "'");
                            FileUtil.deleteDir(dbDir);
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

        public Instant getExpiryTime() {
            return expiryTime;
        }

        public void extendExpiry(final Duration amount) {
            this.expiryTime = Instant.now().plus(amount);
        }

        private void open() {
            if (fetchException != null) {
                throw fetchException;
            }

            final String mapName = doc.getName();

            // If we already fetched the snapshot then reopen.
            LOGGER.debug(() -> "Opening local snapshot for '" + mapName + "'");
            db = PlanBDb.open(doc, dbDir, byteBufferFactory, true);
        }
    }

    @Override
    public PlanBDoc getDoc() {
        return doc;
    }

    private static class DestroyedException extends Exception {

    }
}
