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

package stroom.index;

import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.index.impl.IndexDocument;
import stroom.index.impl.IndexShardDao;
import stroom.index.impl.IndexShardLeases;
import stroom.index.impl.IndexShardPendingStreamDao;
import stroom.index.impl.IndexShardPendingStreamRecovery;
import stroom.index.impl.IndexShardPendingStreams;
import stroom.index.impl.IndexShardWriterCache;
import stroom.index.impl.IndexStore;
import stroom.index.impl.IndexVolumeService;
import stroom.index.impl.Indexer;
import stroom.index.impl.PendingStream;
import stroom.index.impl.PendingStreamState;
import stroom.index.impl.PendingStreamTracker;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexConstants;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShardKey;
import stroom.index.shared.IndexVolume.VolumeType;
import stroom.index.shared.LuceneIndexDoc;
import stroom.index.shared.LuceneIndexField;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValString;
import stroom.search.extraction.FieldValue;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestScenarioCreator;

import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/// Tests that streams which are only part indexed, or whose documents an index shard loses, have their
/// documents removed from every shard, against a real database and real index files.
///
/// The task ids here belong to no processor task, so nothing is put back to run again. That part is tested
/// in TestProcessorTaskRecovery and TestIndexShardPendingStreamRecovery.
class TestIndexShardStreamRecovery extends AbstractCoreIntegrationTest {

    private static final long TASK = 987_654_321L;
    private static final long STREAM_1 = 1_000_001L;
    private static final long STREAM_2 = 1_000_002L;
    private static final long STREAM_3 = 1_000_003L;

    @Inject
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Inject
    private IndexShardDao indexShardDao;
    @Inject
    private IndexShardPendingStreamDao pendingStreamDao;
    @Inject
    private IndexShardPendingStreams indexShardPendingStreams;
    @Inject
    private IndexShardPendingStreamRecovery recovery;
    @Inject
    private IndexShardWriterCache indexShardWriterCache;
    @Inject
    private IndexShardLeases indexShardLeases;
    @Inject
    private IndexVolumeService indexVolumeService;
    @Inject
    private Indexer indexer;
    @Inject
    private IndexStore indexStore;

    @BeforeEach
    void onBefore() {
        indexShardWriterCache.shutdown();
        for (final long streamId : List.of(STREAM_1, STREAM_2, STREAM_3)) {
            pendingStreamDao.deleteStream(streamId, TASK);
        }
    }

    @Test
    void testIndexedStream_rowsGoOnceItsShardsCommit() {
        final IndexShardKey indexShardKey = createIndex("RECOVERY_CLEAN", 1000);

        final PendingStreamTracker tracker = indexShardPendingStreams.begin(STREAM_1, TASK);
        addDocuments(indexShardKey, STREAM_1, tracker, 5);
        assertThat(states(STREAM_1)).containsOnly(PendingStreamState.WRITING);

        assertThat(tracker.end()).isTrue();
        assertThat(states(STREAM_1)).containsOnly(PendingStreamState.WRITTEN);

        indexShardWriterCache.flushAll();
        assertThat(pendingStreamDao.find(STREAM_1, TASK)).isEmpty();

        // Nothing for recovery to do.
        recovery.exec();
        closeAllWriters();
        assertThat(countDocuments(indexShardKey)).isEqualTo(5);
    }

    @Test
    void testFailedTask_leavesNoDocumentsBehind() {
        final IndexShardKey indexShardKey = createIndex("RECOVERY_FAILED_TASK", 1000);
        final PendingStreamTracker tracker1 = indexShardPendingStreams.begin(STREAM_1, TASK);
        addDocuments(indexShardKey, STREAM_1, tracker1, 5);
        assertThat(tracker1.end()).isTrue();

        // A task that gets part way through its stream and then fails.
        final PendingStreamTracker tracker2 = indexShardPendingStreams.begin(STREAM_2, TASK);
        addDocuments(indexShardKey, STREAM_2, tracker2, 3);
        tracker2.fail();

        // Nothing is removed while the task may still be adding documents, which it does here.
        assertThat(states(STREAM_2)).containsOnly(PendingStreamState.ABANDONED, PendingStreamState.RETRY_PENDING);
        recovery.exec();
        assertThat(states(STREAM_2)).containsOnly(PendingStreamState.ABANDONED, PendingStreamState.RETRY_PENDING);
        addDocuments(indexShardKey, STREAM_2, tracker2, 2);

        assertThat(tracker2.end()).isFalse();
        assertThat(states(STREAM_2)).containsOnly(PendingStreamState.DELETE, PendingStreamState.RETRY_PENDING);
        recovery.exec();

        // Its documents have gone and the other stream's are untouched.
        assertThat(pendingStreamDao.find(STREAM_2, TASK)).isEmpty();
        closeAllWriters();
        assertThat(countDocuments(indexShardKey)).isEqualTo(5);
        assertThat(pendingStreamDao.find(STREAM_1, TASK)).isEmpty();
    }

