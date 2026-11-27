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

import stroom.index.impl.IndexShardPurge;
import stroom.index.impl.IndexShardPurgeDao;
import stroom.index.impl.db.IndexDbModule;
import stroom.index.shared.IndexShard.IndexShardStatus;

import com.google.inject.Guice;
import com.google.inject.Injector;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TestIndexShardPurgeDaoImpl {

    private static final long STREAM = 100;
    private static final String WRITER_A = "writerA";
    private static final String WRITER_B = "writerB";

    @Inject
    private IndexShardPurgeDao purgeDao;
    @Inject
    private IndexShardFixtures shards;

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
    void testRequest() {
        final long shard = shards.createCommittedShard(tempDir);

        purgeDao.request(shard, STREAM);
        purgeDao.request(shard, STREAM + 1);

        final IndexShardPurge purge = purgeDao.fetch(shard, STREAM).orElseThrow();
        assertThat(purge.isWaiting()).isTrue();
        assertThat(purge.createTimeMs()).isPositive();
        assertThat(purgeDao.findByShard(shard))
                .extracting(IndexShardPurge::streamId)
                .containsExactly(STREAM, STREAM + 1);
    }

    @Test
    void testRequest_againAfterDeletion_waitsAgain() {
        final long shard = shards.createCommittedShard(tempDir);
        purgeDao.request(shard, STREAM);
        final IndexShardPurge first = purgeDao.fetch(shard, STREAM).orElseThrow();
        final IndexShardPurge deleted = purgeDao.markDeleted(first, WRITER_A).orElseThrow();

        purgeDao.request(shard, STREAM);

        final IndexShardPurge again = purgeDao.fetch(shard, STREAM).orElseThrow();
        assertThat(again.isWaiting()).isTrue();
        assertThat(again.version()).isGreaterThan(deleted.version());
        // Still as old as when it was first asked for.
        assertThat(again.createTimeMs()).isEqualTo(first.createTimeMs());
    }

    @Test
    void testMarkDeleted_onlyIfUnchangedAndWaiting() {
        final long shard = shards.createCommittedShard(tempDir);
        purgeDao.request(shard, STREAM);
        final IndexShardPurge waiting = purgeDao.fetch(shard, STREAM).orElseThrow();

        final IndexShardPurge deleted = purgeDao.markDeleted(waiting, WRITER_A).orElseThrow();

        assertThat(deleted.writerId()).isEqualTo(WRITER_A);
        assertThat(purgeDao.fetch(shard, STREAM)).contains(deleted);
        // Another writer, with what it read before, can't mark it too.
        assertThat(purgeDao.markDeleted(waiting, WRITER_B)).isEmpty();
        // Nor can anyone while it is deleted and not committed.
        assertThat(purgeDao.markDeleted(deleted, WRITER_B)).isEmpty();
        assertThat(purgeDao.fetch(shard, STREAM)).contains(deleted);
    }

    @Test
    void testResetLost() {
        final long shard = shards.createCommittedShard(tempDir);
        shards.setWriter(shard, WRITER_A);
        purgeDao.request(shard, STREAM);
        purgeDao.request(shard, STREAM + 1);
        // Deleted by a writer that has since gone, so never committed.
        purgeDao.markDeleted(purgeDao.fetch(shard, STREAM).orElseThrow(), WRITER_B).orElseThrow();
        final IndexShardPurge byWriter = purgeDao.markDeleted(
                purgeDao.fetch(shard, STREAM + 1).orElseThrow(), WRITER_A).orElseThrow();

        assertThat(purgeDao.resetLost()).isGreaterThanOrEqualTo(1);

        assertThat(purgeDao.fetch(shard, STREAM).orElseThrow().isWaiting()).isTrue();
        assertThat(purgeDao.fetch(shard, STREAM + 1)).contains(byWriter);

        // And all of them once nothing has the shard open.
        shards.setWriter(shard, null);
        purgeDao.resetLost();
        assertThat(purgeDao.fetch(shard, STREAM + 1).orElseThrow().isWaiting()).isTrue();
    }

    @Test
    void testFindShardsToSweep() {
        final long waiting = shards.createCommittedShard(tempDir);
        purgeDao.request(waiting, STREAM);
        final long open = shards.createCommittedShard(tempDir);
        purgeDao.request(open, STREAM);
        shards.setWriter(open, WRITER_A);
        final long purgeUnderWay = shards.createCommittedShard(tempDir);
        purgeDao.request(purgeUnderWay, STREAM);
        purgeDao.markDeleted(purgeDao.fetch(purgeUnderWay, STREAM).orElseThrow(), WRITER_A);
        final long corrupt = shards.createCommittedShard(tempDir);
        purgeDao.request(corrupt, STREAM);
        shards.setStatus(corrupt, IndexShardStatus.CORRUPT);
        final long none = shards.createCommittedShard(tempDir);

        assertThat(purgeDao.findShardsToSweep(Integer.MAX_VALUE))
                .contains(waiting)
                .doesNotContain(open, purgeUnderWay, corrupt, none);
    }

    @Test
    void testDeleteForUnusableShards() {
        final long kept = shards.createCommittedShard(tempDir);
        purgeDao.request(kept, STREAM);
        final long deleted = shards.createCommittedShard(tempDir);
        purgeDao.request(deleted, STREAM);
        shards.setStatus(deleted, IndexShardStatus.DELETED);
        final long corrupt = shards.createCommittedShard(tempDir);
        purgeDao.request(corrupt, STREAM);
        shards.setStatus(corrupt, IndexShardStatus.CORRUPT);
        final long neverCommitted = shards.createCommittedShard(tempDir);
        purgeDao.request(neverCommitted, STREAM);
        shards.clearCommitTime(neverCommitted);
        // Never committed, but open, so it may hold documents of the stream that the purge must remove.
        final long neverCommittedButOpen = shards.createCommittedShard(tempDir);
        purgeDao.request(neverCommittedButOpen, STREAM);
        shards.clearCommitTime(neverCommittedButOpen);
        shards.setWriter(neverCommittedButOpen, WRITER_A);

        assertThat(purgeDao.deleteForUnusableShards()).isGreaterThanOrEqualTo(3);

        assertThat(purgeDao.findByShard(kept)).hasSize(1);
        assertThat(purgeDao.findByShard(neverCommittedButOpen)).hasSize(1);
        assertThat(purgeDao.findByShard(deleted)).isEmpty();
        assertThat(purgeDao.findByShard(corrupt)).isEmpty();
        assertThat(purgeDao.findByShard(neverCommitted)).isEmpty();
    }

    @Test
    void testCountAndOldest() {
        final long shard = shards.createCommittedShard(tempDir);
        final long countBefore = purgeDao.count();
        purgeDao.request(shard, STREAM);

        assertThat(purgeDao.count()).isEqualTo(countBefore + 1);
        assertThat(purgeDao.findOldestCreateTimeMs().orElseThrow())
                .isLessThanOrEqualTo(purgeDao.fetch(shard, STREAM).orElseThrow().createTimeMs());
    }
}
