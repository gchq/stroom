package stroom.index.impl;

import stroom.index.mock.MockIndexShardCreator;
import stroom.index.mock.MockIndexShardDao;
import stroom.index.shared.AllPartition;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.index.shared.IndexShardKey;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolume.VolumeUseState;
import stroom.index.shared.LuceneVersionUtil;
import stroom.node.mock.MockNodeInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.verification.VerificationMode;

import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;

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

    void runTest(
            final IndexShardStatus status, final IndexVolume.Builder indexVolumeBuilder,
            final VolumeUseState volumeUseState, final int documentCount, final int maxDocsPerShard,
            final VerificationMode isDocumentAdded) {
        // given we have an existing shard
        final IndexVolume indexVolume = indexVolumeBuilder.state(volumeUseState).build();
        final IndexShard myIndexShard = indexShardDao.create(
                indexShardKey,
                indexVolume,
                nodeInfo.getThisNodeName(),
                LuceneVersionUtil.CURRENT_LUCENE_VERSION.getDisplayValue());
        myIndexShard.setStatus(status);
        myIndexShard.setDocumentCount(documentCount);

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
