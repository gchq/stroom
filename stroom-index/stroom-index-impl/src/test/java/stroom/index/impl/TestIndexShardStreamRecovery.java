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

import stroom.index.shared.IndexShard;
import stroom.processor.api.HeldProcessorTaskService;
import stroom.processor.api.ProcessorTaskState;
import stroom.processor.shared.TaskStatus;
import stroom.security.mock.MockSecurityContext;
import stroom.util.time.StroomDuration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;

class TestIndexShardStreamRecovery {

    private static final long TASK = 10;
    private static final int RUN = 3;

    private IndexShardDao indexShardDao;
    private IndexShardTaskDao taskDao;
    private IndexShardPurgeDao purgeDao;
    private HeldProcessorTaskService heldProcessorTaskService;
    private IndexShardTaskCompletion completion;
    private IndexShardWriterCache indexShardWriterCache;
    private IndexShardResponsibility indexShardResponsibility;
    private IndexWriterConfig config;
    private IndexShardStreamRecovery recovery;

    @BeforeEach
    void setUp() {
        indexShardDao = Mockito.mock(IndexShardDao.class);
        taskDao = Mockito.mock(IndexShardTaskDao.class);
        purgeDao = Mockito.mock(IndexShardPurgeDao.class);
        heldProcessorTaskService = Mockito.mock(HeldProcessorTaskService.class);
        completion = Mockito.mock(IndexShardTaskCompletion.class);
        indexShardWriterCache = Mockito.mock(IndexShardWriterCache.class);
        indexShardResponsibility = Mockito.mock(IndexShardResponsibility.class);
        Mockito.when(indexShardResponsibility.createTest()).thenReturn(shard -> shard.getId() == 1L);
        config = new IndexWriterConfig(
                null,
                null,
                null,
                null,
                StroomDuration.ofMinutes(20),
                null,
                null,
                StroomDuration.ofMinutes(7));
        recovery = new IndexShardStreamRecovery(
                indexShardDao,
                taskDao,
                purgeDao,
                heldProcessorTaskService,
                completion,
                indexShardWriterCache,
                indexShardResponsibility,
                MockSecurityContext.getInstance(),
                () -> config);
    }

    @Test
    void testReleaseLapsedLeases_withTheConfiguredGraceAndRetention() {
        recovery.releaseLapsedLeases();

        Mockito.verify(indexShardDao).releaseLapsedLeases(7 * 60_000L, 20 * 60_000L);
    }

    @Test
    void testPutBackLostTasks_committingTaskIsPutBack() {
        final IndexShardTaskRow lost = row(1, RUN, IndexShardTaskState.WRITTEN);
        final IndexShardTaskRow replaced = row(2, RUN - 1, IndexShardTaskState.WRITING);
        Mockito.when(taskDao.findLost(null, 1_000)).thenReturn(List.of(lost, replaced));
        stateIs(TaskStatus.COMMITTING, RUN);
        Mockito.when(heldProcessorTaskService.putBack(TASK, RUN, IndexShardStreamRecovery.REASON_DOCUMENTS_LOST))
                .thenReturn(true);

        recovery.putBackLostTasks();

        Mockito.verify(heldProcessorTaskService)
                .putBack(TASK, RUN, IndexShardStreamRecovery.REASON_DOCUMENTS_LOST);
        // The rows of the put back run are left for its next run. Those of the replaced run become purges.
        Mockito.verify(taskDao).toPurges(List.of(replaced));
    }

    @Test
    void testPutBackLostTasks_taskStillProcessing_isLeftToItsRun() {
        Mockito.when(taskDao.findLost(null, 1_000)).thenReturn(List.of(row(1, RUN, IndexShardTaskState.WRITING)));
        stateIs(TaskStatus.PROCESSING, RUN);

        recovery.putBackLostTasks();

        Mockito.verify(heldProcessorTaskService, Mockito.never()).putBack(anyLong(), anyInt(), anyString());
        Mockito.verify(taskDao, Mockito.never()).toPurges(any());
    }

    @Test
    void testFinishHeldTasks_rowsMarkedWrittenThenCompleted() {
        Mockito.when(heldProcessorTaskService.findHeld(any(), Mockito.eq(0L), anyInt()))
                .thenReturn(List.of(new ProcessorTaskState(TASK, TaskStatus.COMMITTING, RUN)));

        recovery.finishHeldTasks();

        final InOrder inOrder = Mockito.inOrder(taskDao, completion);
        inOrder.verify(taskDao).markWritten(TASK, RUN);
        inOrder.verify(completion).completeIfCommitted(TASK, RUN);
    }

