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

package stroom.index.impl.dao;

import stroom.db.util.JooqUtil;
import stroom.index.impl.IndexShardDao;
import stroom.index.impl.IndexShardPendingStreamDao;
import stroom.index.impl.IndexVolumeDao;
import stroom.index.impl.IndexVolumeGroupDao;
import stroom.index.impl.PendingStream;
import stroom.index.impl.PendingStreamState;
import stroom.index.impl.db.IndexDbConnProvider;
import stroom.index.impl.db.IndexDbModule;
import stroom.index.shared.AllPartition;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.index.shared.IndexShardKey;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolumeGroup;

import com.google.inject.Guice;
import com.google.inject.Injector;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static stroom.index.impl.db.jooq.tables.IndexShardPendingStream.INDEX_SHARD_PENDING_STREAM;

class TestIndexShardPendingStreamDaoImpl {

    private static final long STREAM = 100;
    private static final long OTHER_STREAM = 200;
    private static final long TASK = 7;
    private static final long OTHER_TASK = 8;
    private static final long SHARD_1 = 11;
    private static final long SHARD_2 = 12;
    private static final long ATTEMPT_ROW = IndexShardPendingStreamDao.ATTEMPT_ROW_SHARD_ID;

    @Inject
    private IndexShardPendingStreamDao dao;
    @Inject
    private IndexShardDao indexShardDao;
    @Inject
    private IndexVolumeDao indexVolumeDao;
    @Inject
    private IndexVolumeGroupDao indexVolumeGroupDao;
    @Inject
    private IndexDbConnProvider indexDbConnProvider;
    private Path tempDir;

    @BeforeEach
    void beforeEach(@TempDir final Path tempDir) {
        final Injector injector = Guice.createInjector(
                new IndexDbModule(),
                new IndexDaoModule(),
                new TestModule());
        injector.injectMembers(this);
        this.tempDir = tempDir;
        JooqUtil.context(indexDbConnProvider, context -> context
                .deleteFrom(INDEX_SHARD_PENDING_STREAM)
                .execute());
    }

    @Test
    void testBeginAttempt_nothingLeftBehind() {
        assertThat(dao.beginAttempt(STREAM, TASK)).isZero();
        assertThat(dao.find(STREAM, TASK)).isEmpty();
    }

    @Test
    void testNormalAttempt_writingThenWrittenThenCommittedThenGone() {
        dao.addShard(SHARD_1, STREAM, TASK);
        dao.addShard(SHARD_2, STREAM, TASK);
        assertThat(states(STREAM, TASK)).containsOnly(
                Map.entry(SHARD_1, PendingStreamState.WRITING),
                Map.entry(SHARD_2, PendingStreamState.WRITING));
        // A commit while the task is still writing does not make its stream safe.
        assertThat(dao.findWrittenRowIds(SHARD_1)).isEmpty();

        assertThat(dao.endAttempt(STREAM, TASK)).isTrue();
        assertThat(states(STREAM, TASK)).containsOnly(
                Map.entry(SHARD_1, PendingStreamState.WRITTEN),
                Map.entry(SHARD_2, PendingStreamState.WRITTEN));

        // The first shard commits. The rows stay, as they still say which shards the stream touched.
        final List<Long> shard1RowIds = dao.findWrittenRowIds(SHARD_1);
        assertThat(shard1RowIds).hasSize(1);
        dao.markCommitted(shard1RowIds);
        assertThat(states(STREAM, TASK)).containsOnly(
                Map.entry(SHARD_1, PendingStreamState.COMMITTED),
                Map.entry(SHARD_2, PendingStreamState.WRITTEN));

        // The second commits, and the stream is done with.
        dao.markCommitted(dao.findWrittenRowIds(SHARD_2));
        assertThat(dao.find(STREAM, TASK)).isEmpty();
    }

