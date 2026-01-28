/*
 * Copyright 2016-2025 Crown Copyright
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * Thread-safe snapshot shard that automatically rotates snapshots every 10 minutes (default)
 * and closes idle databases after 1 minute (default) of inactivity.
 *
 * <p>Supports high-concurrency reads using lock-free CAS-based guards. Multiple
 * threads can read concurrently without blocking. Snapshot rotation happens
 * asynchronously in the background.
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

    private volatile SnapshotInstance snapshotInstance;
    private final AtomicBoolean rotating = new AtomicBoolean();

    public SnapshotShard(final ByteBuffers byteBuffers,
                         final ByteBufferFactory byteBufferFactory,
                         final Provider<PlanBConfig> configProvider,
                         final StatePaths statePaths,
                         final FileTransferClient fileTransferClient,
                         final PlanBDoc doc,
                         final DbFactory dbFactory) {
        this.byteBuffers = byteBuffers;
        this.byteBufferFactory = byteBufferFactory;
        this.configProvider = configProvider;
        this.statePaths = statePaths;
        this.fileTransferClient = fileTransferClient;
        this.doc = doc;
        this.dbFactory = dbFactory;

        snapshotInstance = new SnapshotInstance(
                byteBuffers,
                byteBufferFactory,
                configProvider,
                statePaths,
                fileTransferClient,
                doc,
                Instant.now(),
                null,
                dbFactory);
    }

    private SnapshotInstance getSnapshotInstance() {
        SnapshotInstance instance = snapshotInstance;

        // If the current instance has expired then asynchronously try to get a new snapshot.
        if (instance.getExpiryTime().isBefore(Instant.now())) {
            if (rotating.compareAndSet(false, true)) {
                CompletableFuture
                        .runAsync(this::rotate)
                        .whenComplete((r, t) -> rotating.set(false));
            }

            // Re-read volatile after triggering rotation to get latest reference
            instance = snapshotInstance;
        }

        return instance;
    }

    private void rotate() {
        try {
            final Instant now = Instant.now();

            // Check again if the current instance has expired, if it has then try to get a new
            // snapshot.
            final SnapshotInstance currentInstance = snapshotInstance;
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

                } else {
                    // Switch the instance and destroy the previous instance.
                    snapshotInstance = newInstance;
                    currentInstance.destroy();
                }

                LOGGER.debug("Snapshot rotation completed successfully");
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
                return function.apply(instance);
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
    public void cleanup() {
        getSnapshotInstance().cleanup();
    }

    @Override
    public boolean delete() {
        getSnapshotInstance().destroy();
        return true;
    }

    private static class SnapshotInstance {

        private final ByteBuffers byteBuffers;
        private final ByteBufferFactory byteBufferFactory;
        private final Provider<PlanBConfig> configProvider;
        private final PlanBDoc doc;
        private final Path dbDir;
        private final RuntimeException fetchException;
        private final ReentrantLock openLock = new ReentrantLock();
        private final Instant currentSnapshotTime;
        private final Guard guard;
        private final DbFactory dbFactory;

        private volatile CurrentDb currentDb;
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
            this.byteBufferFactory = byteBufferFactory;
            this.dbFactory = dbFactory;
            Instant currentSnapshotTime = null;
            Instant expiryTime = null;
            Path dbDir = null;
            RuntimeException fetchException = null;

            // Begin life in use.
            guard = new StripedGuard(this::delete, 64);

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

        public <R> R get(final Function<Db<?, ?>, R> function) {
            return guard.acquire(() -> getDb().get(function));
        }

        public void destroy() {
            guard.destroy();
        }

        public void cleanup() {
            final CurrentDb instance = this.currentDb;
            if (instance != null) {
                final Instant now = Instant.now();
                final Instant oldest = now.minus(configProvider.get().getMinTimeToKeepEnvOpen().getDuration());

                if (instance.getLastAccessTime().isBefore(oldest)) {
                    openLock.lock();
                    try {
                        // Re-check under lock
                        final CurrentDb current = this.currentDb;
                        if (current != null && current.getLastAccessTime().isBefore(oldest)) {
                            current.destroy();
                            this.currentDb = null;
                        }
                    } finally {
                        openLock.unlock();
                    }
                }
            }
        }

        public Instant getExpiryTime() {
            return expiryTime;
        }

        public void extendExpiry(final Duration amount) {
            this.expiryTime = Instant.now().plus(amount);
        }

        public String getInfo() {
            return guard.acquire(() -> getDb().getInfo());
        }

        private CurrentDb getDb() {
            CurrentDb instance = this.currentDb;
            if (instance == null) {
                openLock.lock();
                try {
                    instance = this.currentDb;
                    if (instance == null) {
                        instance = new CurrentDb(byteBuffers, byteBufferFactory, doc, dbDir, dbFactory);
                    }
                    this.currentDb = instance;
                } finally {
                    openLock.unlock();
                }
            }
            return instance;
        }

        private void delete() {
            LOGGER.debug("Deleting DB");

            CurrentDb instance = this.currentDb;
            if (instance != null) {
                openLock.lock();
                try {
                    instance = this.currentDb;
                    if (instance != null) {
                        instance.destroy();
                    }
                } catch (final Exception e) {
                    LOGGER.error(e::getMessage, e);
                } finally {
                    openLock.unlock();
                }
            }
            currentDb = null;

            // Delete this snapshot.
            try {
                if (dbDir != null) {
                    LOGGER.info("Deleting snapshot for '{}'", doc);
                    FileUtil.deleteDir(dbDir);
                }
            } catch (final Exception e) {
                LOGGER.error(e::getMessage, e);
            }
        }
    }

    private static class CurrentDb {

        private final Guard guard;
        private final Db<?, ?> db;
        private volatile Instant lastAccessTime;

        public CurrentDb(final ByteBuffers byteBuffers,
                         final ByteBufferFactory byteBufferFactory,
                         final PlanBDoc doc,
                         final Path dbDir,
                         final DbFactory dbFactory) {
            final String mapName = doc.getName();

            // If we already fetched the snapshot then reopen.
            LOGGER.debug("Opening local snapshot for '{}'", mapName);
            this.db = dbFactory.open(doc, dbDir, byteBuffers, byteBufferFactory, true);
            guard = new StripedGuard(this::close, 64);
            lastAccessTime = Instant.now();
        }

        public <R> R get(final Function<Db<?, ?>, R> function) {
            return guard.acquire(() -> {
                lastAccessTime = Instant.now();
                return function.apply(db);
            });
        }

        public void destroy() {
            guard.destroy();
        }

        public Instant getLastAccessTime() {
            return lastAccessTime;
        }

        public String getInfo() {
            return guard.acquire(() -> {
                lastAccessTime = Instant.now();
                return db.getInfoString();
            });
        }

        private void close() {
            LOGGER.debug("Closing DB");
            db.close();
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
