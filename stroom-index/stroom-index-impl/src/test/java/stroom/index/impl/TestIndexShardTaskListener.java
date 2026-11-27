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

import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.TaskStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;

class TestIndexShardTaskListener {

    private static final long TASK = 10;
    private static final int RUN = 3;

    private final IndexShardTaskRow earlierRun = row(1, RUN - 1);
    private final IndexShardTaskRow olderRun = row(2, RUN - 2);
    private final IndexShardTaskRow thisRun = row(3, RUN);

    private IndexShardTaskDao taskDao;
    private IndexShardTaskCompletion completion;
    private IndexRunRegistry runRegistry;
    private IndexShardTaskListener listener;
    private ProcessorTask task;

    @BeforeEach
    void setUp() {
        taskDao = Mockito.mock(IndexShardTaskDao.class);
        completion = Mockito.mock(IndexShardTaskCompletion.class);
        Mockito.when(taskDao.findByTask(TASK)).thenReturn(List.of(earlierRun, olderRun, thisRun));
        runRegistry = Mockito.mock(IndexRunRegistry.class);
        listener = new IndexShardTaskListener(taskDao, completion, runRegistry);
        task = ProcessorTask.builder().id(TASK).metaId(100L).runCount(RUN).build();
    }

    @Test
    void testBeforeRerun_rowsOfEarlierRunsBecomePurges() {
        listener.beforeRerun(task);

        Mockito.verify(taskDao).toPurges(List.of(earlierRun, olderRun));
    }

    @Test
    void testHeld_rowsAreMarkedWrittenThenTheTaskMayBeCompleted() {
        listener.held(task);

        final InOrder inOrder = Mockito.inOrder(taskDao, completion);
        inOrder.verify(taskDao).markWritten(TASK, RUN);
        inOrder.verify(completion).check(List.of(TASK));
    }

    @Test
    void testHeld_triedAgainIfTheDatabaseCantBeReached() {
        Mockito.when(taskDao.markWritten(TASK, RUN))
                .thenThrow(new RuntimeException("no database"))
                .thenReturn(1);

        listener.held(task);

        Mockito.verify(taskDao, Mockito.times(2)).markWritten(TASK, RUN);
        Mockito.verify(completion).check(List.of(TASK));
    }

    @Test
    void testEnded_aborted_allRowsBecomePurges() {
        listener.ended(task, TaskStatus.ABORTED);

        Mockito.verify(taskDao).toPurges(List.of(earlierRun, olderRun, thisRun));
    }

    @Test
    void testEnded_toBeRunAgain_rowsAreLeftForTheNextRun() {
        listener.ended(task, TaskStatus.RETRY);
        listener.ended(task, TaskStatus.CREATED);
        listener.ended(task, TaskStatus.COMPLETE);

        Mockito.verify(taskDao, Mockito.never()).toPurges(any());
    }

    @Test
    void testRunIsTakenOutOfTheRegistryAsItEnds() {
        listener.held(task);
        listener.ended(task, TaskStatus.RETRY);
        listener.replaced(task);

        Mockito.verify(runRegistry, Mockito.times(3)).remove(TASK, RUN);
    }

    @Test
    void testReplaced_thisRunsRowsBecomePurges() {
        listener.replaced(task);

        Mockito.verify(taskDao).toPurges(List.of(thisRun));
        Mockito.verify(taskDao, Mockito.never()).markWritten(anyLong(), anyInt());
    }

    private static IndexShardTaskRow row(final long shard, final int run) {
        return new IndexShardTaskRow(TASK, shard, 100, run, "writer", IndexShardTaskState.WRITING, 0);
    }
}