    @Test
    void testAddShard_twiceIsHarmless() {
        dao.addShard(SHARD_1, STREAM, TASK);
        dao.addShard(SHARD_1, STREAM, TASK);

        assertThat(dao.find(STREAM, TASK)).hasSize(1);
    }

    @Test
    void testRemoveShard_onlyWhileWriting() {
        dao.addShard(SHARD_1, STREAM, TASK);
        dao.addShard(SHARD_2, STREAM, TASK);
        dao.removeShard(SHARD_1, STREAM, TASK);
        assertThat(states(STREAM, TASK)).containsOnlyKeys(SHARD_2);

        dao.endAttempt(STREAM, TASK);
        dao.removeShard(SHARD_2, STREAM, TASK);
        assertThat(states(STREAM, TASK)).containsOnlyKeys(SHARD_2);
    }

    @Test
    void testSameStreamIndexedByTwoTasks_isTrackedSeparately() {
        dao.addShard(SHARD_1, STREAM, TASK);
        dao.addShard(SHARD_1, STREAM, OTHER_TASK);

        dao.failAttempt(STREAM, TASK);

        assertThat(states(STREAM, TASK).get(SHARD_1)).isEqualTo(PendingStreamState.DELETE);
        assertThat(states(STREAM, OTHER_TASK)).containsOnly(Map.entry(SHARD_1, PendingStreamState.WRITING));
    }

    @Test
    void testFailAttempt_marksEveryShardAndCountsTheAttempt() {
        dao.addShard(SHARD_1, STREAM, TASK);
        dao.addShard(SHARD_2, STREAM, TASK);

        dao.failAttempt(STREAM, TASK);

        assertThat(states(STREAM, TASK)).containsOnly(
                Map.entry(SHARD_1, PendingStreamState.DELETE),
                Map.entry(SHARD_2, PendingStreamState.DELETE),
                Map.entry(ATTEMPT_ROW, PendingStreamState.RETRY_PENDING));
        assertThat(attempt(STREAM, TASK)).isEqualTo(1);
        assertThat(dao.countRowsToDelete(STREAM, TASK)).isEqualTo(2);

        // Failing it again before it has been run again is not another attempt.
        dao.failAttempt(STREAM, TASK);
        assertThat(attempt(STREAM, TASK)).isEqualTo(1);
    }

    @Test
    void testAbandonAttempt_documentsWaitForTheTaskToStop() {
        dao.addShard(SHARD_1, STREAM, TASK);

        // The task fails on one part of its stream but goes on to the next.
        dao.abandonAttempt(STREAM, TASK);

        assertThat(states(STREAM, TASK)).containsOnly(
                Map.entry(SHARD_1, PendingStreamState.ABANDONED),
                Map.entry(ATTEMPT_ROW, PendingStreamState.RETRY_PENDING));
        assertThat(dao.findRowsToDelete(SHARD_1)).isEmpty();
        assertThat(dao.findRetriesToFinish(10)).isEmpty();

        dao.addShard(SHARD_2, STREAM, TASK);
        assertThat(dao.findRowsToDelete(SHARD_1)).isEmpty();

        assertThat(dao.endAttempt(STREAM, TASK)).isFalse();
        assertThat(states(STREAM, TASK)).containsOnly(
                Map.entry(SHARD_1, PendingStreamState.DELETE),
                Map.entry(SHARD_2, PendingStreamState.DELETE),
                Map.entry(ATTEMPT_ROW, PendingStreamState.RETRY_PENDING));
        assertThat(attempt(STREAM, TASK)).isEqualTo(1);
    }

