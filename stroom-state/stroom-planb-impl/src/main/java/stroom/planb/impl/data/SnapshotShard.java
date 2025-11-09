package stroom.planb.impl.data;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.db.Db;
import stroom.planb.impl.db.PlanBDb;
import stroom.planb.impl.db.StatePaths;
import stroom.planb.shared.PlanBDoc;
import stroom.util.date.DateUtil;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Provider;
import org.lmdbjava.LmdbException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

class SnapshotShard implements Shard {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SnapshotShard.class);

    private final ByteBuffers byteBuffers;
    private final ByteBufferFactory byteBufferFactory;
    private final Provider<PlanBConfig> configProvider;
    private final StatePaths statePaths;
    private final FileTransferClient fileTransferClient;
    private final PlanBDoc doc;

    private final ReentrantLock lock = new ReentrantLock();

    private volatile SnapshotInstance snapshotInstance;

    public SnapshotShard(final ByteBuffers byteBuffers,
                         final ByteBufferFactory byteBufferFactory,
                         final Provider<PlanBConfig> configProvider,
                         final StatePaths statePaths,
                         final FileTransferClient fileTransferClient,
                         final PlanBDoc doc) {
        this.byteBuffers = byteBuffers;
        this.byteBufferFactory = byteBufferFactory;
        this.configProvider = configProvider;
        this.statePaths = statePaths;
        this.fileTransferClient = fileTransferClient;
        this.doc = doc;

        snapshotInstance = new SnapshotInstance(
                byteBuffers,
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
                                byteBuffers,
                                byteBufferFactory,
                                configProvider,
                                statePaths,
                                fileTransferClient,
                                doc,
                                Instant.now(),
                                currentInstance.getCurrentSnapshotTime());

                        // If the new shapshot had problems fetching then keep using the current one and extend
                        // its expiry time so we don't keep fetching.
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
    public long deleteOldData(final PlanBDoc doc) {
        // Deletion of old data is not supported on snapshots
        return 0L;
    }

    @Override
    public long condense(final PlanBDoc doc) {
        // Condense is not supported on snapshots
        return 0L;
    }

    @Override
    public void compact() {
        // Compact is not supported on snapshots
    }

    @Override
    public void checkSnapshotStatus(final SnapshotRequest request) {
    }

    @Override
    public void createSnapshot() {
    }

    @Override
    public <R> R get(final Function<Db<?, ?>, R> function) {
        R result = null;

        boolean success = false;
        while (!success) {
            try {
                success = true;
                final SnapshotInstance instance = getDBInstance();
                result = instance.get(function);
            } catch (final TryAgainException e) {
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
    public boolean delete() {
        getDBInstance().destroy();
        return true;
    }

    private static class SnapshotInstance {

        private final ByteBuffers byteBuffers;
        private final ByteBufferFactory byteBufferFactory;
        private final Provider<PlanBConfig> configProvider;
        private final PlanBDoc doc;
        private final Path dbDir;
        private final RuntimeException fetchException;
        private final ReentrantLock lock = new ReentrantLock();
        private final Instant currentSnapshotTime;

        private volatile Db<?, ?> db;
        private volatile boolean open;
        private volatile Instant lastAccessTime;
        private volatile Instant expiryTime;
        private volatile boolean destroy;

        public SnapshotInstance(final ByteBuffers byteBuffers,
                                final ByteBufferFactory byteBufferFactory,
                                final Provider<PlanBConfig> configProvider,
                                final StatePaths statePaths,
                                final FileTransferClient fileTransferClient,
                                final PlanBDoc doc,
                                final Instant createTime,
                                final Instant previousSnapshotTime) {
            this.byteBufferFactory = byteBufferFactory;
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
                    LOGGER.info(() -> "Fetching shard for '" + doc.asDocRef() + "'");

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
                    throw new RuntimeException("Unable to get snapshot shard for '" + doc.asDocRef() + "'");
                }
            } catch (final Exception e) {
                LOGGER.debug(e::getMessage, e);
                fetchException = new RuntimeException(e);
                // If we have an exception then we will want to retry getting a snapshot so expire soon.
                expiryTime = createTime.plus(configProvider.get().getSnapshotRetryFetchInterval());
            }

            this.byteBuffers = byteBuffers;
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

        public <R> R get(final Function<Db<?, ?>, R> function) throws TryAgainException {
            lastAccessTime = Instant.now();
            final Db<?, ?> db = this.db;
            if (db != null) {
                try {
                    return function.apply(db);
                } catch (final LmdbException e) {
                    LOGGER.debug(e::getMessage, e);
                }
            }

            // Try opening the DB amd make the caller try again.
            tryOpen();
            throw new TryAgainException();
        }

        public void destroy() {
            destroy = true;
            lock.lock();
            try {
                cleanup();
            } finally {
                lock.unlock();
            }
        }

        private void cleanup() {
            lock.lock();
            try {
                if (open && (destroy || isIdle())) {
                    db.close();
                    db = null;
                    open = false;
                }

                if (!open && destroy) {
                    // Delete if this is an old snapshot.
                    try {
                        LOGGER.info(() -> "Deleting snapshot for '" + doc.asDocRef() + "'");
                        FileUtil.deleteDir(dbDir);
                    } catch (final Exception e) {
                        LOGGER.error(e::getMessage, e);
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
            if (!open) {
                if (fetchException != null) {
                    throw fetchException;
                }

                final String mapName = doc.getName();

                // If we already fetched the snapshot then reopen.
                LOGGER.debug(() -> "Opening local snapshot for '" + mapName + "'");
                db = PlanBDb.open(doc, dbDir, byteBuffers, byteBufferFactory, true);
                open = true;
            }
        }

        private void tryOpen() {
            lock.lock();
            try {
                // Open if needed.
                if (!destroy && !open) {
                    open();
                    open = true;
                }
            } finally {
                lock.unlock();
            }
        }

        public String getInfo() throws TryAgainException {
            final Db<?, ?> db = this.db;
            if (db != null) {
                try {
                    return db.getInfoString();
                } catch (final LmdbException e) {
                    LOGGER.debug(e::getMessage, e);
                }
            }

            // Try opening the DB amd make the caller try again.
            tryOpen();
            throw new TryAgainException();
        }
    }

    @Override
    public PlanBDoc getDoc() {
        return doc;
    }

    @Override
    public String getInfo() {
        String result = null;
        boolean success = false;
        while (!success) {
            try {
                success = true;
                final SnapshotInstance instance = getDBInstance();
                result = instance.getInfo();
            } catch (final TryAgainException e) {
                LOGGER.debug(e::getMessage, e);
                success = false;
            }
        }
        return result;
    }

    private static class TryAgainException extends Exception {

    }
}
