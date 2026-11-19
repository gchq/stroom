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

import stroom.cluster.lock.api.ClusterLockService;
import stroom.index.shared.IndexException;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.processor.api.ProcessorTaskRecovery;
import stroom.processor.shared.TaskStatus;
import stroom.security.api.SecurityContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;

class TestIndexShardPendingStreamRecovery {

    private static final long SHARD = 11;
    private static final long STREAM = 100;
    private static final long TASK = 7;
    private static final long NOW = 100_000_000L;

    // The shards this node looks after.
    private final Set<Long> responsibleFor = new HashSet<>();

    private IndexShardPendingStreamDao dao;
    private IndexShardDao indexShardDao;
    private IndexShardWriterCache writerCache;
    private IndexShardWriter writer;
    private ProcessorTaskRecovery taskRecovery;
    private IndexShardPendingStreamRecovery recovery;

    @BeforeEach
    void setUp() {
        dao = Mockito.mock(IndexShardPendingStreamDao.class);
        indexShardDao = Mockito.mock(IndexShardDao.class);
        writerCache = Mockito.mock(IndexShardWriterCache.class);
        writer = Mockito.mock(IndexShardWriter.class);
        Mockito.when(writerCache.getOrOpenWriter(anyLong())).thenReturn(writer);
        Mockito.when(writerCache.getIfPresent(anyLong())).thenReturn(Optional.empty());
        taskRecovery = Mockito.mock(ProcessorTaskRecovery.class);
        recovery = create(Optional.of(taskRecovery));
        responsibleFor.add(SHARD);
    }