    @Test
    void testMarkCommitted_manyStreamsAtOnce() {
        // More than one batch, with one stream that is also in a shard that has not committed.
        final int count = 1_200;
        for (long streamId = 1; streamId <= count; streamId++) {
            dao.addShard(SHARD_1, streamId, TASK);
            if (streamId == 600) {
                dao.addShard(SHARD_2, streamId, TASK);
            }
            dao.endAttempt(streamId, TASK);
        }
        final List<Long> rowIds = dao.findWrittenRowIds(SHARD_1);
        assertThat(rowIds).hasSize(count);

        dao.markCommitted(rowIds);

        assertThat(dao.find(1, TASK)).isEmpty();
        assertThat(dao.find(count, TASK)).isEmpty();
        assertThat(states(600, TASK)).containsOnly(
                Map.entry(SHARD_1, PendingStreamState.COMMITTED),
                Map.entry(SHARD_2, PendingStreamState.WRITTEN));
        assertThat(dao.findWrittenRowIds(SHARD_1)).isEmpty();
    }

    @Test
    void testFailShard_failsExactlyTheStreamsItHadNotCommitted() {
        // Committed in the shard that fails, so safe.
        dao.addShard(SHARD_1, 1, TASK);
        dao.addShard(SHARD_2, 1, TASK);
        dao.endAttempt(1, TASK);
        dao.markCommitted(dao.findWrittenRowIds(SHARD_1));
        // Written but not committed.
        dao.addShard(SHARD_1, 2, TASK);
        dao.addShard(SHARD_2, 2, TASK);
        dao.endAttempt(2, TASK);
        // Still being written.
        dao.addShard(SHARD_1, 3, TASK);
        dao.addShard(SHARD_2, 3, TASK);
        // Only in another shard.
        dao.addShard(SHARD_2, 4, TASK);

        assertThat(dao.failShard(SHARD_1)).isEqualTo(2);

        assertThat(states(1, TASK)).containsOnly(
                Map.entry(SHARD_1, PendingStreamState.COMMITTED),
                Map.entry(SHARD_2, PendingStreamState.WRITTEN));
        // Removed from every shard it touched, not only the one that failed.
        assertThat(states(2, TASK)).containsOnly(
                Map.entry(SHARD_1, PendingStreamState.DELETE),
                Map.entry(SHARD_2, PendingStreamState.DELETE),
                Map.entry(ATTEMPT_ROW, PendingStreamState.RETRY_PENDING));
        // Its task may still be adding documents, so they can't be removed yet.
        assertThat(states(3, TASK)).containsOnly(
                Map.entry(SHARD_1, PendingStreamState.ABANDONED),
                Map.entry(SHARD_2, PendingStreamState.ABANDONED),
                Map.entry(ATTEMPT_ROW, PendingStreamState.RETRY_PENDING));
        assertThat(states(4, TASK)).containsOnly(Map.entry(SHARD_2, PendingStreamState.WRITING));

        // Doing it again changes nothing.
        assertThat(dao.failShard(SHARD_1)).isZero();
        assertThat(attempt(2, TASK)).isEqualTo(1);
    }

    @Test
    void testFailShard_streamCommittedInAnotherShardIsRemovedFromThatOneToo() {
        dao.addShard(SHARD_1, STREAM, TASK);
        dao.addShard(SHARD_2, STREAM, TASK);
        dao.endAttempt(STREAM, TASK);
        dao.markCommitted(dao.findWrittenRowIds(SHARD_2));

        dao.failShard(SHARD_1);

        assertThat(states(STREAM, TASK)).containsOnly(
                Map.entry(SHARD_1, PendingStreamState.DELETE),
                Map.entry(SHARD_2, PendingStreamState.DELETE),
                Map.entry(ATTEMPT_ROW, PendingStreamState.RETRY_PENDING));
    }

    @Test
    void testEndAttempt_afterAShardItWroteToFailed() {
        dao.addShard(SHARD_1, STREAM, TASK);
        dao.failShard(SHARD_1);
        // The task carries on for a moment and reaches another shard.
        dao.addShard(SHARD_2, STREAM, TASK);
        assertThat(dao.findRowsToDelete(SHARD_1)).isEmpty();

        assertThat(dao.endAttempt(STREAM, TASK)).isFalse();

        // Now that the task has stopped, everything it wrote can be removed.
        assertThat(states(STREAM, TASK)).containsOnly(
                Map.entry(SHARD_1, PendingStreamState.DELETE),
                Map.entry(SHARD_2, PendingStreamState.DELETE),
                Map.entry(ATTEMPT_ROW, PendingStreamState.RETRY_PENDING));
        assertThat(dao.findRowsToDelete(SHARD_1)).extracting(PendingStream::streamId).containsExactly(STREAM);
        assertThat(dao.findRowsToDelete(SHARD_2)).extracting(PendingStream::streamId).containsExactly(STREAM);
    }

