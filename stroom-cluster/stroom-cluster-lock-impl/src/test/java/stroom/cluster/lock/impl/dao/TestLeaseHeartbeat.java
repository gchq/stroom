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

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The lease is only safe because the holder stops working once it can no longer show it still holds
 * the lock. These cover when that happens and when it must not.
 *
 * <p>Time is supplied rather than measured, so nothing here waits on a real clock.
 */
class TestLeaseHeartbeat {

    private static final long LEASE_MS = 1_000;
    private static final long INTERVAL_MS = 300;

    // Renewals run on the calling thread unless a test says otherwise.
    private static final Executor DIRECT = Runnable::run;

    private long nowNanos = 0;
    private final AtomicInteger leaseLostCount = new AtomicInteger();

    private void advanceMs(final long ms) {
        nowNanos += ms * 1_000_000L;
    }

    private LeaseHeartbeat heartbeat(final IntSupplier renewLease) {
        return heartbeat(renewLease, DIRECT);
    }

    private LeaseHeartbeat heartbeat(final IntSupplier renewLease, final Executor executor) {
        return new LeaseHeartbeat(
                "test-lock",
                LEASE_MS,
                INTERVAL_MS,
                renewLease,
                executor,
                () -> nowNanos,
                leaseLostCount::incrementAndGet);
    }

    @Test
    void renewalKeepsTheLeaseAlive() {
        final LeaseHeartbeat heartbeat = heartbeat(() -> 1);

        for (int i = 0; i < 20; i++) {
            advanceMs(INTERVAL_MS);
            heartbeat.tick();
        }

        assertThat(heartbeat.isFenced()).isFalse();
        assertThat(leaseLostCount).hasValue(0);
    }

    @Test
    void stopsTheWorkWhenTheLockIsNoLongerHeld() {
        final LeaseHeartbeat heartbeat = heartbeat(() -> 0);

        advanceMs(INTERVAL_MS);
        heartbeat.tick();

        assertThat(heartbeat.isFenced()).isTrue();
        assertThat(leaseLostCount).hasValue(1);
    }

    /**
     * Releasing the lock clears what a renewal matches on, so a renewal still in flight comes back
     * having matched nothing. That must not be read as the lock having been taken by someone else —
     * the work it would stop has already finished and the thread has moved on.
     */
    @Test
    void doesNotStopWorkWhenTheRenewalRacedWithTheRelease() {
        final LeaseHeartbeat[] holder = new LeaseHeartbeat[1];
        holder[0] = heartbeat(() -> {
            // The release happens while this renewal is in flight, and clears what it matches on.
            holder[0].stop();
            return 0;
        });

        advanceMs(INTERVAL_MS);
        holder[0].tick();

        assertThat(holder[0].isFenced()).isFalse();
        assertThat(leaseLostCount).hasValue(0);
    }

    @Test
    void ticksDoNothingOnceStopped() {
        final LeaseHeartbeat heartbeat = heartbeat(() -> 0);
        heartbeat.stop();

        advanceMs(LEASE_MS * 5);
        heartbeat.tick();

        assertThat(heartbeat.isFenced()).isFalse();
        assertThat(leaseLostCount).hasValue(0);
    }

    /**
     * A renewal that fails while the lease still has room is only a warning — the next tick can try
     * again.
     */
    @Test
    void toleratesAFailedRenewalEarlyInTheLease() {
        final LeaseHeartbeat heartbeat = heartbeat(() -> {
            throw new RuntimeException("database is down");
        });

        advanceMs(INTERVAL_MS);
        heartbeat.tick();

        assertThat(heartbeat.isFenced()).isFalse();
    }

    /**
     * The work has to stop on the last tick before the lease runs out, not the first one after: from
     * the moment it expires another node is free to take the lock.
     */
    @Test
    void stopsTheWorkBeforeTheLeaseExpiresRatherThanAfter() {
        final LeaseHeartbeat heartbeat = heartbeat(() -> {
            throw new RuntimeException("database is down");
        });

        advanceMs(INTERVAL_MS);          // 300ms in, 700ms of lease left
        heartbeat.tick();
        assertThat(heartbeat.isFenced()).isFalse();

        advanceMs(INTERVAL_MS);          // 600ms in, 400ms left — still room for another tick
        heartbeat.tick();
        assertThat(heartbeat.isFenced()).isFalse();

        advanceMs(INTERVAL_MS);          // 900ms in, 100ms left — the next tick would be too late
        heartbeat.tick();

        assertThat(heartbeat.isFenced()).isTrue();
        // Stopped with 100ms of lease still to run, so no other node could have taken it yet.
        assertThat(nowNanos / 1_000_000L).isLessThan(LEASE_MS);
    }

    /**
     * A renewal that hangs never fails, so nothing on the failure path can notice the lease running
     * out. The tick has to judge that for itself.
     */
    @Test
    void stopsTheWorkWhenARenewalHangs() throws InterruptedException {
        final CountDownLatch renewalStarted = new CountDownLatch(1);
        final CountDownLatch releaseRenewal = new CountDownLatch(1);
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            final LeaseHeartbeat heartbeat = heartbeat(() -> {
                renewalStarted.countDown();
                try {
                    releaseRenewal.await();
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return 1;
            }, executor);

            advanceMs(INTERVAL_MS);
            heartbeat.tick();
            assertThat(renewalStarted.await(10, TimeUnit.SECONDS)).isTrue();

            // The renewal is still stuck. Later ticks find it in flight, so only the tick's own
            // check of the lease can stop the work.
            advanceMs(INTERVAL_MS);
            heartbeat.tick();
            assertThat(heartbeat.isFenced()).isFalse();

            advanceMs(INTERVAL_MS);
            heartbeat.tick();

            assertThat(heartbeat.isFenced()).isTrue();
            assertThat(leaseLostCount).hasValue(1);
        } finally {
            releaseRenewal.countDown();
            executor.shutdownNow();
        }
    }

    /**
     * The lease is set when the renewal runs, not when its reply arrives. Timing it from the reply
     * would date the lease later than it is and leave the work running past the end of it.
     */
    @Test
    void datesTheLeaseFromWhenTheRenewalRanNotWhenItReturned() {
        final LeaseHeartbeat heartbeat = heartbeat(() -> {
            // A slow renewal: half the lease passes before it returns.
            advanceMs(500);
            return 1;
        });

        heartbeat.tick();                // renewal ran at 0ms, returned at 500ms
        advanceMs(200);                  // now 700ms after the renewal ran, 300ms of lease left
        heartbeat.tick();

        // Dated from when it ran, 700ms have passed and the lease cannot survive another tick.
        // Dated from when it returned it would look like 200ms, and the work would run on.
        assertThat(heartbeat.isFenced()).isTrue();
    }

    @Test
    void stopsTheWorkOnlyOnce() {
        final LeaseHeartbeat heartbeat = heartbeat(() -> 0);

        for (int i = 0; i < 5; i++) {
            advanceMs(INTERVAL_MS);
            heartbeat.tick();
        }

        assertThat(leaseLostCount).hasValue(1);
    }
}
