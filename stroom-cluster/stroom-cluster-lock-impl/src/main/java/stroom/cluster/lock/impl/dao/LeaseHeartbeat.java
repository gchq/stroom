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

package stroom.cluster.lock.impl.dao;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

/**
 * Keeps one acquisition of a cluster lock alive for as long as its work runs, and stops that work
 * when it can no longer show the lock is still held.
 *
 * <p>{@link #tick()} is driven by a timer. It returns without waiting on the renewal, so one lock's
 * slow renewal cannot hold up another's, and it skips a renewal while this lock's own previous one is
 * still running — the one in flight sets the same lease.
 *
 * <p>Fencing is what makes the lease safe: once the lease can no longer be shown to be current, some
 * other node is free to take the lock, so the work here has to stop rather than carry on beside it.
 * That is why {@link #tick()} judges the lease itself instead of relying on a renewal failing —
 * a renewal that hangs never fails.
 */
class LeaseHeartbeat {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LeaseHeartbeat.class);

    private final String lockName;
    private final long leaseMs;
    private final long heartbeatIntervalMs;
    /**
     * Renews the lease and returns how many lock rows it matched: 1 while this acquisition still
     * holds the lock, 0 once it does not.
     */
    private final IntSupplier renewLease;
    private final Executor executor;
    private final LongSupplier nanoClock;
    private final Runnable onLeaseLost;

    private final AtomicBoolean inFlight = new AtomicBoolean();
    private final AtomicBoolean fenced = new AtomicBoolean();
    private final AtomicBoolean stopped = new AtomicBoolean();
    // Elapsed time only, so it is unaffected by any machine's clock being adjusted.
    private volatile long lastExtendedNanos;

    LeaseHeartbeat(final String lockName,
                   final long leaseMs,
                   final long heartbeatIntervalMs,
                   final IntSupplier renewLease,
                   final Executor executor,
                   final LongSupplier nanoClock,
                   final Runnable onLeaseLost) {
        this.lockName = lockName;
        this.leaseMs = leaseMs;
        this.heartbeatIntervalMs = heartbeatIntervalMs;
        this.renewLease = renewLease;
        this.executor = executor;
        this.nanoClock = nanoClock;
        this.onLeaseLost = onLeaseLost;
        this.lastExtendedNanos = nanoClock.getAsLong();
    }

    /**
     * Stops any further fencing. Call this before releasing the lock: releasing clears what the
     * renewal matches on, which a renewal already in flight would otherwise read as the lock having
     * been taken by someone else.
     */
    void stop() {
        stopped.set(true);
    }

    boolean isFenced() {
        return fenced.get();
    }

    void tick() {
        if (stopped.get() || fenced.get()) {
            return;
        }
        // Judged on the timer rather than only where a renewal fails, because a renewal that hangs
        // never fails: it holds inFlight, and every later tick would return below having checked
        // nothing while the lease ran out unnoticed.
        if (leaseRunningOut()) {
            fence(LogUtil.message(
                    "Lock {} has not been extended within its {}ms lease", lockName, leaseMs));
            return;
        }
        if (!inFlight.compareAndSet(false, true)) {
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    extend();
                } finally {
                    inFlight.set(false);
                }
            });
        } catch (final RejectedExecutionException e) {
            inFlight.set(false);
            LOGGER.warn("Could not schedule a lease extension for lock {}: {}", lockName, e.getMessage());
        }
    }

    // True once the lease has too little left to survive until the next tick. Judged before it runs
    // out rather than after, because the moment it does another node may take the lock — the holder
    // has to be stopped before that, not after.
    private boolean leaseRunningOut() {
        final long sinceExtendedMs = (nanoClock.getAsLong() - lastExtendedNanos) / 1_000_000L;
        return sinceExtendedMs + heartbeatIntervalMs >= leaseMs;
    }

    private void extend() {
        // Stamped before the renewal, not after: the lease is set at the moment the renewal runs, so
        // timing it from the reply would date the lease later than it really is.
        final long attemptNanos = nanoClock.getAsLong();
        try {
            final int updated = renewLease.getAsInt();
            if (updated == 1) {
                lastExtendedNanos = attemptNanos;
                LOGGER.debug("Extended lease for lock {}", lockName);
            } else if (!stopped.get()) {
                // Re-read after the renewal, so a release that happened while it was in flight — and
                // so matched nothing — is not mistaken for the lock having been lost.
                fence(LogUtil.message(
                        "Lock {} is no longer held by this acquisition, so another node may now hold it",
                        lockName));
            }
        } catch (final Exception e) {
            if (stopped.get()) {
                return;
            }
            // The lease keeps running down while the renewal cannot be made, so a failure this close
            // to the end of it has to stop the work rather than wait for another attempt.
            if (leaseRunningOut()) {
                fence(LogUtil.message(
                        "Lock {} could not be extended within its {}ms lease: {}",
                        lockName, leaseMs, e.getMessage()));
            } else {
                LOGGER.warn("Failed to extend lease for lock {}: {}", lockName, e.getMessage());
            }
        }
    }

    private void fence(final String reason) {
        if (fenced.compareAndSet(false, true)) {
            LOGGER.error("{}. Stopping the work holding it.", reason);
            onLeaseLost.run();
        }
    }
}
