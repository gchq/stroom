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
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.db.Db;
import stroom.planb.impl.db.StatePaths;
import stroom.planb.shared.PlanBDoc;
import stroom.util.concurrent.Guard;
import stroom.util.concurrent.Guard.TryAgainException;
import stroom.util.concurrent.StripedGuard;
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
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;

/**
 * Thread-safe snapshot shard that automatically rotates snapshots every 10 minutes (default).
 *
 * <p>The LMDB database is opened eagerly when a snapshot is fetched and stays open for the
 * lifetime of the instance. When the instance is destroyed (via rotation or explicit delete),
 * the guard ensures all active readers complete before the DB is closed and the directory
 * is deleted. Rotation happens asynchronously in the background and never blocks reads —
 * readers continue using the current instance until the new one is ready.
 *
 * <p>Idle shards are cleaned up by {@link ShardManager}: if a shard has not been accessed
 * within {@code minTimeToKeepSnapshotEnv}, it is evicted from the map and its resources (DB files,
 * snapshot directory) are freed. If someone reads the shard later, it is lazily recreated.
 *
 * <p>Thread safety:
 * <ul>
 *   <li>Reads are lock-free and never block each other
 *   <li>Only one snapshot rotation occurs at a time
 *   <li>Resources are reference-counted and destroyed when unused
 * </ul>
 */
