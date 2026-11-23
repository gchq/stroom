/*
 * Copyright 2019 Crown Copyright
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

package stroom.index.impl.dao;

import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.index.impl.IndexShardDao;
import stroom.index.impl.IndexVolumeDao;
import stroom.index.impl.IndexVolumeGroupDao;
import stroom.index.impl.db.IndexDbModule;
import stroom.index.shared.AllPartition;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.index.shared.IndexShardFields;
import stroom.index.shared.IndexShardKey;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolume.VolumeType;
import stroom.index.shared.IndexVolumeGroup;
import stroom.index.shared.LuceneIndexDoc;
import stroom.index.shared.LuceneIndexDoc.PartitionBy;
import stroom.index.shared.Partition;
import stroom.index.shared.TimePartition;
import stroom.query.api.datasource.QueryField;
import stroom.query.language.functions.FieldIndex;
import stroom.util.io.ByteSizeUnit;
import stroom.util.shared.Clearable;
import stroom.util.shared.Range;

import com.google.inject.Guice;
import com.google.inject.Injector;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TestIndexShardDaoImpl {

    @Inject
    private IndexVolumeDao indexVolumeDao;
    @Inject
    private IndexVolumeGroupDao indexVolumeGroupDao;
    @Inject
    private IndexShardDao indexShardDao;
    @Inject
    private Set<Clearable> clearables;
    private Path tempDir;

    @BeforeEach
    void beforeEach(@TempDir final Path tempDir) {
        final Injector injector = Guice.createInjector(
                new IndexDbModule(),
                new IndexDaoModule(),
                new TestModule());
        injector.injectMembers(this);
        clearables.forEach(Clearable::clear);
        this.tempDir = tempDir;
    }

//    @Test
//    void createAndGet() {
//        // Given
//        final String partitionName = "ALL";
//        final DocRef index = DocRef.builder()
//                .uuid(UUID.randomUUID().toString())
//                .name(TestData.createIndexName())
//                .type(IndexDoc.DOCUMENT_TYPE)
//                .build();
//        final String nodeName = TestData.createNodeName();
//        final String path = TestData.createPath();
//        final String volumeGroupName = TestData.createVolumeGroupName();
//        final Long shardFrom = System.currentTimeMillis();
//        final Long shardTo = shardFrom + 3600;
//
//        // When
//        final IndexVolume createdVolume = createVolume(nodeName, path);
//        createGroup(volumeGroupName);
//        indexVolumeDao.addVolumeToGroup(createdVolume.getId(), volumeGroupName);
//        final IndexShardKey indexShardKey = new IndexShardKey.Builder()
//                .indexUuid(index.getUuid())
//                .partition(partitionName)
//                .shardNo(0)
//                .partitionFromTime(shardFrom)
//                .partitionToTime(shardTo)
//                .build();
//        final IndexShard createdIndexShard = indexShardDao.create(
//        indexShardKey, volumeGroupName, nodeName, "1.0-test");
//        final IndexShard byIdIndexShard = indexShardDao.fetch(createdIndexShard.getId()).orElse(null);
//
//        // Then
//        assertThat(createdIndexShard).isNotNull();
//        assertThat(byIdIndexShard).isNotNull();
//
//        assertThat(createdIndexShard.getVolume()).isNotNull();
//        assertThat(byIdIndexShard.getVolume()).isNotNull();
//    }

    @Test
    void createAndUpdateShard() {
        // Given
        final DocRef index = DocRef.builder()
                .uuid(UUID.randomUUID().toString())
                .name(TestData.createIndexName())
                .type(LuceneIndexDoc.TYPE)
                .build();
        final String nodeName = TestData.createNodeName();

        final String volumeGroupName = TestData.createVolumeGroupName();
        final IndexVolumeGroup indexVolumeGroup = createGroup(volumeGroupName);

        final IndexVolume indexVolume = createVolume(
                nodeName, tempDir.resolve("my_vol1").toString(), indexVolumeGroup);

        // When
        createGroup(volumeGroupName);
        final IndexShardKey indexShardKey = IndexShardKey
                .builder()
                .indexUuid(index.getUuid())
                .partition(AllPartition.INSTANCE)
                .build();

        final IndexShard indexShard = indexShardDao.create(
                indexShardKey, indexVolume, nodeName, "1.0-test");

        Assertions.assertThat(indexShard)
                .isNotNull();
        Assertions.assertThat(indexShard.getDocumentCount())
                .isEqualTo(0);
        Assertions.assertThat(indexShard.getCommitDurationMs())
                .isNull();
        Assertions.assertThat(indexShard.getCommitMs())
                .isNull();

        final long nowMs = Instant.now().toEpochMilli();
        indexShardDao.update(
                indexShard.getId(),
                123,
                456L,
                nowMs,
                ByteSizeUnit.GIBIBYTE.longBytes(5));

        final IndexShard indexShard2 = indexShardDao.fetch(indexShard.getId()).get();

        Assertions.assertThat(indexShard2.getDocumentCount())
                .isEqualTo(123);
        Assertions.assertThat(indexShard2.getCommitDurationMs())
                .isEqualTo(456);
        Assertions.assertThat(indexShard2.getCommitMs())
                .isEqualTo(nowMs);
    }

    // ---- Leases on shards on shared volumes ----

    private static final long LEASE_MS = 60_000;
    private static final String PROCESS_A = "process-a";
    private static final String PROCESS_B = "process-b";

    @Test
    void testClaimLease_marksTheShardAsOpeningForTheClaimer() {
        final IndexShard indexShard = createClosedShard(VolumeType.SHARED, "node1");

        assertThat(indexShardDao.claimLease(indexShard.getId(), "node2", PROCESS_B, LEASE_MS)).isTrue();

        final IndexShard claimed = indexShardDao.fetch(indexShard.getId()).orElseThrow();
        assertThat(claimed.getStatus()).isEqualTo(IndexShardStatus.OPENING);
        // The shard is now the claimer's, whichever node wrote it before.
        assertThat(claimed.getNodeName()).isEqualTo("node2");
        assertThat(indexShardDao.findLeasedShardIds(PROCESS_B)).containsExactly(indexShard.getId());
        assertThat(indexShardDao.findLeasedShardIds(PROCESS_A)).isEmpty();
    }

    @Test
    void testClaimLease_newShardCanBeClaimed() {
        final IndexShard indexShard = createShard(VolumeType.SHARED, "node1");
        assertThat(indexShard.getStatus()).isEqualTo(IndexShardStatus.NEW);

        assertThat(indexShardDao.claimLease(indexShard.getId(), "node1", PROCESS_A, LEASE_MS)).isTrue();
    }

    @Test
    void testClaimLease_secondClaimIsRefused() {
        final IndexShard indexShard = createClosedShard(VolumeType.SHARED, "node1");
        assertThat(indexShardDao.claimLease(indexShard.getId(), "node1", PROCESS_A, LEASE_MS)).isTrue();

        // Not by another process, and not by the same one again.
        assertThat(indexShardDao.claimLease(indexShard.getId(), "node2", PROCESS_B, LEASE_MS)).isFalse();
        assertThat(indexShardDao.claimLease(indexShard.getId(), "node1", PROCESS_A, LEASE_MS)).isFalse();
        assertThat(indexShardDao.fetch(indexShard.getId()).orElseThrow().getNodeName()).isEqualTo("node1");
    }

    @Test
    void testClaimLease_manyProcessesRacing_exactlyOneWins() throws Exception {
        final IndexShard indexShard = createClosedShard(VolumeType.SHARED, "node1");
        final int processCount = 8;
        final CountDownLatch start = new CountDownLatch(1);

        final List<CompletableFuture<Boolean>> claims = IntStream.range(0, processCount)
                .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                    try {
                        start.await();
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return indexShardDao.claimLease(indexShard.getId(), "node" + i, "process-" + i, LEASE_MS);
                }))
                .toList();
        start.countDown();

        int winners = 0;
        for (final CompletableFuture<Boolean> claim : claims) {
            if (claim.get()) {
                winners++;
            }
        }
        assertThat(winners).isEqualTo(1);
    }

    @Test
    void testClaimLease_shardThatIsNotClosedIsRefused() {
        final IndexShard indexShard = createShard(VolumeType.SHARED, "node1");
        indexShardDao.setStatus(indexShard.getId(), IndexShardStatus.OPENING);
        indexShardDao.setStatus(indexShard.getId(), IndexShardStatus.OPEN);

        assertThat(indexShardDao.claimLease(indexShard.getId(), "node2", PROCESS_B, LEASE_MS)).isFalse();

        indexShardDao.logicalDelete(indexShard.getId());
        assertThat(indexShardDao.claimLease(indexShard.getId(), "node2", PROCESS_B, LEASE_MS)).isFalse();
    }

    @Test
    void testRenewLeases_extendsOnlyTheProcessesOwnUnexpiredLeases() {
        final IndexShard shard1 = createClosedShard(VolumeType.SHARED, "node1");
        final IndexShard shard2 = createClosedShard(VolumeType.SHARED, "node1");
        final IndexShard expiredShard = createClosedShard(VolumeType.SHARED, "node1");
        final IndexShard otherProcessShard = createClosedShard(VolumeType.SHARED, "node2");
        indexShardDao.claimLease(shard1.getId(), "node1", PROCESS_A, LEASE_MS);
        indexShardDao.claimLease(shard2.getId(), "node1", PROCESS_A, LEASE_MS);
        // A lease that had run out by the time it was claimed stands for one that was not renewed in time.
        indexShardDao.claimLease(expiredShard.getId(), "node1", PROCESS_A, -1_000);
        indexShardDao.claimLease(otherProcessShard.getId(), "node2", PROCESS_B, LEASE_MS);

        assertThat(indexShardDao.renewLeases(PROCESS_A, LEASE_MS)).isEqualTo(2);

        // An expired lease is not brought back by renewing, as its holder must stop writing first.
        assertThat(indexShardDao.findLeasedShardIds(PROCESS_A))
                .containsExactlyInAnyOrder(shard1.getId(), shard2.getId());
        assertThat(indexShardDao.findLeasedShardIds(PROCESS_B)).containsExactly(otherProcessShard.getId());
    }

    @Test
    void testExpiredLease_isNotTakenByAnotherProcess() {
        // There is no takeover yet, so a lapsed lease stays with its holder until that node comes back.
        final IndexShard indexShard = createClosedShard(VolumeType.SHARED, "node1");
        indexShardDao.claimLease(indexShard.getId(), "node1", PROCESS_A, -1_000);

        assertThat(indexShardDao.claimLease(indexShard.getId(), "node2", PROCESS_B, LEASE_MS)).isFalse();
    }

    @Test
    void testReleaseLease_handsTheShardToAnyNode() {
        final IndexShard indexShard = createClosedShard(VolumeType.SHARED, "node1");
        indexShardDao.claimLease(indexShard.getId(), "node1", PROCESS_A, LEASE_MS);
        indexShardDao.setStatus(indexShard.getId(), IndexShardStatus.OPEN);

        // Only the holder can give the lease up.
        assertThat(indexShardDao.releaseLease(indexShard.getId(), PROCESS_B, null)).isFalse();
        assertThat(indexShardDao.fetch(indexShard.getId()).orElseThrow().getStatus())
                .isEqualTo(IndexShardStatus.OPEN);

        assertThat(indexShardDao.releaseLease(indexShard.getId(), PROCESS_A, null)).isTrue();

        // As when a node shuts down cleanly: the shard is closed and another node can write to it at once.
        assertThat(indexShardDao.fetch(indexShard.getId()).orElseThrow().getStatus())
                .isEqualTo(IndexShardStatus.CLOSED);
        assertThat(indexShardDao.findLeasedShardIds(PROCESS_A)).isEmpty();
        assertThat(indexShardDao.claimLease(indexShard.getId(), "node2", PROCESS_B, LEASE_MS)).isTrue();
    }

    @Test
    void testReleaseLease_expiredLeaseCanStillBeGivenUpByItsHolder() {
        final IndexShard indexShard = createClosedShard(VolumeType.SHARED, "node1");
        indexShardDao.claimLease(indexShard.getId(), "node1", PROCESS_A, -1_000);

        assertThat(indexShardDao.releaseLease(indexShard.getId(), PROCESS_A, null)).isTrue();
        assertThat(indexShardDao.claimLease(indexShard.getId(), "node2", PROCESS_B, LEASE_MS)).isTrue();
    }

    @Test
    void testReleaseLease_deletedShardStaysDeleted() {
        final IndexShard indexShard = createClosedShard(VolumeType.SHARED, "node1");
        indexShardDao.claimLease(indexShard.getId(), "node1", PROCESS_A, LEASE_MS);
        indexShardDao.logicalDelete(indexShard.getId());

        assertThat(indexShardDao.releaseLease(indexShard.getId(), PROCESS_A, null)).isTrue();

        assertThat(indexShardDao.fetch(indexShard.getId()).orElseThrow().getStatus())
                .isEqualTo(IndexShardStatus.DELETED);
    }

    @Test
    void testResetAfterRestart_clearsALeaseLeftByAnEarlierProcess() {
        // A node that starts up resets the shards its predecessor left open.
        final IndexShard indexShard = createClosedShard(VolumeType.SHARED, "node1");
        indexShardDao.claimLease(indexShard.getId(), "node1", PROCESS_A, LEASE_MS);
        indexShardDao.setStatus(indexShard.getId(), IndexShardStatus.OPEN);
        assertThat(indexShardDao.isLeased(indexShard.getId())).isTrue();

        indexShardDao.resetAfterRestart(indexShard.getId(), "node1", PROCESS_B);

        assertThat(indexShardDao.isLeased(indexShard.getId())).isFalse();
        assertThat(indexShardDao.findLeasedShardIds(PROCESS_A)).isEmpty();
        assertThat(indexShardDao.claimLease(indexShard.getId(), "node1", PROCESS_B, LEASE_MS)).isTrue();
    }

    @Test
    void testResetAfterRestart_leavesTheStartingProcessesOwnLease() {
        final IndexShard indexShard = createClosedShard(VolumeType.SHARED, "node1");
        indexShardDao.claimLease(indexShard.getId(), "node1", PROCESS_B, LEASE_MS);
        indexShardDao.setStatus(indexShard.getId(), IndexShardStatus.OPEN);

        indexShardDao.resetAfterRestart(indexShard.getId(), "node1", PROCESS_B);

        assertThat(indexShardDao.findLeasedShardIds(PROCESS_B)).containsExactly(indexShard.getId());
        assertThat(indexShardDao.fetch(indexShard.getId()).orElseThrow().getStatus())
                .isEqualTo(IndexShardStatus.OPEN);
    }

    @Test
    void testPendingStreamRecoveryFlag_setOnlyByAnUncleanClose() {
        final IndexShard cleanShard = createClosedShard(VolumeType.SHARED, "node1");
        final IndexShard uncleanShard = createClosedShard(VolumeType.SHARED, "node1");
        final IndexShard localShard = createClosedShard(VolumeType.LOCAL, "node1");
        indexShardDao.claimLease(cleanShard.getId(), "node1", PROCESS_A, LEASE_MS);
        indexShardDao.claimLease(uncleanShard.getId(), "node1", PROCESS_A, LEASE_MS);
        // A closed shard is opened in two steps.
        assertThat(indexShardDao.setStatus(localShard.getId(), IndexShardStatus.OPENING)).isTrue();
        assertThat(indexShardDao.setStatus(localShard.getId(), IndexShardStatus.OPEN)).isTrue();

        indexShardDao.releaseLease(cleanShard.getId(), PROCESS_A, null, false);
        indexShardDao.releaseLease(uncleanShard.getId(), PROCESS_A, null, true);
        indexShardDao.reset(localShard.getId(), true);

        assertThat(indexShardDao.needsPendingStreamRecovery(cleanShard.getId())).isFalse();
        assertThat(indexShardDao.needsPendingStreamRecovery(uncleanShard.getId())).isTrue();
        assertThat(indexShardDao.needsPendingStreamRecovery(localShard.getId())).isTrue();
        assertThat(indexShardDao.findClosedShardsNeedingPendingStreamRecovery(100))
                .extracting(IndexShard::getId)
                .contains(uncleanShard.getId(), localShard.getId())
                .doesNotContain(cleanShard.getId());

        // A later clean close does not clear it. Only marking the streams does.
        indexShardDao.claimLease(uncleanShard.getId(), "node2", PROCESS_B, LEASE_MS);
        assertThat(indexShardDao.findClosedShardsNeedingPendingStreamRecovery(100))
                .extracting(IndexShard::getId)
                .doesNotContain(uncleanShard.getId());
        indexShardDao.releaseLease(uncleanShard.getId(), PROCESS_B, null, false);
        assertThat(indexShardDao.needsPendingStreamRecovery(uncleanShard.getId())).isTrue();

        indexShardDao.clearPendingStreamRecovery(uncleanShard.getId());
        assertThat(indexShardDao.needsPendingStreamRecovery(uncleanShard.getId())).isFalse();

        indexShardDao.setNeedsPendingStreamRecovery(cleanShard.getId());
        assertThat(indexShardDao.needsPendingStreamRecovery(cleanShard.getId())).isTrue();
    }

    @Test
    void testResetAfterRestart_leavesAShardThatAnotherNodeHasTakenOn() {
        // The shard was this node's when it listed the shards to reset. Since then it has been taken
        // from this node's dead process and leased by another node.
        final IndexShard indexShard = createClosedShard(VolumeType.SHARED, "node1");
        indexShardDao.claimLease(indexShard.getId(), "node2", PROCESS_A, LEASE_MS);
        indexShardDao.setStatus(indexShard.getId(), IndexShardStatus.OPEN);

        indexShardDao.resetAfterRestart(indexShard.getId(), "node1", PROCESS_B);

        assertThat(indexShardDao.findLeasedShardIds(PROCESS_A)).containsExactly(indexShard.getId());
        assertThat(indexShardDao.fetch(indexShard.getId()).orElseThrow().getStatus())
                .isEqualTo(IndexShardStatus.OPEN);
        assertThat(indexShardDao.needsPendingStreamRecovery(indexShard.getId())).isFalse();
    }

    @Test
    void testResetAfterRestart_marksTheShardAsNotClosedCleanly() {
        final IndexShard indexShard = createClosedShard(VolumeType.LOCAL, "node1");
        // A closed shard is opened in two steps.
        assertThat(indexShardDao.setStatus(indexShard.getId(), IndexShardStatus.OPENING)).isTrue();
        assertThat(indexShardDao.setStatus(indexShard.getId(), IndexShardStatus.OPEN)).isTrue();

        indexShardDao.resetAfterRestart(indexShard.getId(), "node1", PROCESS_B);

        assertThat(indexShardDao.needsPendingStreamRecovery(indexShard.getId())).isTrue();
    }

    @Test
    void testReleaseLapsedLeases() {
        final IndexShard lapsed = createClosedShard(VolumeType.SHARED, "node1");
        final IndexShard lapsedAndDeleted = createClosedShard(VolumeType.SHARED, "node1");
        final IndexShard live = createClosedShard(VolumeType.SHARED, "node1");
        final IndexShard unleased = createClosedShard(VolumeType.SHARED, "node1");
        // Leases that ran out an hour ago, and one with a minute still to run.
        indexShardDao.claimLease(lapsed.getId(), "node1", PROCESS_A, -3_600_000);
        indexShardDao.setStatus(lapsed.getId(), IndexShardStatus.OPEN);
        indexShardDao.claimLease(lapsedAndDeleted.getId(), "node1", PROCESS_A, -3_600_000);
        indexShardDao.logicalDelete(lapsedAndDeleted.getId());
        indexShardDao.claimLease(live.getId(), "node1", PROCESS_B, LEASE_MS);

        // Not yet out of the grace period.
        assertThat(indexShardDao.releaseLapsedLeases(7_200_000, 1_000)).isZero();
        assertThat(indexShardDao.isLeased(lapsed.getId())).isTrue();

        assertThat(indexShardDao.releaseLapsedLeases(300_000, 1_000)).isEqualTo(2);

        // Closed, free for any node, and marked so that its uncommitted streams are recovered.
        final IndexShard taken = indexShardDao.fetch(lapsed.getId()).orElseThrow();
        assertThat(taken.getStatus()).isEqualTo(IndexShardStatus.CLOSED);
        assertThat(indexShardDao.isLeased(lapsed.getId())).isFalse();
        assertThat(indexShardDao.needsPendingStreamRecovery(lapsed.getId())).isTrue();
        assertThat(indexShardDao.claimLease(lapsed.getId(), "node2", PROCESS_B, LEASE_MS)).isTrue();

        // A deleted shard stays deleted, and can now have its files removed. It is never opened again,
        // so it is not marked.
        assertThat(indexShardDao.fetch(lapsedAndDeleted.getId()).orElseThrow().getStatus())
                .isEqualTo(IndexShardStatus.DELETED);
        assertThat(indexShardDao.isLeased(lapsedAndDeleted.getId())).isFalse();
        assertThat(indexShardDao.needsPendingStreamRecovery(lapsedAndDeleted.getId())).isFalse();

        // A lease that is being renewed, and a shard with none, are untouched.
        assertThat(indexShardDao.findLeasedShardIds(PROCESS_B)).contains(live.getId());
        assertThat(indexShardDao.needsPendingStreamRecovery(live.getId())).isFalse();
        assertThat(indexShardDao.needsPendingStreamRecovery(unleased.getId())).isFalse();

        // The old holder can no longer renew or give up what it has lost.
        assertThat(indexShardDao.findLeasedShardIds(PROCESS_A)).isEmpty();
        assertThat(indexShardDao.releaseLease(lapsed.getId(), PROCESS_A, null, true)).isFalse();
    }

    @Test
    void testReleaseLapsedLeases_negativeGraceNeverTakesALiveLease() {
        final IndexShard live = createClosedShard(VolumeType.SHARED, "node1");
        indexShardDao.claimLease(live.getId(), "node1", PROCESS_B, LEASE_MS);

        // Two hours of negative grace would otherwise reach leases with up to two hours left to run.
        assertThat(indexShardDao.releaseLapsedLeases(-7_200_000, 1_000)).isZero();

        assertThat(indexShardDao.findLeasedShardIds(PROCESS_B)).containsExactly(live.getId());
    }

    @Test
    void testSetNeedsPendingStreamRecovery_notOnceAnotherProcessHasTheShard() {
        final IndexShard indexShard = createClosedShard(VolumeType.SHARED, "node1");
        indexShardDao.claimLease(indexShard.getId(), "node2", PROCESS_B, LEASE_MS);

        // Said by the old holder as it finally closes its writer, after the shard was taken from it.
        indexShardDao.setNeedsPendingStreamRecovery(indexShard.getId());

        assertThat(indexShardDao.needsPendingStreamRecovery(indexShard.getId())).isFalse();
    }

    @Test
    void testReset_leavesALeasedShardAlone() {
        // Only the holder of the lease may close a leased shard, by giving the lease up.
        final IndexShard indexShard = createClosedShard(VolumeType.SHARED, "node1");
        indexShardDao.claimLease(indexShard.getId(), "node1", PROCESS_A, LEASE_MS);
        indexShardDao.setStatus(indexShard.getId(), IndexShardStatus.OPEN);

        indexShardDao.reset(indexShard.getId());

        assertThat(indexShardDao.findLeasedShardIds(PROCESS_A)).containsExactly(indexShard.getId());
        assertThat(indexShardDao.fetch(indexShard.getId()).orElseThrow().getStatus())
                .isEqualTo(IndexShardStatus.OPEN);
    }

    @Test
    void testReset_closesAnOpenShardThatHasNoLease() {
        final IndexShard indexShard = createClosedShard(VolumeType.LOCAL, "node1");
        // A closed shard is opened in two steps.
        assertThat(indexShardDao.setStatus(indexShard.getId(), IndexShardStatus.OPENING)).isTrue();
        assertThat(indexShardDao.setStatus(indexShard.getId(), IndexShardStatus.OPEN)).isTrue();
        assertThat(indexShardDao.isLeased(indexShard.getId())).isFalse();

        indexShardDao.reset(indexShard.getId());

        assertThat(indexShardDao.fetch(indexShard.getId()).orElseThrow().getStatus())
                .isEqualTo(IndexShardStatus.CLOSED);
    }

    @Test
    void testFindUnleasedShardsOnSharedVolumes() {
        final String indexUuid = UUID.randomUUID().toString();
        final IndexShard closedShared = createClosedShard(VolumeType.SHARED, "node1", indexUuid);
        final IndexShard leasedShared = createClosedShard(VolumeType.SHARED, "node1", indexUuid);
        indexShardDao.claimLease(leasedShared.getId(), "node1", PROCESS_A, LEASE_MS);
        // Not found: never opened, on a local volume, and another index.
        createShard(VolumeType.SHARED, "node1", indexUuid);
        createClosedShard(VolumeType.LOCAL, "node1", indexUuid);
        createClosedShard(VolumeType.SHARED, "node1");

        final String partition = AllPartition.INSTANCE.getLabel();
        assertThat(indexShardDao.findUnleasedShardsOnSharedVolumes(indexUuid, partition))
                .extracting(IndexShard::getId)
                .containsExactly(closedShared.getId());
        assertThat(indexShardDao.findUnleasedShardsOnSharedVolumes(indexUuid, "another partition")).isEmpty();
        // The volume comes with the shard, as it is needed to open it.
        assertThat(indexShardDao.findUnleasedShardsOnSharedVolumes(indexUuid, partition))
                .allSatisfy(shard -> assertThat(shard.getVolume().isShared()).isTrue());
    }

    @Test
    void testFindShardsWithCommitsToPrune() {
        final IndexShard dueShard = createClosedShard(VolumeType.SHARED, "node1");
        final IndexShard notYetDueShard = createClosedShard(VolumeType.SHARED, "node1");
        final IndexShard nothingToPruneShard = createClosedShard(VolumeType.SHARED, "node1");
        final IndexShard reopenedShard = createClosedShard(VolumeType.SHARED, "node1");
        for (final IndexShard shard : List.of(dueShard, notYetDueShard, nothingToPruneShard, reopenedShard)) {
            indexShardDao.claimLease(shard.getId(), "node1", PROCESS_A, LEASE_MS);
        }
        // Its writer closed with replaced commits whose time is already up.
        indexShardDao.releaseLease(dueShard.getId(), PROCESS_A, -1_000L);
        indexShardDao.releaseLease(notYetDueShard.getId(), PROCESS_A, 3_600_000L);
        indexShardDao.releaseLease(nothingToPruneShard.getId(), PROCESS_A, null);
        // Due, but a writer has it open again and will see to its own commits.
        indexShardDao.releaseLease(reopenedShard.getId(), PROCESS_A, -1_000L);
        indexShardDao.claimLease(reopenedShard.getId(), "node2", PROCESS_B, LEASE_MS);

        assertThat(indexShardDao.findShardsWithCommitsToPrune(10))
                .extracting(IndexShard::getId)
                .containsExactly(dueShard.getId());

        // A later close that leaves nothing behind keeps the time that is already set.
        indexShardDao.claimLease(dueShard.getId(), "node1", PROCESS_A, LEASE_MS);
        indexShardDao.releaseLease(dueShard.getId(), PROCESS_A, null);
        assertThat(indexShardDao.findShardsWithCommitsToPrune(10))
                .extracting(IndexShard::getId)
                .containsExactly(dueShard.getId());

        indexShardDao.clearPruneCommitsTime(dueShard.getId());
        assertThat(indexShardDao.findShardsWithCommitsToPrune(10)).isEmpty();
    }

    @Test
    void testFindShardsWithCommitsToPrune_limit() {
        for (int i = 0; i < 3; i++) {
            final IndexShard shard = createClosedShard(VolumeType.SHARED, "node1");
            indexShardDao.claimLease(shard.getId(), "node1", PROCESS_A, LEASE_MS);
            indexShardDao.releaseLease(shard.getId(), PROCESS_A, -1_000L);
        }

        assertThat(indexShardDao.findShardsWithCommitsToPrune(2)).hasSize(2);
    }

    private IndexShard createClosedShard(final VolumeType volumeType, final String nodeName) {
        return createClosedShard(volumeType, nodeName, UUID.randomUUID().toString());
    }

    // A shard that has been opened and closed, as one that has been written to would be.
    private IndexShard createClosedShard(final VolumeType volumeType,
                                         final String nodeName,
                                         final String indexUuid) {
        final IndexShard indexShard = createShard(volumeType, nodeName, indexUuid);
        assertThat(indexShardDao.setStatus(indexShard.getId(), IndexShardStatus.OPENING)).isTrue();
        assertThat(indexShardDao.setStatus(indexShard.getId(), IndexShardStatus.CLOSED)).isTrue();
        return indexShardDao.fetch(indexShard.getId()).orElseThrow();
    }

    private IndexShard createShard(final VolumeType volumeType, final String nodeName) {
        return createShard(volumeType, nodeName, UUID.randomUUID().toString());
    }

    private IndexShard createShard(final VolumeType volumeType, final String nodeName, final String indexUuid) {
        final IndexVolumeGroup indexVolumeGroup = createGroup(TestData.createVolumeGroupName());
        final IndexVolume indexVolume = indexVolumeDao.create(IndexVolume
                .builder()
                .nodeName(VolumeType.SHARED == volumeType
                        ? null
                        : nodeName)
                .path(tempDir.resolve(UUID.randomUUID().toString()).toString())
                .volumeType(volumeType)
                .indexVolumeGroupId(indexVolumeGroup.getId())
                .stampAudit("test")
                .build());
        final IndexShardKey indexShardKey = IndexShardKey
                .builder()
                .indexUuid(indexUuid)
                .partition(AllPartition.INSTANCE)
                .build();
        return indexShardDao.create(indexShardKey, indexVolume, nodeName, "1.0-test");
    }

    private IndexVolume createVolume(final String nodeName,
                                     final String path,
                                     final IndexVolumeGroup indexVolumeGroup) {
        final IndexVolume indexVolume = IndexVolume
                .builder()
                .nodeName(nodeName)
                .path(path)
                .indexVolumeGroupId(indexVolumeGroup.getId())
                .stampAudit("test")
                .build();
        return indexVolumeDao.create(indexVolume);
    }

    private IndexVolumeGroup createGroup(final String name) {
        final IndexVolumeGroup indexVolumeGroup = IndexVolumeGroup
                .builder()
                .name(name)
                .stampAudit("test")
                .build();
        return indexVolumeGroupDao.getOrCreate(indexVolumeGroup);
    }

    @Test
    void testSearch() {
        // Given
        final DocRef index = DocRef.builder()
                .uuid(UUID.randomUUID().toString())
                .name(TestData.createIndexName())
                .type(LuceneIndexDoc.TYPE)
                .build();
        final String nodeName = TestData.createNodeName();

        final String volumeGroupName = TestData.createVolumeGroupName();
        final IndexVolumeGroup indexVolumeGroup = createGroup(volumeGroupName);

        final IndexVolume indexVolume = createVolume(
                nodeName, tempDir.resolve("my_vol1").toString(), indexVolumeGroup);

        // When
        createGroup(volumeGroupName);
        final IndexShardKey indexShardKey = IndexShardKey
                .builder()
                .indexUuid(index.getUuid())
                .partition(AllPartition.INSTANCE)
                .build();

        indexShardDao.create(indexShardKey, indexVolume, nodeName, "1.0-test");
        indexShardDao.create(indexShardKey, indexVolume, nodeName, "1.0-test");

        final List<QueryField> fields = IndexShardFields.getFields();
        assertThat(fields.size()).isEqualTo(12);

        for (final QueryField field : fields) {
            final FieldIndex fieldIndex = new FieldIndex();
            fieldIndex.create(field.getFldName());

            final AtomicInteger count = new AtomicInteger();
            indexShardDao.search(new ExpressionCriteria(), fieldIndex, values -> {
                count.incrementAndGet();
                assertThat(values.length).isEqualTo(1);
            });
            assertThat(count.get()).isEqualTo(2);
        }
    }

    @Test
    void testFind_partitionTimeRange_bothBounds() {
        // Mid to late March finds March's month shard, the year shard and the unpartitioned one
        final String indexUuid = createMixedShards();

        assertThat(findPartitions(indexUuid, new Range<>(ms("2024-03-15"), ms("2024-03-31"))))
                .containsExactlyInAnyOrder("all", "2024", "2024-03");
    }

    @Test
    void testFind_partitionTimeRange_fromOnly() {
        final String indexUuid = createMixedShards();

        assertThat(findPartitions(indexUuid, new Range<>(ms("2024-04-15"), null)))
                .containsExactlyInAnyOrder("all", "2024", "2024-05");
    }

    @Test
    void testFind_partitionTimeRange_toOnly() {
        final String indexUuid = createMixedShards();

        assertThat(findPartitions(indexUuid, new Range<>(null, ms("2024-02-15"))))
                .containsExactlyInAnyOrder("all", "2024", "2024-01");
    }

    @Test
    void testFind_partitionTimeRange_partitionEndIsExclusive() {
        // January's partition ends at the start of February, so a range from then leaves it out
        final String indexUuid = createMixedShards();

        assertThat(findPartitions(indexUuid, new Range<>(ms("2024-02-01"), ms("2024-02-15"))))
                .containsExactlyInAnyOrder("all", "2024");
    }

    @Test
    void testFind_noPartitionTimeRange() {
        final String indexUuid = createMixedShards();

        assertThat(findPartitions(indexUuid, null))
                .containsExactlyInAnyOrder("all", "2024", "2024-01", "2024-03", "2024-05");
    }

    // Creates the shards of an index whose partitioning has changed: one written with No partition
    // (no partition times), one for the year 2024 and month shards for January, March and May 2024.
    private String createMixedShards() {
        final String indexUuid = UUID.randomUUID().toString();
        final String nodeName = TestData.createNodeName();
        final IndexVolumeGroup indexVolumeGroup = createGroup(TestData.createVolumeGroupName());
        final IndexVolume indexVolume = createVolume(
                nodeName, tempDir.resolve("my_vol1").toString(), indexVolumeGroup);
        createShard(indexUuid, AllPartition.INSTANCE, indexVolume, nodeName);
        createShard(indexUuid,
                new TimePartition(PartitionBy.YEAR, 1, ms("2024-01-01"), ms("2025-01-01"), "2024"),
                indexVolume,
                nodeName);
        createMonthShard(indexUuid, "2024-01", "2024-02", indexVolume, nodeName);
        createMonthShard(indexUuid, "2024-03", "2024-04", indexVolume, nodeName);
        createMonthShard(indexUuid, "2024-05", "2024-06", indexVolume, nodeName);
        return indexUuid;
    }

    private void createMonthShard(final String indexUuid,
                                  final String month,
                                  final String nextMonth,
                                  final IndexVolume indexVolume,
                                  final String nodeName) {
        final TimePartition partition = new TimePartition(
                PartitionBy.MONTH, 1, ms(month + "-01"), ms(nextMonth + "-01"), month);
        createShard(indexUuid, partition, indexVolume, nodeName);
    }

    private List<String> findPartitions(final String indexUuid, final Range<Long> partitionTimeRange) {
        final FindIndexShardCriteria criteria = FindIndexShardCriteria
                .builder()
                .partitionTimeRange(partitionTimeRange)
                .build();
        criteria.getIndexUuidSet().add(indexUuid);
        return indexShardDao.find(criteria)
                .getValues()
                .stream()
                .map(IndexShard::getPartition)
                .toList();
    }

    private static long ms(final String date) {
        return Instant.parse(date + "T00:00:00Z").toEpochMilli();
    }

    private void createShard(final String indexUuid,
                             final Partition partition,
                             final IndexVolume indexVolume,
                             final String nodeName) {
        final IndexShardKey indexShardKey = IndexShardKey
                .builder()
                .indexUuid(indexUuid)
                .partition(partition)
                .build();
        indexShardDao.create(indexShardKey, indexVolume, nodeName, "1.0-test");
    }
}
