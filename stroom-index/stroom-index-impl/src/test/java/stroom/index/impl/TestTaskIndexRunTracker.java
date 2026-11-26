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
import stroom.processor.api.RunIndexing;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.TaskStatus;
import stroom.security.mock.MockSecurityContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;

class TestTaskIndexRunTracker {

    private static final long TASK = 10;
    private static final long SHARD = 1;
    private static final long STREAM = 100;
    private static final int RUN = 2;
    private static final String WRITER = "writer";
    private static final String OTHER_WRITER = "otherWriter";

    private IndexShardTaskDao taskDao;
    private HeldProcessorTaskService heldProcessorTaskService;
    private RunIndexing runIndexing;
    private IndexShardPurger purger;
    private IndexShardWriter writer;
    private IndexShardWriter otherWriter;
    private TaskIndexRunTracker tracker;

    @BeforeEach
    void setUp() {
        taskDao = Mockito.mock(IndexShardTaskDao.class);
        heldProcessorTaskService = Mockito.mock(HeldProcessorTaskService.class);
        Mockito.when(taskDao.fetch(anyLong(), anyLong())).thenReturn(Optional.empty());
        Mockito.when(taskDao.insertWriting(anyLong(), anyLong(), anyLong(), anyInt(), anyString()))
                .thenAnswer(invocation -> Optional.of(row(
                        invocation.getArgument(3), invocation.getArgument(4), IndexShardTaskState.WRITING)));
        stateIs(TaskStatus.PROCESSING, RUN);
        runIndexing = new RunIndexing();
        purger = Mockito.mock(IndexShardPurger.class);
        writer = writer(WRITER);
        otherWriter = writer(OTHER_WRITER);
        final ProcessorTask task = ProcessorTask.builder().id(TASK).metaId(STREAM).runCount(RUN).build();
        tracker = new TaskIndexRunTracker(
                taskDao, heldProcessorTaskService, MockSecurityContext.getInstance(), task, runIndexing, purger);
    }

    @Test
    void testFirstDocument_rowIsSavedOnceForEachShardAndWriter() {
        tracker.beforeAdd(SHARD, writer);
        tracker.added(SHARD, WRITER);
        tracker.beforeAdd(SHARD, writer);
        tracker.added(SHARD, WRITER);

        Mockito.verify(taskDao, Mockito.times(1)).insertWriting(TASK, SHARD, STREAM, RUN, WRITER);
        assertThat(tracker.hasWritten()).isTrue();
    }

    @Test
    void testFirstDocument_taskCheckedOnlyAfterTheRowIsSaved() {
        tracker.beforeAdd(SHARD, writer);

        final InOrder inOrder = Mockito.inOrder(taskDao, heldProcessorTaskService);
        inOrder.verify(taskDao).insertWriting(TASK, SHARD, STREAM, RUN, WRITER);
        inOrder.verify(heldProcessorTaskService).getStates(List.of(TASK));
    }

    @Test
    void testTaskNoLongerThisRuns_runIsStopped() {
        // Reaped and run again elsewhere since this run started.
        stateIs(TaskStatus.PROCESSING, RUN + 1);

        assertThatThrownBy(() -> tracker.beforeAdd(SHARD, writer)).isInstanceOf(RunStoppedException.class);
    }

    @Test
    void testRowOfALaterRun_runIsStopped() {
        Mockito.when(taskDao.fetch(TASK, SHARD))
                .thenReturn(Optional.of(row(RUN + 1, OTHER_WRITER, IndexShardTaskState.WRITING)));

        assertThatThrownBy(() -> tracker.beforeAdd(SHARD, writer)).isInstanceOf(RunStoppedException.class);

        Mockito.verify(taskDao, Mockito.never()).insertWriting(anyLong(), anyLong(), anyLong(), anyInt(), any());
    }

    @Test
    void testRowOfAnEarlierRun_isPurgedAndReplaced() {
        final IndexShardTaskRow earlier = row(RUN - 1, OTHER_WRITER, IndexShardTaskState.COMMITTED);
        Mockito.when(taskDao.fetch(TASK, SHARD)).thenReturn(Optional.of(earlier));
        Mockito.when(taskDao.toPurges(List.of(earlier))).thenReturn(List.of(earlier));

        tracker.beforeAdd(SHARD, writer);

        final InOrder inOrder = Mockito.inOrder(taskDao);
        inOrder.verify(taskDao).toPurges(List.of(earlier));
        inOrder.verify(taskDao).insertWriting(TASK, SHARD, STREAM, RUN, WRITER);
    }