    @Test
    void testMarkCommitted_doesNotSaveARowThatHasSinceBeenMarkedForRemoval() {
        dao.addShard(SHARD_1, STREAM, TASK);
        dao.addShard(SHARD_2, STREAM, TASK);
        dao.endAttempt(STREAM, TASK);
        final List<Long> rowIds = dao.findWrittenRowIds(SHARD_1);
        // The other shard's commit fails while this shard is committing.
        dao.failShard(SHARD_2);

        dao.markCommitted(rowIds);

        assertThat(states(STREAM, TASK).get(SHARD_1)).isEqualTo(PendingStreamState.DELETE);
    }

    @Test
    void testRecovery_removeDocumentsThenRetryThenSucceed() {
        dao.addShard(SHARD_1, STREAM, TASK);
        dao.addShard(SHARD_2, STREAM, TASK);
        dao.failAttempt(STREAM, TASK);

        // The task is not put back until the documents have gone from every shard.
        assertThat(dao.findShardIdsWithRowsToDelete()).containsExactlyInAnyOrder(SHARD_1, SHARD_2);
        assertThat(dao.findRetriesToFinish(10)).isEmpty();
        dao.deleteRows(ids(dao.findRowsToDelete(SHARD_1)));
        assertThat(dao.findRetriesToFinish(10)).isEmpty();
        dao.deleteRows(ids(dao.findRowsToDelete(SHARD_2)));
        assertThat(dao.findShardIdsWithRowsToDelete()).isEmpty();

        final List<PendingStream> retries = dao.findRetriesToFinish(10);
        assertThat(retries).hasSize(1);
        assertThat(retries.getFirst().streamId()).isEqualTo(STREAM);
        assertThat(retries.getFirst().taskId()).isEqualTo(TASK);
        assertThat(retries.getFirst().attempt()).isEqualTo(1);
        dao.setRetrying(retries.getFirst().id());
        assertThat(dao.findRetriesToFinish(10)).isEmpty();
        assertThat(states(STREAM, TASK)).containsOnly(Map.entry(ATTEMPT_ROW, PendingStreamState.RETRYING));

        // The second attempt works, and nothing is left.
        assertThat(dao.beginAttempt(STREAM, TASK)).isZero();
        dao.addShard(SHARD_1, STREAM, TASK);
        assertThat(dao.endAttempt(STREAM, TASK)).isTrue();
        dao.markCommitted(dao.findWrittenRowIds(SHARD_1));
        assertThat(dao.find(STREAM, TASK)).isEmpty();
    }

    @Test
    void testAttemptCount_goesUpWithEachFailedAttempt() {
        for (int attempt = 1; attempt <= 3; attempt++) {
            assertThat(dao.beginAttempt(STREAM, TASK)).isZero();
            dao.addShard(SHARD_1, STREAM, TASK);
            dao.failAttempt(STREAM, TASK);
            assertThat(attempt(STREAM, TASK)).isEqualTo(attempt);
            dao.deleteRows(ids(dao.findRowsToDelete(SHARD_1)));
            dao.setRetrying(dao.findRetriesToFinish(10).getFirst().id());
        }
    }

