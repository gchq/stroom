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
import stroom.cache.impl.CacheManagerImpl;
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
import stroom.util.cache.CacheConfig;
import stroom.util.io.PathCreator;
import stroom.util.io.SimplePathCreator;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.LongConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;

class TestIndexShardWriterCacheImpl {

    private static final long SHARD = 1;
    private static final String INSTANCE_ID = "instance";

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
    @Mock
    private IndexShardLeases indexShardLeases;
    @Mock
    private IndexShardCommitRecorder commitRecorder;

    private IndexShardWriterCacheImpl writerCache;

    @BeforeEach
    void setUp(@TempDir final Path tempDir) throws Exception {
        MockitoAnnotations.openMocks(this).close();
        final PathCreator pathCreator = new SimplePathCreator(() -> tempDir, () -> tempDir);

        Mockito.when(indexShardDao.fetch(anyLong())).thenReturn(Optional.of(createShard(VolumeType.SHARED)));
        Mockito.when(indexShardDao.setStatus(anyLong(), any())).thenReturn(true);
        Mockito.when(indexShardDao.markOpen(anyLong(), any())).thenReturn(true);
        Mockito.when(indexShardDao.closeWriter(anyLong(), any(), any(), any(), anyBoolean()))
                .thenReturn(Optional.of(Set.of()));
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
        Mockito.when(indexShardLeases.claim(anyLong())).thenReturn(true);
        Mockito.when(indexShardLeases.leaseCheck(anyLong())).thenReturn(() -> true);
        Mockito.when(indexShardLeases.getInstanceId()).thenReturn(INSTANCE_ID);

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
                cacheManager,
                indexShardLeases,
                commitRecorder);
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
        Mockito.when(indexShardDao.findShardsWithCommitsToPrune(anyInt()))
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
        Mockito.when(indexShardDao.findShardsWithCommitsToPrune(anyInt()))
                .thenReturn(List.of(createShard(VolumeType.SHARED)));
        Mockito.doThrow(new RuntimeException("cannot delete")).when(firstWriter).pruneReplacedCommits();

        writerCache.pruneReplacedCommits();