    @Test
    void testFailedCommit_removesExactlyTheUncommittedStreams_andDoingItTwiceChangesNothing() {
        final IndexShardKey indexShardKey = createIndex("RECOVERY_FAILED_COMMIT", 1000);
        // Committed.
        final PendingStreamTracker tracker1 = indexShardPendingStreams.begin(STREAM_1, TASK);
        addDocuments(indexShardKey, STREAM_1, tracker1, 5);
        assertThat(tracker1.end()).isTrue();
        indexShardWriterCache.flushAll();
        // Written but not committed.
        final PendingStreamTracker tracker2 = indexShardPendingStreams.begin(STREAM_2, TASK);
        addDocuments(indexShardKey, STREAM_2, tracker2, 4);
        assertThat(tracker2.end()).isTrue();
        // Still being written.
        final PendingStreamTracker tracker3 = indexShardPendingStreams.begin(STREAM_3, TASK);
        addDocuments(indexShardKey, STREAM_3, tracker3, 3);

        // The shard's commit fails, which is what its writer then reports.
        final Set<Long> shardIds = shardIds(STREAM_2);
        assertThat(shardIds).hasSize(1);
        shardIds.forEach(pendingStreamDao::failShard);

        assertThat(states(STREAM_2)).containsOnly(PendingStreamState.DELETE, PendingStreamState.RETRY_PENDING);
        assertThat(states(STREAM_3)).containsOnly(PendingStreamState.ABANDONED, PendingStreamState.RETRY_PENDING);

        // The stream whose task has finished is removed. The other waits for its task to stop.
        recovery.exec();
        assertThat(pendingStreamDao.find(STREAM_2, TASK)).isEmpty();
        assertThat(states(STREAM_3)).containsOnly(PendingStreamState.ABANDONED, PendingStreamState.RETRY_PENDING);

        // The task carries on for a moment, then finds at the end that its stream was not indexed.
        addDocuments(indexShardKey, STREAM_3, tracker3, 2);
        assertThat(tracker3.end()).isFalse();
        recovery.exec();
        assertThat(pendingStreamDao.find(STREAM_3, TASK)).isEmpty();

        // Recovery is safe to repeat.
        recovery.exec();
        recovery.removeMarkedDocuments();

        closeAllWriters();
        assertThat(countDocuments(indexShardKey)).isEqualTo(5);
    }

    @Test
    void testStreamSpanningShards_isRemovedFromAllOfThem() {
        // Shards that fill after two documents, so a stream soon spills into more.
        final IndexShardKey indexShardKey = createIndex("RECOVERY_SPANNING", 2);
        final PendingStreamTracker tracker1 = indexShardPendingStreams.begin(STREAM_1, TASK);
        addDocuments(indexShardKey, STREAM_1, tracker1, 1);
        assertThat(tracker1.end()).isTrue();

        final PendingStreamTracker tracker2 = indexShardPendingStreams.begin(STREAM_2, TASK);
        addDocuments(indexShardKey, STREAM_2, tracker2, 7);
        assertThat(shardIds(STREAM_2).size()).isGreaterThanOrEqualTo(3);
        tracker2.fail();
        assertThat(tracker2.end()).isFalse();

        recovery.exec();

        assertThat(pendingStreamDao.find(STREAM_2, TASK)).isEmpty();
        closeAllWriters();
        assertThat(countDocuments(indexShardKey)).isEqualTo(1);
    }

    @Test
    void testTaskRunAgain_earlierAttemptsDocumentsAreRemovedBeforeItWrites() {
        final IndexShardKey indexShardKey = createIndex("RECOVERY_RERUN", 1000);
        // An attempt that never ended, as when the node running it died.
        final PendingStreamTracker firstAttempt = indexShardPendingStreams.begin(STREAM_1, TASK);
        addDocuments(indexShardKey, STREAM_1, firstAttempt, 4);
        indexShardWriterCache.flushAll();

        // The same task starting again waits for those documents to go, and this node removes them.
        final PendingStreamTracker secondAttempt = indexShardPendingStreams.begin(STREAM_1, TASK);
        assertThat(pendingStreamDao.find(STREAM_1, TASK)).isEmpty();

        addDocuments(indexShardKey, STREAM_1, secondAttempt, 6);
        assertThat(secondAttempt.end()).isTrue();
        closeAllWriters();

        // Indexed once, not once and a bit.
        assertThat(countDocuments(indexShardKey)).isEqualTo(6);
        assertThat(pendingStreamDao.find(STREAM_1, TASK)).isEmpty();
    }

