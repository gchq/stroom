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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;

class TestPendingStreamTracker {

    private static final long STREAM = 100;
    private static final long TASK = 7;
    private static final long SHARD_1 = 11;
    private static final long SHARD_2 = 12;

    private IndexShardPendingStreamDao dao;
    private PendingStreamTracker tracker;

    @BeforeEach
    void setUp() {
        dao = Mockito.mock(IndexShardPendingStreamDao.class);
        tracker = new PendingStreamTracker(dao, STREAM, TASK);
    }

    @Test
    void testShardIsRecordedOnceBeforeItsFirstDocument() {
        for (int i = 0; i < 5; i++) {
            tracker.beforeAdd(SHARD_1);
            tracker.added(SHARD_1);
        }
        tracker.beforeAdd(SHARD_2);
        tracker.added(SHARD_2);

        Mockito.verify(dao, Mockito.times(1)).addShard(SHARD_1, STREAM, TASK);
        Mockito.verify(dao, Mockito.times(1)).addShard(SHARD_2, STREAM, TASK);
    }

    @Test
    void testShardThatCouldNotBeRecordedIsTriedAgain() {
        Mockito.doThrow(new RuntimeException("no database"))
                .doNothing()
                .when(dao).addShard(anyLong(), anyLong(), anyLong());

        try {
            tracker.beforeAdd(SHARD_1);
        } catch (final RuntimeException e) {
            // The document is not added, so the shard holds nothing unrecorded.
        }
        tracker.beforeAdd(SHARD_1);

        Mockito.verify(dao, Mockito.times(2)).addShard(SHARD_1, STREAM, TASK);
    }

    @Test
    void testShardThatTookNothingIsForgotten() {
        tracker.beforeAdd(SHARD_1);
        tracker.addFailed(SHARD_1);

        Mockito.verify(dao).removeShard(SHARD_1, STREAM, TASK);

        // And recorded afresh if it is used after all.
        tracker.beforeAdd(SHARD_1);
        Mockito.verify(dao, Mockito.times(2)).addShard(SHARD_1, STREAM, TASK);
    }

    @Test
    void testShardThatTookSomethingIsKept() {
        tracker.beforeAdd(SHARD_1);
        tracker.added(SHARD_1);
        tracker.beforeAdd(SHARD_1);
        tracker.addFailed(SHARD_1);

        Mockito.verify(dao, Mockito.never()).removeShard(anyLong(), anyLong(), anyLong());
    }

    @Test
    void testEnd() {
        Mockito.when(dao.endAttempt(STREAM, TASK)).thenReturn(true);
        tracker.beforeAdd(SHARD_1);

        assertThat(tracker.end()).isTrue();

        // Ending twice does nothing more.
        assertThat(tracker.end()).isTrue();
        Mockito.verify(dao, Mockito.times(1)).endAttempt(STREAM, TASK);
    }

    @Test
    void testEnd_attemptHasFailed() {
        Mockito.when(dao.endAttempt(STREAM, TASK)).thenReturn(false);

        assertThat(tracker.end()).isFalse();
    }

    @Test
    void testFail_documentsAreOnlyRemovedOnceTheTaskHasStopped() {
        tracker.beforeAdd(SHARD_1);

        tracker.fail();
        tracker.fail();
        // The task carries on with the next part of the stream and reaches another shard.
        tracker.beforeAdd(SHARD_2);
        tracker.added(SHARD_2);

        // Ending is what says the task has stopped. It reports the failure even if the rows say nothing.
        Mockito.when(dao.endAttempt(STREAM, TASK)).thenReturn(true);
        assertThat(tracker.end()).isFalse();
        assertThat(tracker.end()).isFalse();

        final InOrder inOrder = Mockito.inOrder(dao);
        inOrder.verify(dao).addShard(SHARD_1, STREAM, TASK);
        inOrder.verify(dao, Mockito.times(1)).abandonAttempt(STREAM, TASK);
        inOrder.verify(dao).addShard(SHARD_2, STREAM, TASK);
        inOrder.verify(dao, Mockito.times(1)).endAttempt(STREAM, TASK);
        Mockito.verify(dao, Mockito.never()).failAttempt(anyLong(), anyLong());
    }
}
