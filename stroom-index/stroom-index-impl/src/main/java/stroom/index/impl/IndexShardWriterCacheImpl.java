/*
 * Copyright 2017 Crown Copyright
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
import stroom.index.shared.LuceneIndexDoc;
import stroom.index.shared.LuceneVersion;
import stroom.index.shared.LuceneVersionUtil;
import stroom.node.api.NodeInfo;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TerminateHandlerFactory;
import stroom.util.concurrent.StripedLock;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;

@Singleton
public class IndexShardWriterCacheImpl implements IndexShardWriterCache {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexShardWriterCacheImpl.class);

    private final NodeInfo nodeInfo;
    private final IndexShardService indexShardService;
    private final LuceneIndexDocCache luceneIndexDocCache;
    private final IndexShardManager indexShardManager;
    private final StroomCache<Long, IndexShardWriter> cache;
    private final StripedLock stripedLock = new StripedLock();
    private final AtomicLong openWriterCount = new AtomicLong();
    private final IndexShardWriterExecutorProvider executorProvider;
    private final TaskContextFactory taskContextFactory;
    private final SecurityContext securityContext;
    private final PathCreator pathCreator;
    private final LuceneProviderFactory luceneProviderFactory;

    @Inject
    public IndexShardWriterCacheImpl(final NodeInfo nodeInfo,
                                     final IndexShardService indexShardService,
                                     final Provider<IndexWriterConfig> indexWriterConfigProvider,
                                     final LuceneIndexDocCache luceneIndexDocCache,
                                     final IndexShardManager indexShardManager,
                                     final IndexShardWriterExecutorProvider executorProvider,
                                     final TaskContextFactory taskContextFactory,
                                     final SecurityContext securityContext,
                                     final PathCreator pathCreator,
                                     final LuceneProviderFactory luceneProviderFactory,
                                     final CacheManager cacheManager) {
        this.nodeInfo = nodeInfo;
        this.indexShardService = indexShardService;
        this.luceneIndexDocCache = luceneIndexDocCache;
        this.indexShardManager = indexShardManager;
        this.executorProvider = executorProvider;
        this.taskContextFactory = taskContextFactory;
        this.securityContext = securityContext;
        this.pathCreator = pathCreator;
        this.luceneProviderFactory = luceneProviderFactory;

        cache = cacheManager.create(
                "Index Shard Writer Cache",
                () -> indexWriterConfigProvider.get().getIndexShardWriterCache(),
                (k, v) -> close(v, getExecutor()));

    }

    @Override
    public Optional<IndexShardWriter> getIfPresent(final long indexShardId) {
        return cache.getIfPresent(indexShardId);
    }

    @Override
    public IndexShardWriter getOrOpenWriter(final long indexShardId) {
        return cache.get(indexShardId, (k) -> {
            final IndexShardWriter indexShardWriter = openWriter(k);
            if (indexShardWriter == null) {
                throw new IndexException("Unable to create writer for " + indexShardId);
            }

            return indexShardWriter;
        });
    }

    /**
     * We expect to get lock exceptions as writers are removed from the open writers cache and closed
     * asynchronously via `removeElementsExceedingTTLandTTI`. If this happens we expect this exception and
     * will return null from this method so that the calling code will create a new shard instead.
     * This means more shards are created but stops closing shards from blocking indexing.
     *
     * @param indexShardId The id of the index shard.
     * @return A writer for the index shard.
     */
    private IndexShardWriter openWriter(final long indexShardId) {
        IndexShardWriter indexShardWriter = null;

        final Lock lock = stripedLock.getLockForKey(indexShardId);
        lock.lock();
        try {
            final IndexShard indexShard = indexShardService.loadById(indexShardId);

            // Get the index fields.
            final LuceneIndexDoc luceneIndexDoc = luceneIndexDocCache.get(new DocRef(LuceneIndexDoc.DOCUMENT_TYPE,
                    indexShard.getIndexUuid()));

            // Mark the index shard as opening.
            final boolean isNew = IndexShardStatus.NEW.equals(indexShard.getStatus());
            LOGGER.debug(() -> "Opening " + indexShardId);
            if (indexShardManager.setStatus(indexShardId, IndexShardStatus.OPENING)) {
                try {
                    final LuceneVersion luceneVersion = LuceneVersionUtil
                            .getLuceneVersion(indexShard.getIndexVersion());
                    final LuceneProvider luceneProvider = luceneProviderFactory.get(luceneVersion);
                    final IndexShardWriter writer = luceneProvider.createIndexShardWriter(
                            indexShard,
                            luceneIndexDoc.getMaxDocsPerShard());

                    // We have opened the index so update the DB object.
                    if (indexShardManager.setStatus(indexShardId, IndexShardStatus.OPEN)) {
                        // Output some debug.
                        LOGGER.debug(() ->
                                "Opened " + indexShardId + " in " +
                                        (System.currentTimeMillis() - writer.getCreationTime()) + "ms");
                    }

                    indexShardWriter = writer;

                } catch (final UncheckedLockObtainException t) {
                    // We expect to get lock exceptions as writers are removed from the open writers cache and closed
                    // asynchronously via `removeElementsExceedingTTLandTTI`. If this happens we expect this exception
                    // and will return null from this method so that the calling code will create a new shard instead.
                    // This means more shards are created but stops closing shards from blocking indexing.
                    LOGGER.debug(() -> "Error opening " + indexShardId, t);
                    LOGGER.trace(t::getMessage, t);

                } catch (final RuntimeException e) {
                    // Something unexpected went wrong.
                    if (isNew) {
                        LOGGER.error(() -> "Deleting new index shard because (" + e + ")", e);
                        if (indexShardManager.setStatus(indexShardId, IndexShardStatus.DELETED)) {
                            // Output some debug.
                            LOGGER.debug(() -> "Deleted " + indexShardId);
                        }

                    } else {
                        LOGGER.error(e::getMessage, e);
                    }
                }

                // Something went wrong so set the shard state back to closed.
                if (indexShardManager.setStatus(indexShardId, IndexShardStatus.CLOSED)) {
                    // Output some debug.
                    LOGGER.debug(() -> "Closed " + indexShardId);
                }
            }

            openWriterCount.incrementAndGet();
            return indexShardWriter;

        } finally {
            lock.unlock();
        }
    }

    @Override
    public void flush(final long indexShardId) {
        final Optional<IndexShardWriter> optional = cache.getIfPresent(indexShardId);
        optional.ifPresent(indexShardWriter -> {
            LOGGER.debug(() -> "Flush index shard " + indexShardId);
            indexShardWriter.flush();
        });
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
                            flush(indexShardId, getExecutor())
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
        LOGGER.logDurationIfDebugEnabled(
                cache::evictExpiredElements,
                "sweep()");
    }

    @Override
    public void close(final IndexShardWriter indexShardWriter) {
        taskContextFactory.current().info(() ->
                "Closing index shard writer for shard: " + indexShardWriter.getIndexShardId());
        cache.invalidate(indexShardWriter.getIndexShardId());
    }

    @Override
    public void delete(final long indexShardId) {
        if (indexShardManager.setStatus(indexShardId, IndexShardStatus.DELETED)) {
            // Output some debug.
            LOGGER.debug(() -> "Deleted " + indexShardId);
        }
        cache.invalidate(indexShardId);
    }

    private CompletableFuture<Void> flush(final long indexShardId,
                                          final Executor executor) {
        final Lock lock = stripedLock.getLockForKey(indexShardId);
        lock.lock();
        final Runnable runnable = taskContextFactory.context(
                "Flushing writer",
                TerminateHandlerFactory.NOOP_FACTORY,
                taskContext -> cache.getIfPresent(indexShardId).ifPresent(indexShardWriter -> {
                    try {
                        taskContext.info(() ->
                                "Flushing writer for index shard " + indexShardWriter);

                        // Flush the shard.
                        indexShardWriter.flush();
                    } catch (final RuntimeException e) {
                        LOGGER.error(e::getMessage, e);
                    }
                }));
        return CompletableFuture
                .runAsync(runnable, executor)
                .whenComplete((r, t) -> {
                    lock.unlock();
                    if (t != null) {
                        LOGGER.error(t::getMessage, t);
                    }
                });
    }

    private Executor getExecutor() {
//        if (sychronous.get()) {
//            return executorProvider.getSyncExecutor();
//        }
        return executorProvider.getAsyncExecutor();
    }

    private void close(final IndexShardWriter indexShardWriter,
                       final Executor executor) {
        final long indexShardId = indexShardWriter.getIndexShardId();
        final Lock lock = stripedLock.getLockForKey(indexShardId);
        lock.lock();
        try {
            // Set the status of the shard to closing so it won't be used again immediately when removed
            // from the map.
            indexShardManager.setStatus(indexShardId, IndexShardStatus.CLOSING);
            // Close the shard.
            final Runnable supplier = taskContextFactory.context(
                    "Closing writer",
                    TerminateHandlerFactory.NOOP_FACTORY,
                    taskContext -> {
                        try {
                            try {
                                LOGGER.debug(() ->
                                        "Closing " + indexShardId);
                                LOGGER.trace(() ->
                                        "Closing " + indexShardId);

                                taskContext.info(() -> "Closing writer for index shard " + indexShardId);

                                // Close the shard.
                                indexShardWriter.close();

                            } finally {
                                // Update the shard status.
                                if (indexShardManager.setStatus(indexShardId, IndexShardStatus.CLOSED)) {
                                    // Output some debug.
                                    LOGGER.debug(() -> "Closed " + indexShardId);
                                }
                            }
                        } catch (final RuntimeException e) {
                            LOGGER.error(e::getMessage, e);
                        }
                    });
            final CompletableFuture<Void> completableFuture = CompletableFuture.runAsync(
                    supplier,
                    executor);
            completableFuture.whenComplete((r, t) -> {
                openWriterCount.decrementAndGet();
                lock.unlock();
                if (t != null) {
                    LOGGER.error(t::getMessage, t);
                }
            });
        } catch (final Exception e) {
            LOGGER.error(e::getMessage, e);
            openWriterCount.decrementAndGet();
            lock.unlock();
        }
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
                final ResultPage<IndexShard> indexShardResultPage = indexShardService.find(criteria);
                for (final IndexShard indexShard : indexShardResultPage.getValues()) {
                    clean(indexShard);
                }
            }, "Index shard writer cache startup");
        });
    }

    public synchronized void shutdown() {
        securityContext.asProcessingUser(() -> {
            LOGGER.info(() -> "Index shard writer cache shutdown");
            LOGGER.logDurationIfDebugEnabled(() -> {
                try {
                    // Close any remaining writers.
                    cache.clear();

                    // Report on closing progress.
                    if (openWriterCount.get() > 0) {
                        // Create a scheduled executor for us to continually log index shard writer action progress.
                        try (final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor()) {
                            // Start logging action progress.
                            executor.scheduleAtFixedRate(() ->
                                            LOGGER.info(() ->
                                                    "Waiting for " + openWriterCount.get() + " index shards to close"),
                                    10,
                                    10,
                                    TimeUnit.SECONDS);

                            while (openWriterCount.get() > 0) {
                                ThreadUtil.sleep(500);
                            }
                            // Shut down the progress logging executor.
                            executor.shutdown();
                        }
                    }
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
            indexShard.setStatus(IndexShardStatus.CLOSED);
            indexShardService.forceStatus(indexShard.getId(), IndexShardStatus.CLOSED);
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