    @Test
    void testShardNotClosedCleanly_itsUncommittedStreamsAreRecoveredWhenItIsNextOpened() {
        final IndexShardKey indexShardKey = createIndexOnSharedVolumes("RECOVERY_UNCLEAN");
        final PendingStreamTracker tracker1 = indexShardPendingStreams.begin(STREAM_1, TASK);
        addDocuments(indexShardKey, STREAM_1, tracker1, 5);
        assertThat(tracker1.end()).isTrue();
        indexShardWriterCache.flushAll();
        final PendingStreamTracker tracker2 = indexShardPendingStreams.begin(STREAM_2, TASK);
        addDocuments(indexShardKey, STREAM_2, tracker2, 4);
        assertThat(tracker2.end()).isTrue();
        final long shardId = shardIds(STREAM_2).iterator().next();

        // The node loses the shard's lease before it commits again, so its writer discards the second
        // stream's documents and closes.
        assertThat(indexShardDao.releaseLease(shardId, indexShardLeases.getInstanceId(), null)).isTrue();
        indexShardLeases.renew();
        closeAllWriters();

        // The stream still looks as if it is waiting for a commit, but the shard says otherwise.
        assertThat(states(STREAM_2)).containsOnly(PendingStreamState.WRITTEN);
        assertThat(indexShardDao.needsPendingStreamRecovery(shardId)).isTrue();

        // Recovery marks the stream, without waiting for the shard to be written to again, and removes it.
        recovery.exec();

        assertThat(indexShardDao.needsPendingStreamRecovery(shardId)).isFalse();
        assertThat(pendingStreamDao.find(STREAM_2, TASK)).isEmpty();
        closeAllWriters();
        assertThat(countDocuments(indexShardKey)).isEqualTo(5);
    }

    @Test
    void testShardClosedCleanlyWhileATaskIsStillWriting_streamIsNotFailed() {
        final IndexShardKey indexShardKey = createIndex("RECOVERY_CLEAN_CLOSE", 1000);
        final PendingStreamTracker tracker = indexShardPendingStreams.begin(STREAM_1, TASK);
        addDocuments(indexShardKey, STREAM_1, tracker, 3);

        // The writer is closed part way through the stream, e.g. because it had been open too long, and
        // is opened again by the task's next document.
        closeAllWriters();
        addDocuments(indexShardKey, STREAM_1, tracker, 2);
        assertThat(states(STREAM_1)).containsOnly(PendingStreamState.WRITING);
        assertThat(tracker.end()).isTrue();
        closeAllWriters();

        // Closed after the task finished, with nothing to mark the stream committed but the tidy up.
        recovery.exec();
        assertThat(pendingStreamDao.find(STREAM_1, TASK)).isEmpty();
        assertThat(countDocuments(indexShardKey)).isEqualTo(5);
    }

    private void addDocuments(final IndexShardKey indexShardKey,
                              final long streamId,
                              final PendingStreamTracker tracker,
                              final int count) {
        for (int i = 0; i < count; i++) {
            final IndexDocument document = new IndexDocument();
            document.add(new FieldValue(
                    LuceneIndexField.createIdField(IndexConstants.STREAM_ID), ValLong.create(streamId)));
            document.add(new FieldValue(LuceneIndexField.createField("test"), ValString.create("test")));
            indexer.addDocument(indexShardKey, document, tracker);
        }
    }

    // The states of a stream's rows, the attempt row included.
    private Set<PendingStreamState> states(final long streamId) {
        return pendingStreamDao.find(streamId, TASK)
                .stream()
                .map(PendingStream::state)
                .collect(Collectors.toSet());
    }

    private Set<Long> shardIds(final long streamId) {
        return pendingStreamDao.find(streamId, TASK)
                .stream()
                .map(PendingStream::shardId)
                .filter(shardId -> shardId != IndexShardPendingStreamDao.ATTEMPT_ROW_SHARD_ID)
                .collect(Collectors.toSet());
    }

    // What the shards of the index hold, as recorded when their writers close.
    private int countDocuments(final IndexShardKey indexShardKey) {
        final FindIndexShardCriteria criteria = FindIndexShardCriteria.matchAll();
        criteria.getIndexUuidSet().add(indexShardKey.getIndexUuid());
        return indexShardDao.find(criteria)
                .getValues()
                .stream()
                .mapToInt(IndexShard::getDocumentCount)
                .sum();
    }

    private IndexShardKey createIndex(final String indexName, final int maxDocsPerShard) {
        final DocRef indexRef = commonTestScenarioCreator.createIndex(
                indexName, commonTestScenarioCreator.createIndexFields(), maxDocsPerShard);
        return IndexShardKey.createKey(indexStore.readDocument(indexRef));
    }

    // Creates an index and marks every index volume as shared, so that its shards are created on one.
    private IndexShardKey createIndexOnSharedVolumes(final String indexName) {
        final DocRef indexRef = commonTestScenarioCreator.createIndex(indexName);
        final LuceneIndexDoc index = indexStore.readDocument(indexRef);
        indexVolumeService.find(new ExpressionCriteria())
                .getValues()
                .stream()
                .filter(volume -> !volume.isShared())
                .forEach(volume -> indexVolumeService.update(volume.copy()
                        .volumeType(VolumeType.SHARED)
                        .build()));
        return IndexShardKey.createKey(index);
    }

    // Returns once every writer has closed.
    private void closeAllWriters() {
        indexShardWriterCache.shutdown();
    }
}