    @Test
    void testShardReopenedAfterACleanClose_rowGoesBackToWriting() {
        final IndexShardTaskRow committed = row(RUN, OTHER_WRITER, IndexShardTaskState.COMMITTED);
        Mockito.when(taskDao.fetch(TASK, SHARD)).thenReturn(Optional.of(committed));
        Mockito.when(taskDao.update(committed, RUN, WRITER, IndexShardTaskState.WRITING))
                .thenReturn(Optional.of(row(RUN, WRITER, IndexShardTaskState.WRITING)));

        tracker.beforeAdd(SHARD, writer);

        Mockito.verify(taskDao).update(committed, RUN, WRITER, IndexShardTaskState.WRITING);
        assertThat(runIndexing.isDocumentsLost()).isFalse();
    }

    @Test
    void testShardReopenedWithoutACleanClose_documentsAreLost() {
        // Still writing, under another writer, so the documents given to it were never committed.
        Mockito.when(taskDao.fetch(TASK, SHARD))
                .thenReturn(Optional.of(row(RUN, OTHER_WRITER, IndexShardTaskState.WRITING)));

        assertThatThrownBy(() -> tracker.beforeAdd(SHARD, writer)).isInstanceOf(RunStoppedException.class);

        assertThat(runIndexing.isDocumentsLost()).isTrue();
        Mockito.verify(taskDao, Mockito.never()).update(any(), anyInt(), any(), any());
    }

    @Test
    void testAddFailed_beforeAnyDocument_newRowIsRemoved() {
        tracker.beforeAdd(SHARD, writer);

        tracker.addFailed(SHARD, WRITER);

        Mockito.verify(taskDao).delete(row(RUN, WRITER, IndexShardTaskState.WRITING));
        // A later document saves it again.
        tracker.beforeAdd(SHARD, writer);
        Mockito.verify(taskDao, Mockito.times(2)).insertWriting(TASK, SHARD, STREAM, RUN, WRITER);
    }

    @Test
    void testAddFailed_beforeAnyDocument_rowIsPutBackAsItWas() {
        final IndexShardTaskRow committed = row(RUN, OTHER_WRITER, IndexShardTaskState.COMMITTED);
        final IndexShardTaskRow writing = row(RUN, WRITER, IndexShardTaskState.WRITING);
        Mockito.when(taskDao.fetch(TASK, SHARD)).thenReturn(Optional.of(committed));
        Mockito.when(taskDao.update(committed, RUN, WRITER, IndexShardTaskState.WRITING))
                .thenReturn(Optional.of(writing));
        tracker.beforeAdd(SHARD, writer);

        tracker.addFailed(SHARD, WRITER);

        // Otherwise it would be taken for lost documents, and the committed documents thrown away.
        Mockito.verify(taskDao).update(writing, RUN, OTHER_WRITER, IndexShardTaskState.COMMITTED);
        Mockito.verify(taskDao, Mockito.never()).delete(any());
    }

    @Test
    void testAddFailed_afterADocument_rowIsKept() {
        tracker.beforeAdd(SHARD, writer);
        tracker.added(SHARD, WRITER);

        tracker.addFailed(SHARD, WRITER);

        Mockito.verify(taskDao, Mockito.never()).delete(any());
        Mockito.verify(taskDao, Mockito.never()).update(any(), anyInt(), any(), any());
    }

    @Test
    void testNothingWritten() {
        assertThat(tracker.hasWritten()).isFalse();
    }

