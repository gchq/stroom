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
import stroom.util.time.StroomDuration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;

class TestIndexShardLeases {

    private static final long SHARD = 5;
    private static final long OTHER_SHARD = 6;
    private static final Duration LEASE = Duration.ofSeconds(60);
    // A lease is trusted for three quarters of its length after it was last renewed.
    private static final Duration USABLE = Duration.ofSeconds(45);

    private final AtomicLong nanoClock = new AtomicLong(1_000_000_000L);
    private final Set<Long> leasedInDb = new HashSet<>();
    private final List<Long> lostShardIds = new ArrayList<>();

    private IndexShardDao indexShardDao;
    private IndexWriterConfig config;
    private IndexShardLeases leases;

    @BeforeEach
    void setUp() {
        indexShardDao = Mockito.mock(IndexShardDao.class);
        // The database grants every claim and reports whatever this test says is still leased.
        Mockito.when(indexShardDao.claimLease(anyLong(), any(), any(), anyLong())).thenAnswer(invocation -> {
            leasedInDb.add(invocation.getArgument(0));
            return true;
        });
        Mockito.when(indexShardDao.findLeasedShardIds(any())).thenAnswer(invocation -> new HashSet<>(leasedInDb));

        final NodeInfo nodeInfo = Mockito.mock(NodeInfo.class);
        Mockito.when(nodeInfo.getThisNodeName()).thenReturn("node1");

        config = Mockito.mock(IndexWriterConfig.class);
        Mockito.when(config.getShardLeaseDuration()).thenReturn(StroomDuration.of(LEASE));
        // Tests renew by hand. The timer does not fire in the time one takes.
        Mockito.when(config.getShardLeaseRenewInterval()).thenReturn(StroomDuration.ofHours(1));

        leases = new IndexShardLeases(indexShardDao, nodeInfo, () -> config, nanoClock::get);
        leases.setLostLeaseHandler(lostShardIds::add);
    }

    @AfterEach
    void tearDown() {
        leases.stop();
    }

    @Test
    void testClaim_isMadeForThisNodeAndProcess() {
        assertThat(leases.claim(SHARD)).isTrue();

        Mockito.verify(indexShardDao).claimLease(SHARD, "node1", leases.getInstanceId(), LEASE.toMillis());
        assertThat(leases.isHeld(SHARD)).isTrue();
        assertThat(leases.isHeld(OTHER_SHARD)).isFalse();
    }

    @Test
    void testClaim_refusedByTheDatabase() {
        Mockito.when(indexShardDao.claimLease(eq(SHARD), any(), any(), anyLong())).thenReturn(false);

        assertThat(leases.claim(SHARD)).isFalse();
        assertThat(leases.isHeld(SHARD)).isFalse();
    }

    @Test
    void testEachProcessHasItsOwnInstanceId() {
        final IndexShardLeases otherProcess = new IndexShardLeases(indexShardDao, null, null, nanoClock::get);

        assertThat(leases.getInstanceId()).isNotBlank().isNotEqualTo(otherProcess.getInstanceId());
    }

    @Test
    void testIsHeld_stopsBeingTrustedBeforeTheLeaseExpires() {
        leases.claim(SHARD);
        final BooleanSupplier leaseCheck = leases.leaseCheck(SHARD);

        advance(USABLE.minusMillis(1));
        assertThat(leaseCheck.getAsBoolean()).isTrue();

        // With a quarter of the lease still to run, the writer has already stopped.
        advance(Duration.ofMillis(1));
        assertThat(leaseCheck.getAsBoolean()).isFalse();
    }

    @Test
    void testRenew_extendsTheLease() {
        leases.claim(SHARD);
        advance(Duration.ofSeconds(20));

        leases.renew();

        Mockito.verify(indexShardDao).renewLeases(leases.getInstanceId(), LEASE.toMillis());
        // Trusted for the usable period from the renewal, not from the claim.
        advance(USABLE.minusMillis(1));
        assertThat(leases.isHeld(SHARD)).isTrue();
        advance(Duration.ofMillis(1));
        assertThat(leases.isHeld(SHARD)).isFalse();
        assertThat(lostShardIds).isEmpty();
    }

    @Test
    void testRenew_leaseFoundToHaveGone_writerIsToldToStop() {
        leases.claim(SHARD);
        leases.claim(OTHER_SHARD);
        // It expired before this renewal, e.g. because the process was frozen for a while.
        leasedInDb.remove(SHARD);

        leases.renew();

        assertThat(lostShardIds).containsExactly(SHARD);
        assertThat(leases.isHeld(SHARD)).isFalse();
        assertThat(leases.isHeld(OTHER_SHARD)).isTrue();

        // It is only reported once.
        leases.renew();
        assertThat(lostShardIds).containsExactly(SHARD);
    }

    @Test
    void testRenew_databaseUnavailable_leasesRunOutOnTheirOwn() {
        leases.claim(SHARD);
        Mockito.when(indexShardDao.renewLeases(any(), anyLong())).thenThrow(new RuntimeException("no database"));

        advance(Duration.ofSeconds(20));
        leases.renew();
        advance(Duration.ofSeconds(20));
        leases.renew();

        // Still inside the time the last successful renewal covers.
        assertThat(leases.isHeld(SHARD)).isTrue();
        assertThat(lostShardIds).isEmpty();

        // No renewal has worked, so the writer stops without being told to.
        advance(Duration.ofSeconds(5));
        assertThat(leases.isHeld(SHARD)).isFalse();
    }

