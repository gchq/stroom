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

import stroom.processor.api.HeldProcessorTaskService;
import stroom.processor.api.ProcessorTaskState;
import stroom.processor.shared.TaskStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

class TestIndexShardPurger {

    private static final long SHARD = 1;
    private static final long STREAM = 100;
    private static final long OWN_TASK = 10;
    private static final String WRITER = "writer";

    private IndexShardPurgeDao purgeDao;
    private IndexShardTaskDao taskDao;
    private HeldProcessorTaskService heldProcessorTaskService;
    private IndexRunRegistry runRegistry;
    private IndexShardWriter writer;
    private IndexShardPurger purger;

    @BeforeEach
    void setUp() {
        purgeDao = Mockito.mock(IndexShardPurgeDao.class);
        taskDao = Mockito.mock(IndexShardTaskDao.class);
        heldProcessorTaskService = Mockito.mock(HeldProcessorTaskService.class);
        runRegistry = new IndexRunRegistry();
        writer = Mockito.mock(IndexShardWriter.class);
        Mockito.when(writer.getWriterId()).thenReturn(WRITER);
        Mockito.when(taskDao.findByShardAndStream(SHARD, STREAM)).thenReturn(List.of());
        Mockito.when(purgeDao.fetch(SHARD, STREAM)).thenReturn(Optional.empty());
        Mockito.when(purgeDao.markDeleted(any(), any())).thenAnswer(invocation -> Optional.of(
                invocation.<IndexShardPurge>getArgument(0)));
        purger = new IndexShardPurger(purgeDao, taskDao, heldProcessorTaskService, runRegistry);
    }

    @Test
    void testBeforeWriting_staleRowsOfOtherTasksBecomePurgesFirst() {
        final IndexShardTaskRow aborted = row(20, 1, IndexShardTaskState.COMMITTED);
        final IndexShardTaskRow deletedUnfinished = row(21, 1, IndexShardTaskState.WRITTEN);
        final IndexShardTaskRow deletedFinished = row(22, 1, IndexShardTaskState.COMMITTED);
        final IndexShardTaskRow replaced = row(23, 1, IndexShardTaskState.WRITING);
        final IndexShardTaskRow live = row(24, 2, IndexShardTaskState.WRITING);
        final IndexShardTaskRow own = row(OWN_TASK, 1, IndexShardTaskState.WRITING);
        Mockito.when(taskDao.findByShardAndStream(SHARD, STREAM)).thenReturn(
                List.of(aborted, deletedUnfinished, deletedFinished, replaced, live, own));
        Mockito.when(heldProcessorTaskService.getStates(any())).thenReturn(Map.of(
                20L, state(20, TaskStatus.ABORTED, 1),
                21L, state(21, TaskStatus.DELETED, 0),
                22L, state(22, TaskStatus.DELETED, 0),
                23L, state(23, TaskStatus.PROCESSING, 2),
                24L, state(24, TaskStatus.PROCESSING, 2)));

        purger.purgeBeforeWriting(SHARD, STREAM, OWN_TASK, writer);

        Mockito.verify(taskDao).toPurges(List.of(aborted, deletedUnfinished, replaced));
    }

    @Test
    void testBeforeWriting_waitingPurgeIsDoneAndRecorded() {
        final IndexShardPurge waiting = new IndexShardPurge(SHARD, STREAM, null, 1, 0);
        Mockito.when(purgeDao.fetch(SHARD, STREAM)).thenReturn(Optional.of(waiting));

        purger.purgeBeforeWriting(SHARD, STREAM, OWN_TASK, writer);

        final InOrder inOrder = Mockito.inOrder(writer, purgeDao);
        inOrder.verify(writer).deleteStreams(List.of(STREAM));
        inOrder.verify(purgeDao).markDeleted(waiting, WRITER);
    }

    @Test
    void testBeforeWriting_purgeNotRecorded_nothingMayBeAdded() {
        Mockito.when(purgeDao.fetch(SHARD, STREAM))
                .thenReturn(Optional.of(new IndexShardPurge(SHARD, STREAM, null, 1, 0)));
        Mockito.doReturn(Optional.empty()).when(purgeDao).markDeleted(any(), any());

        assertThatThrownBy(() -> purger.purgeBeforeWriting(SHARD, STREAM, OWN_TASK, writer))
                .isInstanceOf(IndexRunTrackingException.class);
    }

