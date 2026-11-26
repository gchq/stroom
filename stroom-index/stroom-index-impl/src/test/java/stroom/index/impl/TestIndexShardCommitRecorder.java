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

import stroom.security.mock.MockSecurityContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;

class TestIndexShardCommitRecorder {

    private static final long SHARD = 1;
    private static final String WRITER = "writer";

    private IndexShardTaskDao taskDao;
    private IndexShardPurgeDao purgeDao;
    private IndexShardTaskCompletion completion;
    private IndexShardPurger purger;
    private IndexShardWriter writer;
    private IndexShardCommitRecorder recorder;

    @BeforeEach
    void setUp() {
        taskDao = Mockito.mock(IndexShardTaskDao.class);
        purgeDao = Mockito.mock(IndexShardPurgeDao.class);
        completion = Mockito.mock(IndexShardTaskCompletion.class);
        purger = Mockito.mock(IndexShardPurger.class);
        writer = Mockito.mock(IndexShardWriter.class);
        Mockito.when(writer.getWriterId()).thenReturn(WRITER);
        recorder = new IndexShardCommitRecorder(
                taskDao, purgeDao, completion, purger, MockSecurityContext.getInstance());
    }

    @Test
    void testCommit_listedBeforeAndMarkedAfter() {
        final IndexShardTaskRow row = new IndexShardTaskRow(
                10, SHARD, 100, 1, WRITER, IndexShardTaskState.WRITTEN, 0);
        final IndexShardPurge ours = new IndexShardPurge(SHARD, 200, WRITER, 2, 0);
        final IndexShardPurge waiting = new IndexShardPurge(SHARD, 201, null, 1, 0);
        Mockito.when(taskDao.findWritten(SHARD, WRITER)).thenReturn(List.of(row));
        Mockito.when(purgeDao.findByShard(SHARD)).thenReturn(List.of(ours, waiting));
        Mockito.when(taskDao.markCommitted(SHARD, WRITER, List.of(row), List.of(ours))).thenReturn(true);

        final Runnable afterCommit = recorder.createListener(SHARD, writer).beforeCommit();
        // The waiting purges are done first, so the commit removes them too.
        final InOrder inOrder = Mockito.inOrder(purger, taskDao);
        inOrder.verify(purger).purgeWaiting(SHARD, writer);
        inOrder.verify(taskDao).findWritten(SHARD, WRITER);
        // Nothing is marked until the commit has succeeded.
        Mockito.verify(taskDao, Mockito.never()).markCommitted(anyLong(), any(), any(), any());

        afterCommit.run();

        Mockito.verify(taskDao).markCommitted(SHARD, WRITER, List.of(row), List.of(ours));
        Mockito.verify(completion).check(Set.of(10L));
    }

    @Test
    void testCommit_shardNoLongerTheWriters_completesNothing() {
        final IndexShardTaskRow row = new IndexShardTaskRow(
                10, SHARD, 100, 1, WRITER, IndexShardTaskState.WRITTEN, 0);
        Mockito.when(taskDao.findWritten(SHARD, WRITER)).thenReturn(List.of(row));
        Mockito.when(taskDao.markCommitted(anyLong(), any(), any(), any())).thenReturn(false);

        recorder.createListener(SHARD, writer).beforeCommit().run();

        Mockito.verify(completion, Mockito.never()).check(any());
    }

    @Test
    void testCommit_nothingWritten_nothingRecorded() {
        recorder.createListener(SHARD, writer).beforeCommit().run();

        Mockito.verify(taskDao, Mockito.never()).markCommitted(anyLong(), any(), any(), any());
    }
}