class SnapshotShard implements Shard {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SnapshotShard.class);

    private static final int MAX_ATTEMPTS = 100; // Reasonable upper bound

    private final ByteBuffers byteBuffers;
    private final ByteBufferFactory byteBufferFactory;
    private final Provider<PlanBConfig> configProvider;
    private final StatePaths statePaths;
    private final FileTransferClient fileTransferClient;
    private final PlanBDoc doc;
    private final DbFactory dbFactory;
    private final Executor executor;

    private final AtomicReference<SnapshotInstance> snapshotRef;
    private final AtomicBoolean rotating = new AtomicBoolean();
    private volatile Instant lastAccessTime = Instant.now();

    public SnapshotShard(final ByteBuffers byteBuffers,
                         final ByteBufferFactory byteBufferFactory,
                         final Provider<PlanBConfig> configProvider,
                         final StatePaths statePaths,
                         final FileTransferClient fileTransferClient,
                         final PlanBDoc doc,
                         final DbFactory dbFactory,
                         final Executor executor) {
        this.byteBuffers = byteBuffers;
        this.byteBufferFactory = byteBufferFactory;
        this.configProvider = configProvider;
        this.statePaths = statePaths;
        this.fileTransferClient = fileTransferClient;
        this.doc = doc;
        this.dbFactory = dbFactory;
        this.executor = executor;

        snapshotRef = new AtomicReference<>(new SnapshotInstance(
                byteBuffers,
                byteBufferFactory,
                configProvider,
                statePaths,
                fileTransferClient,
                doc,
                Instant.now(),
                null,
                dbFactory));
    }

    private SnapshotInstance getSnapshotInstance() {
        final SnapshotInstance instance = snapshotRef.get();
        if (instance == null) {
            throw new ShardClosedException();
        }

        // If the current instance has expired then asynchronously try to get a new snapshot.
        if (instance.getExpiryTime().isBefore(Instant.now())) {
            if (rotating.compareAndSet(false, true)) {
                CompletableFuture
                        .runAsync(this::rotate, executor)
                        .whenComplete((ignoredResult, ignoredThrowable) ->
                                rotating.set(false));
            }

            // Re-read after triggering rotation to get latest reference.
            final SnapshotInstance latest = snapshotRef.get();
            if (latest == null) {
                throw new ShardClosedException();
            }
            return latest;
        }

        return instance;
    }

    private void rotate() {
        try {
            final Instant now = Instant.now();

            final SnapshotInstance currentInstance = snapshotRef.get();
            // If null, the shard has been closed — abandon rotation.
            if (currentInstance == null) {
                return;
            }

            // Check again if the current instance has expired, if it has then try to get a new
            // snapshot.
            if (currentInstance.getExpiryTime().isBefore(now)) {
                LOGGER.debug("Starting snapshot rotation");

                final SnapshotInstance newInstance = new SnapshotInstance(
                        byteBuffers,
                        byteBufferFactory,
                        configProvider,
                        statePaths,
                        fileTransferClient,
                        doc,
                        now,
                        currentInstance.getCurrentSnapshotTime(),
                        dbFactory);

                // If the new snapshot had problems fetching then keep using the current one and extend
                // its expiry time so we don't keep fetching.
                if (newInstance.hasFetchException()) {
                    // Extend the expiry time of the current instance so we don't just keep infinitely retrying
                    // to update this snapshot.
                    currentInstance.extendExpiry(
                            configProvider.get().getSnapshotRetryFetchInterval().getDuration());

                    // Clean up the abandoned instance's directory and guard to prevent resource leaks.
                    newInstance.destroy();
                } else {
                    // Atomically swap: only succeeds if nobody else has changed the reference
                    // (i.e., delete() hasn't set it to null).
                    if (snapshotRef.compareAndSet(currentInstance, newInstance)) {
                        currentInstance.destroy();
                    } else {
                        // The shard was closed (or somehow swapped) while we were fetching.
                        // Discard the new instance to prevent a resource leak.
                        LOGGER.debug("Snapshot rotation aborted — shard was closed during fetch");
                        newInstance.destroy();
                    }
                }

                LOGGER.debug("Snapshot rotation completed");
            }
        } catch (final Exception e) {
            LOGGER.error("Error during snapshot rotation", e);
        }
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
        return tryGet(instance -> instance.get(function));
    }

    @Override
    public String getInfo() {
        return tryGet(SnapshotInstance::getInfo);
    }

    private <R> R tryGet(final Function<SnapshotInstance, R> function) {
        Exception lastException = null;
        for (int attempts = 0; attempts < MAX_ATTEMPTS; attempts++) {
            try {
                final SnapshotInstance instance = getSnapshotInstance();
                final R result = function.apply(instance);
                lastAccessTime = Instant.now();
                return result;
            } catch (final ShardClosedException e) {
                // Shard has been deleted — fail immediately, don't retry.
                throw e;
            } catch (final TryAgainException e) {
                LOGGER.trace("Retry attempt {} due to: {}", attempts, e.getMessage());
                lastException = e;
                // Brief backoff
                LockSupport.parkNanos(1);
            } catch (final Exception e) {
                // Propagate other exceptions immediately
                throw new RuntimeException("Unexpected error during snapshot access", e);
            }
        }
        throw new RuntimeException("Failed to acquire snapshot after " + MAX_ATTEMPTS + " attempts", lastException);
    }

    @Override
    public boolean isIdle() {
        final Duration idleTimeout = configProvider.get().getMinTimeToKeepSnapshotEnv().getDuration();
        return lastAccessTime.plus(idleTimeout).isBefore(Instant.now());
    }

    @Override
    public boolean delete() {
        final SnapshotInstance instance = snapshotRef.getAndSet(null);
        if (instance != null) {
            instance.destroy();
        }
        return true;
    }

    /**
     * Represents a single snapshot of data fetched from a remote node.
     *
     * <p>The LMDB database is opened eagerly during construction (after a successful fetch)
     * and remains open for the lifetime of this instance. A single {@link StripedGuard}
     * protects against use-after-close: readers call {@link #get} which acquires the guard,
     * and {@link #destroy()} waits for all active readers to finish before running
     * {@link #delete()} which closes the DB and removes the directory.
     */
    private static class SnapshotInstance {

        private final PlanBDoc doc;
        private final Path dbDir;
        private final RuntimeException fetchException;
        private final Instant currentSnapshotTime;
        private final Guard guard;
        private final Db<?, ?> db;

        private volatile Instant expiryTime;

        public SnapshotInstance(final ByteBuffers byteBuffers,
                                final ByteBufferFactory byteBufferFactory,
                                final Provider<PlanBConfig> configProvider,
                                final StatePaths statePaths,
                                final FileTransferClient fileTransferClient,
                                final PlanBDoc doc,
                                final Instant createTime,
                                final Instant previousSnapshotTime,
                                final DbFactory dbFactory) {
            Instant currentSnapshotTime = null;
            Instant expiryTime = null;
            Path dbDir = null;
            RuntimeException fetchException = null;
            Db<?, ?> db = null;

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

                // Eagerly open the DB now that the snapshot is fetched.
                final String mapName = doc.getName();
                LOGGER.debug("Opening local snapshot for '{}'", mapName);
                db = dbFactory.open(doc, dbDir, byteBuffers, byteBufferFactory, true);

            } catch (final Exception e) {
                LOGGER.debug(e::getMessage, e);
                fetchException = new RuntimeException(e);
                // If we have an exception then we will want to retry getting a snapshot so expire soon.
                expiryTime = createTime.plus(configProvider.get().getSnapshotRetryFetchInterval());
            }

            this.currentSnapshotTime = currentSnapshotTime;
            this.expiryTime = expiryTime;
            this.doc = doc;
            this.dbDir = dbDir;
            this.fetchException = fetchException;
            this.db = db;

            // Begin life in use. The guard ensures delete() only runs when all readers have finished.
            guard = new StripedGuard(this::delete, 64);
        }

        public boolean hasFetchException() {
            return fetchException != null;
        }

        public Instant getCurrentSnapshotTime() {
            return currentSnapshotTime;
        }

        public <R> R get(final Function<Db<?, ?>, R> function) {
            return guard.acquire(() -> {
                if (db == null) {
                    return null;
                }
                return function.apply(db);
            });
        }

        public void destroy() {
            guard.destroy();
        }

        public Instant getExpiryTime() {
            return expiryTime;
        }

        public void extendExpiry(final Duration amount) {
            this.expiryTime = Instant.now().plus(amount);
        }

        public String getInfo() {
            return guard.acquire(() -> {
                if (db == null) {
                    return "No data (fetch exception)";
                }
                return db.getInfoString();
            });
        }

        private void delete() {
            LOGGER.debug("Deleting snapshot instance for '{}'", doc);

            // Close the DB.
            if (db != null) {
                try {
                    db.close();
                } catch (final Exception e) {
                    LOGGER.error(e::getMessage, e);
                }
            }

            // Delete this snapshot directory.
            if (dbDir != null) {
                try {
                    LOGGER.info("Deleting snapshot for '{}'", doc);
                    FileUtil.deleteDir(dbDir);
                } catch (final Exception e) {
                    LOGGER.error(e::getMessage, e);
                }
            }
        }
    }

    /**
     * Thrown when an operation is attempted on a shard that has been closed/deleted.
     * Package-private so that {@link ShardManager} can catch and retry with a fresh shard.
     */
    static class ShardClosedException extends RuntimeException {

        ShardClosedException() {
            super("Shard has been closed");
        }
    }

    @Override
    public PlanBDoc getDoc() {
        return doc;
    }

    public interface DbFactory {

        Db<?, ?> open(PlanBDoc doc,
                      Path dbDir,
                      ByteBuffers byteBuffers,
                      ByteBufferFactory byteBufferFactory,
                      boolean readOnly);
    }
}
