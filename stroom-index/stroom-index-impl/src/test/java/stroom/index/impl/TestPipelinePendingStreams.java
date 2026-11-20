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
import stroom.meta.shared.Meta;
import stroom.pipeline.state.MetaHolder;
import stroom.pipeline.state.StreamProcessorHolder;
import stroom.processor.shared.ProcessorTask;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;

class TestPipelinePendingStreams {

    private static final long STREAM = 100;
    private static final long TASK = 7;

    private IndexShardPendingStreams indexShardPendingStreams;
    private IndexShardPendingStreamDao dao;
    private MetaHolder metaHolder;
    private StreamProcessorHolder streamProcessorHolder;
    private PipelinePendingStreams pipelinePendingStreams;

    @BeforeEach
    void setUp() {
        dao = Mockito.mock(IndexShardPendingStreamDao.class);
        Mockito.when(dao.endAttempt(STREAM, TASK)).thenReturn(true);
        indexShardPendingStreams = Mockito.mock(IndexShardPendingStreams.class);
        Mockito.when(indexShardPendingStreams.begin(STREAM, TASK))
                .thenAnswer(invocation -> new PendingStreamTracker(dao, STREAM, TASK));

        final Meta meta = Mockito.mock(Meta.class);
        Mockito.when(meta.getId()).thenReturn(STREAM);
        metaHolder = Mockito.mock(MetaHolder.class);
        Mockito.when(metaHolder.getMeta()).thenReturn(meta);

        final ProcessorTask task = Mockito.mock(ProcessorTask.class);
        Mockito.when(task.getId()).thenReturn(TASK);
        streamProcessorHolder = new StreamProcessorHolder();
        streamProcessorHolder.setStreamProcessor(null, task);

        pipelinePendingStreams = new PipelinePendingStreams(
                indexShardPendingStreams, metaHolder, streamProcessorHolder);
    }

    @Test
    void testOneFilter() {
        final PendingStreamTracker tracker = pipelinePendingStreams.start();

        assertThat(tracker).isNotNull();
        assertThat(tracker.getStreamId()).isEqualTo(STREAM);
        assertThat(pipelinePendingStreams.end()).isTrue();
        Mockito.verify(dao).endAttempt(STREAM, TASK);
    }

    @Test
    void testTwoFilters_shareOneRecordThatEndsWithTheLast() {
        final PendingStreamTracker tracker1 = pipelinePendingStreams.start();
        final PendingStreamTracker tracker2 = pipelinePendingStreams.start();

        assertThat(tracker2).isSameAs(tracker1);
        Mockito.verify(indexShardPendingStreams, Mockito.times(1)).begin(STREAM, TASK);

        // The first filter to finish does not end it, as the other may still be adding documents.
        assertThat(pipelinePendingStreams.end()).isTrue();
        Mockito.verify(dao, Mockito.never()).endAttempt(anyLong(), anyLong());

        assertThat(pipelinePendingStreams.end()).isTrue();
        Mockito.verify(dao).endAttempt(STREAM, TASK);
    }

    @Test
    void testEnd_attemptFailed() {
        Mockito.when(dao.endAttempt(STREAM, TASK)).thenReturn(false);
        pipelinePendingStreams.start();

        assertThat(pipelinePendingStreams.end()).isFalse();
    }

    @Test
    void testFail() {
        pipelinePendingStreams.start();

        pipelinePendingStreams.fail();

        Mockito.verify(dao).abandonAttempt(STREAM, TASK);
        Mockito.verify(dao, Mockito.never()).endAttempt(anyLong(), anyLong());

        // The filter that failed has reported it, so ending has nothing more to say, but it must still
        // tell the record that the task has stopped adding documents.
        assertThat(pipelinePendingStreams.end()).isTrue();
        Mockito.verify(dao).endAttempt(STREAM, TASK);
    }

    @Test
    void testNoProcessorTask_nothingIsRecorded() {
        // As when a pipeline is run by something other than a processor task, which nothing could run again.
        streamProcessorHolder.setStreamProcessor(null, null);

        assertThat(pipelinePendingStreams.start()).isNull();
        assertThat(pipelinePendingStreams.end()).isTrue();
        Mockito.verifyNoInteractions(indexShardPendingStreams);
    }

    @Test
    void testNoStream_nothingIsRecorded() {
        Mockito.when(metaHolder.getMeta()).thenReturn(null);

        assertThat(pipelinePendingStreams.start()).isNull();
        assertThat(pipelinePendingStreams.end()).isTrue();
        Mockito.verifyNoInteractions(indexShardPendingStreams);
    }

    @Test
    void testBeginFails_isNotTriedAgainByTheNextFilter() {
        Mockito.when(indexShardPendingStreams.begin(STREAM, TASK)).thenThrow(new IndexException("not removed"));

        assertThatThrownBy(() -> pipelinePendingStreams.start()).isInstanceOf(IndexException.class);

        assertThat(pipelinePendingStreams.start()).isNull();
        Mockito.verify(indexShardPendingStreams, Mockito.times(1)).begin(STREAM, TASK);
    }
}
