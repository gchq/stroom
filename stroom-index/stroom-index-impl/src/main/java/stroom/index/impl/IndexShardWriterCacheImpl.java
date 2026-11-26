/*
 * Copyright 2016 Crown Copyright
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
import stroom.docref.DocRef;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexException;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.LuceneIndexDoc;
import stroom.index.shared.LuceneVersion;
import stroom.index.shared.LuceneVersionUtil;
import stroom.node.api.NodeInfo;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TerminateHandlerFactory;
import stroom.task.api.ThreadPoolImpl;
import stroom.task.shared.ThreadPool;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Singleton
public class IndexShardWriterCacheImpl implements IndexShardWriterCache {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexShardWriterCacheImpl.class);

    private static final ThreadPool THREAD_POOL = new ThreadPoolImpl("Index Shard Writer Cache", 3);
    // The most closed shards to remove replaced commits from in one run.
    private static final int PRUNE_BATCH_SIZE = 100;

    private final NodeInfo nodeInfo;
    private final LuceneIndexDocCache luceneIndexDocCache;
    private final IndexShardDao indexShardDao;
    private final StroomCache<Long, IndexShardWriter> cache;
    private final Map<Long, IndexShardWriter> openShards = new ConcurrentHashMap<>();
    // The writers removed from the cache whose closes are queued but have not yet run.
    private final Set<IndexShardWriter> queuedCloses = ConcurrentHashMap.newKeySet();
    private final Executor executor;
    private final TaskContextFactory taskContextFactory;
    private final SecurityContext securityContext;
    private final PathCreator pathCreator;
    private final Provider<LuceneProviderFactory> luceneProviderFactoryProvider;
    private final Provider<IndexWriterConfig> indexWriterConfigProvider;
    private final IndexShardLeases indexShardLeases;
    // The open shards that are on shared volumes, which this node holds a lease on while it writes to them.
    private final Set<Long> leasedShards = ConcurrentHashMap.newKeySet();

    @Inject
    public IndexShardWriterCacheImpl(final NodeInfo nodeInfo,
                                     final Provider<IndexWriterConfig> indexWriterConfigProvider,
                                     final LuceneIndexDocCache luceneIndexDocCache,
                                     final IndexShardDao indexShardDao,
                                     final ExecutorProvider executorProvider,
                                     final TaskContextFactory taskContextFactory,
                                     final SecurityContext securityContext,
                                     final PathCreator pathCreator,
                                     final Provider<LuceneProviderFactory> luceneProviderFactoryProvider,
                                     final CacheManager cacheManager,
                                     final IndexShardLeases indexShardLeases) {
        this.nodeInfo = nodeInfo;
        this.luceneIndexDocCache = luceneIndexDocCache;
        this.indexShardDao = indexShardDao;
        this.executor = executorProvider.get(THREAD_POOL);
        this.taskContextFactory = taskContextFactory;
        this.securityContext = securityContext;
        this.pathCreator = pathCreator;
        this.luceneProviderFactoryProvider = luceneProviderFactoryProvider;
        this.indexWriterConfigProvider = indexWriterConfigProvider;
        this.indexShardLeases = indexShardLeases;

        cache = cacheManager.create(
                "Index Shard Writer Cache",
                () -> indexWriterConfigProvider.get().getIndexShardWriterCache(),
                (k, v) -> close(v, executor));

        // A shard whose lease has gone must not be written to again. Dropping its writer closes it, and
        // the writer discards what it has not committed when it finds the lease is no longer held.
        indexShardLeases.setLostLeaseHandler(this::onLostLease);
    }

    // Called on the thread that renews leases, which must not wait for a shard to open, so the writer is
    // dropped on another thread.
    private void onLostLease(final long indexShardId) {
        CompletableFuture
                .runAsync(() -> dropWriterOfLostLease(indexShardId), executor)
                .whenComplete((r, t) -> {
                    if (t != null) {
                        LOGGER.error("Error closing index shard {} after losing its lease", indexShardId, t);
                    }
                });
    }

    /// Drops the writer of a shard whose lease has been lost, which closes it. Done under the cache's lock
    /// for the shard, so it waits for a writer being opened. If the lease is held again by then, the writer
    /// in the cache is a new one with a lease of its own, and is left alone.
    ///
    /// @param indexShardId The id of the shard.
    void dropWriterOfLostLease(final long indexShardId) {
        cache.compute(indexShardId, (k, v) -> v == null || indexShardLeases.isHeld(indexShardId)
                ? v
                : null);
        cache.evictExpiredElements();
    }

    @Override
    public Optional<IndexShardWriter> getIfPresent(final long indexShardId) {
        return cache.getIfPresent(indexShardId);
    }

    @Override
    public IndexShardWriter getOrOpenWriter(final long indexShardId) {
        return cache.get(indexShardId, this::open);
    }

    /**
     * @param indexShardId The id of the index shard.
     * @return A writer for the index shard.
     */
    private IndexShardWriter open(final long indexShardId) {
        final AtomicReference<RuntimeException> failure = new AtomicReference<>();
        final IndexShardWriter writer = openShards.compute(indexShardId, (k, v) -> {
            if (v != null) {
                closeWriter(v);
            }
            try {
                return openWriter(indexShardId);
            } catch (final RuntimeException e) {
                // The old writer has been closed, so it must not stay in the map to be closed again
                // later, when the shard may be another node's.
                failure.set(e);
                return null;
            }
        });
        if (failure.get() != null) {
            throw failure.get();
        }
        return writer;
    }

    private IndexShardWriter openWriter(final long indexShardId) {
        LOGGER.debug(() -> "Opening " + indexShardId);

        return securityContext.asProcessingUserResult(() -> {
            final Optional<IndexShard> optional = indexShardDao.fetch(indexShardId);
            if (optional.isEmpty()) {
                throw new IndexException("Unable to find index shard with id = " + indexShardId);
            }

            // Get the index fields.
            final IndexShard indexShard = optional.get();
            final LuceneIndexDoc luceneIndexDoc = luceneIndexDocCache.get(new DocRef(LuceneIndexDoc.TYPE,
                    indexShard.getIndexUuid()));

            // Mark the index shard as opening.
            final boolean isNew = IndexShardStatus.NEW.equals(indexShard.getStatus());
            // Any node may write to a shard on a shared volume, so this node must first claim its lease,
            // which also marks it as opening. Done outside the try block as a shard that another node holds
            // is not this node's to reset or delete.
            final boolean leased = isOnSharedVolume(indexShard);
            if (leased) {
                if (!indexShardLeases.claim(indexShardId)) {
                    throw new IndexException("Unable to claim the lease on index shard " + indexShardId
                                             + " as it is not closed or another node holds it");
                }
                leasedShards.add(indexShardId);
            }
            try {
                if (!leased && !indexShardDao.setStatus(indexShardId, IndexShardStatus.OPENING)) {
                    throw new IndexException("Unable to set index shard status to OPENING");
                }

                final LuceneVersion luceneVersion = LuceneVersionUtil
                        .getLuceneVersion(indexShard.getIndexVersion());
                final LuceneProvider luceneProvider = luceneProviderFactoryProvider.get().get(luceneVersion);
                final IndexShardWriter writer = luceneProvider.createIndexShardWriter(
                        indexShard,
                        luceneIndexDoc.getMaxDocsPerShard());
                try {
                    if (leased) {
                        writer.setLeaseCheck(
                                indexShardLeases.getInstanceId(),
                                indexShardLeases.leaseCheck(indexShardId));
                    }

                    // We have opened the index so update the DB object.
                    if (indexShardDao.setStatus(indexShardId, IndexShardStatus.OPEN)) {
                        // Output some debug.
                        LOGGER.debug(() ->
                                "Opened " + indexShardId + " in " +
                                (System.currentTimeMillis() - writer.getCreationTime()) + "ms");
                    }
                } catch (final RuntimeException e) {
                    // Nothing else has this writer. Left open it would keep Lucene's lock on the shard, so
                    // this process could not open it again.
                    writer.close();
                    throw e;
                }

                return writer;

            } catch (final RuntimeException e) {
                if (isNew) {
                    try {
                        // If this was a new shard then delete it immediately.
                        LOGGER.error(() -> "Deleting new index shard because (" + e + ")", e);
                        try {
                            indexShardDao.logicalDelete(indexShardId, indexShardLeases.getInstanceId());
                        } finally {
                            releaseLease(indexShardId, null);
                        }
                    } catch (final RuntimeException e2) {
                        LOGGER.error(() -> "Unable to delete new index shard (" + e2.getMessage() + ")", e2);
                    }

                } else {
                    try {
                        // Something went wrong so set the shard state back to closed.
                        LOGGER.error(() -> "Error opening index shard " + indexShardId, e);
                        resetOrRelease(indexShardId, null);
                    } catch (final RuntimeException e2) {
                        LOGGER.error(() -> "Unable to reset index shard (" + e2.getMessage() + ")", e2);
                    }
                }

                throw e;
            }
        });
    }

    private void closeWriter(final IndexShardWriter indexShardWriter) {
        final long indexShardId = indexShardWriter.getIndexShardId();
        securityContext.asProcessingUser(() -> {
            try {
                try {
                    LOGGER.debug(() ->
                            "Closing " + indexShardId);
                    LOGGER.trace(() ->
                            "Closing " + indexShardId);

                    // Set the status of the shard to closing so it won't be used again immediately when removed
                    // from the map. Not if the shard's lease has been lost, as the shard may by now have
                    // been taken from this node and be another node's to set the status of.
                    if (!leasedShards.contains(indexShardId) || indexShardLeases.isHeld(indexShardId)) {
                        try {
                            indexShardDao.setStatus(indexShardId, IndexShardStatus.CLOSING);
                        } catch (final RuntimeException e) {
                            // The writer is closed all the same.
                            LOGGER.error("Unable to mark index shard {} as closing", indexShardId, e);
                        }
                    }

                    // Close the shard.
                    indexShardWriter.close();

                } finally {
                    // Update the shard status, and give up the lease if there is one so that any node may
                    // write to the shard next.
                    resetOrRelease(indexShardId, getPruneCommitsDelayMs(indexShardWriter));
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
            }
        });
    }

    private static boolean isOnSharedVolume(final IndexShard indexShard) {
        return NullSafe.test(indexShard.getVolume(), IndexVolume::isShared);
    }

    // Marks a shard as closed. A shard on a local volume is simply reset. One on a shared volume has its
    // lease given up in the same update, and only if this process still holds it.
    private void resetOrRelease(final long indexShardId, final Long pruneCommitsDelayMs) {
        if (leasedShards.contains(indexShardId)) {
            releaseLease(indexShardId, pruneCommitsDelayMs);
        } else {
            indexShardDao.reset(indexShardId);
        }
    }

    private void releaseLease(final long indexShardId, final Long pruneCommitsDelayMs) {
        if (leasedShards.remove(indexShardId)) {
            indexShardLeases.release(indexShardId, pruneCommitsDelayMs);
        }
    }

    // A writer that closes while still keeping commits it has replaced leaves them in the shard. They must
    // stay for as long as a reader on another node might be using them, after which they can be removed.
    private Long getPruneCommitsDelayMs(final IndexShardWriter indexShardWriter) {
        return indexShardWriter.hasReplacedCommits()
                ? indexWriterConfigProvider.get().getReplacedCommitRetention().toMillis()
                : null;
    }

    /// Removes the replaced commits left in closed shards on shared volumes once they have been kept for
    /// long enough. A closed shard has no writer to remove them, and a full shard may never have one again,
    /// so without this their files would stay on disk for good.
    void pruneReplacedCommits() {
        securityContext.asProcessingUser(() -> {
            try {
                for (final IndexShard indexShard : indexShardDao.findShardsWithCommitsToPrune(PRUNE_BATCH_SIZE)) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }
                    pruneReplacedCommits(indexShard);
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
            }
        });
    }

    private void pruneReplacedCommits(final IndexShard indexShard) {
        final long indexShardId = indexShard.getId();
        // Under the same lock as opening and closing a writer for the shard.
        openShards.compute(indexShardId, (k, openWriter) -> {
            // The shard is unleased, so any node may prune it, but only one at a time, and not while
            // another node is writing to it. Holding the lease ensures both. If it can't be claimed
            // another node is pruning it or has opened it, and the writer it opens will do the job.
            if (openWriter == null && indexShardLeases.claim(indexShardId)) {
                Long pruneCommitsDelayMs = null;
                try {
                    final LuceneIndexDoc luceneIndexDoc = luceneIndexDocCache.get(
                            new DocRef(LuceneIndexDoc.TYPE, indexShard.getIndexUuid()));
                    final LuceneVersion luceneVersion = LuceneVersionUtil
                            .getLuceneVersion(indexShard.getIndexVersion());
                    final IndexShardWriter writer = luceneProviderFactoryProvider.get()
                            .get(luceneVersion)
                            .createIndexShardWriter(indexShard, luceneIndexDoc.getMaxDocsPerShard());
                    try {
                        writer.setLeaseCheck(
                                indexShardLeases.getInstanceId(),
                                indexShardLeases.leaseCheck(indexShardId));
                        writer.pruneReplacedCommits();
                    } finally {
                        writer.close();
                        pruneCommitsDelayMs = getPruneCommitsDelayMs(writer);
                    }
                    if (pruneCommitsDelayMs == null) {
                        indexShardDao.clearPruneCommitsTime(indexShardId);
                    }
                    LOGGER.debug(() -> "Pruned replaced commits of index shard " + indexShardId);
                } catch (final RuntimeException e) {
                    LOGGER.error(() -> "Error pruning replaced commits of index shard " + indexShardId, e);
                    // Try again later rather than on every run, so that a shard that can't be pruned
                    // does not hold up the others.
                    pruneCommitsDelayMs = indexWriterConfigProvider.get().getReplacedCommitRetention().toMillis();
                } finally {
                    indexShardLeases.release(indexShardId, pruneCommitsDelayMs);
                }
            }
            return openWriter;
        });
    }

    @Override
    public void flush(final long indexShardId) {
        final Optional<IndexShardWriter> optional = cache.getIfPresent(indexShardId);
        optional.ifPresent(indexShardWriter -> {
            LOGGER.debug(() -> "Flush index shard " + indexShardId);
            flush(indexShardWriter);
        });
    }

    private void flush(final IndexShardWriter indexShardWriter) {
        try {
            indexShardWriter.flush();
        } finally {
            // A shard that is given no more documents is only ever flushed, and being flushed keeps its
            // writer in the cache, so nothing else would ever close it.
            if (indexShardWriter.isUnusable()) {
                close(indexShardWriter);
            }
        }
    }

    /**
     * This is called by the lifecycle service and will call flush on all open writers.
     */
    @Override
    public void flushAll() {
        LOGGER.logDurationIfDebugEnabled(() -> {
            try {
                final Set<Long> openWritersIdSet = new HashSet<>(cache.keySet());
                if (!openWritersIdSet.isEmpty()) {
                    // Flush all writers.
                    final CountDownLatch countDownLatch = new CountDownLatch(openWritersIdSet.size());
                    openWritersIdSet.forEach(indexShardId -> {
                        try {
                            flush(indexShardId, executor)
                                    .thenRun(countDownLatch::countDown);
                        } catch (final RuntimeException e) {
                            LOGGER.error(e::getMessage, e);
                            countDownLatch.countDown();
                        }
                    });
                    countDownLatch.await();
                }
            } catch (final InterruptedException e) {
                LOGGER.trace(e::getMessage, e);
                // Continue to interrupt this thread.
                Thread.currentThread().interrupt();
            }
        }, "flushAll()");
    }

    /**
     * This is called by the lifecycle service and remove writers that are past their TTL, TTI or LRU
     * items that exceed the capacity.
     */
    @Override
    public void sweep() {
        // Before evicting, as the cache reports what it evicts on another thread, so writers it has just
        // evicted would look like orphans.
        LOGGER.logDurationIfDebugEnabled(
                this::closeOrphanedWriters,
                "closeOrphanedWriters()");
        LOGGER.logDurationIfDebugEnabled(
                cache::evictExpiredElements,
                "sweep()");
        LOGGER.logDurationIfDebugEnabled(
                (Runnable) this::pruneReplacedCommits,
                "pruneReplacedCommits()");
    }

    /// Closes the open writers that the cache no longer holds. The cache is replaced when its settings are
    /// changed, and the writers in the old one are never removed from it, so nothing else would flush or
    /// close them, and shutting down would wait for them for ever.
    void closeOrphanedWriters() {
        // Iterating the cache does not count as using its entries.
        final Map<Long, IndexShardWriter> cached = new HashMap<>();
        cache.forEach(cached::put);
        openShards.forEach((indexShardId, writer) -> {
            if (cached.get(indexShardId) != writer && !queuedCloses.contains(writer)) {
                // Under the cache's lock for the shard, as the writer may be one that is still being opened.
                final AtomicBoolean orphaned = new AtomicBoolean();
                cache.compute(indexShardId, (k, v) -> {
                    orphaned.set(v != writer);
                    return v;
                });
                if (orphaned.get()) {
                    LOGGER.info("Closing the writer of index shard {}, which the writer cache no longer holds",
                            indexShardId);
                    close(writer, executor);
                }
            }
        });
    }

    @Override
    public void close(final IndexShardWriter indexShardWriter) {
        // The caller may have had this writer for some time, so the shard may since have been opened again.
        cache.compute(indexShardWriter.getIndexShardId(), (k, v) -> v == indexShardWriter
                ? null
                : v);
        cache.evictExpiredElements();
    }

    @Override
    public void delete(final long indexShardId) {
        if (indexShardDao.logicalDelete(indexShardId, indexShardLeases.getInstanceId())) {
            LOGGER.debug("Deleted {}", indexShardId);
            cache.invalidate(indexShardId);
            cache.evictExpiredElements();
        } else if (indexShardDao.isLeased(indexShardId)) {
            // Another node is writing to it. It is deleted once that node has given it up.
            LOGGER.info("Index shard {} not deleted as another node holds the lease on it", indexShardId);
        } else {
            LOGGER.debug("Index shard {} not deleted as it no longer exists", indexShardId);
        }
    }

    private CompletableFuture<Void> flush(final long indexShardId,
                                          final Executor executor) {
        return securityContext.asProcessingUserResult(() -> {
            final Runnable runnable = taskContextFactory.context(
                    "Flushing writer",
                    TerminateHandlerFactory.NOOP_FACTORY,
                    taskContext -> cache.getIfPresent(indexShardId).ifPresent(indexShardWriter -> {
                        try {
                            taskContext.info(() ->
                                    "Flushing writer for index shard " + indexShardWriter);

                            // Flush the shard.
                            flush(indexShardWriter);
                        } catch (final RuntimeException e) {
                            LOGGER.error(e::getMessage, e);
                        }
                    }));
            return CompletableFuture
                    .runAsync(runnable, executor);
        });
    }

    private void close(final IndexShardWriter indexShardWriter,
                       final Executor executor) {
        final long indexShardId = indexShardWriter.getIndexShardId();
        queuedCloses.add(indexShardWriter);
        securityContext.asProcessingUser(() -> {
            try {
                // Close the shard.
                final Runnable supplier = taskContextFactory.context(
                        "Closing writer",
                        TerminateHandlerFactory.NOOP_FACTORY,
                        taskContext -> {
                            taskContext.info(() -> "Closing writer for index shard " +
                                                   indexShardId);
                            queuedCloses.remove(indexShardWriter);
                            openShards.compute(indexShardId, (k, v) -> {
                                if (v != indexShardWriter) {
                                    // The shard was opened again before this ran, which closed the old
                                    // writer. The new one is in use.
                                    return v;
                                }
                                closeWriter(v);
                                return null;
                            });
                        });
                CompletableFuture
                        .runAsync(
                                supplier,
                                executor)
                        .whenComplete((r, t) -> {
                            if (t != null) {
                                LOGGER.error(t::getMessage, t);
                            }
                        });
            } catch (final Exception e) {
                // Not queued, so left for the orphan check to close.
                queuedCloses.remove(indexShardWriter);
                LOGGER.error(e::getMessage, e);
            }
        });
    }

    synchronized void startup() {
        securityContext.asProcessingUser(() -> {
            LOGGER.info(() -> "Index shard writer cache startup");
            LOGGER.logDurationIfDebugEnabled(() -> {
                // Make sure all open shards are marked as closed.
                final FindIndexShardCriteria criteria = FindIndexShardCriteria.matchAll();
                criteria.getNodeNameSet().add(nodeInfo.getThisNodeName());
                criteria.getIndexShardStatusSet().add(IndexShardStatus.OPEN);
                criteria.getIndexShardStatusSet().add(IndexShardStatus.OPENING);
                criteria.getIndexShardStatusSet().add(IndexShardStatus.CLOSING);
                final ResultPage<IndexShard> indexShardResultPage = indexShardDao.find(criteria);
                for (final IndexShard indexShard : indexShardResultPage.getValues()) {
                    clean(indexShard);
                }
                // A shard deleted while it was open is not reset above, as it stays deleted, but the lease
                // the earlier process held on it would stop its files ever being deleted.
                try {
                    indexShardDao.clearLeasesOfUnusableShardsAfterRestart(
                            nodeInfo.getThisNodeName(),
                            indexShardLeases.getInstanceId());
                } catch (final RuntimeException e) {
                    LOGGER.error(e::getMessage, e);
                }
            }, "Index shard writer cache startup");
        });
    }

    public synchronized void shutdown() {
        securityContext.asProcessingUser(() -> {
            LOGGER.info(() -> "Index shard writer cache shutdown");
            LOGGER.logDurationIfDebugEnabled(() -> {
                try {
                    // Close any remaining writers, those the cache holds and any it no longer does.
                    closeOrphanedWriters();
                    cache.clear();

                    // Report on closing progress.
                    if (!openShards.isEmpty()) {
                        // Create a scheduled executor for us to continually log index shard writer action progress.
                        try (final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor()) {
                            // Start logging action progress.
                            executor.scheduleAtFixedRate(() ->
                                            LOGGER.info(() ->
                                                    "Waiting for " + openShards.size() + " index shards to close"),
                                    10,
                                    10,
                                    TimeUnit.SECONDS);

                            while (!openShards.isEmpty()) {
                                ThreadUtil.sleep(500);
                            }
                            // Shut down the progress logging executor.
                            executor.shutdown();
                        }
                    }
                    // Every writer has closed and given up its lease, so there is nothing left to renew.
                    // Renewal must carry on until then or a slow close could lose its lease part way.
                    indexShardLeases.stop();
                } catch (final UncheckedInterruptedException e) {
                    LOGGER.error(e::getMessage, e);
                    // Continue to interrupt this thread.
                    Thread.currentThread().interrupt();
                }
            }, "Index shard writer cache shutdown");
        });
    }

    private void clean(final IndexShard indexShard) {
        try {
            LOGGER.info(() -> "Changing shard status to closed (" + indexShard + ")");
            // The writer that left the shard open may have been keeping commits it had replaced, which nothing
            // will have recorded. Any time that is recorded is from an earlier close, so is too soon.
            final Long pruneCommitsDelayMs = NullSafe.test(indexShard.getVolume(), IndexVolume::isShared)
                    ? indexWriterConfigProvider.get().getReplacedCommitRetention().toMillis()
                    : null;
            // Clears any lease an earlier process on this node held on the shard.
            indexShardDao.resetAfterRestart(
                    indexShard.getId(),
                    nodeInfo.getThisNodeName(),
                    indexShardLeases.getInstanceId(),
                    pruneCommitsDelayMs);
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }

        try {
            LOGGER.info(() -> "Clearing any lingering locks (" + indexShard + ")");
            final Path dir = IndexShardUtil.getIndexPath(indexShard, pathCreator);
            deleteLockFiles(dir);
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    /**
     * Remove any lingering lock files in an index shard directory. These lock files can be left behind if the JVM
     * terminates abnormally and need to be removed when the system restarts.
     *
     * @param dir The directory to remove lock files from.
     */
    @Deprecated // Should no longer be needed if the new ShardLockFactory works ok
    private void deleteLockFiles(final Path dir) {
        // Delete any lingering lock files from previous uses of the index shard.
        if (Files.isDirectory(dir)) {
            try (final DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.lock")) {
                stream.forEach(file -> {
                    try {
                        Files.deleteIfExists(file);
                    } catch (final IOException e) {
                        LOGGER.error(e::getMessage, e);
                    }
                });
            } catch (final IOException e) {
                LOGGER.error(e::getMessage, e);
            }
        }
    }
}
