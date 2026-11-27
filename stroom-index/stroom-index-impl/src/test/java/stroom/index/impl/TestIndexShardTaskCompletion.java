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
import stroom.security.mock.MockSecurityContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;

class TestIndexShardTaskCompletion {

    private static final long TASK = 10;
    private static final int RUN = 2;

    private IndexShardTaskDao taskDao;
    private HeldProcessorTaskService heldProcessorTaskService;
    private IndexShardTaskCompletion completion;

    @BeforeEach
    void setUp() {
        taskDao = Mockito.mock(IndexShardTaskDao.class);
        heldProcessorTaskService = Mockito.mock(HeldProcessorTaskService.class);
        Mockito.when(heldProcessorTaskService.completeHeld(anyLong(), anyInt())).thenReturn(true);
        completion = new IndexShardTaskCompletion(taskDao, heldProcessorTaskService, MockSecurityContext.getInstance());
    }

    @Test
    void testAllRowsOfTheRunCommitted_isCompletedInOrder() {
        stateIs(TaskStatus.COMMITTING, RUN);
        Mockito.when(taskDao.findByTask(TASK)).thenReturn(List.of(
                row(1, RUN, IndexShardTaskState.COMMITTED),
                row(2, RUN, IndexShardTaskState.COMMITTED),
                // An earlier run's, which plays no part.
                row(3, RUN - 1, IndexShardTaskState.WRITING)));

        completion.check(Set.of(TASK));

        final InOrder inOrder = Mockito.inOrder(heldProcessorTaskService, taskDao);
        inOrder.verify(heldProcessorTaskService).getStates(Set.of(TASK));
        inOrder.verify(taskDao).findByTask(TASK);
        inOrder.verify(heldProcessorTaskService).completeHeld(TASK, RUN);
        inOrder.verify(taskDao).deleteRun(TASK, RUN);
    }

    @Test
    void testARowOfTheRunNotCommitted_isNotCompleted() {
        stateIs(TaskStatus.COMMITTING, RUN);
        Mockito.when(taskDao.findByTask(TASK)).thenReturn(List.of(
                row(1, RUN, IndexShardTaskState.COMMITTED),
                row(2, RUN, IndexShardTaskState.WRITTEN)));

        completion.check(Set.of(TASK));

        Mockito.verify(heldProcessorTaskService, Mockito.never()).completeHeld(anyLong(), anyInt());
        Mockito.verify(taskDao, Mockito.never()).deleteRun(anyLong(), anyInt());
    }

    @Test
    void testNotCommitting_rowsAreNotEvenRead() {
        stateIs(TaskStatus.PROCESSING, RUN);

        completion.check(Set.of(TASK));

        Mockito.verify(taskDao, Mockito.never()).findByTask(anyLong());
        Mockito.verify(heldProcessorTaskService, Mockito.never()).completeHeld(anyLong(), anyInt());
    }

    @Test
    void testNoLongerCommittingWhenCompleted_rowsAreKept() {
        stateIs(TaskStatus.COMMITTING, RUN);
        Mockito.when(taskDao.findByTask(TASK)).thenReturn(List.of(row(1, RUN, IndexShardTaskState.COMMITTED)));
        Mockito.when(heldProcessorTaskService.completeHeld(TASK, RUN)).thenReturn(false);

        completion.check(Set.of(TASK));

        Mockito.verify(taskDao, Mockito.never()).deleteRun(anyLong(), anyInt());
    }

    private void stateIs(final TaskStatus status, final int runCount) {
        Mockito.when(heldProcessorTaskService.getStates(Set.of(TASK)))
                .thenReturn(Map.of(TASK, new ProcessorTaskState(TASK, status, runCount)));
    }

    private static IndexShardTaskRow row(final long shard, final int run, final IndexShardTaskState state) {
        return new IndexShardTaskRow(TASK, shard, 100, run, "writer", state, 0);
    }
}
