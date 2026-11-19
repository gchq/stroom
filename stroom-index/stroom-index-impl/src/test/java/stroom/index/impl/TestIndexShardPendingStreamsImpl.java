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

import stroom.index.shared.IndexException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;

class TestIndexShardPendingStreamsImpl {

    private static final long STREAM = 100;
    private static final long TASK = 7;

    private IndexShardPendingStreamDao dao;
    private IndexShardPendingStreamRecovery recovery;

    @BeforeEach
    void setUp() {
        dao = Mockito.mock(IndexShardPendingStreamDao.class);
        recovery = Mockito.mock(IndexShardPendingStreamRecovery.class);
    }

    @Test
    void testBegin_nothingLeftByAnEarlierAttempt() {
        Mockito.when(dao.beginAttempt(STREAM, TASK)).thenReturn(0);

        final PendingStreamTracker tracker = create(Duration.ofSeconds(10)).begin(STREAM, TASK);

        assertThat(tracker.getStreamId()).isEqualTo(STREAM);
        Mockito.verifyNoInteractions(recovery);
        Mockito.verify(dao, Mockito.never()).countRowsToDelete(anyLong(), anyLong());
    }

    @Test
    void testBegin_waitsForAnEarlierAttemptsDocumentsToBeRemoved() {
        Mockito.when(dao.beginAttempt(STREAM, TASK)).thenReturn(2);
        // This node removes those in one shard. Another node removes the rest a little later.
        Mockito.when(dao.countRowsToDelete(STREAM, TASK)).thenReturn(1, 1, 0);

        final PendingStreamTracker tracker = create(Duration.ofSeconds(10)).begin(STREAM, TASK);

        assertThat(tracker).isNotNull();
        Mockito.verify(recovery, Mockito.times(3)).removeMarkedDocuments();
        Mockito.verify(dao, Mockito.never()).failAttempt(anyLong(), anyLong());
    }

    @Test
    void testBegin_recoveryFailureIsNotFatal() {
        Mockito.when(dao.beginAttempt(STREAM, TASK)).thenReturn(1);
        Mockito.doThrow(new RuntimeException("shard busy")).when(recovery).removeMarkedDocuments();
        Mockito.when(dao.countRowsToDelete(STREAM, TASK)).thenReturn(1, 0);

        assertThat(create(Duration.ofSeconds(10)).begin(STREAM, TASK)).isNotNull();
    }

    @Test
    void testBegin_givesUpAndLeavesTheTaskToBeRunAgain() {
        Mockito.when(dao.beginAttempt(STREAM, TASK)).thenReturn(1);
        Mockito.when(dao.countRowsToDelete(STREAM, TASK)).thenReturn(1);

        assertThatThrownBy(() -> create(Duration.ofMillis(20)).begin(STREAM, TASK))
                .isInstanceOf(IndexException.class)
                .hasMessageContaining("stream " + STREAM);

        Mockito.verify(dao).failAttempt(STREAM, TASK);
    }

    private IndexShardPendingStreamsImpl create(final Duration deleteTimeout) {
        return new IndexShardPendingStreamsImpl(dao, () -> recovery, deleteTimeout, Duration.ofMillis(1));
    }
}
