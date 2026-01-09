/*
 * Copyright 2016-2025 Crown Copyright
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
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
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

@Singleton
public class IndexShardWriterCacheImpl implements IndexShardWriterCache {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexShardWriterCacheImpl.class);

    private static final ThreadPool THREAD_POOL = new ThreadPoolImpl("Index Shard Writer Cache", 3);

    private final NodeInfo nodeInfo;
    private final LuceneIndexDocCache luceneIndexDocCache;
    private final IndexShardDao indexShardDao;
    private final StroomCache<Long, IndexShardWriter> cache;
    private final Map<Long, IndexShardWriter> openShards = new ConcurrentHashMap<>();
    private final Executor executor;
    private final TaskContextFactory taskContextFactory;
    private final SecurityContext securityContext;
    private final PathCreator pathCreator;
    private final Provider<LuceneProviderFactory> luceneProviderFactoryProvider;

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
                                     final CacheManager cacheManager) {
        this.nodeInfo = nodeInfo;
        this.luceneIndexDocCache = luceneIndexDocCache;
        this.indexShardDao = indexShardDao;
        this.executor = executorProvider.get(THREAD_POOL);
        this.taskContextFactory = taskContextFactory;
        this.securityContext = securityContext;
        this.pathCreator = pathCreator;
        this.luceneProviderFactoryProvider = luceneProviderFactoryProvider;

        cache = cacheManager.create(
                "Index Shard Writer Cache",
                () -> indexWriterConfigProvider.get().getIndexShardWriterCache(),
                (k, v) -> close(k, executor));

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
        return openShards.compute(indexShardId, (k, v) -> {
            if (v != null) {
                closeWriter(v);
            }
            return openWriter(indexShardId);
        });
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
            try {
                if (!indexShardDao.setStatus(indexShardId, IndexShardStatus.OPENING)) {
                    throw new IndexException("Unable to set index shard status to OPENING");
                }

                final LuceneVersion luceneVersion = LuceneVersionUtil
                        .getLuceneVersion(indexShard.getIndexVersion());
                final LuceneProvider luceneProvider = luceneProviderFactoryProvider.get().get(luceneVersion);
                final IndexShardWriter writer = luceneProvider.createIndexShardWriter(
                        indexShard,
                        luceneIndexDoc.getMaxDocsPerShard());

                // We have opened the index so update the DB object.
                if (indexShardDao.setStatus(indexShardId, IndexShardStatus.OPEN)) {
                    // Output some debug.
                    LOGGER.debug(() ->
                            "Opened " + indexShardId + " in " +
                            (System.currentTimeMillis() - writer.getCreationTime()) + "ms");
                }

                return writer;

            } catch (final RuntimeException e) {
                if (isNew) {
                    try {
                        // If this was a new shard then delete it immediately.
                        LOGGER.error(() -> "Deleting new index shard because (" + e + ")", e);
                        indexShardDao.logicalDelete(indexShardId);
                    } catch (final RuntimeException e2) {
                        LOGGER.error(() -> "Unable to delete new index shard (" + e2.getMessage() + ")", e2);
                    }

                } else {
                    try {
                        // Something went wrong so set the shard state back to closed.
                        LOGGER.error(() -> "Error opening index shard " + indexShardId, e);
                        indexShardDao.reset(indexShardId);
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
                    // from the map.
                    indexShardDao.setStatus(indexShardId, IndexShardStatus.CLOSING);

                    // Close the shard.
                    indexShardWriter.close();

                } finally {
                    // Update the shard status.
                    indexShardDao.reset(indexShardId);
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
            }
        });
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
        LOGGER.logDurationIfDebugEnabled(
                cache::evictExpiredElements,
                "sweep()");
    }

    @Override
    public void close(final IndexShardWriter indexShardWriter) {
        cache.invalidate(indexShardWriter.getIndexShardId());
        cache.evictExpiredElements();
    }

    @Override
    public void delete(final long indexShardId) {
        indexShardDao.logicalDelete(indexShardId);
        LOGGER.debug(() -> "Deleted " + indexShardId);
        cache.invalidate(indexShardId);
        cache.evictExpiredElements();
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
                            indexShardWriter.flush();
                        } catch (final RuntimeException e) {
                            LOGGER.error(e::getMessage, e);
                        }
                    }));
            return CompletableFuture
                    .runAsync(runnable, executor);
        });
    }

    private void close(final long indexShardId,
                       final Executor executor) {
        securityContext.asProcessingUser(() -> {
            try {
                // Close the shard.
                final Runnable supplier = taskContextFactory.context(
                        "Closing writer",
                        TerminateHandlerFactory.NOOP_FACTORY,
                        taskContext -> {
                            taskContext.info(() -> "Closing writer for index shard " +
                                                   indexShardId);
                            openShards.compute(indexShardId, (k, v) -> {
                                if (v != null) {
                                    closeWriter(v);
                                }
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
            indexShardDao.reset(indexShard.getId());
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