        Mockito.verify(firstWriter).close();
        Mockito.verify(indexShardDao, Mockito.never()).clearPruneCommitsTime(anyLong());
        Mockito.verify(indexShardLeases).release(SHARD, getRetentionMs());
    }

    @Test
    void testPrune_shardThatCanNoLongerBeClaimedIsLeftAlone() {
        // Another node has opened it, or it has been deleted, since it was found.
        Mockito.when(indexShardLeases.claim(SHARD)).thenReturn(false);
        Mockito.when(indexShardDao.findShardsWithCommitsToPrune(anyInt()))
                .thenReturn(List.of(createShard(VolumeType.SHARED)));

        writerCache.pruneReplacedCommits();

        Mockito.verify(luceneProvider, Mockito.never()).createIndexShardWriter(any(), anyInt());
        Mockito.verify(indexShardLeases, Mockito.never()).release(anyLong(), any());
    }

    @Test
    void testStartup_shardLeftOpenOnASharedVolumeHasItsReplacedCommitsKeptThenPruned() {
        // Its writer never closed, so nothing recorded what it was keeping for readers on other nodes. A
        // time recorded by an earlier close must not be used either.
        Mockito.when(indexShardDao.find(any()))
                .thenReturn(ResultPage.createUnboundedList(List.of(createShard(VolumeType.SHARED))));

        writerCache.startup();

        Mockito.verify(indexShardDao).resetAfterRestart(
                SHARD, new MockNodeInfo().getThisNodeName(), INSTANCE_ID, getRetentionMs());
        // Also the leases of the shards it had open that were deleted meanwhile.
        Mockito.verify(indexShardDao).clearLeasesOfUnusableShardsAfterRestart(
                new MockNodeInfo().getThisNodeName(), INSTANCE_ID);
    }

    @Test
    void testStartup_shardLeftOpenOnALocalVolumeHasNothingToPrune() {
        Mockito.when(indexShardDao.find(any()))
                .thenReturn(ResultPage.createUnboundedList(List.of(createShard(VolumeType.LOCAL))));

        writerCache.startup();

        Mockito.verify(indexShardDao).resetAfterRestart(
                SHARD, new MockNodeInfo().getThisNodeName(), INSTANCE_ID, null);
    }

    @Test
    void testOpenNewShard_deleteFails_leaseIsStillGivenUp() {
        Mockito.when(indexShardDao.fetch(anyLong())).thenReturn(Optional.of(createShard(VolumeType.SHARED)
                .copy()
                .status(IndexShardStatus.NEW)
                .build()));
        Mockito.when(luceneProvider.createIndexShardWriter(any(), anyInt()))
                .thenThrow(new RuntimeException("no volume"));
        Mockito.doThrow(new RuntimeException("no database")).when(indexShardDao).logicalDelete(SHARD, INSTANCE_ID);

        assertThatThrownBy(() -> writerCache.getOrOpenWriter(SHARD)).hasMessage("no volume");

        // Kept, the lease would leave the shard unusable by any node until this process stopped.
        Mockito.verify(indexShardLeases).release(SHARD, null);
    }

    @Test
    void testDelete_shardAnotherNodeHoldsIsLeftOpenThere() {
        Mockito.when(indexShardDao.logicalDelete(SHARD, INSTANCE_ID)).thenReturn(false);
        Mockito.when(indexShardDao.isLeased(SHARD)).thenReturn(true);

        writerCache.delete(SHARD);

        Mockito.verify(cache, Mockito.never()).invalidate(any());
    }

    @Test
    void testDelete_closesThisNodesWriter() {
        Mockito.when(indexShardDao.logicalDelete(SHARD, INSTANCE_ID)).thenReturn(true);

        writerCache.delete(SHARD);

        Mockito.verify(cache).invalidate(SHARD);
    }

    private static long getRetentionMs() {
        return new IndexWriterConfig().getReplacedCommitRetention().toMillis();
    }

    private static IndexShard createShard(final VolumeType volumeType) {
        return createShard(SHARD, volumeType);
    }

    private static IndexShard createShard(final long id, final VolumeType volumeType) {
        return IndexShard
                .builder()
                .id(id)
                .indexUuid("index")
                .partition("all")
                .status(IndexShardStatus.CLOSED)
                .volume(IndexVolume.builder().path("volume").volumeType(volumeType).build())
                .indexVersion(LuceneVersionUtil.getCurrentVersion())
                .build();
    }


    // --------------------------------------------------------------------------------


    /// Tests against a real cache, whose closes and flushes are queued until a test runs them.
    @Nested
    class WithRealCache {

        private final LinkedBlockingQueue<Runnable> queuedTasks = new LinkedBlockingQueue<>();
        // Every writer that has been opened, in order.
        private final List<IndexShardWriter> writers = new ArrayList<>();
        // The writers that have been found to be unusable.
        private final Set<IndexShardWriter> unusable = ConcurrentHashMap.newKeySet();
        private final AtomicReference<IndexWriterConfig> config = new AtomicReference<>(new IndexWriterConfig());

        private CacheManagerImpl realCacheManager;
        private volatile VolumeType volumeType;
        private IndexShardWriterCacheImpl realWriterCache;
        private LongConsumer lostLeaseHandler;

        @BeforeEach
        void setUp() {
            realCacheManager = new CacheManagerImpl();
            volumeType = VolumeType.LOCAL;
            Mockito.when(indexShardDao.fetch(anyLong())).thenAnswer(invocation ->
                    Optional.of(createShard(invocation.getArgument(0), volumeType)));
            Mockito.when(luceneProvider.createIndexShardWriter(any(), anyInt())).thenAnswer(invocation ->
                    createWriter(invocation.<IndexShard>getArgument(0).getId()));
            Mockito.when(executorProvider.get(any())).thenReturn(queuedTasks::add);

            realWriterCache = new IndexShardWriterCacheImpl(
                    new MockNodeInfo(),
                    config::get,
                    luceneIndexDocCache,
                    indexShardDao,
                    executorProvider,
                    new SimpleTaskContextFactory(),
                    MockSecurityContext.getInstance(),
                    null,
                    () -> new LuceneProviderFactory(Set.of(luceneProvider)),
                    realCacheManager,
                    indexShardLeases,
                    commitRecorder);

            final ArgumentCaptor<LongConsumer> handlerCaptor = ArgumentCaptor.forClass(LongConsumer.class);
            Mockito.verify(indexShardLeases, Mockito.atLeastOnce()).setLostLeaseHandler(handlerCaptor.capture());
            lostLeaseHandler = handlerCaptor.getValue();
        }

        @AfterEach
        void tearDown() {
            realCacheManager.close();
        }

        @Test
        void testClose() throws InterruptedException {
            final IndexShardWriter writer = realWriterCache.getOrOpenWriter(SHARD);

            realWriterCache.close(writer);
            runQueuedTask();

            Mockito.verify(writer).close();
            assertThat(realWriterCache.getIfPresent(SHARD)).isEmpty();
            verifyClosedInDatabase();
        }

        @Test
        void testClose_markingAsClosingFails_writerIsStillClosed() throws InterruptedException {
            final IndexShardWriter writer = realWriterCache.getOrOpenWriter(SHARD);
            Mockito.when(indexShardDao.setStatus(SHARD, IndexShardStatus.CLOSING))
                    .thenThrow(new RuntimeException("no database"));

            realWriterCache.close(writer);
            runQueuedTask();

            // Left open, it would keep Lucene's lock while the shard was recorded as closed.
            Mockito.verify(writer).close();
            verifyClosedInDatabase();
        }

        @Test
        void testClose_shardOpenedAgainFirst_newWriterIsLeftOpen() throws InterruptedException {
            final IndexShardWriter oldWriter = realWriterCache.getOrOpenWriter(SHARD);
            realWriterCache.close(oldWriter);
            final Runnable queuedClose = takeQueuedTask();

            // Opening the shard again closes the old writer without waiting for the close that is queued.
            final IndexShardWriter newWriter = realWriterCache.getOrOpenWriter(SHARD);
            assertThat(newWriter).isNotSameAs(oldWriter);
            Mockito.verify(oldWriter).close();

            queuedClose.run();

            Mockito.verify(newWriter, Mockito.never()).close();
            Mockito.verify(oldWriter).close();
            assertThat(realWriterCache.getOrOpenWriter(SHARD)).isSameAs(newWriter);
            verifyClosedInDatabase();

            // The new writer can still be closed in its turn.
            realWriterCache.close(newWriter);
            runQueuedTask();
            Mockito.verify(newWriter).close();
        }

        @Test
        void testClose_writerAlreadyReplaced_newWriterStaysInUse() throws InterruptedException {
            // Two threads find that the same writer can't be used.
            final IndexShardWriter oldWriter = realWriterCache.getOrOpenWriter(SHARD);
            realWriterCache.close(oldWriter);
            runQueuedTask();
            final IndexShardWriter newWriter = realWriterCache.getOrOpenWriter(SHARD);

            realWriterCache.close(oldWriter);

            assertNothingQueued();
            assertThat(realWriterCache.getIfPresent(SHARD)).containsSame(newWriter);
            Mockito.verify(newWriter, Mockito.never()).close();
        }

        @Test
        void testFlush_usableWriterStaysOpen() throws InterruptedException {
            final IndexShardWriter writer = realWriterCache.getOrOpenWriter(SHARD);

            realWriterCache.flush(SHARD);

            Mockito.verify(writer).flush();
            assertNothingQueued();
            assertThat(realWriterCache.getIfPresent(SHARD)).containsSame(writer);
        }

        @Test
        void testFlush_writerFoundToBeUnusableIsClosed() throws InterruptedException {
            final IndexShardWriter writer = realWriterCache.getOrOpenWriter(SHARD);
            becomeUnusableWhenFlushed(writer, null);

            realWriterCache.flush(SHARD);
            runQueuedTask();

            assertThat(realWriterCache.getIfPresent(SHARD)).isEmpty();
            Mockito.verify(writer).close();
            verifyClosedInDatabase();
        }

        @Test
        void testFlushAll_writerFoundToBeUnusableIsClosed() throws InterruptedException {
            final IndexShardWriter writer = realWriterCache.getOrOpenWriter(SHARD);
            final IndexShardWriter otherWriter = realWriterCache.getOrOpenWriter(SHARD + 1);
            becomeUnusableWhenFlushed(writer, null);

            // Waits for its flushes, so it must have a thread of its own.
            final Thread flushThread = new Thread(realWriterCache::flushAll);
            flushThread.start();
            // A flush for each shard, and the close that one of them leads to.
            runQueuedTask();
            runQueuedTask();
            runQueuedTask();
            flushThread.join();

            assertNothingQueued();
            assertThat(realWriterCache.getIfPresent(SHARD)).isEmpty();
            Mockito.verify(writer).close();
            assertThat(realWriterCache.getIfPresent(SHARD + 1)).isPresent();
            Mockito.verify(otherWriter, Mockito.never()).close();
        }

        @Test
        void testFlush_failureIsThrownAndUnusableWriterIsStillClosed() throws InterruptedException {
            final IndexShardWriter writer = realWriterCache.getOrOpenWriter(SHARD);
            becomeUnusableWhenFlushed(writer, new IllegalStateException("cannot flush"));

            assertThatThrownBy(() -> realWriterCache.flush(SHARD)).hasMessage("cannot flush");
            runQueuedTask();

            Mockito.verify(writer).close();
        }

        @Test
        void testOpen_statusCannotBeSet_writerIsClosed() {
            Mockito.when(indexShardDao.markOpen(eq(SHARD), any()))
                    .thenThrow(new RuntimeException("no database"))
                    .thenReturn(true);

            assertThatThrownBy(() -> realWriterCache.getOrOpenWriter(SHARD)).hasMessage("no database");

            // Nothing has the writer, so nothing else could close it.
            assertThat(writers).hasSize(1);
            Mockito.verify(writers.getFirst()).close();
            assertThat(realWriterCache.getIfPresent(SHARD)).isEmpty();

            // The shard can be opened once the database is back.
            assertThat(realWriterCache.getOrOpenWriter(SHARD)).isSameAs(writers.getLast());
            assertThat(writers).hasSize(2);
            Mockito.verify(writers.getLast(), Mockito.never()).close();
        }

        @Test
        void testLostLease_writerIsDroppedOnAnotherThread() throws InterruptedException {
            volumeType = VolumeType.SHARED;
            final IndexShardWriter writer = realWriterCache.getOrOpenWriter(SHARD);
            Mockito.when(indexShardLeases.isHeld(SHARD)).thenReturn(false);

            // Called on the thread that renews leases, which must not wait for anything.
            lostLeaseHandler.accept(SHARD);
            assertThat(realWriterCache.getIfPresent(SHARD)).containsSame(writer);

            // Dropping the writer, then closing it.
            runQueuedTask();
            runQueuedTask();

            assertThat(realWriterCache.getIfPresent(SHARD)).isEmpty();
            Mockito.verify(writer).close();
        }

        @Test
        void testLostLease_shardClaimedAgainSince_newWriterIsLeftAlone() throws InterruptedException {
            volumeType = VolumeType.SHARED;
            final IndexShardWriter writer = realWriterCache.getOrOpenWriter(SHARD);
            // The writer the lease was lost under has already gone, and this one holds a new lease.
            Mockito.when(indexShardLeases.isHeld(SHARD)).thenReturn(true);

            realWriterCache.dropWriterOfLostLease(SHARD);

            assertNothingQueued();
            assertThat(realWriterCache.getIfPresent(SHARD)).containsSame(writer);
            Mockito.verify(writer, Mockito.never()).close();
        }

        @Test
        void testSettingsChanged_writersOfTheOldCacheAreClosed() throws InterruptedException {
            final IndexShardWriter writer = realWriterCache.getOrOpenWriter(SHARD);
            config.set(new IndexWriterConfig(
                    null,
                    CacheConfig.builder().maximumSize(50L).build(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null));
            realCacheManager.getCache("Index Shard Writer Cache").rebuild();
            assertThat(realWriterCache.getIfPresent(SHARD)).isEmpty();

            realWriterCache.closeOrphanedWriters();
            runQueuedTask();

            Mockito.verify(writer).close();
            verifyClosedInDatabase();

            // A writer that the new cache holds is left alone.
            final IndexShardWriter newWriter = realWriterCache.getOrOpenWriter(SHARD);
            realWriterCache.closeOrphanedWriters();
            assertNothingQueued();
            Mockito.verify(newWriter, Mockito.never()).close();
        }

        @Test
        void testOrphanCheck_writerWhoseCloseIsQueuedIsLeftToIt() throws InterruptedException {
            final IndexShardWriter writer = realWriterCache.getOrOpenWriter(SHARD);
            realWriterCache.close(writer);
            final Runnable queuedClose = takeQueuedTask();

            realWriterCache.closeOrphanedWriters();

            // Only the close that was already queued.
            assertNothingQueued();
            queuedClose.run();
            Mockito.verify(writer).close();
        }

        @Test
        void testOpen_eachOpeningHasAWriterIdOfItsOwn() throws InterruptedException {
            final IndexShardWriter first = realWriterCache.getOrOpenWriter(SHARD);
            realWriterCache.close(first);
            runQueuedTask();
            final IndexShardWriter second = realWriterCache.getOrOpenWriter(SHARD);

            assertThat(first.getWriterId()).isNotNull();
            assertThat(second.getWriterId()).isNotNull().isNotEqualTo(first.getWriterId());
            Mockito.verify(indexShardDao).markOpen(SHARD, first.getWriterId());
            Mockito.verify(indexShardDao).markOpen(SHARD, second.getWriterId());
            Mockito.verify(commitRecorder).createListener(SHARD, first.getWriterId());
        }

        @Test
        void testOpen_shardDeletedMeanwhile_writerIsClosed() {
            Mockito.when(indexShardDao.markOpen(eq(SHARD), any())).thenReturn(false);

            assertThatThrownBy(() -> realWriterCache.getOrOpenWriter(SHARD))
                    .hasMessageContaining("no longer being opened");

            Mockito.verify(writers.getFirst()).close();
        }

        @Test
        void testClose_cleanClose_recordsItAndCompletesTasks() throws InterruptedException {
            final IndexShardWriter writer = realWriterCache.getOrOpenWriter(SHARD);
            final String writerId = writer.getWriterId();
            Mockito.when(writer.isClosedCleanly()).thenReturn(true);
            Mockito.when(indexShardDao.closeWriter(eq(SHARD), eq(writerId), any(), any(), eq(true)))
                    .thenReturn(Optional.of(Set.of(7L)));

            realWriterCache.close(writer);
            runQueuedTask();

            Mockito.verify(indexShardDao).closeWriter(SHARD, writerId, null, null, true);
            Mockito.verify(commitRecorder).closed(Set.of(7L));
            Mockito.verify(indexShardDao, Mockito.never()).reset(anyLong());
        }

        @Test
        void testClose_shardTakenOverMeanwhile_leaseIsGivenUpAsBefore() throws InterruptedException {
            volumeType = VolumeType.SHARED;
            final IndexShardWriter writer = realWriterCache.getOrOpenWriter(SHARD);
            Mockito.when(indexShardDao.closeWriter(anyLong(), any(), any(), any(), anyBoolean()))
                    .thenReturn(Optional.empty());

            realWriterCache.close(writer);
            runQueuedTask();

            Mockito.verify(indexShardDao).closeWriter(SHARD, writer.getWriterId(), INSTANCE_ID, null, false);
            Mockito.verify(indexShardLeases).release(SHARD, null);
            Mockito.verify(commitRecorder, Mockito.never()).closed(any());
        }

        @Test
        void testClose_databaseUnavailable_isTriedAgain() throws InterruptedException {
            volumeType = VolumeType.SHARED;
            final IndexShardWriter writer = realWriterCache.getOrOpenWriter(SHARD);
            Mockito.when(indexShardDao.closeWriter(anyLong(), any(), any(), any(), anyBoolean()))
                    .thenThrow(new RuntimeException("no database"))
                    .thenReturn(Optional.of(Set.of()));

            realWriterCache.close(writer);
            runQueuedTask();

            Mockito.verify(indexShardDao, Mockito.times(2))
                    .closeWriter(SHARD, writer.getWriterId(), INSTANCE_ID, null, false);
            // Given up with the close, in the same transaction.
            Mockito.verify(indexShardLeases).forget(SHARD);
            Mockito.verify(indexShardLeases, Mockito.never()).release(anyLong(), any());
        }

        private void verifyClosedInDatabase() {
            Mockito.verify(indexShardDao).closeWriter(eq(SHARD), any(), isNull(), any(), anyBoolean());
        }

        private IndexShardWriter createWriter(final long indexShardId) {
            final IndexShardWriter writer = Mockito.mock(IndexShardWriter.class);
            Mockito.when(writer.getIndexShardId()).thenReturn(indexShardId);
            final AtomicReference<String> writerId = new AtomicReference<>();
            Mockito.doAnswer(invocation -> {
                writerId.set(invocation.getArgument(0));
                return null;
            }).when(writer).setWriterId(any());
            Mockito.when(writer.getWriterId()).thenAnswer(invocation -> writerId.get());
            Mockito.when(writer.isUnusable()).thenAnswer(invocation -> unusable.contains(writer));
            writers.add(writer);
            return writer;
        }

        // The writer is found to be unusable by its next flush, which then throws the failure if there is one.
        private void becomeUnusableWhenFlushed(final IndexShardWriter writer, final RuntimeException failure) {
            Mockito.doAnswer(invocation -> {
                unusable.add(writer);
                if (failure != null) {
                    throw failure;
                }
                return null;
            }).when(writer).flush();
        }

        private void runQueuedTask() throws InterruptedException {
            takeQueuedTask().run();
        }

        // Closes are queued by another thread, as that is how the cache reports what it has removed.
        private Runnable takeQueuedTask() throws InterruptedException {
            final Runnable task = queuedTasks.poll(10, TimeUnit.SECONDS);
            assertThat(task).isNotNull();
            return task;
        }

        private void assertNothingQueued() throws InterruptedException {
            assertThat(queuedTasks.poll(500, TimeUnit.MILLISECONDS)).isNull();
        }
    }
}
