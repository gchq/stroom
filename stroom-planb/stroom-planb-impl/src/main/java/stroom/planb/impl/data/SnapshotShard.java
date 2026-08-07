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
import stroom.node.api.NodeCallException;
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.dao.Db;
import stroom.planb.impl.dao.StatePaths;
import stroom.planb.shared.PlanBDoc;
import stroom.util.concurrent.Guard;
import stroom.util.concurrent.Guard.TryAgainException;
import stroom.util.concurrent.StripedGuard;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.date.DateUtil;
import stroom.util.io.FileUtil;
import stroom.util.io.PathSegmentUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Provider;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;

/**
 * Thread-safe snapshot shard that serves reads from a locally fetched snapshot of a shard stored on another
 * node.
 *
 * <p>The strategy trades a bounded amount of staleness for throughput:
 * <ul>
 *   <li><b>Fresh snapshot</b> (confirmed current within {@code minTimeToKeepSnapshots}): served directly,
 *       lock free.
 *   <li><b>Stale snapshot</b> (older than that, but confirmed current within
 *       {@code minTimeToKeepSnapshotEnv}): served directly while a refresh runs asynchronously. Reads never
 *       block when we hold servable data.
 *   <li><b>No servable snapshot</b> (never fetched, fetch failed, or data too stale): whether a read blocks
 *       or fails is decided by the recency of failure evidence. If the last fetch attempt failed within
 *       {@code snapshotRetryFetchInterval} the read fails fast with that cause. Otherwise the read triggers
 *       a fetch and waits for it, bounded by the same interval — there is no point failing fast when a fetch
 *       may well succeed, e.g. the very first fetch, or the first read after a long idle period.
 * </ul>
 *
 * <p>{@code minTimeToKeepSnapshotEnv} therefore bounds both how stale served data may be and how long an idle
 * environment is retained by {@link ShardManager} cleanup. These are deliberately the same duration:
 * confirmations only happen on read triggered rotations, so a shard's staleness is always at least its
 * idleness, meaning idle eviction only ever destroys data that has already aged out of servability.
 *
 * <p>A response of NOT_MODIFIED from the store node is a confirmation that our data is current, not a
 * failure. It refreshes the confirmed current time of the instance we already hold, so an unchanged store is
 * re-checked at the lifespan cadence rather than re-fetched or treated as an error.
 *
 * <p>Thread safety:
 * <ul>
 *   <li>Reads on a servable snapshot are lock free and never block each other
 *   <li>Only one fetch runs at a time; readers that need to wait all wait on the same in flight fetch
 *   <li>Instances are guarded so they are only destroyed when all active readers have finished
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
    // The rotation currently in flight, if any, so that readers with no servable snapshot can wait for it
    // rather than each starting their own fetch.
    private final AtomicReference<CompletableFuture<Void>> rotationRef = new AtomicReference<>();

    // Any read attempt counts as access, including a failing one. A shard that is failing under demand must
    // stay alive, as the instance it holds carries currentSnapshotTime which lets a recovered store node heal
    // it with a cheap NOT_MODIFIED check instead of a full transfer.
    private volatile Instant lastAccessTime = Instant.now();

    // Evidence of the most recent completed (or timed out) fetch attempt that failed. This is what decides
    // whether a read with no servable snapshot blocks on a fetch or fails fast: recent failure means a fetch
    // is very unlikely to succeed, so waiting just stalls the caller. Cleared on any successful contact with
    // the store node. Writes race benignly between the rotation thread and timed out waiters.
    private volatile Instant lastFetchFailureTime;
    private volatile RuntimeException lastFetchFailure;

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

        // Deliberately no fetch here. The first fetch happens via rotation on first read, so construction is
        // fast and the ShardManager creation lock is not held across a network call.
        snapshotRef = new AtomicReference<>(SnapshotInstance.empty(doc));
    }

    @Override
    public <R> R get(final Function<Db<?, ?>, R> function) {
        lastAccessTime = Instant.now();
        Exception lastTryAgain = null;

        for (int attempts = 0; attempts < MAX_ATTEMPTS; attempts++) {
            final SnapshotInstance instance = snapshotRef.get();
            if (instance == null) {
                throw new ShardClosedException();
            }

            if (instance.isServable(getStaleBound())) {
                // We hold data that is at worst acceptably stale, so serve it. If it is due a refresh then
                // trigger one asynchronously, unless we recently failed, in which case wait out the retry
                // window rather than hammering a failing node.
                if (instance.needsRefresh(getLifespan()) && !isInFailureWindow()) {
                    startRotation();
                }
                try {
                    return instance.get(function);
                } catch (final TryAgainException e) {
                    // The instance was destroyed under us by rotation or cleanup, retry with the new one.
                    LOGGER.trace("Retry attempt {} due to: {}", attempts, e.getMessage());
                    lastTryAgain = e;
                    LockSupport.parkNanos(1);
                } catch (final Exception e) {
                    // A failure from the database read itself. Propagate immediately, wrapped so the log
                    // names the doc involved.
                    throw new RuntimeException("Error during snapshot access for '" +
                                               doc.asDocRef() +
                                               "': " +
                                               e.getMessage(), e);
                }

            } else {
                // Nothing servable. Fail fast if we have recent evidence that fetching fails, otherwise this
                // read triggers a fetch and waits for it, as failing fast would just convert a probably
                // successful wait into a guaranteed error.
                if (isInFailureWindow()) {
                    throw createUnavailableException();
                }
                awaitRotation(startRotation());
                // Loop: either the fetch succeeded and the new instance is servable, or a failure (including
                // a timeout waiting) has been recorded and the next pass fails fast with the latest cause.
            }
        }
        throw new RuntimeException("Failed to acquire snapshot after " + MAX_ATTEMPTS + " attempts",
                lastTryAgain);
    }

    @Override
    public String getInfo() {
        lastAccessTime = Instant.now();

        for (int attempts = 0; attempts < MAX_ATTEMPTS; attempts++) {
            final SnapshotInstance instance = snapshotRef.get();
            if (instance == null) {
                throw new ShardClosedException();
            }

            if (instance.isServable(getStaleBound())) {
                if (instance.needsRefresh(getLifespan()) && !isInFailureWindow()) {
                    startRotation();
                }
                try {
                    return instance.getInfo();
                } catch (final TryAgainException e) {
                    LOGGER.trace("Retry attempt {} due to: {}", attempts, e.getMessage());
                    LockSupport.parkNanos(1);
                }

            } else {
                // This renders the shard info listing so it must report the state we are in rather than wait,
                // and it must not throw, as the caller drops the shard from the listing on error, which would
                // leave a failing shard invisible rather than showing its problem. It still triggers healing.
                if (!isInFailureWindow()) {
                    startRotation();
                }
                final RuntimeException failure = lastFetchFailure;
                if (instance.hasData()) {
                    return "No data served. Snapshot is too stale, last confirmed current at "
                           + instance.getConfirmedCurrentTime()
                           + NullSafe.getOrElse(failure, f -> ". Last fetch failure: " + f.getMessage(), "");
                }
                return failure != null
                        ? "No data. Snapshot fetch failed: " + failure.getMessage()
                        : "No data. No snapshot has been fetched yet.";
            }
        }
        throw new RuntimeException("Failed to acquire snapshot after " + MAX_ATTEMPTS + " attempts");
    }

    private Duration getLifespan() {
        return configProvider.get().getMinTimeToKeepSnapshots().getDuration();
    }

    /**
     * How stale served data may be, measured from when it was last confirmed current. This is the same
     * duration that bounds idle environment retention, see the class javadoc. Clamped to at least the
     * snapshot lifespan, as a bound below the refresh cadence would age data out before we even tried to
     * refresh it.
     */
    private Duration getStaleBound() {
        final Duration lifespan = getLifespan();
        final Duration envKeep = configProvider.get().getMinTimeToKeepSnapshotEnv().getDuration();
        return envKeep.compareTo(lifespan) >= 0
                ? envKeep
                : lifespan;
    }

    private Duration getRetryInterval() {
        return configProvider.get().getSnapshotRetryFetchInterval().getDuration();
    }

    private boolean isInFailureWindow() {
        final Instant failureTime = lastFetchFailureTime;
        return failureTime != null &&
               Instant.now().isBefore(failureTime.plus(getRetryInterval()));
    }

    private void recordFetchFailure(final RuntimeException e) {
        lastFetchFailure = e;
        lastFetchFailureTime = Instant.now();
    }

    private void clearFetchFailure() {
        lastFetchFailure = null;
        lastFetchFailureTime = null;
    }

    private RuntimeException createUnavailableException() {
        final RuntimeException cause = lastFetchFailure;
        return new RuntimeException("Plan B snapshot is not available for '" +
                                    doc.asDocRef() +
                                    "'" +
                                    NullSafe.getOrElse(cause, c -> ": " + c.getMessage(), ""),
                cause);
    }

    /**
     * Start a rotation if one isn't already running, returning the in flight rotation either way so callers
     * can wait for it. Completes normally whether or not the fetch succeeded, as {@link #rotate()} records
     * its own outcome.
     */
    private CompletableFuture<Void> startRotation() {
        final CompletableFuture<Void> inFlight = rotationRef.get();
        if (inFlight != null) {
            return inFlight;
        }

        final CompletableFuture<Void> created = new CompletableFuture<>();
        if (!rotationRef.compareAndSet(null, created)) {
            // Someone else started one first, so wait on theirs. If they have already finished then there is
            // nothing to wait for.
            final CompletableFuture<Void> other = rotationRef.get();
            return other != null
                    ? other
                    : CompletableFuture.completedFuture(null);
        }

        CompletableFuture
                .runAsync(this::rotate, executor)
                .whenComplete((ignoredResult, ignoredThrowable) -> {
                    // Clear before completing so a waiter that wakes and finds the snapshot still unusable
                    // starts a fresh rotation rather than joining this completed one.
                    rotationRef.set(null);
                    created.complete(null);
                });
        return created;
    }

    private void awaitRotation(final CompletableFuture<Void> rotation) {
        final Duration timeout = getRetryInterval();
        try {
            rotation.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw UncheckedInterruptedException.create(e);
        } catch (final TimeoutException e) {
            // The fetch is taking longer than the retry interval, e.g. an unresponsive node, as there is no
            // client side timeout on the fetch itself. Record it as a failure so that other reads fail fast
            // for the retry window instead of each waiting a full interval against the same in flight fetch.
            // If the fetch eventually succeeds it will clear this.
            LOGGER.debug(() -> "Timed out after " + timeout + " waiting for a snapshot for '" +
                               doc.asDocRef() + "'");
            recordFetchFailure(new RuntimeException("Timed out after " +
                                                    timeout +
                                                    " waiting for a snapshot for '" +
                                                    doc.asDocRef() +
                                                    "'"));
        } catch (final ExecutionException e) {
            // rotate() handles its own errors, so this is unexpected plumbing failure.
            LOGGER.debug(() -> "Error waiting for a snapshot for '" + doc.asDocRef() + "': " + e.getMessage(),
                    e);
        }
    }

    private void rotate() {
        try {
            final SnapshotInstance current = snapshotRef.get();
            // If null, the shard has been closed — abandon rotation.
            if (current == null) {
                return;
            }

            // Check again now we are the single rotation in flight, in case another rotation refreshed the
            // snapshot between the trigger and now.
            if (!current.needsRefresh(getLifespan())) {
                return;
            }

            LOGGER.debug("Starting snapshot rotation");
            try {
                final SnapshotInstance newInstance = fetchSnapshot(current.getCurrentSnapshotTime());

                // Atomically swap: only succeeds if nobody else has changed the reference
                // (i.e., delete() hasn't set it to null).
                if (snapshotRef.compareAndSet(current, newInstance)) {
                    current.destroy();
                    clearFetchFailure();
                } else {
                    // The shard was closed while we were fetching. Discard the new instance to prevent a
                    // resource leak.
                    LOGGER.debug("Snapshot rotation aborted — shard was closed during fetch");
                    newInstance.destroy();
                }

            } catch (final NotModifiedException e) {
                // The store node has confirmed that the data we hold is still current. This is a successful
                // contact, not a failure: it resets both staleness and the refresh cadence, so an unchanged
                // store is re-checked at the lifespan interval rather than re-fetched or treated as an error.
                LOGGER.debug(() -> "Snapshot confirmed current for '" + doc.asDocRef() + "'");
                current.confirmCurrent();
                clearFetchFailure();

            } catch (final RuntimeException e) {
                LOGGER.debug(e::getMessage, e);
                recordFetchFailure(e);
            }
            LOGGER.debug("Snapshot rotation completed");

        } catch (final Exception e) {
            LOGGER.error("Error during snapshot rotation", e);
        }
    }

    /**
     * Fetch a snapshot from one of the configured nodes and open it, returning a servable instance.
     *
     * @param previousSnapshotTime The data time of the snapshot we already hold, if any, so the store node
     *                             can answer NOT_MODIFIED instead of sending an unchanged snapshot again.
     * @throws NotModifiedException If the store node confirms the data we hold is still current.
     * @throws RuntimeException     If no node could supply a snapshot.
     */
    private SnapshotInstance fetchSnapshot(final Instant previousSnapshotTime) {
        final Instant createTime = Instant.now();
        // The timestamp is only for humans reading the dir listing, and has millisecond
        // resolution, so it is not unique on its own: a snapshot destroyed and refetched inside
        // one millisecond would land on the dir the outgoing instance is still mapping (its
        // destroy is deferred until the last reader finishes), giving two envs on one dir with
        // one deleting the other's files. Nothing parses this name, so a uuid makes it unique.
        final Path dbDir = statePaths
                .getSnapshotDir()
                .resolve(PathSegmentUtil.requireSafeSegment(doc.getUuid()))
                .resolve(DateUtil.createFileDateTimeString(createTime) + "__" + UUID.randomUUID());

        try {
            // Create dir.
            Files.createDirectories(dbDir);

            final SnapshotRequest request = new SnapshotRequest(
                    doc.asDocRef(),
                    0L,
                    NullSafe.get(previousSnapshotTime, Instant::toEpochMilli));
            NodeCallException lastUnreachable = null;
            final List<String> nodes = NullSafe.list(configProvider.get().getNodeList());
            for (final String node : nodes) {
                LOGGER.info(() -> "Fetching shard for '" + doc.asDocRef() + "'");

                try {
                    // Fetch snapshot.
                    final Instant currentSnapshotTime = fileTransferClient.fetchSnapshot(node, request, dbDir);

                    // Eagerly open the DB now that the snapshot is fetched.
                    LOGGER.debug("Opening local snapshot for '{}'", doc.getName());
                    final Db<?, ?> db = dbFactory.open(doc, dbDir, byteBuffers, byteBufferFactory, true);
                    return new SnapshotInstance(doc, dbDir, currentSnapshotTime, db);

                } catch (final NodeCallException e) {
                    // We couldn't reach this node, so try the next one. Every configured node is sent a copy
                    // of all the data, so any of them can supply the snapshot.
                    //
                    // Note that we deliberately only fail over when a node is unreachable. If a node answers,
                    // its answer stands, even if that answer is an error. A missing snapshot, a not modified
                    // response or a permission failure are not specific to the node we asked, so asking
                    // another would just hide the problem behind a slower, noisier failure. See gh-5689.
                    LOGGER.warn(() -> "Unable to reach node '" + node + "' for '" + doc.asDocRef() +
                                      "', trying next node: " + e.getMessage());
                    LOGGER.debug(e::getMessage, e);
                    lastUnreachable = e;

                    // Discard anything a partial fetch may have left behind before trying another node.
                    FileUtil.deleteDir(dbDir);
                    Files.createDirectories(dbDir);
                }
            }

            if (lastUnreachable != null) {
                throw new RuntimeException("Unable to reach any Plan B node for '" + doc.asDocRef() +
                                           "', tried " + nodes + ": " + lastUnreachable.getMessage(),
                        lastUnreachable);
            }
            throw new RuntimeException("Unable to get snapshot shard for '" + doc.asDocRef() +
                                       "' as no Plan B nodes are configured");

        } catch (final IOException e) {
            // Don't leave a part fetched snapshot behind, whatever the failure.
            FileUtil.deleteDir(dbDir);
            throw new UncheckedIOException(e);
        } catch (final RuntimeException e) {
            // Includes NotModifiedException, which the caller treats as confirmation, not failure.
            FileUtil.deleteDir(dbDir);
            throw e;
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
    public boolean isIdle() {
        final Duration idleTimeout = configProvider.get().getMinTimeToKeepSnapshotEnv().getDuration();
        return lastAccessTime.plus(idleTimeout).isBefore(Instant.now());
    }

    /**
     * Always returns true, but the DB close and dir delete are only immediate when no reader
     * is in flight; otherwise the guard defers them to the thread of the last reader to
     * finish, so the env may still be open when this returns. Callers must not assume the
     * dir has gone (e.g. by deleting a parent dir) on return. Nothing needs that today:
     * fetched snapshot dirs left behind are swept by {@link #deleteFetchedSnapshots} at
     * next startup.
     */
    @Override
    public boolean delete() {
        final SnapshotInstance instance = snapshotRef.getAndSet(null);
        if (instance != null) {
            instance.destroy();
        }
        return true;
    }

    @Override
    public void close() {
        // A snapshot is a transient local copy, so closing it is the same as discarding it.
        // destroy() flags the instance; the DB close and dir delete run immediately if no
        // readers are in flight, otherwise on the thread of the last reader to finish.
        delete();
    }

    @Override
    public PlanBDoc getDoc() {
        return doc;
    }

    /**
     * A single fetched snapshot. Passive: fetching happens in {@link SnapshotShard#fetchSnapshot} and
     * outcome tracking is shard level, so an instance either holds an open DB or is the initial empty
     * placeholder.
     *
     * <p>The LMDB database is opened eagerly after a successful fetch and remains open for the lifetime of
     * this instance. A single {@link StripedGuard} protects against use-after-close: readers call
     * {@link #get} which acquires the guard, and {@link #destroy()} waits for all active readers to finish
     * before running {@link #delete()} which closes the DB and removes the directory.
     */
    private static class SnapshotInstance {

        private final PlanBDoc doc;
        private final Path dbDir;
        private final Instant currentSnapshotTime;
        private final Guard guard;
        private final Db<?, ?> db;

        // When we last successfully confirmed with the store node that the data we hold is current, either
        // by fetching it or by a NOT_MODIFIED response. This, not the age of the data, is what staleness is
        // measured from: old data that the store confirms is unchanged is not stale.
        private volatile Instant confirmedCurrentTime;

        private SnapshotInstance(final PlanBDoc doc,
                                 final Path dbDir,
                                 final Instant currentSnapshotTime,
                                 final Db<?, ?> db) {
            this.doc = doc;
            this.dbDir = dbDir;
            this.currentSnapshotTime = currentSnapshotTime;
            this.db = db;
            this.confirmedCurrentTime = db == null
                    ? null
                    : Instant.now();

            // Begin life in use. The guard ensures delete() only runs when all readers have finished.
            guard = new StripedGuard(this::delete, 64);
        }

        /**
         * The placeholder a shard holds before any fetch has completed. Never servable.
         */
        public static SnapshotInstance empty(final PlanBDoc doc) {
            return new SnapshotInstance(doc, null, null, null);
        }

        public boolean hasData() {
            return db != null;
        }

        /**
         * Do we hold data that is acceptably stale to serve?
         */
        public boolean isServable(final Duration staleBound) {
            final Instant confirmed = confirmedCurrentTime;
            return db != null &&
                   confirmed != null &&
                   !Instant.now().isAfter(confirmed.plus(staleBound));
        }

        /**
         * Is the data due a refresh? True for the empty placeholder and for data past its lifespan.
         */
        public boolean needsRefresh(final Duration lifespan) {
            final Instant confirmed = confirmedCurrentTime;
            return db == null ||
                   confirmed == null ||
                   Instant.now().isAfter(confirmed.plus(lifespan));
        }

        /**
         * The store node has confirmed the data we hold is still current.
         */
        public void confirmCurrent() {
            confirmedCurrentTime = Instant.now();
        }

        public Instant getConfirmedCurrentTime() {
            return confirmedCurrentTime;
        }

        public Instant getCurrentSnapshotTime() {
            return currentSnapshotTime;
        }

        public <R> R get(final Function<Db<?, ?>, R> function) {
            return guard.acquire(() -> {
                if (db == null) {
                    throw new RuntimeException("Snapshot database is not available");
                }
                return function.apply(db);
            });
        }

        public String getInfo() {
            return guard.acquire(() -> {
                if (db == null) {
                    return "No data. Snapshot database is not available.";
                }
                return db.getInfoString();
            });
        }

        public void destroy() {
            guard.destroy();
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

    public interface DbFactory {

        Db<?, ?> open(PlanBDoc doc,
                      Path dbDir,
                      ByteBuffers byteBuffers,
                      ByteBufferFactory byteBufferFactory,
                      boolean readOnly);
    }
}
