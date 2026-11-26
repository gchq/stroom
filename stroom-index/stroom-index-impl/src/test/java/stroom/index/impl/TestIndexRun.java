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

import stroom.index.shared.AllPartition;
import stroom.index.shared.IndexShardKey;
import stroom.pipeline.state.StreamProcessorHolder;
import stroom.processor.api.RunIndexing;
import stroom.processor.shared.ProcessorTask;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

class TestIndexRun {

    private static final IndexShardKey KEY = new IndexShardKey("index", AllPartition.INSTANCE);

    private final IndexDocument document = new IndexDocument();
    private Indexer indexer;
    private IndexRunTracker tracker;
    private IndexRunTrackerFactory factory;
    private RunIndexing runIndexing;
    private StreamProcessorHolder holder;

    @BeforeEach
    void setUp() {
        indexer = Mockito.mock(Indexer.class);
        tracker = Mockito.mock(IndexRunTracker.class);
        factory = Mockito.mock(IndexRunTrackerFactory.class);
        Mockito.when(factory.create(any(), any())).thenReturn(tracker);
        runIndexing = new RunIndexing();
        holder = new StreamProcessorHolder();
        holder.setStreamProcessor(
                null,
                ProcessorTask.builder().id(10).metaId(100L).runCount(1).build(),
                null,
                runIndexing);
    }

    @Test
    void testNotARunOfATask_recordsNothing() {
        final IndexRun indexRun = IndexRun.start(new StreamProcessorHolder(), factory);

        indexRun.addDocument(indexer, KEY, document);

        Mockito.verify(indexer).addDocument(KEY, document, IndexRunTracker.NONE);
        Mockito.verify(factory, Mockito.never()).create(any(), any());
    }

    @Test
    void testFirstDocumentWritten_runIsHeld() {
        final IndexRun indexRun = IndexRun.start(holder, factory);
        Mockito.when(tracker.hasWritten()).thenReturn(false).thenReturn(true);

        indexRun.addDocument(indexer, KEY, document);
        assertThat(runIndexing.isHeld()).isFalse();
        indexRun.addDocument(indexer, KEY, document);

        assertThat(runIndexing.isHeld()).isTrue();
        Mockito.verify(indexer, Mockito.times(2)).addDocument(KEY, document, tracker);
    }

    @Test
    void testDocumentRefused_countedFault() {
        final IndexRun indexRun = IndexRun.start(holder, factory);
        Mockito.doThrow(new DocumentRefusedException("immense term", null))
                .when(indexer).addDocument(any(), any(), any());

        assertThatThrownBy(() -> indexRun.addDocument(indexer, KEY, document))
                .isInstanceOf(DocumentRefusedException.class);

        assertThat(runIndexing.getCouldNotIndexReason()).contains("immense term");
        assertThat(runIndexing.isCouldNotIndexCounted()).isTrue();
    }

    @Test
    void testIndexNotWritable_faultNotCounted() {
        final IndexRun indexRun = IndexRun.start(holder, factory);
        Mockito.doThrow(new RuntimeException("no volume")).when(indexer).addDocument(any(), any(), any());

        assertThatThrownBy(() -> indexRun.addDocument(indexer, KEY, document)).hasMessage("no volume");

        assertThat(runIndexing.getCouldNotIndexReason()).contains("no volume");
        assertThat(runIndexing.isCouldNotIndexCounted()).isFalse();
    }

    @Test
    void testRunStopped_saysNothingMore() {
        final IndexRun indexRun = IndexRun.start(holder, factory);
        Mockito.doThrow(new RunStoppedException("replaced")).when(indexer).addDocument(any(), any(), any());

        assertThatThrownBy(() -> indexRun.addDocument(indexer, KEY, document))
                .isInstanceOf(RunStoppedException.class);

        assertThat(runIndexing.getCouldNotIndexReason()).isEmpty();
    }
}