    @Test
    void testTidyRows_complete_rowsOfItsRunRemovedAndEarlierOnesPurged() {
        final IndexShardTaskRow ofRun = row(1, RUN, IndexShardTaskState.COMMITTED);
        final IndexShardTaskRow earlier = row(2, RUN - 1, IndexShardTaskState.COMMITTED);
        tidy(TaskStatus.COMPLETE, ofRun, earlier);

        Mockito.verify(taskDao).delete(ofRun);
        Mockito.verify(taskDao).toPurges(List.of(earlier));
    }

    @Test
    void testTidyRows_aborted_allPurged() {
        final IndexShardTaskRow ofRun = row(1, RUN, IndexShardTaskState.COMMITTED);
        final IndexShardTaskRow earlier = row(2, RUN - 1, IndexShardTaskState.WRITING);
        tidy(TaskStatus.ABORTED, ofRun, earlier);

        Mockito.verify(taskDao).toPurges(List.of(ofRun, earlier));
    }

    @Test
    void testTidyRows_deletedUnfinished_allPurged() {
        final IndexShardTaskRow notCommitted = row(1, RUN, IndexShardTaskState.WRITTEN);
        final IndexShardTaskRow committed = row(2, RUN, IndexShardTaskState.COMMITTED);
        tidy(TaskStatus.DELETED, notCommitted, committed);

        Mockito.verify(taskDao).toPurges(List.of(notCommitted, committed));
        Mockito.verify(taskDao, Mockito.never()).delete(any());
    }

    @Test
    void testTidyRows_deletedAfterItCompleted_whatItIndexedStays() {
        final IndexShardTaskRow committed = row(1, RUN, IndexShardTaskState.COMMITTED);
        tidy(TaskStatus.DELETED, committed);

        Mockito.verify(taskDao).delete(committed);
        Mockito.verify(taskDao, Mockito.never()).toPurges(any());
    }

    @Test
    void testTidyRows_toBeRunAgain_onlyRowsOfEarlierRunsPurged() {
        final IndexShardTaskRow ofRun = row(1, RUN, IndexShardTaskState.WRITING);
        final IndexShardTaskRow earlier = row(2, RUN - 1, IndexShardTaskState.WRITING);
        tidy(TaskStatus.RETRY, ofRun, earlier);

        Mockito.verify(taskDao).toPurges(List.of(earlier));
        Mockito.verify(taskDao, Mockito.never()).delete(any());
    }

    @Test
    void testSweepPurges_onlyShardsThisNodeLooksAfter() {
        Mockito.when(purgeDao.findShardsToSweep(anyInt())).thenReturn(List.of(1L, 2L));
        Mockito.when(indexShardDao.fetch(anyLong())).thenAnswer(invocation -> Optional.of(
                IndexShard.builder().id(invocation.getArgument(0)).build()));

        recovery.sweepPurges();

        final InOrder inOrder = Mockito.inOrder(indexShardWriterCache);
        inOrder.verify(indexShardWriterCache).getOrOpenWriter(1L);
        inOrder.verify(indexShardWriterCache).flush(1L);
        Mockito.verify(indexShardWriterCache, Mockito.never()).getOrOpenWriter(2L);
    }

    @Test
    void testExec_aFailingPartDoesNotStopTheRest() {
        Mockito.when(indexShardDao.releaseLapsedLeases(anyLong(), anyLong()))
                .thenThrow(new RuntimeException("no database"));
        Mockito.when(taskDao.findLost(any(), anyInt())).thenReturn(List.of());
        Mockito.when(taskDao.findNotUpdatedSince(anyLong(), any(), anyInt())).thenReturn(List.of());
        Mockito.when(heldProcessorTaskService.findHeld(any(), anyLong(), anyInt())).thenReturn(List.of());
        Mockito.when(purgeDao.findOldestCreateTimeMs()).thenReturn(Optional.empty());

        assertThatCode(recovery::exec).doesNotThrowAnyException();

        Mockito.verify(taskDao).deleteForDeletedShards();
        Mockito.verify(purgeDao).resetLost();
    }

    private void tidy(final TaskStatus status, final IndexShardTaskRow... rows) {
        Mockito.when(taskDao.findNotUpdatedSince(anyLong(), Mockito.isNull(), anyInt())).thenReturn(List.of(rows));
        stateIs(status, RUN);

        recovery.tidyRows();
    }

    private void stateIs(final TaskStatus status, final int runCount) {
        Mockito.when(heldProcessorTaskService.getStates(any()))
                .thenReturn(Map.of(TASK, new ProcessorTaskState(TASK, status, runCount)));
    }

    private static IndexShardTaskRow row(final long shard, final int run, final IndexShardTaskState state) {
        return new IndexShardTaskRow(TASK, shard, 100, run, "writer", state, 0);
    }
}