    @Test
    void testRenew_handlerFailureDoesNotStopOtherLeasesBeingHandled() {
        leases.claim(SHARD);
        leases.claim(OTHER_SHARD);
        leasedInDb.clear();
        final List<Long> handled = new ArrayList<>();
        leases.setLostLeaseHandler(shardId -> {
            handled.add(shardId);
            throw new RuntimeException("could not close");
        });

        leases.renew();

        assertThat(handled).containsExactlyInAnyOrder(SHARD, OTHER_SHARD);
    }

    @Test
    void testRenew_nothingLeased_doesNotTouchTheDatabase() {
        leases.renew();

        Mockito.verify(indexShardDao, Mockito.never()).renewLeases(any(), anyLong());
    }

    @Test
    void testRelease() {
        leases.claim(SHARD);

        leases.release(SHARD, 1_800_000L);

        Mockito.verify(indexShardDao).releaseLease(SHARD, leases.getInstanceId(), 1_800_000L, false);
        assertThat(leases.isHeld(SHARD)).isFalse();

        // A released lease is not then reported as lost.
        leasedInDb.remove(SHARD);
        leases.renew();
        assertThat(lostShardIds).isEmpty();
    }

    @Test
    void testRelease_databaseUnavailable_isTriedAgainAtTheNextRenewal() {
        leases.claim(SHARD);
        Mockito.when(indexShardDao.releaseLease(anyLong(), any(), any(), anyBoolean()))
                .thenThrow(new RuntimeException("no database"))
                .thenReturn(true);

        // Does not throw, and the lease is no longer relied on.
        leases.release(SHARD, 1_800_000L, true);
        assertThat(leases.isHeld(SHARD)).isFalse();

        leases.renew();

        // Given up with the same prune delay and the same word that its writer did not finish cleanly, so
        // the shard does not stay leased to this process.
        Mockito.verify(indexShardDao, Mockito.times(2))
                .releaseLease(SHARD, leases.getInstanceId(), 1_800_000L, true);

        // And only until it has worked.
        leases.renew();
        Mockito.verify(indexShardDao, Mockito.times(2)).releaseLease(anyLong(), any(), any(), anyBoolean());
        assertThat(lostShardIds).isEmpty();
    }

    @Test
    void testRenewInterval_usedAsConfiguredWhenShortEnough() {
        Mockito.when(config.getShardLeaseRenewInterval()).thenReturn(StroomDuration.of(Duration.ofSeconds(20)));

        assertThat(leases.getEffectiveRenewIntervalMs()).isEqualTo(20_000);
    }

    @Test
    void testRenewInterval_shortenedWhenTooLongForTheLease() {
        // As long as the lease itself, which would have every writer stop between renewals.
        Mockito.when(config.getShardLeaseRenewInterval()).thenReturn(StroomDuration.of(LEASE));

        // Two renewals fit in the 45 seconds a lease is trusted for.
        assertThat(leases.getEffectiveRenewIntervalMs()).isEqualTo(22_500);
    }

    @Test
    void testRelease_leaseAlreadyGone_uncleanCloseIsStillRecorded() {
        leases.claim(SHARD);
        // Nothing to give up, as when the lease has been cleared from the shard by someone else.
        Mockito.when(indexShardDao.releaseLease(anyLong(), any(), any(), anyBoolean())).thenReturn(false);

        leases.release(SHARD, null, true);

        Mockito.verify(indexShardDao).setNeedsPendingStreamRecovery(SHARD);
    }

    @Test
    void testRelease_retriedAndLeaseFoundGone_uncleanCloseIsStillRecorded() {
        leases.claim(SHARD);
        Mockito.when(indexShardDao.releaseLease(anyLong(), any(), any(), anyBoolean()))
                .thenThrow(new RuntimeException("no database"))
                .thenReturn(false);
        leases.release(SHARD, null, true);
        Mockito.verify(indexShardDao, Mockito.never()).setNeedsPendingStreamRecovery(anyLong());

        leases.renew();

        Mockito.verify(indexShardDao).setNeedsPendingStreamRecovery(SHARD);
    }

    @Test
    void testRelease_leaseAlreadyGone_cleanCloseRecordsNothing() {
        leases.claim(SHARD);
        Mockito.when(indexShardDao.releaseLease(anyLong(), any(), any(), anyBoolean())).thenReturn(false);

        leases.release(SHARD, null, false);

        Mockito.verify(indexShardDao, Mockito.never()).setNeedsPendingStreamRecovery(anyLong());
    }

    @Test
    void testStop_canBeCalledBeforeAnythingIsLeasedAndMoreThanOnce() {
        leases.stop();
        leases.claim(SHARD);
        leases.stop();
        leases.stop();

        // Stopping the timer does not give up the lease.
        assertThat(leases.isHeld(SHARD)).isTrue();
    }

    private void advance(final Duration duration) {
        nanoClock.addAndGet(duration.toNanos());
    }
}
