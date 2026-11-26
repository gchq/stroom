/*
 * Copyright 2025 Crown Copyright
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

import stroom.index.mock.MockIndexShardCreator;
import stroom.index.mock.MockIndexShardDao;
import stroom.index.shared.AllPartition;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexException;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.index.shared.IndexShardKey;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolume.VolumeType;
import stroom.index.shared.IndexVolume.VolumeUseState;
import stroom.index.shared.LuceneVersionUtil;
import stroom.node.mock.MockNodeInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.verification.VerificationMode;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class TestActiveShards {

    @TempDir
    static Path tempDir;

    MockIndexShardDao indexShardDao = new MockIndexShardDao();
    MockNodeInfo nodeInfo = new MockNodeInfo();
    MockIndexShardCreator indexShardCreator = new MockIndexShardCreator(() -> tempDir, indexShardDao);
    IndexShardKey indexShardKey = new IndexShardKey(UUID.randomUUID().toString(), AllPartition.INSTANCE);

    @Mock
    IndexShardWriterCache indexShardWriterCache;
    @Mock
    IndexShardWriter myIndexShardWriter;
    @Mock
    IndexShardWriter anotherIndexShardWriter;

    @BeforeEach
    void setup() {
    }

    private static final IndexVolume.Builder FULL = IndexVolume.builder().bytesUsed(1000L).bytesTotal(1000L);
    private static final IndexVolume.Builder NOT_FULL = IndexVolume.builder().bytesUsed(0L).bytesTotal(1000L);

    public static Stream<Arguments> activeShards() {
        return Stream.of(
                // document will be added existing active shards with space
                Arguments.of(IndexShardStatus.NEW, NOT_FULL, VolumeUseState.ACTIVE, 0, 1),
                Arguments.of(IndexShardStatus.OPEN, NOT_FULL, VolumeUseState.ACTIVE, 0, 1),
                Arguments.of(IndexShardStatus.OPEN, NOT_FULL, VolumeUseState.ACTIVE, 0, 1),
                Arguments.of(IndexShardStatus.CLOSED, NOT_FULL, VolumeUseState.ACTIVE, 0, 1),
                Arguments.of(IndexShardStatus.CLOSING, NOT_FULL, VolumeUseState.ACTIVE, 0, 1)
        );
    }

    @ParameterizedTest
    @MethodSource("activeShards")
    void testAddDocumentToExistingShard(
            final IndexShardStatus status, final IndexVolume.Builder indexVolumeBuilder,
            final VolumeUseState volumeUseState, final int documentCount, final int maxDocsPerShard) {
        runTest(status, indexVolumeBuilder, volumeUseState, documentCount, maxDocsPerShard, times(1));
    }

    public static Stream<Arguments> notActiveShards() {
        return Stream.of(
                // document will not be added to existing corrupt/deleted, full or non-active shards
                Arguments.of(IndexShardStatus.CORRUPT, FULL, VolumeUseState.CLOSED, 1, 1),
                Arguments.of(IndexShardStatus.NEW, NOT_FULL, VolumeUseState.CLOSED, 0, 1),
                Arguments.of(IndexShardStatus.NEW, FULL, VolumeUseState.ACTIVE, 0, 1),
                Arguments.of(IndexShardStatus.NEW, NOT_FULL, VolumeUseState.ACTIVE, 1, 1),
                Arguments.of(IndexShardStatus.CORRUPT, NOT_FULL, VolumeUseState.ACTIVE, 0, 1),
                Arguments.of(IndexShardStatus.DELETED, NOT_FULL, VolumeUseState.ACTIVE, 0, 1),
                Arguments.of(IndexShardStatus.NEW, NOT_FULL, VolumeUseState.INACTIVE, 0, 1),
                Arguments.of(IndexShardStatus.NEW, NOT_FULL, VolumeUseState.CLOSED, 0, 1)
        );
    }

    @ParameterizedTest
    @MethodSource("notActiveShards")
    void testDoNotAddDocumentToExistingShard(
            final IndexShardStatus status, final IndexVolume.Builder indexVolumeBuilder,
            final VolumeUseState volumeUseState, final int documentCount, final int maxDocsPerShard) {
        runTest(status, indexVolumeBuilder, volumeUseState, documentCount, maxDocsPerShard, never());
    }

    @Test
    void testShardsOfOtherNodesOnASharedVolume_onlyAsManyAsNeededAreTaken() {
        // Closed shards with room, left on a shared volume by a node that has gone.
        final IndexVolume sharedVolume = IndexVolume.builder()
                .bytesUsed(0L)
                .bytesTotal(1000L)
                .state(VolumeUseState.ACTIVE)
                .volumeType(VolumeType.SHARED)
                .build();
        final Set<Long> otherNodesShardIds = new HashSet<>();
        for (int i = 0; i < 5; i++) {
            final IndexShard indexShard = indexShardDao.create(
                    indexShardKey,
                    sharedVolume,
                    "otherNode",
                    LuceneVersionUtil.CURRENT_LUCENE_VERSION.getDisplayValue());
            indexShardDao.setStatus(indexShard.getId(), IndexShardStatus.CLOSED);
            otherNodesShardIds.add(indexShard.getId());
        }
        Mockito.when(indexShardWriterCache.getOrOpenWriter(anyLong())).thenReturn(anotherIndexShardWriter);

        final ActiveShards activeShards = new ActiveShards(nodeInfo, indexShardWriterCache, indexShardDao,
                indexShardCreator, 2, 100, indexShardKey);
        for (int i = 0; i < 20; i++) {
            activeShards.addDocument(new IndexDocument());
        }

        // Two shards were wanted, so two of the five are written to. The rest are left for other nodes,
        // and no new shard is created.
        final ArgumentCaptor<Long> shardIdCaptor = ArgumentCaptor.forClass(Long.class);
        Mockito.verify(indexShardWriterCache, times(20)).getOrOpenWriter(shardIdCaptor.capture());
        final Set<Long> usedShardIds = new HashSet<>(shardIdCaptor.getAllValues());
        assertThat(usedShardIds).hasSize(2);
        assertThat(otherNodesShardIds).containsAll(usedShardIds);
    }

    @Test
    void testWriterFoundToBeUnusable_isClosedAndItsShardOpenedAgain() {
        // Lucene closed the first writer after an error, so it fails every document it is given.
        Mockito.doThrow(new IllegalStateException("this IndexWriter is closed"))
                .when(myIndexShardWriter).addDocument(any());
        Mockito.when(myIndexShardWriter.isUnusable()).thenReturn(true);
        Mockito.when(indexShardWriterCache.getOrOpenWriter(anyLong()))
                .thenReturn(myIndexShardWriter)
                .thenReturn(anotherIndexShardWriter);
        final ActiveShards activeShards = createActiveShards();

        activeShards.addDocument(new IndexDocument());

        Mockito.verify(indexShardWriterCache).close(myIndexShardWriter);
        Mockito.verify(anotherIndexShardWriter).addDocument(any());
        // There is nothing wrong with the shard, so it is used again and no other is created.
        assertThat(indexShardDao.find(FindIndexShardCriteria.matchAll()).size()).isOne();
    }

    @Test
    void testDocumentBreaksAUsableWriter_isNotTriedAgain() {
        // Lucene closes its writer after an error it can't recover from, here caused by the document.
        final IllegalStateException tragicEvent = new IllegalStateException("tragic event");
        Mockito.when(myIndexShardWriter.isUnusable()).thenReturn(true);
        Mockito.when(myIndexShardWriter.isBrokenBy(tragicEvent)).thenReturn(true);
        Mockito.doThrow(tragicEvent).when(myIndexShardWriter).addDocument(any());
        Mockito.when(indexShardWriterCache.getOrOpenWriter(anyLong())).thenReturn(myIndexShardWriter);
        final ActiveShards activeShards = createActiveShards();

        assertThatThrownBy(() -> activeShards.addDocument(new IndexDocument()))
                .isInstanceOf(IndexException.class)
                .hasMessageContaining("tragic event");

        // Tried in one writer only. Tried in others it would break each of them in turn.
        Mockito.verify(myIndexShardWriter, times(1)).addDocument(any());
        Mockito.verify(indexShardWriterCache, times(1)).getOrOpenWriter(anyLong());
        Mockito.verify(indexShardWriterCache).close(myIndexShardWriter);
        assertThat(indexShardDao.find(FindIndexShardCriteria.matchAll()).size()).isOne();
    }

    @Test
    void testWriterClosedByAnotherThreadWhileAdding_documentIsAddedElsewhere() {
        // Usable until the document is given to it, when another thread closes it, e.g. as its lease was
        // lost. The document did not break it.
        final AtomicBoolean closed = new AtomicBoolean();
        Mockito.when(myIndexShardWriter.isUnusable()).thenAnswer(invocation -> closed.get());
        Mockito.doAnswer(invocation -> {
            closed.set(true);
            throw new IllegalStateException("this IndexWriter is closed");
        }).when(myIndexShardWriter).addDocument(any());
        Mockito.when(indexShardWriterCache.getOrOpenWriter(anyLong()))
                .thenReturn(myIndexShardWriter)
                .thenReturn(anotherIndexShardWriter);
        final ActiveShards activeShards = createActiveShards();

        activeShards.addDocument(new IndexDocument());

        Mockito.verify(indexShardWriterCache).close(myIndexShardWriter);
        Mockito.verify(anotherIndexShardWriter).addDocument(any());
    }

    @Test
    void testDocumentRefusedByEveryShard_atMostOneShardIsMadeForIt() {
        // Lucene refuses the document, e.g. for an immense term, but its writer is fine.
        Mockito.doThrow(new IllegalArgumentException("immense term"))
                .when(myIndexShardWriter).addDocument(any());
        Mockito.when(indexShardWriterCache.getOrOpenWriter(anyLong())).thenReturn(myIndexShardWriter);
        final ActiveShards activeShards = createActiveShards();

        assertThatThrownBy(() -> activeShards.addDocument(new IndexDocument()))
                .isInstanceOf(DocumentRefusedException.class)
                .hasMessageContaining("immense term");

        // Even a new, empty shard refused it, so no more are made, and the writers stay in use.
        assertThat(indexShardDao.find(FindIndexShardCriteria.matchAll()).size()).isEqualTo(2);
        Mockito.verify(indexShardWriterCache, never()).close(any());
    }

    @Test
    void testDocumentRefusedByAShardWhoseFieldTypeHasChanged_goesToANewShard() {
        // The existing shard holds a field of a type that has since changed.
        Mockito.doThrow(new IllegalArgumentException("Inconsistency of field data structures"))
                .when(myIndexShardWriter).addDocument(any());
        Mockito.when(indexShardWriterCache.getOrOpenWriter(anyLong()))
                .thenAnswer(invocation -> invocation.<Long>getArgument(0) == 1L
                        ? myIndexShardWriter
                        : anotherIndexShardWriter);
        final ActiveShards activeShards = createActiveShards();

        activeShards.addDocument(new IndexDocument());

        Mockito.verify(anotherIndexShardWriter).addDocument(any());
        assertThat(indexShardDao.find(FindIndexShardCriteria.matchAll()).size()).isEqualTo(2);
    }

    @Test
    void testWriterClosing_shardStaysInUse() {
        Mockito.doThrow(new WriterClosingException("closing")).when(myIndexShardWriter).addDocument(any());
        Mockito.when(indexShardWriterCache.getOrOpenWriter(anyLong()))
                .thenReturn(myIndexShardWriter)
                .thenReturn(anotherIndexShardWriter);
        final ActiveShards activeShards = createActiveShards();

        activeShards.addDocument(new IndexDocument());

        // Added to the same shard by its new writer, and no shard is made.
        Mockito.verify(anotherIndexShardWriter).addDocument(any());
        assertThat(indexShardDao.find(FindIndexShardCriteria.matchAll()).size()).isOne();
    }

    @Test
    void testRunTrackingFails_shardStaysInUse() {
        Mockito.when(indexShardWriterCache.getOrOpenWriter(anyLong())).thenReturn(myIndexShardWriter);
        final IndexRunTracker tracker = Mockito.mock(IndexRunTracker.class);
        Mockito.doThrow(new RuntimeException("lock wait timeout")).when(tracker).beforeAdd(anyLong(), any());
        final ActiveShards activeShards = createActiveShards();

        assertThatThrownBy(() -> activeShards.addDocument(new IndexDocument(), tracker))
                .isInstanceOf(IndexRunTrackingException.class)
                .hasMessageContaining("lock wait timeout");

        Mockito.verify(myIndexShardWriter, never()).addDocument(any());
        assertThat(indexShardDao.find(FindIndexShardCriteria.matchAll()).size()).isOne();
    }

    @Test
    void testRefusedByAnOldShardOnly_isNotTheDocumentsFault() {
        // The old shard holds a field of a type that has since changed, and no new shard can be opened.
        Mockito.doThrow(new IllegalArgumentException("Inconsistency of field data structures"))
                .when(myIndexShardWriter).addDocument(any());
        Mockito.when(indexShardWriterCache.getOrOpenWriter(anyLong())).thenAnswer(invocation -> {
            if (invocation.<Long>getArgument(0) == 1L) {
                return myIndexShardWriter;
            }
            throw new IndexException("Unable to claim the lease");
        });
        final ActiveShards activeShards = createActiveShards();

        assertThatThrownBy(() -> activeShards.addDocument(new IndexDocument()))
                .isInstanceOf(IndexException.class)
                .isNotInstanceOf(DocumentRefusedException.class);
    }

    @Test
    void testRunTracker_toldOfEachDocumentAroundTheAdd() {
        Mockito.when(myIndexShardWriter.getWriterId()).thenReturn("writer");
        Mockito.when(indexShardWriterCache.getOrOpenWriter(anyLong())).thenReturn(myIndexShardWriter);
        final IndexRunTracker tracker = Mockito.mock(IndexRunTracker.class);
        final ActiveShards activeShards = createActiveShards();

        activeShards.addDocument(new IndexDocument(), tracker);

        final InOrder inOrder = Mockito.inOrder(tracker, myIndexShardWriter);
        inOrder.verify(tracker).beforeAdd(anyLong(), Mockito.eq(myIndexShardWriter));
        inOrder.verify(myIndexShardWriter).addDocument(any());
        inOrder.verify(tracker).added(anyLong(), Mockito.eq("writer"));
    }

    @Test
    void testRunTracker_shardFull_toldTheAddFailed() {
        Mockito.when(myIndexShardWriter.getWriterId()).thenReturn("writer");
        Mockito.doThrow(new ShardFullException("full")).when(myIndexShardWriter).addDocument(any());
        Mockito.when(indexShardWriterCache.getOrOpenWriter(anyLong()))
                .thenReturn(myIndexShardWriter)
                .thenReturn(anotherIndexShardWriter);
        final IndexRunTracker tracker = Mockito.mock(IndexRunTracker.class);
        final ActiveShards activeShards = createActiveShards();

        activeShards.addDocument(new IndexDocument(), tracker);

        Mockito.verify(tracker).addFailed(anyLong(), Mockito.eq("writer"));
        Mockito.verify(anotherIndexShardWriter).addDocument(any());
    }

    @Test
    void testRunTracker_runStopped_documentIsNotAdded() {
        Mockito.when(indexShardWriterCache.getOrOpenWriter(anyLong())).thenReturn(myIndexShardWriter);
        final IndexRunTracker tracker = Mockito.mock(IndexRunTracker.class);
        Mockito.doThrow(new RunStoppedException("replaced")).when(tracker).beforeAdd(anyLong(), any());
        final ActiveShards activeShards = createActiveShards();

        assertThatThrownBy(() -> activeShards.addDocument(new IndexDocument(), tracker))
                .isInstanceOf(RunStoppedException.class);

        Mockito.verify(myIndexShardWriter, never()).addDocument(any());
        Mockito.verify(indexShardWriterCache, never()).close(any());
    }

    private ActiveShards createActiveShards() {
        return new ActiveShards(nodeInfo, indexShardWriterCache, indexShardDao,
                indexShardCreator, 1, 100, indexShardKey);
    }

    void runTest(
            final IndexShardStatus status,
            final IndexVolume.Builder indexVolumeBuilder,
            final VolumeUseState volumeUseState,
            final int documentCount,
            final int maxDocsPerShard,
            final VerificationMode isDocumentAdded) {
        // given we have an existing shard
        final IndexVolume indexVolume = indexVolumeBuilder.state(volumeUseState).build();
        final IndexShard myIndexShard = indexShardDao.create(
                indexShardKey,
                indexVolume,
                nodeInfo.getThisNodeName(),
                LuceneVersionUtil.CURRENT_LUCENE_VERSION.getDisplayValue());
        indexShardDao.setStatus(myIndexShard.getId(), status);
        indexShardDao.update(myIndexShard.getId(), documentCount, 0L, 0L, 0L, null);

        Mockito.lenient().when(indexShardWriterCache.getOrOpenWriter(anyLong())).thenReturn(anotherIndexShardWriter);
        Mockito.lenient().when(indexShardWriterCache.getOrOpenWriter(myIndexShard.getId()))
                .thenReturn(myIndexShardWriter);

        // when we get the active shards
        final ActiveShards activeShards = new ActiveShards(nodeInfo, indexShardWriterCache, indexShardDao,
                indexShardCreator, 1, maxDocsPerShard, indexShardKey);
        // and add a document
        activeShards.addDocument(new IndexDocument());

        // then verify whether the document is added to the associated shard writer or not
        Mockito.verify(myIndexShardWriter, isDocumentAdded).addDocument(any());
    }
}
