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

import stroom.index.impl.IndexShardDao;
import stroom.index.impl.IndexShardPurge;
import stroom.index.impl.IndexShardPurgeDao;
import stroom.index.impl.IndexShardTaskDao;
import stroom.index.impl.IndexShardTaskRow;
import stroom.index.impl.IndexShardTaskState;
import stroom.index.impl.db.IndexDbModule;
import stroom.index.shared.IndexShard.IndexShardStatus;

import com.google.inject.Guice;
import com.google.inject.Injector;
import jakarta.inject.Inject;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestIndexShardTaskDaoImpl {

    // Task ids unique across the tests, as the database is shared between them.
    private static final AtomicLong NEXT_TASK_ID = new AtomicLong(System.currentTimeMillis() * 1000);
    private static final long STREAM = 100;
    private static final String WRITER_A = "writerA";
    private static final String WRITER_B = "writerB";

    @Inject
    private IndexShardTaskDao taskDao;
    @Inject
    private IndexShardPurgeDao purgeDao;
    @Inject
    private IndexShardFixtures shards;
    @Inject
    private IndexShardDao indexShardDao;

    @TempDir
    private Path tempDir;

    @BeforeEach
    void beforeEach() {
        final Injector injector = Guice.createInjector(
                new IndexDbModule(),
                new IndexDaoModule(),
                new TestModule());
        injector.injectMembers(this);
    }

    @Test
    void testInsertWriting() {
        final long shard = shards.createCommittedShard(tempDir);
        final long task = nextTaskId();

        final IndexShardTaskRow row = taskDao.insertWriting(task, shard, STREAM, 1, WRITER_A).orElseThrow();

        assertThat(row.state()).isEqualTo(IndexShardTaskState.WRITING);
        assertThat(taskDao.fetch(task, shard)).contains(row);
        assertThat(taskDao.findByTask(task)).containsExactly(row);
        assertThat(taskDao.findByShardAndStream(shard, STREAM)).containsExactly(row);
        assertThat(row.updateTimeMs()).isCloseTo(taskDao.getDatabaseTimeMs(), Offset.offset(60_000L));

        // One row for each task and shard.
        assertThat(taskDao.insertWriting(task, shard, STREAM, 2, WRITER_B)).isEmpty();
        assertThat(taskDao.fetch(task, shard)).contains(row);
    }

    @Test
    void testUpdate_onlyIfUnchanged() {
        final long shard = shards.createCommittedShard(tempDir);
        final IndexShardTaskRow row = insert(shard, 1, WRITER_A);

        final IndexShardTaskRow committed = taskDao.update(row, 1, WRITER_A, IndexShardTaskState.COMMITTED)
                .orElseThrow();
        assertThat(committed.state()).isEqualTo(IndexShardTaskState.COMMITTED);
        assertThat(taskDao.fetch(row.taskId(), shard)).contains(committed);

        // The row as first read has changed.
        assertThat(taskDao.update(row, 2, WRITER_B, IndexShardTaskState.WRITING)).isEmpty();
        assertThat(taskDao.fetch(row.taskId(), shard)).contains(committed);

        // Back to writing, by a new writer, as when a shard is reopened during a run.
        final IndexShardTaskRow reopened = taskDao.update(committed, 1, WRITER_B, IndexShardTaskState.WRITING)
                .orElseThrow();
        assertThat(reopened.writerId()).isEqualTo(WRITER_B);
    }

    @Test
    void testDelete_onlyIfUnchanged() {
        final long shard = shards.createCommittedShard(tempDir);
        final IndexShardTaskRow row = insert(shard, 1, WRITER_A);
        final IndexShardTaskRow changed = taskDao.update(row, 2, WRITER_A, IndexShardTaskState.WRITING)
                .orElseThrow();

        assertThat(taskDao.delete(row)).isFalse();
        assertThat(taskDao.delete(changed)).isTrue();
        assertThat(taskDao.fetch(row.taskId(), shard)).isEmpty();
    }

    @Test
    void testMarkWritten_onlyTheWritingRowsOfTheRun() {
        final long shard1 = shards.createCommittedShard(tempDir);
        final long shard2 = shards.createCommittedShard(tempDir);
        final long shard3 = shards.createCommittedShard(tempDir);
        final long task = nextTaskId();
        taskDao.insertWriting(task, shard1, STREAM, 2, WRITER_A);
        final IndexShardTaskRow committed = taskDao.update(
                taskDao.insertWriting(task, shard2, STREAM, 2, WRITER_A).orElseThrow(),
                2, WRITER_A, IndexShardTaskState.COMMITTED).orElseThrow();
        // Left by an earlier run.
        taskDao.insertWriting(task, shard3, STREAM, 1, WRITER_A);

        assertThat(taskDao.markWritten(task, 2)).isOne();

        assertThat(taskDao.fetch(task, shard1).orElseThrow().state()).isEqualTo(IndexShardTaskState.WRITTEN);
        assertThat(taskDao.fetch(task, shard2)).contains(committed);
        assertThat(taskDao.fetch(task, shard3).orElseThrow().state()).isEqualTo(IndexShardTaskState.WRITING);
    }

    @Test
    void testFindWritten() {
        final long shard = shards.createCommittedShard(tempDir);
        final IndexShardTaskRow writtenByA = written(insert(shard, 1, WRITER_A));
        written(insert(shard, 1, WRITER_B));
        insert(shard, 1, WRITER_A);

        assertThat(taskDao.findWritten(shard, WRITER_A)).containsExactly(writtenByA);
    }

    @Test
    void testMarkCommitted_onlyWhatWasListedAndIsUnchanged() {
        final long shard = shards.createCommittedShard(tempDir);
        shards.setWriter(shard, WRITER_A);
        final IndexShardTaskRow listed = written(insert(shard, 1, WRITER_A));
        final IndexShardTaskRow takenOver = written(insert(shard, 1, WRITER_A));
        final List<IndexShardTaskRow> rows = taskDao.findWritten(shard, WRITER_A);
        final IndexShardPurge purge = deletedPurge(shard, 1, WRITER_A);
        final IndexShardPurge askedAgain = deletedPurge(shard, 2, WRITER_A);
        final IndexShardPurge deletedAgain = deletedPurge(shard, 3, WRITER_A);
        final List<IndexShardPurge> purges = purgeDao.findByShard(shard);

        // While the commit runs, a row ends that was not listed, another run takes over a listed row, and
        // purges are asked for again, one of them deleted again by the same writer.
        final IndexShardTaskRow endedDuringCommit = written(insert(shard, 1, WRITER_A));
        final IndexShardTaskRow rerun = taskDao.update(takenOver, 2, WRITER_A, IndexShardTaskState.WRITING)
                .orElseThrow();
        purgeDao.request(shard, askedAgain.streamId());
        purgeDao.request(shard, deletedAgain.streamId());
        purgeDao.markDeleted(purgeDao.fetch(shard, deletedAgain.streamId()).orElseThrow(), WRITER_A);

        assertThat(taskDao.markCommitted(shard, WRITER_A, rows, purges)).isTrue();

        assertThat(state(listed)).isEqualTo(IndexShardTaskState.COMMITTED);
        assertThat(state(endedDuringCommit)).isEqualTo(IndexShardTaskState.WRITTEN);
        assertThat(taskDao.fetch(rerun.taskId(), shard)).contains(rerun);
        assertThat(purgeDao.fetch(shard, purge.streamId())).isEmpty();
        assertThat(purgeDao.fetch(shard, askedAgain.streamId())).isPresent();
        assertThat(purgeDao.fetch(shard, deletedAgain.streamId())).isPresent();
    }

    @Test
    void testMarkCommitted_shardNoLongerTheWritersRecordsNothing() {
        final long shard = shards.createCommittedShard(tempDir);
        shards.setWriter(shard, WRITER_A);
        final IndexShardTaskRow row = written(insert(shard, 1, WRITER_A));
        final IndexShardPurge purge = deletedPurge(shard, 1, WRITER_A);
        // Taken over by another node while the commit ran.
        shards.setWriter(shard, WRITER_B);

        assertThat(taskDao.markCommitted(shard, WRITER_A, List.of(row), List.of(purge))).isFalse();

        assertThat(state(row)).isEqualTo(IndexShardTaskState.WRITTEN);
        assertThat(purgeDao.fetch(shard, purge.streamId())).isPresent();

        // Nor once nothing has the shard open.
        shards.setWriter(shard, null);
        assertThat(taskDao.markCommitted(shard, WRITER_A, List.of(row), List.of(purge))).isFalse();
    }

    @Test
    void testMarkCommitted_rowsOfAnotherShardAreRefused() {
        final long shard = shards.createCommittedShard(tempDir);
        final long otherShard = shards.createCommittedShard(tempDir);
        final IndexShardTaskRow row = written(insert(otherShard, 1, WRITER_A));

        assertThatThrownBy(() -> taskDao.markCommitted(shard, WRITER_A, List.of(row), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testMarkCommitted_manyRows() {
        final long shard = shards.createCommittedShard(tempDir);
        shards.setWriter(shard, WRITER_A);
        for (int i = 0; i < 1_200; i++) {
            written(insert(shard, 1, WRITER_A));
        }
        final List<IndexShardTaskRow> rows = taskDao.findWritten(shard, WRITER_A);

        assertThat(taskDao.markCommitted(shard, WRITER_A, rows, List.of())).isTrue();

        assertThat(taskDao.findWritten(shard, WRITER_A)).isEmpty();
    }

    @Test
    void testDeleteRun_onlyThatRun() {
        final long shard1 = shards.createCommittedShard(tempDir);
        final long shard2 = shards.createCommittedShard(tempDir);
        final long task = nextTaskId();
        taskDao.insertWriting(task, shard1, STREAM, 2, WRITER_A);
        final IndexShardTaskRow earlier = taskDao.insertWriting(task, shard2, STREAM, 1, WRITER_A).orElseThrow();

        assertThat(taskDao.deleteRun(task, 2)).isOne();

        assertThat(taskDao.findByTask(task)).containsExactly(earlier);
    }

    @Test
    void testToPurges() {
        final long shard = shards.createCommittedShard(tempDir);
        final IndexShardTaskRow row = insert(shard, 1, WRITER_A);
        final IndexShardTaskRow stale = insert(shard, 1, WRITER_A);
        final IndexShardTaskRow takenOver = taskDao.update(stale, 2, WRITER_B, IndexShardTaskState.WRITING)
                .orElseThrow();
        // A purge of the stream that a writer had already deleted but not committed.
        final IndexShardPurge deleted = deletedPurge(shard, STREAM, WRITER_A);

        assertThat(taskDao.toPurges(List.of(row, stale))).containsExactly(row);

        assertThat(taskDao.fetch(row.taskId(), shard)).isEmpty();
        assertThat(taskDao.fetch(stale.taskId(), shard)).contains(takenOver);
        // Waiting again, as the run may have written more since the stream was deleted.
        final IndexShardPurge purge = purgeDao.fetch(shard, STREAM).orElseThrow();
        assertThat(purge.isWaiting()).isTrue();
        assertThat(purge.version()).isGreaterThan(deleted.version());
        assertThat(taskDao.toPurges(List.of())).isEmpty();
    }

    @Test
    void testFindLost() {
        final long openShard = shards.createCommittedShard(tempDir);
        shards.setWriter(openShard, WRITER_A);
        final long closedShard = shards.createCommittedShard(tempDir);
        final long corruptShard = shards.createCommittedShard(tempDir);
        shards.setWriter(corruptShard, WRITER_A);
        shards.setStatus(corruptShard, IndexShardStatus.CORRUPT);
        final long deletedShard = shards.createCommittedShard(tempDir);
        shards.setStatus(deletedShard, IndexShardStatus.DELETED);

        insert(openShard, 1, WRITER_A);
        final IndexShardTaskRow oldWriter = insert(openShard, 1, WRITER_B);
        final IndexShardTaskRow writerClosed = written(insert(closedShard, 1, WRITER_A));
        taskDao.update(insert(closedShard, 1, WRITER_A), 1, WRITER_A, IndexShardTaskState.COMMITTED);
        final IndexShardTaskRow corrupt = insert(corruptShard, 1, WRITER_A);
        insert(deletedShard, 1, WRITER_A);

        final List<IndexShardTaskRow> lost = findAllLost();
        assertThat(lost).contains(oldWriter, writerClosed, corrupt);
        assertThat(lost).allMatch(row -> row.indexShardId() != deletedShard);
        assertThat(lost).noneMatch(row -> row.indexShardId() == openShard && WRITER_A.equals(row.writerId()));
        assertThat(lost).noneMatch(row -> row.state() == IndexShardTaskState.COMMITTED);
    }

    @Test
    void testFindLost_inPages() {
        final long shard = shards.createCommittedShard(tempDir);
        for (int i = 0; i < 5; i++) {
            insert(shard, 1, WRITER_A);
        }
        final List<IndexShardTaskRow> all = taskDao.findLost(null, Integer.MAX_VALUE)
                .stream()
                .filter(row -> row.indexShardId() == shard)
                .toList();
        assertThat(all).hasSize(5);

        final List<IndexShardTaskRow> firstPage = taskDao.findLost(all.get(1), 2);

        assertThat(firstPage).hasSize(2);
        assertThat(firstPage.getFirst()).isEqualTo(all.get(2));
    }

    @Test
    void testFindNotUpdatedSince() {
        final long shard = shards.createCommittedShard(tempDir);
        final IndexShardTaskRow row = insert(shard, 1, WRITER_A);

        assertThat(taskDao.findNotUpdatedSince(row.updateTimeMs() + 1, null, Integer.MAX_VALUE)).contains(row);
        assertThat(taskDao.findNotUpdatedSince(row.updateTimeMs(), null, Integer.MAX_VALUE)).doesNotContain(row);
    }

    @Test
    void testDeleteForDeletedShards() {
        final long shard = shards.createCommittedShard(tempDir);
        final long deletedShard = shards.createCommittedShard(tempDir);
        final IndexShardTaskRow kept = insert(shard, 1, WRITER_A);
        final IndexShardTaskRow dropped = insert(deletedShard, 1, WRITER_A);
        shards.setStatus(deletedShard, IndexShardStatus.DELETED);

        assertThat(taskDao.deleteForDeletedShards()).isGreaterThanOrEqualTo(1);

        assertThat(taskDao.fetch(kept.taskId(), shard)).contains(kept);
        assertThat(taskDao.fetch(dropped.taskId(), deletedShard)).isEmpty();
    }

    @Test
    void testRowsAndPurgesGoWithTheirShard() {
        final long shard = shards.createCommittedShard(tempDir);
        final IndexShardTaskRow row = insert(shard, 1, WRITER_A);
        purgeDao.request(shard, STREAM);

        shards.deleteRow(shard);

        assertThat(taskDao.fetch(row.taskId(), shard)).isEmpty();
        assertThat(purgeDao.findByShard(shard)).isEmpty();
    }

    @Test
    void testMarkOpen_recordsTheWriter() {
        final long shard = shards.createCommittedShard(tempDir);
        // Only a shard that is being opened.
        assertThat(indexShardDao.markOpen(shard, WRITER_A)).isFalse();
        indexShardDao.setStatus(shard, IndexShardStatus.OPENING);

        assertThat(indexShardDao.markOpen(shard, WRITER_A)).isTrue();

        assertThat(indexShardDao.fetch(shard).orElseThrow().getStatus()).isEqualTo(IndexShardStatus.OPEN);
        // A row of that writer is not lost, as the shard still has it.
        final IndexShardTaskRow row = insert(shard, 1, WRITER_A);
        assertThat(findAllLost()).doesNotContain(row);
    }

    @Test
    void testCloseWriter_cleanClose_marksAllTheWritersRowsAndPurgesAndClosesTheShard() {
        final long shard = openShard(WRITER_A);
        final IndexShardTaskRow writing = insert(shard, 1, WRITER_A);
        final IndexShardTaskRow written = written(insert(shard, 1, WRITER_A));
        final IndexShardTaskRow otherWriters = written(insert(shard, 1, WRITER_B));
        final IndexShardPurge purge = deletedPurge(shard, 1, WRITER_A);
        purgeDao.request(shard, 2);

        final Optional<Set<Long>> taskIds = indexShardDao.closeWriter(shard, WRITER_A, null, null, true);

        // Only tasks whose runs had ended may now be complete.
        assertThat(taskIds).contains(Set.of(written.taskId()));
        assertThat(state(writing)).isEqualTo(IndexShardTaskState.COMMITTED);
        assertThat(state(written)).isEqualTo(IndexShardTaskState.COMMITTED);
        assertThat(state(otherWriters)).isEqualTo(IndexShardTaskState.WRITTEN);
        assertThat(purgeDao.fetch(shard, purge.streamId())).isEmpty();
        assertThat(purgeDao.fetch(shard, 2)).isPresent();
        assertThat(indexShardDao.fetch(shard).orElseThrow().getStatus()).isEqualTo(IndexShardStatus.CLOSED);
        // Nothing has it open, so the other writer's row has lost its documents.
        assertThat(findAllLost()).contains(otherWriters);
    }

    @Test
    void testCloseWriter_uncleanClose_marksNothingButClosesTheShard() {
        final long shard = openShard(WRITER_A);
        final IndexShardTaskRow written = written(insert(shard, 1, WRITER_A));

        assertThat(indexShardDao.closeWriter(shard, WRITER_A, null, null, false)).contains(Set.of());

        assertThat(state(written)).isEqualTo(IndexShardTaskState.WRITTEN);
        assertThat(indexShardDao.fetch(shard).orElseThrow().getStatus()).isEqualTo(IndexShardStatus.CLOSED);
        assertThat(findAllLost()).contains(written);
    }

    @Test
    void testCloseWriter_shardNoLongerTheWriters_doesNothing() {
        final long shard = openShard(WRITER_B);
        final IndexShardTaskRow written = written(insert(shard, 1, WRITER_A));

        assertThat(indexShardDao.closeWriter(shard, WRITER_A, null, null, true)).isEmpty();

        assertThat(state(written)).isEqualTo(IndexShardTaskState.WRITTEN);
        assertThat(indexShardDao.fetch(shard).orElseThrow().getStatus()).isEqualTo(IndexShardStatus.OPEN);
    }

    @Test
    void testCloseWriter_leaseLost_doesNothing() {
        final long shard = shards.createCommittedShard(tempDir);
        assertThat(indexShardDao.claimLease(shard, "node1", "processA", -60_000)).isTrue();
        assertThat(indexShardDao.markOpen(shard, WRITER_A)).isTrue();
        final IndexShardTaskRow written = written(insert(shard, 1, WRITER_A));
        // Taken from the process meanwhile, as its lease lapsed.
        indexShardDao.releaseLapsedLeases(0, 0);

        assertThat(indexShardDao.closeWriter(shard, WRITER_A, "processA", null, true)).isEmpty();

        assertThat(state(written)).isEqualTo(IndexShardTaskState.WRITTEN);
    }

    @Test
    void testCloseWriter_withTheLease_givesItUp() {
        final long shard = shards.createCommittedShard(tempDir);
        assertThat(indexShardDao.claimLease(shard, "node1", "processA", 60_000)).isTrue();
        assertThat(indexShardDao.markOpen(shard, WRITER_A)).isTrue();

        assertThat(indexShardDao.closeWriter(shard, WRITER_A, "processA", null, true)).isPresent();

        assertThat(indexShardDao.isLeased(shard)).isFalse();
        assertThat(indexShardDao.claimLease(shard, "node2", "processB", 60_000)).isTrue();
    }

    @Test
    void testWriterIdIsClearedWhenTheShardIsGivenUp() {
        final long shard = shards.createCommittedShard(tempDir);
        assertThat(indexShardDao.claimLease(shard, "node1", "processA", 60_000)).isTrue();
        assertThat(indexShardDao.markOpen(shard, WRITER_A)).isTrue();
        final IndexShardTaskRow row = insert(shard, 1, WRITER_A);
        assertThat(findAllLost()).doesNotContain(row);

        indexShardDao.releaseLease(shard, "processA", null);

        assertThat(findAllLost()).contains(row);
    }

    private long openShard(final String writerId) {
        final long shard = shards.createCommittedShard(tempDir);
        indexShardDao.setStatus(shard, IndexShardStatus.OPENING);
        assertThat(indexShardDao.markOpen(shard, writerId)).isTrue();
        return shard;
    }

    private IndexShardTaskRow insert(final long shard, final int run, final String writerId) {
        return taskDao.insertWriting(nextTaskId(), shard, STREAM, run, writerId).orElseThrow();
    }

    private IndexShardTaskRow written(final IndexShardTaskRow row) {
        return taskDao.update(row, row.run(), row.writerId(), IndexShardTaskState.WRITTEN).orElseThrow();
    }

    private IndexShardPurge deletedPurge(final long shard, final long streamId, final String writerId) {
        purgeDao.request(shard, streamId);
        return purgeDao.markDeleted(purgeDao.fetch(shard, streamId).orElseThrow(), writerId).orElseThrow();
    }

    private IndexShardTaskState state(final IndexShardTaskRow row) {
        return taskDao.fetch(row.taskId(), row.indexShardId()).orElseThrow().state();
    }

    private List<IndexShardTaskRow> findAllLost() {
        return taskDao.findLost(null, Integer.MAX_VALUE);
    }

    private static long nextTaskId() {
        return NEXT_TASK_ID.incrementAndGet();
    }
}
