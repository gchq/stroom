/*
 * Copyright 2026 Crown Copyright
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

package stroom.index.impl;

import stroom.node.api.NodeInfo;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

/// Holds the leases this process has on index shards on shared volumes.
///
/// A node must hold a shard's lease to write to it. The lease is a few columns on the shard's row: the node
/// name, the id of the process, and an expiry time in database time. Every change to it is one conditional
/// update, so two nodes that race for a shard cannot both win. This class claims and releases leases,
/// extends them all on a timer and answers the question a writer asks before it adds a document or commits:
/// does this process still hold the lease?
///
/// That question is answered without asking the database, from the time of the last renewal that succeeded
/// on this process's own monotonic clock. The answer becomes no some while before the lease would expire,
/// so that a writer stops before another node could have taken the shard over. If a renewal finds that a
/// lease has gone, the handler given to [#setLostLeaseHandler(LongConsumer)] is told, so the writer can be
/// closed without committing.
///
/// Shards on local volumes have no lease. Only their volume's node can reach them.
@Singleton
public class IndexShardLeases {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexShardLeases.class);

    // The share of the lease duration for which a lease is trusted after it was last renewed. The rest is
    // the margin in which a writer has stopped but the lease has not yet expired.
    private static final double USABLE_FRACTION = 0.75;

    private final IndexShardDao indexShardDao;
    private final NodeInfo nodeInfo;
    private final Provider<IndexWriterConfig> indexWriterConfigProvider;
    private final LongSupplier nanoClock;
    private final LongSupplier millisClock;
    // Made anew each time the process starts, so a lease held by an earlier process on this node is not
    // mistaken for one held by this process.
    private final String instanceId = UUID.randomUUID().toString();
    // When each lease was last known to be held.
    private final Map<Long, Renewal> lastRenewed = new ConcurrentHashMap<>();

    // Leases this process could not give up because the database was unavailable, with the prune delay each
    // was to be released with. They are given up when it next can be. Guarded by this.
    private final Map<Long, FailedRelease> failedReleases = new HashMap<>();

    private volatile LongConsumer lostLeaseHandler = shardId -> {
    };
    private ScheduledExecutorService renewalExecutor;
    private volatile boolean warnedOfInterval;

    /// Creates the lease holder with the system's clocks.
    ///
    /// @param indexShardDao             Reads and writes the lease columns.
    /// @param nodeInfo                  Names this node.
    /// @param indexWriterConfigProvider Supplies the lease duration and renewal interval.
    @Inject
    IndexShardLeases(final IndexShardDao indexShardDao,
                     final NodeInfo nodeInfo,
                     final Provider<IndexWriterConfig> indexWriterConfigProvider) {
        this(indexShardDao, nodeInfo, indexWriterConfigProvider, System::nanoTime, System::currentTimeMillis);
    }

    /// Creates the lease holder with given clocks.
    ///
    /// @param indexShardDao             Reads and writes the lease columns.
    /// @param nodeInfo                  Names this node.
    /// @param indexWriterConfigProvider Supplies the lease duration and renewal interval.
    /// @param nanoClock                 A monotonic clock in nanoseconds.
    /// @param millisClock               The time of day in milliseconds, which unlike the monotonic clock
    ///                                  moves on while the host is suspended.
    IndexShardLeases(final IndexShardDao indexShardDao,
                     final NodeInfo nodeInfo,
                     final Provider<IndexWriterConfig> indexWriterConfigProvider,
                     final LongSupplier nanoClock,
                     final LongSupplier millisClock) {
        this.indexShardDao = indexShardDao;
        this.nodeInfo = nodeInfo;
        this.indexWriterConfigProvider = indexWriterConfigProvider;
        this.nanoClock = nanoClock;
        this.millisClock = millisClock;
    }

    /// Gets the id of this process, which is what tells its leases from any other's.
    ///
    /// @return The id.
    public String getInstanceId() {
        return instanceId;
    }

    /// Sets what is told when a renewal finds that a lease this process held has gone.
    ///
    /// @param lostLeaseHandler Given the id of the shard. It must stop the shard being written to.
    public void setLostLeaseHandler(final LongConsumer lostLeaseHandler) {
        this.lostLeaseHandler = lostLeaseHandler;
    }

    /// Tries to claim the lease on a shard, which also marks the shard as opening.
    ///
    /// @param shardId The id of the shard, which must be on a shared volume.
    /// @return True if this process now holds the lease. False if the shard is not closed or another
    /// process holds its lease.
    public synchronized boolean claim(final long shardId) {
        // A lease this process has still to give up must go first. A retry of that release cannot tell the
        // old lease from the one claimed here, as this process holds both, so it would give up the new one.
        final FailedRelease failedRelease = failedReleases.get(shardId);
        if (failedRelease != null) {
            releaseInDatabase(shardId, failedRelease);
            failedReleases.remove(shardId);
        }

        // Taken before the claim is made, so the lease is never thought to last longer than it does.
        final Renewal now = now();
        final boolean claimed = indexShardDao.claimLease(
                shardId,
                nodeInfo.getThisNodeName(),
                instanceId,
                getLeaseDuration().toMillis());
        if (claimed) {
            lastRenewed.put(shardId, now);
            startRenewing();
        }
        LOGGER.debug("claim() - shardId: {}, claimed: {}", shardId, claimed);
        return claimed;
    }

    /// Gives up the lease on a shard, which also marks the shard as closed, so any node may claim it.
    ///
    /// @param shardId             The id of the shard.
    /// @param pruneCommitsDelayMs How long from now the shard's replaced commits must be kept for, or null
    ///                            if its writer left none behind.
    public synchronized void release(final long shardId, final Long pruneCommitsDelayMs) {
        lastRenewed.remove(shardId);
        final FailedRelease release = new FailedRelease(pruneCommitsDelayMs);
        try {
            releaseInDatabase(shardId, release);
        } catch (final RuntimeException e) {
            // Left as it is the shard would stay leased to this process, and unusable, until it restarts.
            failedReleases.put(shardId, release);
            LOGGER.error(() -> LogUtil.message("Unable to give up the lease on index shard {}. Will try again: {}",
                    shardId, LogUtil.exceptionMessage(e)), e);
        }
    }

    private void releaseInDatabase(final long shardId, final FailedRelease release) {
        final boolean released = indexShardDao.releaseLease(shardId, instanceId, release.pruneCommitsDelayMs());
        LOGGER.debug("releaseInDatabase() - shardId: {}, released: {}", shardId, released);
    }

    /// Says whether this process can still rely on holding the lease on a shard.
    ///
    /// @param shardId The id of the shard.
    /// @return True if the lease was claimed or renewed recently enough that it cannot have expired.
    public boolean isHeld(final long shardId) {
        final Renewal renewal = lastRenewed.get(shardId);
        if (renewal == null) {
            return false;
        }
        final double usableMs = getLeaseDuration().toMillis() * USABLE_FRACTION;
        // Both clocks, as the monotonic one stops while the host is suspended, when other nodes see the
        // lease expire, and the time of day can be put back.
        return nanoClock.getAsLong() - renewal.nanos() < usableMs * 1_000_000
               && millisClock.getAsLong() - renewal.millis() < usableMs;
    }

    private Renewal now() {
        return new Renewal(nanoClock.getAsLong(), millisClock.getAsLong());
    }

    /// Makes the check a writer uses before it adds a document or commits.
    ///
    /// @param shardId The id of the shard.
    /// @return Returns true while the lease on the shard is held.
    public BooleanSupplier leaseCheck(final long shardId) {
        return () -> isHeld(shardId);
    }

    /// Extends every lease this process holds, and finds any it has lost. Run on a timer.
    public void renew() {
        final List<Long> lostShardIds = new ArrayList<>();
        synchronized (this) {
            retryFailedReleases();
            if (lastRenewed.isEmpty()) {
                return;
            }
            // Taken before the renewal is made, so a lease is never thought to last longer than it does.
            final Renewal now = now();
            final Set<Long> leasedShardIds;
            try {
                indexShardDao.renewLeases(instanceId, getLeaseDuration().toMillis());
                leasedShardIds = indexShardDao.findLeasedShardIds(instanceId);
            } catch (final RuntimeException e) {
                // The leases are not extended, so the writers stop of their own accord once the last
                // renewal that worked is too long ago.
                LOGGER.error(() -> LogUtil.message("Unable to renew index shard leases: {}",
                        LogUtil.exceptionMessage(e)), e);
                return;
            }
            for (final Long shardId : lastRenewed.keySet()) {
                if (leasedShardIds.contains(shardId)) {
                    lastRenewed.put(shardId, now);
                } else {
                    lostShardIds.add(shardId);
                }
            }
            lostShardIds.forEach(lastRenewed::remove);
        }

        // Told outside the lock, as closing a writer releases its lease.
        for (final Long shardId : lostShardIds) {
            LOGGER.error(() -> LogUtil.message(
                    "The lease on index shard {} expired before it could be renewed. The shard will be closed " +
                    "and any documents that had not been committed to it will be discarded.", shardId));
            try {
                lostLeaseHandler.accept(shardId);
            } catch (final RuntimeException e) {
                LOGGER.error("Error closing index shard {} after losing its lease", shardId, e);
            }
        }
    }

    private void retryFailedReleases() {
        final Iterator<Entry<Long, FailedRelease>> iterator = failedReleases.entrySet().iterator();
        while (iterator.hasNext()) {
            final Entry<Long, FailedRelease> entry = iterator.next();
            try {
                releaseInDatabase(entry.getKey(), entry.getValue());
                iterator.remove();
            } catch (final RuntimeException e) {
                LOGGER.debug(() -> LogUtil.message("Still unable to give up the lease on index shard {}",
                        entry.getKey()), e);
                // The database is probably still unavailable, so leave the rest for next time.
                break;
            }
        }
    }

    /// Stops renewing leases. For use once every writer has been closed and its lease given up.
    public synchronized void stop() {
        if (renewalExecutor != null) {
            renewalExecutor.shutdownNow();
            renewalExecutor = null;
        }
    }

    /// Gets how long to wait between renewals. This is the configured interval unless that is too long for
    /// the configured lease, when it is shortened so that two renewals fit in the time a lease is trusted
    /// for. Otherwise writers would stop between renewals, and one late renewal would lose every lease.
    ///
    /// @return The interval in milliseconds.
    long getEffectiveRenewIntervalMs() {
        final long configuredMs = getRenewInterval().toMillis();
        final long longestMs = (long) (getLeaseDuration().toMillis() * USABLE_FRACTION / 2);
        if (configuredMs > longestMs && !warnedOfInterval) {
            warnedOfInterval = true;
            LOGGER.warn("stroom.index.writer.shardLeaseRenewInterval ({}ms) is too long for " +
                        "shardLeaseDuration. Renewing index shard leases every {}ms instead.",
                    configuredMs, longestMs);
        }
        return Math.max(1, Math.min(configuredMs, longestMs));
    }

    // Renewal starts with the first lease, so that a process that never writes to a shared volume has no
    // timer, and one that does can't forget to start it.
    private void startRenewing() {
        if (renewalExecutor == null) {
            renewalExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
                final Thread thread = new Thread(runnable, "Index Shard Lease Renewal");
                thread.setDaemon(true);
                return thread;
            });
            scheduleNextRenewal(renewalExecutor);
        }
    }

    // Each renewal schedules the next, so that a change to the configured interval takes effect.
    private void scheduleNextRenewal(final ScheduledExecutorService executor) {
        try {
            executor.schedule(() -> {
                try {
                    renew();
                } catch (final RuntimeException e) {
                    LOGGER.error("Error renewing index shard leases", e);
                } finally {
                    scheduleNextRenewal(executor);
                }
            }, getEffectiveRenewIntervalMs(), TimeUnit.MILLISECONDS);
        } catch (final RejectedExecutionException e) {
            LOGGER.debug("Index shard lease renewal has been stopped");
        }
    }

    private Duration getLeaseDuration() {
        return indexWriterConfigProvider.get().getShardLeaseDuration().getDuration();
    }

    private Duration getRenewInterval() {
        return indexWriterConfigProvider.get().getShardLeaseRenewInterval().getDuration();
    }


    // --------------------------------------------------------------------------------


    private record FailedRelease(Long pruneCommitsDelayMs) {

    }


    // --------------------------------------------------------------------------------


    // When a lease was last known to be held, on each clock.
    private record Renewal(long nanos, long millis) {

    }
}