    @Test
    void testBeforeWriting_alreadyDoneByThisWriter_isNotDoneAgain() {
        Mockito.when(purgeDao.fetch(SHARD, STREAM))
                .thenReturn(Optional.of(new IndexShardPurge(SHARD, STREAM, WRITER, 2, 0)));

        purger.purgeBeforeWriting(SHARD, STREAM, OWN_TASK, writer);

        Mockito.verify(writer, Mockito.never()).deleteStreams(any());
    }

    @Test
    void testBeforeWriting_doneByAWriterThatHasGone_isDoneAgain() {
        final IndexShardPurge waitingAgain = new IndexShardPurge(SHARD, STREAM, null, 3, 0);
        Mockito.when(purgeDao.fetch(SHARD, STREAM))
                .thenReturn(Optional.of(new IndexShardPurge(SHARD, STREAM, "goneWriter", 2, 0)))
                .thenReturn(Optional.of(new IndexShardPurge(SHARD, STREAM, "goneWriter", 2, 0)))
                .thenReturn(Optional.of(waitingAgain));

        purger.purgeBeforeWriting(SHARD, STREAM, OWN_TASK, writer);

        Mockito.verify(purgeDao).request(SHARD, STREAM);
        Mockito.verify(writer).deleteStreams(List.of(STREAM));
        Mockito.verify(purgeDao).markDeleted(waitingAgain, WRITER);
    }

    @Test
    void testPurgeWaiting_onlyWaitingPurges_andOneFailureStopsNoOther() {
        final IndexShardPurge first = new IndexShardPurge(SHARD, 101, null, 1, 0);
        final IndexShardPurge done = new IndexShardPurge(SHARD, 102, WRITER, 2, 0);
        final IndexShardPurge third = new IndexShardPurge(SHARD, 103, null, 1, 0);
        Mockito.when(purgeDao.findByShard(SHARD)).thenReturn(List.of(first, done, third));
        Mockito.when(purgeDao.fetch(SHARD, 101)).thenReturn(Optional.of(first));
        Mockito.when(purgeDao.fetch(SHARD, 103)).thenReturn(Optional.of(third));
        Mockito.doThrow(new RuntimeException("closing")).when(writer).deleteStreams(List.of(101L));

        purger.purgeWaiting(SHARD, writer);

        Mockito.verify(writer).deleteStreams(List.of(103L));
        Mockito.verify(writer, Mockito.never()).deleteStreams(List.of(102L));
        Mockito.verify(purgeDao).markDeleted(third, WRITER);
    }

    @Test
    void testPurge_replacedRunOnThisNodeIsStoppedFirst() {
        final TaskIndexRunTracker replacedRun = run(30, 1);
        final TaskIndexRunTracker liveRun = run(31, 1);
        Mockito.when(heldProcessorTaskService.getStates(any())).thenReturn(Map.of(
                30L, state(30, TaskStatus.PROCESSING, 2),
                31L, state(31, TaskStatus.PROCESSING, 1)));
        Mockito.when(taskDao.fetch(30, SHARD)).thenReturn(Optional.empty());
        Mockito.when(taskDao.fetch(31, SHARD)).thenReturn(Optional.empty());
        final IndexShardPurge waiting = new IndexShardPurge(SHARD, STREAM, null, 1, 0);
        Mockito.when(purgeDao.fetch(SHARD, STREAM)).thenReturn(Optional.of(waiting));

        purger.purgeBeforeWriting(SHARD, STREAM, OWN_TASK, writer);

        final InOrder inOrder = Mockito.inOrder(replacedRun, writer);
        inOrder.verify(replacedRun).stop();
        inOrder.verify(writer).deleteStreams(List.of(STREAM));
        // Its task is still on that run, so it is not stopped.
        Mockito.verify(liveRun, Mockito.never()).stop();
    }

    private TaskIndexRunTracker run(final long taskId, final int run) {
        final TaskIndexRunTracker tracker = Mockito.mock(TaskIndexRunTracker.class);
        Mockito.when(tracker.getTaskId()).thenReturn(taskId);
        Mockito.when(tracker.getRun()).thenReturn(run);
        Mockito.when(tracker.getStreamId()).thenReturn(STREAM);
        Mockito.when(tracker.hasWrittenTo(SHARD)).thenReturn(true);
        runRegistry.add(tracker);
        return tracker;
    }

    private static ProcessorTaskState state(final long taskId, final TaskStatus status, final int runCount) {
        return new ProcessorTaskState(taskId, status, runCount);
    }

    private static IndexShardTaskRow row(final long taskId, final int run, final IndexShardTaskState state) {
        return new IndexShardTaskRow(taskId, SHARD, STREAM, run, "otherWriter", state, 0);
    }
}