    @Test
    void testBeginAttempt_earlierAttemptThatNeverEndedIsMarkedForRemoval() {
        // The node running the task died, and the task has been started again.
        dao.addShard(SHARD_1, STREAM, TASK);
        dao.addShard(SHARD_2, STREAM, TASK);

        assertThat(dao.beginAttempt(STREAM, TASK)).isEqualTo(2);

        // The task is running again already, so nothing needs to put it back.
        assertThat(states(STREAM, TASK)).containsOnly(
                Map.entry(SHARD_1, PendingStreamState.DELETE),
                Map.entry(SHARD_2, PendingStreamState.DELETE));
        dao.deleteRows(ids(dao.findRowsToDelete(SHARD_1)));
        assertThat(dao.countRowsToDelete(STREAM, TASK)).isEqualTo(1);
        dao.deleteRows(ids(dao.findRowsToDelete(SHARD_2)));
        assertThat(dao.countRowsToDelete(STREAM, TASK)).isZero();
    }

    @Test
    void testBeginAttempt_taskRunAgainBeforeItWasPutBack() {
        dao.addShard(SHARD_1, STREAM, TASK);
        dao.failAttempt(STREAM, TASK);

        dao.beginAttempt(STREAM, TASK);

        assertThat(states(STREAM, TASK).get(ATTEMPT_ROW)).isEqualTo(PendingStreamState.RETRYING);
        assertThat(dao.findRetriesToFinish(10)).isEmpty();
    }

    @Test
    void testFailStaleAttempt() {
        dao.addShard(SHARD_1, STREAM, TASK);
        final long afterInsert = System.currentTimeMillis() + 1_000;

        // Changed too recently.
        assertThat(dao.findStaleAttempts(0, 10)).isEmpty();
        assertThat(dao.failStaleAttempt(STREAM, TASK, 0)).isFalse();
        assertThat(states(STREAM, TASK)).containsOnly(Map.entry(SHARD_1, PendingStreamState.WRITING));

        assertThat(dao.findStaleAttempts(afterInsert, 10))
                .extracting(PendingStream::streamId)
                .containsExactly(STREAM);
        assertThat(dao.failStaleAttempt(STREAM, TASK, afterInsert)).isTrue();
        assertThat(states(STREAM, TASK)).containsOnly(
                Map.entry(SHARD_1, PendingStreamState.DELETE),
                Map.entry(ATTEMPT_ROW, PendingStreamState.RETRY_PENDING));

        // Nothing is being written any more, so there is nothing stale.
        assertThat(dao.findStaleAttempts(afterInsert + 10_000, 10)).isEmpty();
        assertThat(dao.failStaleAttempt(STREAM, TASK, afterInsert + 10_000)).isFalse();
    }

    @Test
    void testMarkCommittedInClosedShards() {
        final IndexShard closedShard = createShard(IndexShardStatus.CLOSED);
        final IndexShard openShard = createShard(IndexShardStatus.OPEN);
        final IndexShard uncleanShard = createShard(IndexShardStatus.OPEN);
        indexShardDao.reset(uncleanShard.getId(), true);
        // Written streams, and one that is still being written.
        dao.addShard(closedShard.getId(), STREAM, TASK);
        dao.addShard(openShard.getId(), STREAM, TASK);
        dao.addShard(uncleanShard.getId(), STREAM, TASK);
        dao.endAttempt(STREAM, TASK);
        dao.addShard(closedShard.getId(), OTHER_STREAM, TASK);

        assertThat(dao.markCommittedInClosedShards()).isEqualTo(1);

        assertThat(states(STREAM, TASK)).containsOnly(
                // A shard that was closed cleanly has committed everything.
                Map.entry(closedShard.getId(), PendingStreamState.COMMITTED),
                Map.entry(openShard.getId(), PendingStreamState.WRITTEN),
                // One whose writer stopped without a final commit has not.
                Map.entry(uncleanShard.getId(), PendingStreamState.WRITTEN));
        assertThat(states(OTHER_STREAM, TASK))
                .containsOnly(Map.entry(closedShard.getId(), PendingStreamState.WRITING));
    }