    @Test
    void testFirstDocument_streamIsPurgedAfterTheRowIsSavedAndTheTaskChecked() {
        tracker.beforeAdd(SHARD, writer);

        final InOrder inOrder = Mockito.inOrder(taskDao, heldProcessorTaskService, purger);
        inOrder.verify(taskDao).insertWriting(TASK, SHARD, STREAM, RUN, WRITER);
        inOrder.verify(heldProcessorTaskService).getStates(List.of(TASK));
        inOrder.verify(purger).purgeBeforeWriting(SHARD, STREAM, TASK, writer);
        // Only once for each shard and writer.
        tracker.added(SHARD, WRITER);
        tracker.beforeAdd(SHARD, writer);
        Mockito.verify(purger, Mockito.times(1)).purgeBeforeWriting(anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    void testStopped_addsNothingMoreAndReportsItsDocumentsLost() {
        tracker.beforeAdd(SHARD, writer);
        tracker.added(SHARD, WRITER);

        tracker.stop();

        assertThatThrownBy(() -> tracker.beforeAdd(SHARD, writer)).isInstanceOf(RunStoppedException.class);
        assertThat(runIndexing.isDocumentsLost()).isTrue();
    }

    @Test
    void testStop_waitsForADocumentBeingAdded() throws Exception {
        tracker.beforeAdd(SHARD, writer);
        final Thread stopper = new Thread(tracker::stop);
        stopper.start();
        stopper.join(200);
        // Still being added.
        assertThat(stopper.isAlive()).isTrue();

        tracker.added(SHARD, WRITER);

        stopper.join(10_000);
        assertThat(stopper.isAlive()).isFalse();
    }

    @Test
    void testStop_whileTheRunIsSavingItsRow_doesNotWaitAndTheRunAddsNothing() throws Exception {
        // The run is held up saving its row, e.g. waiting for the writer a purge holds while it stops the run.
        final CountDownLatch saving = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        Mockito.doAnswer(invocation -> {
            saving.countDown();
            release.await();
            return null;
        }).when(purger).purgeBeforeWriting(anyLong(), anyLong(), anyLong(), any());
        final Thread runThread = new Thread(() -> {
            try {
                tracker.beforeAdd(SHARD, writer);
            } catch (final RunStoppedException e) {
                // Expected.
            }
        });
        runThread.start();
        assertThat(saving.await(10, TimeUnit.SECONDS)).isTrue();

        final Thread stopper = new Thread(tracker::stop);
        stopper.start();
        stopper.join(10_000);
        assertThat(stopper.isAlive()).isFalse();

        release.countDown();
        runThread.join(10_000);
        assertThat(runIndexing.isDocumentsLost()).isTrue();
        assertThatThrownBy(() -> tracker.beforeAdd(SHARD, writer)).isInstanceOf(RunStoppedException.class);
    }

    @Test
    void testHasWrittenTo_answersWhileTheRunHoldsItsLock() throws Exception {
        tracker.beforeAdd(SHARD, writer);
        // As when the run is saving a row and waiting for a writer that a purge holds, while the purge asks it
        // whether it has written to the shard.
        final CountDownLatch locked = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final Thread runThread = new Thread(() -> {
            synchronized (tracker) {
                locked.countDown();
                try {
                    release.await();
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        runThread.start();
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
        try {
            final CompletableFuture<Boolean> answer = CompletableFuture.supplyAsync(() -> tracker.hasWrittenTo(SHARD));

            assertThat(answer.get(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            release.countDown();
            runThread.join();
        }
    }

    @Test
    void testKnowsWhatItHasWrittenTo() {
        assertThat(tracker.hasWrittenTo(SHARD)).isFalse();

        tracker.beforeAdd(SHARD, writer);

        assertThat(tracker.hasWrittenTo(SHARD)).isTrue();
        assertThat(tracker.getTaskId()).isEqualTo(TASK);
        assertThat(tracker.getRun()).isEqualTo(RUN);
        assertThat(tracker.getStreamId()).isEqualTo(STREAM);
    }

    private static IndexShardWriter writer(final String writerId) {
        final IndexShardWriter writer = Mockito.mock(IndexShardWriter.class);
        Mockito.when(writer.getWriterId()).thenReturn(writerId);
        return writer;
    }

    private void stateIs(final TaskStatus status, final int runCount) {
        Mockito.when(heldProcessorTaskService.getStates(List.of(TASK)))
                .thenReturn(Map.of(TASK, new ProcessorTaskState(TASK, status, runCount)));
    }

    private static IndexShardTaskRow row(final int run, final String writerId, final IndexShardTaskState state) {
        return new IndexShardTaskRow(TASK, SHARD, STREAM, run, writerId, state, 0);
    }
}
