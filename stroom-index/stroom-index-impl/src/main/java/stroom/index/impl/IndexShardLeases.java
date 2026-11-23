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
    // Made anew each time the process starts, so a lease held by an earlier process on this node is not
    // mistaken for one held by this process.
    private final String instanceId = UUID.randomUUID().toString();
    // When each lease was last known to be held, on this process's monotonic clock.
    private final Map<Long, Long> lastRenewedNanos = new ConcurrentHashMap<>();

    // Leases this process could not give up because the database was unavailable, with the prune delay each
    // was to be released with. They are given up when it next can be. Guarded by this.
    private final Map<Long, FailedRelease> failedReleases = new HashMap<>();

    private volatile LongConsumer lostLeaseHandler = shardId -> {
    };
    private ScheduledExecutorService renewalExecutor;
    private volatile boolean warnedOfInterval;

    @Inject
    IndexShardLeases(final IndexShardDao indexShardDao,
                     final NodeInfo nodeInfo,
                     final Provider<IndexWriterConfig> indexWriterConfigProvider) {
        this(indexShardDao, nodeInfo, indexWriterConfigProvider, System::nanoTime);
    }

    /// Creates the lease holder with a given clock.
    ///
    /// @param indexShardDao             Reads and writes the lease columns.
    /// @param nodeInfo                  Names this node.
    /// @param indexWriterConfigProvider Supplies the lease duration and renewal interval.
    /// @param nanoClock                 A monotonic clock in nanoseconds.
    IndexShardLeases(final IndexShardDao indexShardDao,
                     final NodeInfo nodeInfo,
                     final Provider<IndexWriterConfig> indexWriterConfigProvider,
                     final LongSupplier nanoClock) {
        this.indexShardDao = indexShardDao;
        this.nodeInfo = nodeInfo;
        this.indexWriterConfigProvider = indexWriterConfigProvider;
        this.nanoClock = nanoClock;
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
        // Taken before the claim is made, so the lease is never thought to last longer than it does.
        final long nowNanos = nanoClock.getAsLong();
        final boolean claimed = indexShardDao.claimLease(
                shardId,
                nodeInfo.getThisNodeName(),
                instanceId,
                getLeaseDuration().toMillis());
        if (claimed) {
            lastRenewedNanos.put(shardId, nowNanos);
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
    public void release(final long shardId, final Long pruneCommitsDelayMs) {
        release(shardId, pruneCommitsDelayMs, false);
    }

    /// Gives up the lease on a shard as [#release(long, Long)] does, and can record that its writer
    /// stopped without a final commit.
    ///
    /// @param shardId               The id of the shard.
    /// @param pruneCommitsDelayMs   How long from now the shard's replaced commits must be kept for, or
    ///                              null if its writer left none behind.
    /// @param recoverPendingStreams True if the streams the shard holds pending rows for must be recovered.
    public synchronized void release(final long shardId,
                                     final Long pruneCommitsDelayMs,
                                     final boolean recoverPendingStreams) {
        lastRenewedNanos.remove(shardId);
        try {
            final boolean released = indexShardDao.releaseLease(
                    shardId, instanceId, pruneCommitsDelayMs, recoverPendingStreams);
            if (!released && recoverPendingStreams) {
                // The lease had already gone from the shard, so the release could not record that the
                // writer discarded what it had not committed. It must still be recorded. At worst a
                // stream that was safe is indexed again.
                indexShardDao.setNeedsPendingStreamRecovery(shardId);
            }
            LOGGER.debug("release() - shardId: {}, released: {}", shardId, released);
        } catch (final RuntimeException e) {
            // Left as it is the shard would stay leased to this process, and unusable, until it restarts.
            failedReleases.put(shardId, new FailedRelease(pruneCommitsDelayMs, recoverPendingStreams));
            LOGGER.error(() -> "Unable to give up the lease on index shard " + shardId
                               + ". Will try again: " + LogUtil.exceptionMessage(e), e);
        }
    }

    /// Says whether this process can still rely on holding the lease on a shard.
    ///
    /// @param shardId The id of the shard.
    /// @return True if the lease was claimed or renewed recently enough that it cannot have expired.
    public boolean isHeld(final long shardId) {
        final Long renewedNanos = lastRenewedNanos.get(shardId);
        return renewedNanos != null
               && nanoClock.getAsLong() - renewedNanos < getLeaseDuration().toNanos() * USABLE_FRACTION;
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
            if (lastRenewedNanos.isEmpty()) {
                return;
            }
            // Taken before the renewal is made, so a lease is never thought to last longer than it does.
            final long nowNanos = nanoClock.getAsLong();
            final Set<Long> leasedShardIds;
            try {
                indexShardDao.renewLeases(instanceId, getLeaseDuration().toMillis());
                leasedShardIds = indexShardDao.findLeasedShardIds(instanceId);
            } catch (final RuntimeException e) {
                // The leases are not extended, so the writers stop of their own accord once the last
                // renewal that worked is too long ago.
                LOGGER.error(() -> "Unable to renew index shard leases: " + LogUtil.exceptionMessage(e), e);
                return;
            }
            for (final Long shardId : lastRenewedNanos.keySet()) {
                if (leasedShardIds.contains(shardId)) {
                    lastRenewedNanos.put(shardId, nowNanos);
                } else {
                    lostShardIds.add(shardId);
                }
            }
            lostShardIds.forEach(lastRenewedNanos::remove);
        }

        // Told outside the lock, as closing a writer releases its lease.
        for (final Long shardId : lostShardIds) {
            LOGGER.error(() -> LogUtil.message(
                    "The lease on index shard {} expired before it could be renewed. The shard will be closed " +
                    "and any documents that had not been committed to it will be discarded.", shardId));
            try {
                lostLeaseHandler.accept(shardId);
            } catch (final RuntimeException e) {
                LOGGER.error(() -> "Error closing index shard " + shardId + " after losing its lease", e);
            }
        }
    }

    private void retryFailedReleases() {
        final Iterator<Entry<Long, FailedRelease>> iterator = failedReleases.entrySet().iterator();
        while (iterator.hasNext()) {
            final Entry<Long, FailedRelease> entry = iterator.next();
            try {
                final boolean released = indexShardDao.releaseLease(
                        entry.getKey(),
                        instanceId,
                        entry.getValue().pruneCommitsDelayMs(),
                        entry.getValue().recoverPendingStreams());
                if (!released && entry.getValue().recoverPendingStreams()) {
                    // As when a first release finds the lease already gone.
                    indexShardDao.setNeedsPendingStreamRecovery(entry.getKey());
                }
                iterator.remove();
            } catch (final RuntimeException e) {
                LOGGER.debug(() -> "Still unable to give up the lease on index shard " + entry.getKey(), e);
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
                    LOGGER.error(() -> "Error renewing index shard leases", e);
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


    private record FailedRelease(Long pruneCommitsDelayMs, boolean recoverPendingStreams) {

    }
}
