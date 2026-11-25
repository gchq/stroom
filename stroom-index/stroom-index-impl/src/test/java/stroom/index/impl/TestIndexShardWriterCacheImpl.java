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

import stroom.cache.api.CacheManager;
import stroom.cache.api.StroomCache;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolume.VolumeType;
import stroom.index.shared.LuceneIndexDoc;
import stroom.index.shared.LuceneVersionUtil;
import stroom.node.mock.MockNodeInfo;
import stroom.security.mock.MockSecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.SimpleTaskContextFactory;
import stroom.util.io.PathCreator;
import stroom.util.io.SimplePathCreator;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;

class TestIndexShardWriterCacheImpl {

    private static final long SHARD = 1;

    @Mock
    private IndexShardDao indexShardDao;
    @Mock
    private LuceneIndexDocCache luceneIndexDocCache;
    @Mock
    private LuceneProvider luceneProvider;
    @Mock
    private ExecutorProvider executorProvider;
    @Mock
    private CacheManager cacheManager;
    @Mock
    private StroomCache<Object, Object> cache;
    @Mock
    private IndexShardWriter firstWriter;
    @Mock
    private IndexShardWriter secondWriter;

    private IndexShardWriterCacheImpl writerCache;

    @BeforeEach
    void setUp(@TempDir final Path tempDir) throws Exception {
        MockitoAnnotations.openMocks(this).close();
        final PathCreator pathCreator = new SimplePathCreator(() -> tempDir, () -> tempDir);

        Mockito.when(indexShardDao.fetch(anyLong())).thenReturn(Optional.of(createShard(VolumeType.SHARED)));
        Mockito.when(indexShardDao.setStatus(anyLong(), any())).thenReturn(true);
        Mockito.when(luceneIndexDocCache.get(any())).thenReturn(LuceneIndexDoc
                .builder()
                .uuid("index")
                .name("index")
                .build());
        Mockito.when(luceneProvider.getLuceneVersion()).thenReturn(LuceneVersionUtil.CURRENT_LUCENE_VERSION);
        Mockito.when(firstWriter.getIndexShardId()).thenReturn(SHARD);
        Mockito.when(secondWriter.getIndexShardId()).thenReturn(SHARD);
        // Always asks for a new writer, as when the old one has been evicted but is not closed yet.
        Mockito.when(cache.get(any(), any())).thenAnswer(invocation ->
                invocation.<Function<Object, Object>>getArgument(1).apply(invocation.getArgument(0)));
        Mockito.when(cacheManager.create(anyString(), any(), any())).thenReturn(cache);

        final LuceneProviderFactory luceneProviderFactory = new LuceneProviderFactory(Set.of(luceneProvider));
        writerCache = new IndexShardWriterCacheImpl(
                new MockNodeInfo(),
                IndexWriterConfig::new,
                luceneIndexDocCache,
                indexShardDao,
                executorProvider,
                new SimpleTaskContextFactory(),
                MockSecurityContext.getInstance(),
                pathCreator,
                () -> luceneProviderFactory,
                cacheManager);
    }

    @Test
    void testReopenFails_oldWriterIsOnlyClosedOnce() {
        Mockito.when(luceneProvider.createIndexShardWriter(any(), anyInt()))
                .thenReturn(firstWriter)
                .thenThrow(new RuntimeException("no volume"))
                .thenReturn(secondWriter);
        assertThat(writerCache.getOrOpenWriter(SHARD)).isSameAs(firstWriter);

        assertThatThrownBy(() -> writerCache.getOrOpenWriter(SHARD)).hasMessage("no volume");
        assertThat(writerCache.getOrOpenWriter(SHARD)).isSameAs(secondWriter);

        Mockito.verify(firstWriter, Mockito.times(1)).close();
        Mockito.verify(secondWriter, Mockito.never()).close();
    }

    @Test
    void testReopenFails_shardCanStillBePruned() {
        Mockito.when(luceneProvider.createIndexShardWriter(any(), anyInt()))
                .thenReturn(firstWriter)
                .thenThrow(new RuntimeException("no volume"))
                .thenReturn(secondWriter);
        Mockito.when(indexShardDao.findShardsWithCommitsToPrune(anyString(), anyInt()))
                .thenReturn(List.of(createShard(VolumeType.SHARED)));
        writerCache.getOrOpenWriter(SHARD);
        assertThatThrownBy(() -> writerCache.getOrOpenWriter(SHARD)).hasMessage("no volume");

        writerCache.pruneReplacedCommits();

        Mockito.verify(secondWriter).pruneReplacedCommits();
        Mockito.verify(secondWriter).close();
        Mockito.verify(indexShardDao).clearPruneCommitsTime(SHARD);
    }

    @Test
    void testPrune_failureIsTriedAgainLater() {
        Mockito.when(luceneProvider.createIndexShardWriter(any(), anyInt())).thenReturn(firstWriter);
        Mockito.when(indexShardDao.findShardsWithCommitsToPrune(anyString(), anyInt()))
                .thenReturn(List.of(createShard(VolumeType.SHARED)));
        Mockito.doThrow(new RuntimeException("cannot delete")).when(firstWriter).pruneReplacedCommits();

        writerCache.pruneReplacedCommits();

        Mockito.verify(firstWriter).close();
        Mockito.verify(indexShardDao, Mockito.never()).clearPruneCommitsTime(anyLong());
        Mockito.verify(indexShardDao).reset(SHARD, getRetentionMs());
    }

    @Test
    void testPrune_shardThatCanNoLongerBeOpenedIsLeftAlone() {
        // It has been deleted since it was found.
        Mockito.when(indexShardDao.setStatus(SHARD, IndexShardStatus.OPENING)).thenReturn(false);
        Mockito.when(indexShardDao.findShardsWithCommitsToPrune(anyString(), anyInt()))
                .thenReturn(List.of(createShard(VolumeType.SHARED)));

        writerCache.pruneReplacedCommits();

        Mockito.verify(luceneProvider, Mockito.never()).createIndexShardWriter(any(), anyInt());
        Mockito.verify(indexShardDao, Mockito.never()).reset(anyLong(), any());
    }

    @Test
    void testStartup_shardLeftOpenOnASharedVolumeHasItsReplacedCommitsKeptThenPruned() {
        // Its writer never closed, so nothing recorded what it was keeping for readers on other nodes. A
        // time recorded by an earlier close must not be used either.
        Mockito.when(indexShardDao.find(any()))
                .thenReturn(ResultPage.createUnboundedList(List.of(createShard(VolumeType.SHARED))));

        writerCache.startup();

        Mockito.verify(indexShardDao).reset(SHARD, getRetentionMs());
    }

    @Test
    void testStartup_shardLeftOpenOnALocalVolumeHasNothingToPrune() {
        Mockito.when(indexShardDao.find(any()))
                .thenReturn(ResultPage.createUnboundedList(List.of(createShard(VolumeType.LOCAL))));

        writerCache.startup();

        Mockito.verify(indexShardDao).reset(SHARD, null);
    }

    private static long getRetentionMs() {
        return new IndexWriterConfig().getReplacedCommitRetention().toMillis();
    }

    private static IndexShard createShard(final VolumeType volumeType) {
        return IndexShard
                .builder()
                .id(SHARD)
                .indexUuid("index")
                .partition("all")
                .status(IndexShardStatus.CLOSED)
                .volume(IndexVolume.builder().path("volume").volumeType(volumeType).build())
                .indexVersion(LuceneVersionUtil.getCurrentVersion())
                .build();
    }
}