    @Test
    void testDeleteCommittedStreams() {
        final IndexShard closedShard = createShard(IndexShardStatus.CLOSED);
        final IndexShard openShard = createShard(IndexShardStatus.OPEN);
        dao.addShard(closedShard.getId(), STREAM, TASK);
        dao.endAttempt(STREAM, TASK);
        dao.addShard(closedShard.getId(), OTHER_STREAM, TASK);
        dao.addShard(openShard.getId(), OTHER_STREAM, TASK);
        dao.endAttempt(OTHER_STREAM, TASK);
        dao.markCommittedInClosedShards();

        assertThat(dao.deleteCommittedStreams(10)).isEqualTo(1);

        assertThat(dao.find(STREAM, TASK)).isEmpty();
        // Not yet committed in every shard.
        assertThat(dao.find(OTHER_STREAM, TASK)).hasSize(2);
    }

    @Test
    void testDeleteRowsOfDeletedShards() {
        final IndexShard shard = createShard(IndexShardStatus.CLOSED);
        final IndexShard deletedShard = createShard(IndexShardStatus.CLOSED);
        indexShardDao.logicalDelete(deletedShard.getId());
        final long missingShardId = 999_999_999L;
        dao.addShard(shard.getId(), STREAM, TASK);
        dao.addShard(deletedShard.getId(), STREAM, TASK);
        dao.addShard(missingShardId, STREAM, TASK);
        dao.failAttempt(STREAM, TASK);

        assertThat(dao.deleteRowsOfDeletedShards()).isEqualTo(2);

        // The attempt row is not a shard's row and stays.
        assertThat(states(STREAM, TASK)).containsOnly(
                Map.entry(shard.getId(), PendingStreamState.DELETE),
                Map.entry(ATTEMPT_ROW, PendingStreamState.RETRY_PENDING));
    }

    @Test
    void testDeleteStream() {
        dao.addShard(SHARD_1, STREAM, TASK);
        dao.failAttempt(STREAM, TASK);
        dao.addShard(SHARD_1, OTHER_STREAM, TASK);

        dao.deleteStream(STREAM, TASK);

        assertThat(dao.find(STREAM, TASK)).isEmpty();
        assertThat(dao.find(OTHER_STREAM, TASK)).hasSize(1);
    }

    private Map<Long, PendingStreamState> states(final long streamId, final long taskId) {
        return dao.find(streamId, taskId)
                .stream()
                .collect(Collectors.toMap(PendingStream::shardId, PendingStream::state));
    }

    private int attempt(final long streamId, final long taskId) {
        return dao.find(streamId, taskId)
                .stream()
                .filter(row -> row.shardId() == ATTEMPT_ROW)
                .findFirst()
                .orElseThrow()
                .attempt();
    }

    private List<Long> ids(final List<PendingStream> rows) {
        return rows.stream().map(PendingStream::id).toList();
    }

    private IndexShard createShard(final IndexShardStatus status) {
        final IndexVolumeGroup indexVolumeGroup = indexVolumeGroupDao.getOrCreate(IndexVolumeGroup
                .builder()
                .name(TestData.createVolumeGroupName())
                .stampAudit("test")
                .build());
        final IndexVolume indexVolume = indexVolumeDao.create(IndexVolume
                .builder()
                .nodeName("node1")
                .path(tempDir.resolve(UUID.randomUUID().toString()).toString())
                .indexVolumeGroupId(indexVolumeGroup.getId())
                .stampAudit("test")
                .build());
        final IndexShardKey indexShardKey = IndexShardKey
                .builder()
                .indexUuid(UUID.randomUUID().toString())
                .partition(AllPartition.INSTANCE)
                .build();
        final IndexShard indexShard = indexShardDao.create(indexShardKey, indexVolume, "node1", "1.0-test");
        assertThat(indexShardDao.setStatus(indexShard.getId(), IndexShardStatus.OPENING)).isTrue();
        assertThat(indexShardDao.setStatus(indexShard.getId(), status)).isTrue();
        return indexShard;
    }
}
