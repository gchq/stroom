package stroom.planb.impl.data;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.docref.DocRef;
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.db.Db;
import stroom.planb.impl.db.PlanBDb;
import stroom.planb.impl.db.StatePaths;
import stroom.planb.shared.PlanBDoc;
import stroom.util.date.DateUtil;
import stroom.util.io.FileUtil;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Provider;
import org.jspecify.annotations.NonNull;
import org.lmdbjava.LmdbException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

class SnapshotShard implements Shard {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SnapshotShard.class);
    private static final long LAST_ACCESS_TIME_TOUCH_INTERVAL_MS = 10_000;

    private final ByteBuffers byteBuffers;
    private final Provider<PlanBConfig> configProvider;
    private final StatePaths statePaths;
    private final FileTransferClient fileTransferClient;
    private final PlanBDoc doc;
    private final DocRef docRef;

    private volatile SnapshotInstance snapshotInstance;
    private final ReentrantLock snapshotUpdateLock = new ReentrantLock();

    public SnapshotShard(final ByteBuffers byteBuffers,
                         final Provider<PlanBConfig> configProvider,
                         final StatePaths statePaths,
                         final FileTransferClient fileTransferClient,
                         final PlanBDoc doc) {
        this.byteBuffers = byteBuffers;
        this.configProvider = configProvider;
        this.statePaths = statePaths;
        this.fileTransferClient = fileTransferClient;
        this.doc = Objects.requireNonNull(doc);
        this.docRef = doc.asDocRef();

        snapshotInstance = new SnapshotInstance(
                byteBuffers,
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
        if (instance.hasExpired()) {
            // Need to spawn the thread before the re-test so that the spawned thread can hold the lock
            CompletableFuture.runAsync(() -> {
                // Complete this thread ASAP if another thread is doing it, we can use the existing instance
                // in the meantime. Else, lots of threads would all block waiting for one to finish the
                // update, when they could be using the existing instance
                if (snapshotUpdateLock.tryLock()) {
                    try {
                        final SnapshotInstance currentInstance = snapshotInstance;
                        // Check again if the current instance has expired then asynchronously try to get a new
                        // shapshot.
                        // TODO why was this testing instance rather than currentInstance?
                        //  Another thread may have updated snapshotInstance before we got the lock
                        if (currentInstance.hasExpired()) {
                            final SnapshotInstance newInstance = new SnapshotInstance(
                                    byteBuffers,
                                    configProvider,
                                    statePaths,
                                    fileTransferClient,
                                    doc,
                                    Instant.now(),
                                    currentInstance.getCurrentSnapshotTime());

                            // If the new shapshot had problems fetching then keep using the current one and extend
                            // its expiry time so we don't keep fetching.
                            if (newInstance.hasFetchException()) {
                                // Extend the expiry time of the current instance so we don't just
                                // keep infinitely retrying to update this snapshot.
                                currentInstance.extendExpiry(
                                        configProvider.get().getSnapshotRetryFetchInterval().getDuration());
                            } else {
                                // Switch the instance and destroy the previous instance.
                                snapshotInstance = newInstance;
                                // Other threads may still be reading from it, so we need to wait till it is idle
                                // before destroying
                                currentInstance.waitThenDestroy();
                            }
                        }
                    } finally {
                        snapshotUpdateLock.unlock();
                    }
                } else {
                    LOGGER.debug("getDBInstance() - tryLock failed, docRef: {}", docRef);
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


    // --------------------------------------------------------------------------------


    private static class SnapshotInstance {

        private final ByteBuffers byteBuffers;
        private final Provider<PlanBConfig> configProvider;
        private final PlanBDoc doc;
        private final DocRef docRef;
        private final Path dbDir;
        private final RuntimeException fetchException;
        private final ReentrantLock lock = new ReentrantLock();
        private final Instant currentSnapshotTime;

        private volatile Db<?, ?> db;
        private volatile boolean open;
        private volatile long lastAccessTimeEpochMs;
        private volatile Instant expiryTime;
        private volatile boolean destroy;

        public SnapshotInstance(final ByteBuffers byteBuffers,
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
            this.docRef = doc.asDocRef();

            try {
                // Get the snapshot dir.
                dbDir = statePaths
                        .getSnapshotDir()
                        .resolve(docRef.getUuid())
                        .resolve(DateUtil.createFileDateTimeString(createTime));

                // Create dir.
                Files.createDirectories(dbDir);

                // Go and get a snapshot.
                boolean fetchComplete = false;
                final SnapshotRequest request = new SnapshotRequest(
                        docRef,
                        0L,
                        NullSafe.get(previousSnapshotTime, Instant::toEpochMilli));
                for (final String node : configProvider.get().getNodeList()) {
                    LOGGER.info("Fetching shard for '{}', node: {}, dbDir: {}", docRef, node, dbDir);

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
                    throw new RuntimeException("Unable to get snapshot shard for '" + docRef + "'");
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
            touchLastAccessTime();
            final Db<?, ?> db = this.db;
            if (db != null && !destroy) {
                try {
                    // TODO AT - This timer is a bit of a temporary fix to try to stop the LMDB env from being
                    //  closed while reads are happening. Ideally we should be using the read/write locks
                    //  from StampedLock to block destructive methods while reads are happening.
                    //  hasExpired() is used to determine when destroy() is called and this timer won't
                    //  help with that. Needs a discussion with 66.
                    // Periodically touch the lastAccessTime to prevent the db being seen to be idle
                    // when there is a long-running function.
                    final Timer timer = createLastAccessTouchPeriodicTimer();
                    try {
                        return function.apply(db);
                    } finally {
                        // Make sure the lastAccessTime represents when we actually finished
                        timer.cancel();
                        touchLastAccessTime();
                    }
                } catch (final LmdbException e) {
                    LOGGER.debug(e::getMessage, e);
                }
            }

            // Try opening the DB amd make the caller try again.
            tryOpen();
            throw new TryAgainException();
        }

        private @NonNull Timer createLastAccessTouchPeriodicTimer() {
            final TimerTask timerTask = new TimerTask() {
                final long startTimeMs = System.currentTimeMillis();

                @Override
                public void run() {
                    touchLastAccessTime();
                }

                @Override
                public boolean cancel() {
                    final boolean cancelled = super.cancel();
                    touchLastAccessTime();
                    // lastAccessTime is not final, hence if block
                    if (LOGGER.isTraceEnabled()) {
                        final long finishTimeMs = lastAccessTimeEpochMs;
                        LOGGER.trace("TimerTask.cancel() - cancelled = {}, docRef: {}, dbDir: {}, duration: {}",
                                cancelled, docRef, dbDir, Duration.ofMillis(finishTimeMs - startTimeMs));
                    }
                    return cancelled;
                }
            };

            final Timer timer = new Timer();
            timer.scheduleAtFixedRate(
                    timerTask,
                    LAST_ACCESS_TIME_TOUCH_INTERVAL_MS,
                    LAST_ACCESS_TIME_TOUCH_INTERVAL_MS);
            return timer;
        }

        /**
         * Wait for the snapshot to be idle, then destroy it.
         */
        public void waitThenDestroy() {
            LOGGER.debug(() -> LogUtil.message(
                    "waitThenDestroy(), open: {}, lastAccessTime: {}, docRef: {}, dbDir: {}",
                    open, Instant.ofEpochMilli(lastAccessTimeEpochMs), docRef, dbDir));

            // Mark as destroyed to stop any other threads using it.
            destroy = true;

            final DurationTimer durationTimer = DurationTimer.start();
            // Have to keep checking isIdle as other threads may have touched lastAccessTimeEpochMs
            while (!isIdle()) {
                final Duration remainingIdleTime = getRemainingIdleTime();
                if (remainingIdleTime.isPositive()) {
                    try {
                        // TODO replace the sleep loop if we change this class to use a pair of read/write locks
                        Thread.sleep(remainingIdleTime.toMillis());
                        LOGGER.debug(() -> LogUtil.message(
                                "waitThenDestroy(), open: {}, lastAccessTime: {}, durationTimer: {}, " +
                                "docRef: {}, dbDir: {}",
                                open,
                                Instant.ofEpochMilli(lastAccessTimeEpochMs),
                                durationTimer,
                                docRef,
                                dbDir));
                    } catch (final InterruptedException e) {
                        LOGGER.debug(e::getMessage, e);
                        Thread.currentThread().interrupt();
                        // Just swallow and crack on with the destruction
                    }
                }
            }
            lock.lock();
            try {
                cleanup();
            } finally {
                lock.unlock();
            }
        }

        /**
         * Destroy the snapshot immediately.
         */
        public void destroy() {
            LOGGER.debug(() -> LogUtil.message("destroy(), open: {}, lastAccessTime: {}, docRef: {}, dbDir: {}",
                    open, Instant.ofEpochMilli(lastAccessTimeEpochMs), docRef, dbDir));
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
                    LOGGER.debug(() -> LogUtil.message(
                            "cleanup(), Closing db, destroy: {}, lastAccessTime: {}, docRef: {}, dbDir: {}",
                            destroy, Instant.ofEpochMilli(lastAccessTimeEpochMs), docRef, dbDir));
                    db.close();
                    db = null;
                    open = false;
                }

                if (!open && destroy) {
                    // Delete if this is an old snapshot.
                    try {
                        LOGGER.info("cleanup() - Deleting snapshot for docRef: {}, dbDir: {}", docRef, dbDir);
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
            final Duration minIdleDuration = configProvider.get().getMinTimeToKeepEnvOpen().getDuration();
            return Instant.now()
                    .isAfter(Instant.ofEpochMilli(lastAccessTimeEpochMs).plus(minIdleDuration));
        }

        /**
         * @return The {@link Duration} between now and the time at which the minimum idle
         * time has been reached. May be negative.
         */
        private Duration getRemainingIdleTime() {
            final Duration minIdleDuration = configProvider.get().getMinTimeToKeepEnvOpen().getDuration();
            final Instant minIdleEndTime = Instant.ofEpochMilli(lastAccessTimeEpochMs).plus(minIdleDuration);
            final Instant now = Instant.now();
            return Duration.between(now, minIdleEndTime);
        }

        private void touchLastAccessTime() {
            lastAccessTimeEpochMs = System.currentTimeMillis();
        }

        public Instant getExpiryTime() {
            return expiryTime;
        }

        public boolean hasExpired() {
            return expiryTime.isBefore(Instant.now());
        }

        public void extendExpiry(final Duration amount) {
            this.expiryTime = Instant.now().plus(amount);
        }

        private void open() {
            if (!open) {
                if (fetchException != null) {
                    throw fetchException;
                }
                // If we already fetched the snapshot then reopen.
                LOGGER.debug("open() - Opening local snapshot for docRef: {}, dbDir: {}", docRef, dbDir);
                db = PlanBDb.open(doc, dbDir, byteBuffers, true);
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


    // --------------------------------------------------------------------------------


    private static class TryAgainException extends Exception {

    }

}