    private IndexShardPendingStreamRecovery create(final Optional<ProcessorTaskRecovery> taskRecovery) {
        final IndexShardResponsibility responsibility = Mockito.mock(IndexShardResponsibility.class);
        Mockito.when(responsibility.createTest())
                .thenReturn(indexShard -> responsibleFor.contains(indexShard.getId()));

        final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        Mockito.doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(securityContext).asProcessingUser(any());

        final ClusterLockService clusterLockService = Mockito.mock(ClusterLockService.class);
        Mockito.doAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return null;
        }).when(clusterLockService).tryLock(anyString(), any());

        return new IndexShardPendingStreamRecovery(
                dao,
                indexShardDao,
                writerCache,
                responsibility,
                taskRecovery,
                clusterLockService,
                securityContext,
                IndexWriterConfig::new);
    }

    @Test
    void testRemoveMarkedDocuments_removesThenCommitsThenClearsTheRows() {
        givenShard(SHARD, IndexShardStatus.CLOSED);
        Mockito.when(dao.findShardIdsWithRowsToDelete()).thenReturn(List.of(SHARD));
        Mockito.when(dao.findRowsToDelete(SHARD)).thenReturn(List.of(
                row(1, SHARD, STREAM, PendingStreamState.DELETE, 0),
                row(2, SHARD, STREAM + 1, PendingStreamState.DELETE, 0)));

        recovery.removeMarkedDocuments();

        final InOrder inOrder = Mockito.inOrder(writer, dao);
        inOrder.verify(writer).deleteStreams(Set.of(STREAM, STREAM + 1));
        inOrder.verify(dao).deleteRows(List.of(1L, 2L));
    }

    @Test
    void testRemoveMarkedDocuments_removalFails_rowsAreKeptToTryAgain() {
        givenShard(SHARD, IndexShardStatus.CLOSED);
        Mockito.when(dao.findShardIdsWithRowsToDelete()).thenReturn(List.of(SHARD));
        Mockito.when(dao.findRowsToDelete(SHARD))
                .thenReturn(List.of(row(1, SHARD, STREAM, PendingStreamState.DELETE, 0)));
        Mockito.doThrow(new IndexException("no lease")).when(writer).deleteStreams(any());

        recovery.removeMarkedDocuments();

        Mockito.verify(dao, Mockito.never()).deleteRows(any());
    }

    @Test
    void testRemoveMarkedDocuments_oneShardFailing_othersAreStillDone() {
        final long otherShard = SHARD + 1;
        responsibleFor.add(otherShard);
        givenShard(SHARD, IndexShardStatus.CLOSED);
        givenShard(otherShard, IndexShardStatus.CLOSED);
        Mockito.when(dao.findShardIdsWithRowsToDelete()).thenReturn(List.of(SHARD, otherShard));
        Mockito.when(dao.findRowsToDelete(SHARD))
                .thenReturn(List.of(row(1, SHARD, STREAM, PendingStreamState.DELETE, 0)));
        Mockito.when(dao.findRowsToDelete(otherShard))
                .thenReturn(List.of(row(2, otherShard, STREAM, PendingStreamState.DELETE, 0)));
        Mockito.when(writerCache.getOrOpenWriter(SHARD)).thenThrow(new IndexException("another node has it"));

        recovery.removeMarkedDocuments();

        Mockito.verify(dao).deleteRows(List.of(2L));
        Mockito.verify(dao, Mockito.never()).deleteRows(List.of(1L));
    }

    @Test
    void testRemoveMarkedDocuments_shardOfAnotherNodeIsLeftForIt() {
        responsibleFor.clear();
        givenShard(SHARD, IndexShardStatus.OPEN);
        Mockito.when(dao.findShardIdsWithRowsToDelete()).thenReturn(List.of(SHARD));
        Mockito.when(dao.findRowsToDelete(SHARD))
                .thenReturn(List.of(row(1, SHARD, STREAM, PendingStreamState.DELETE, 0)));

        recovery.removeMarkedDocuments();

        Mockito.verify(writerCache, Mockito.never()).getOrOpenWriter(anyLong());
        Mockito.verify(dao, Mockito.never()).deleteRows(any());
    }

    @Test
    void testRemoveMarkedDocuments_shardOpenOnThisNodeIsAlwaysDone() {
        // Whatever the general rule says, this node has the writer.
        responsibleFor.clear();
        givenShard(SHARD, IndexShardStatus.OPEN);
        Mockito.when(writerCache.getIfPresent(SHARD)).thenReturn(Optional.of(writer));
        Mockito.when(dao.findShardIdsWithRowsToDelete()).thenReturn(List.of(SHARD));
        Mockito.when(dao.findRowsToDelete(SHARD))
                .thenReturn(List.of(row(1, SHARD, STREAM, PendingStreamState.DELETE, 0)));

        recovery.removeMarkedDocuments();

        Mockito.verify(writer).deleteStreams(Set.of(STREAM));
        Mockito.verify(dao).deleteRows(List.of(1L));
    }

    @ParameterizedTest
    @EnumSource(value = IndexShardStatus.class, names = {"DELETED", "CORRUPT"})
    void testRemoveMarkedDocuments_shardThatCannotBeWrittenTo_rowsAreJustCleared(final IndexShardStatus status) {
        givenShard(SHARD, status);
        Mockito.when(dao.findShardIdsWithRowsToDelete()).thenReturn(List.of(SHARD));
        Mockito.when(dao.findRowsToDelete(SHARD))
                .thenReturn(List.of(row(1, SHARD, STREAM, PendingStreamState.DELETE, 0)));

        recovery.removeMarkedDocuments();

        Mockito.verify(writerCache, Mockito.never()).getOrOpenWriter(anyLong());
        Mockito.verify(dao).deleteRows(List.of(1L));
    }

    @Test
    void testRemoveMarkedDocuments_shardGone_rowsAreJustCleared() {
        Mockito.when(indexShardDao.fetch(SHARD)).thenReturn(Optional.empty());
        Mockito.when(dao.findShardIdsWithRowsToDelete()).thenReturn(List.of(SHARD));
        Mockito.when(dao.findRowsToDelete(SHARD))
                .thenReturn(List.of(row(1, SHARD, STREAM, PendingStreamState.DELETE, 0)));

        recovery.removeMarkedDocuments();

        Mockito.verify(dao).deleteRows(List.of(1L));
    }

    @Test
    void testRemoveMarkedDocuments_taskStillWriting_nothingIsOpened() {
        Mockito.when(dao.findShardIdsWithRowsToDelete()).thenReturn(List.of(SHARD));
        // The rows of a task that may still be adding documents are not offered.
        Mockito.when(dao.findRowsToDelete(SHARD)).thenReturn(List.of());

        recovery.removeMarkedDocuments();

        Mockito.verifyNoInteractions(writerCache);
    }

    @Test
    void testReleaseLapsedLeases_usesTheConfiguredGraceAndRetention() {
        recovery.releaseLapsedLeases();

        // Five minutes of grace, and replaced commits kept for half an hour.
        Mockito.verify(indexShardDao).releaseLapsedLeases(300_000, 1_800_000);
    }

    @Test
    void testExec_takesLapsedShardsBeforeRecoveringClosedOnes() {
        Mockito.when(indexShardDao.findClosedShardsNeedingPendingStreamRecovery(anyInt())).thenReturn(List.of());
        Mockito.when(dao.findShardIdsWithRowsToDelete()).thenReturn(List.of());

        recovery.exec();

        // So that a shard taken in this run has its streams recovered in the same run.
        final InOrder inOrder = Mockito.inOrder(indexShardDao);
        inOrder.verify(indexShardDao).releaseLapsedLeases(anyLong(), anyLong());
        inOrder.verify(indexShardDao).findClosedShardsNeedingPendingStreamRecovery(anyInt());
    }

    @Test
    void testRecoverClosedShards_opensTheOnesThisNodeLooksAfter() {
        final IndexShard mine = shard(SHARD, IndexShardStatus.CLOSED);
        final IndexShard another = shard(SHARD + 1, IndexShardStatus.CLOSED);
        Mockito.when(indexShardDao.findClosedShardsNeedingPendingStreamRecovery(anyInt()))
                .thenReturn(List.of(mine, another));

        recovery.recoverClosedShards();

        // Without opening a writer on them.
        Mockito.verify(writerCache).recoverPendingStreams(mine);
        Mockito.verify(writerCache, Mockito.never()).recoverPendingStreams(another);
        Mockito.verify(writerCache, Mockito.never()).getOrOpenWriter(anyLong());
    }

    @Test
    void testRecoverClosedShards_oneFailing_othersAreStillDone() {
        final IndexShard first = shard(SHARD, IndexShardStatus.CLOSED);
        final IndexShard second = shard(SHARD + 1, IndexShardStatus.CLOSED);
        responsibleFor.add(SHARD + 1);
        Mockito.when(indexShardDao.findClosedShardsNeedingPendingStreamRecovery(anyInt()))
                .thenReturn(List.of(first, second));
        Mockito.when(writerCache.recoverPendingStreams(first)).thenThrow(new RuntimeException("no database"));

        recovery.recoverClosedShards();

        Mockito.verify(writerCache).recoverPendingStreams(second);
    }

    @Test
    void testFinishRetries_taskIsPutBackAndTheAttemptRowKept() {
        Mockito.when(dao.findRetriesToFinish(anyInt()))
                .thenReturn(List.of(row(5, 0, STREAM, PendingStreamState.RETRY_PENDING, 1)));
        Mockito.when(taskRecovery.getStatus(TASK)).thenReturn(Optional.of(TaskStatus.COMPLETE));
        Mockito.when(taskRecovery.retry(eq(TASK), anyString())).thenReturn(true);

        recovery.finishRetries();

        Mockito.verify(taskRecovery).retry(eq(TASK), contains("failed attempt 1 of 3"));
        Mockito.verify(dao).setRetrying(5);
        Mockito.verify(dao, Mockito.never()).deleteStream(anyLong(), anyLong());
    }

    @Test
    void testFinishRetries_taskStillRunning_triedAgainNextTime() {
        Mockito.when(dao.findRetriesToFinish(anyInt()))
                .thenReturn(List.of(row(5, 0, STREAM, PendingStreamState.RETRY_PENDING, 1)));
        Mockito.when(taskRecovery.getStatus(TASK)).thenReturn(Optional.of(TaskStatus.PROCESSING));
        Mockito.when(taskRecovery.retry(eq(TASK), anyString())).thenReturn(false);

        recovery.finishRetries();

        Mockito.verify(dao, Mockito.never()).setRetrying(anyLong());
        Mockito.verify(dao, Mockito.never()).deleteStream(anyLong(), anyLong());
    }

    @Test
    void testFinishRetries_thirdFailure_taskIsSetToFailedAndLeft() {
        Mockito.when(dao.findRetriesToFinish(anyInt()))
                .thenReturn(List.of(row(5, 0, STREAM, PendingStreamState.RETRY_PENDING, 3)));
        Mockito.when(taskRecovery.getStatus(TASK)).thenReturn(Optional.of(TaskStatus.COMPLETE));
        Mockito.when(taskRecovery.fail(eq(TASK), anyString())).thenReturn(true);

        recovery.finishRetries();

        Mockito.verify(taskRecovery).fail(eq(TASK), contains("3 times"));
        Mockito.verify(taskRecovery, Mockito.never()).retry(anyLong(), anyString());
        Mockito.verify(dao).deleteStream(STREAM, TASK);
    }

    @Test
    void testFinishRetries_taskDeleted_rowsAreCleared() {
        Mockito.when(dao.findRetriesToFinish(anyInt()))
                .thenReturn(List.of(row(5, 0, STREAM, PendingStreamState.RETRY_PENDING, 1)));
        Mockito.when(taskRecovery.getStatus(TASK)).thenReturn(Optional.of(TaskStatus.DELETED));

        recovery.finishRetries();

        Mockito.verify(dao).deleteStream(STREAM, TASK);
        Mockito.verify(taskRecovery, Mockito.never()).retry(anyLong(), anyString());
    }

    @Test
    void testFinishRetries_noTaskAnyMore_rowsAreCleared() {
        Mockito.when(dao.findRetriesToFinish(anyInt()))
                .thenReturn(List.of(row(5, 0, STREAM, PendingStreamState.RETRY_PENDING, 1)));
        Mockito.when(taskRecovery.getStatus(TASK)).thenReturn(Optional.empty());

        recovery.finishRetries();

        Mockito.verify(dao).deleteStream(STREAM, TASK);
    }

    @Test
    void testFinishRetries_oneFailing_othersAreStillDone() {
        Mockito.when(dao.findRetriesToFinish(anyInt())).thenReturn(List.of(
                row(5, 0, STREAM, PendingStreamState.RETRY_PENDING, 1),
                new PendingStream(6, 0, STREAM + 1, TASK + 1, 1, PendingStreamState.RETRY_PENDING)));
        Mockito.when(taskRecovery.getStatus(TASK)).thenThrow(new RuntimeException("no database"));
        Mockito.when(taskRecovery.getStatus(TASK + 1)).thenReturn(Optional.of(TaskStatus.COMPLETE));
        Mockito.when(taskRecovery.retry(eq(TASK + 1), anyString())).thenReturn(true);

        recovery.finishRetries();

        Mockito.verify(dao).setRetrying(6);
    }

    @Test
    void testFinishRetries_nothingToRunTasks_rowsAreLeft() {
        recovery = create(Optional.empty());

        recovery.finishRetries();

        Mockito.verify(dao, Mockito.never()).findRetriesToFinish(anyInt());
    }

    @Test
    void testFailStaleAttempts_taskNoLongerRunning() {
        Mockito.when(dao.findStaleAttempts(anyLong(), anyInt())).thenReturn(List.of(
                row(1, SHARD, STREAM, PendingStreamState.WRITING, 0),
                // A second shard of the same attempt.
                row(2, SHARD + 1, STREAM, PendingStreamState.WRITING, 0)));
        Mockito.when(taskRecovery.getStatus(TASK)).thenReturn(Optional.of(TaskStatus.COMPLETE));

        recovery.failStaleAttempts(NOW);

        // Ten minutes is how long a row is left before its task is looked at.
        Mockito.verify(dao).findStaleAttempts(eq(NOW - 600_000), anyInt());
        Mockito.verify(dao, Mockito.times(1)).failStaleAttempt(STREAM, TASK, NOW - 600_000);
        Mockito.verify(taskRecovery, Mockito.times(1)).getStatus(TASK);
    }

    @Test
    void testFailStaleAttempts_taskStillRunning_isLeftAlone() {
        // A long task, not a dead one.
        Mockito.when(dao.findStaleAttempts(anyLong(), anyInt()))
                .thenReturn(List.of(row(1, SHARD, STREAM, PendingStreamState.WRITING, 0)));
        Mockito.when(taskRecovery.getStatus(TASK)).thenReturn(Optional.of(TaskStatus.PROCESSING));

        recovery.failStaleAttempts(NOW);

        Mockito.verify(dao, Mockito.never()).failStaleAttempt(anyLong(), anyLong(), anyLong());
    }

    @Test
    void testExec_onePartFailing_theRestStillRun() {
        Mockito.when(indexShardDao.findClosedShardsNeedingPendingStreamRecovery(anyInt()))
                .thenThrow(new RuntimeException("no database"));
        Mockito.when(dao.findShardIdsWithRowsToDelete()).thenReturn(List.of());
        Mockito.when(dao.findStaleAttempts(anyLong(), anyInt())).thenReturn(List.of());
        Mockito.when(dao.findRetriesToFinish(anyInt())).thenReturn(List.of());

        recovery.exec();

        Mockito.verify(dao).findShardIdsWithRowsToDelete();
        Mockito.verify(dao).markCommittedInClosedShards();
        Mockito.verify(dao).deleteRowsOfDeletedShards();
        Mockito.verify(dao).deleteCommittedStreams(anyInt());
        Mockito.verify(dao).findRetriesToFinish(anyInt());
    }

    private void givenShard(final long shardId, final IndexShardStatus status) {
        Mockito.when(indexShardDao.fetch(shardId)).thenReturn(Optional.of(shard(shardId, status)));
    }

    private IndexShard shard(final long shardId, final IndexShardStatus status) {
        return IndexShard.builder()
                .id(shardId)
                .nodeName("node1")
                .status(status)
                .build();
    }

    private PendingStream row(final long id,
                              final long shardId,
                              final long streamId,
                              final PendingStreamState state,
                              final int attempt) {
        return new PendingStream(id, shardId, streamId, TASK, attempt, state);
    }
}
