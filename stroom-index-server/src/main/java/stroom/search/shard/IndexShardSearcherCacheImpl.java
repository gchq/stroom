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

package stroom.search.shard;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import org.apache.lucene.index.IndexWriter;
import stroom.entity.shared.Clearable;
import stroom.index.IndexShardService;
import stroom.index.IndexShardWriter;
import stroom.index.IndexShardWriterCache;
import stroom.index.shared.IndexShard;
import stroom.jobsystem.JobTrackedSchedule;
import stroom.search.SearchException;
import stroom.task.ExecutorProvider;
import stroom.task.TaskContext;
import stroom.task.ThreadPoolImpl;
import stroom.util.cache.CacheManager;
import stroom.util.cache.CacheUtil;
import stroom.util.lifecycle.StroomFrequencySchedule;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;
import stroom.util.shared.ThreadPool;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Singleton
public class IndexShardSearcherCacheImpl implements IndexShardSearcherCache, Clearable {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexShardSearcherCacheImpl.class);
    private static final int MAX_CACHE_ENTRIES = 2;

    private final CacheManager cacheManager;
    private final IndexShardService indexShardService;
    private final IndexShardWriterCache indexShardWriterCache;
    private final Executor executor;
    private final AtomicLong closing = new AtomicLong();
    private final TaskContext taskContext;

    private volatile long maxOpenShards = MAX_CACHE_ENTRIES;
    private volatile LoadingCache<Key, IndexShardSearcher> cache;

    @Inject
    IndexShardSearcherCacheImpl(final CacheManager cacheManager,
                                final IndexShardService indexShardService,
                                final IndexShardWriterCache indexShardWriterCache,
                                final ExecutorProvider executorProvider,
                                final TaskContext taskContext) {
        this.cacheManager = cacheManager;
        this.indexShardService = indexShardService;
        this.indexShardWriterCache = indexShardWriterCache;

        final ThreadPool threadPool = new ThreadPoolImpl("Index Shard Searcher Cache", 3, 0, Integer.MAX_VALUE);
        executor = executorProvider.getExecutor(threadPool);

        this.taskContext = taskContext;
    }

    @SuppressWarnings("unchecked")
    private LoadingCache<Key, IndexShardSearcher> getCache() {
        LoadingCache<Key, IndexShardSearcher> result = cache;
        if (result == null) {
            synchronized (this) {
                result = cache;
                if (result == null) {
                    final RemovalListener<Key, IndexShardSearcher> removalListener = notification -> destroy(notification.getKey(), notification.getValue());

                    final CacheLoader<Key, IndexShardSearcher> cacheLoader = CacheLoader.from(k -> {
                        if (k == null) {
                            throw new NullPointerException("Null key supplied");
                        }

                        try {
                            final IndexShard indexShard = indexShardService.loadById(k.indexShardId);
                            if (indexShard == null) {
                                throw new SearchException("Unable to find index shard with id = " + k.indexShardId);
                            }

                            return new IndexShardSearcherImpl(indexShard, k.indexWriter);
                        } catch (final RuntimeException e) {
                            LOGGER.error(e::getMessage, e);
                            throw e;
                        }
                    });

                    final CacheBuilder cacheBuilder = CacheBuilder.newBuilder()
                            .maximumSize(maxOpenShards)
                            .expireAfterAccess(1, TimeUnit.MINUTES)
                            .removalListener(removalListener);
                    result = cacheBuilder.build(cacheLoader);
                    cacheManager.replaceCache("Index Shard Searcher Cache", cacheBuilder, result);
                    this.cache = result;
                }
            }
        }

        return result;
    }

    @Override
    public IndexShardSearcher get(final Long indexShardId) {
        final IndexWriter indexWriter = getWriter(indexShardId);
        final Key key = new Key(indexShardId, indexWriter);
        return getCache().getUnchecked(key);
    }

    @Override
    public boolean isCached(final Long indexShardId) {
        final IndexWriter indexWriter = getWriter(indexShardId);
        final Key key = new Key(indexShardId, indexWriter);
        return getCache().getIfPresent(key) != null;
    }

    private void destroy(final Key key, final Object value) {
        if (value != null && value instanceof IndexShardSearcherImpl) {
            final IndexShardSearcherImpl indexShardSearcher = (IndexShardSearcherImpl) value;

            closing.incrementAndGet();
            executor.execute(() -> {
                try {
                    taskContext.setName("Closing searcher");
                    taskContext.info("Closing searcher for index shard " + key.indexShardId);

                    indexShardSearcher.destroy();
                } finally {
                    closing.decrementAndGet();
                }
            });
        }
    }

    private IndexWriter getWriter(final Long indexShardId) {
        IndexWriter indexWriter = null;

        // Load the current index shard.
        final IndexShardWriter indexShardWriter = indexShardWriterCache.getWriterByShardId(indexShardId);
        if (indexShardWriter != null) {
            indexWriter = indexShardWriter.getWriter();
        }

        return indexWriter;
    }

    @Override
    public void clear() {
        LOGGER.info(() -> "Clearing index shard searcher cache");
        final LogExecutionTime logExecutionTime = new LogExecutionTime();

        ScheduledExecutorService executor = null;

        try {
            // Close any remaining writers.
            CacheUtil.clear(getCache());

            // Report on closing progress.
            if (closing.get() > 0) {
                // Create a scheduled executor for us to continually log close progress.
                executor = Executors.newSingleThreadScheduledExecutor();
                // Start logging action progress.
                executor.scheduleAtFixedRate(() -> LOGGER.info(() -> "Waiting for " + closing.get() + " readers to close"), 10, 10, TimeUnit.SECONDS);

                while (closing.get() > 0) {
                    Thread.sleep(500);
                }
            }
        } catch (final InterruptedException e) {
            LOGGER.error(e::getMessage, e);

            // Continue to interrupt this thread.
            Thread.currentThread().interrupt();
        } finally {
            if (executor != null) {
                // Shut down the progress logging executor.
                executor.shutdown();
            }
        }

        LOGGER.info(() -> "Finished clearing index shard searcher cache in " + logExecutionTime);
    }

    @Override
    public long getMaxOpenShards() {
        return maxOpenShards;
    }

    @Override
    public void setMaxOpenShards(final long maxOpenShards) {
        if (this.maxOpenShards != maxOpenShards) {
            synchronized (this) {
                final LoadingCache<Key, IndexShardSearcher> result = cache;
                this.maxOpenShards = maxOpenShards;
                cache = null;

                if (result != null) {
                    // Clean up.
                    CacheUtil.clear(result);
                }
            }
        }
    }

    /**
     * This is called by the lifecycle service and remove writers that are past their TTL, TTI or LRU items that exceed the capacity.
     */
    @StroomFrequencySchedule("10m")
    @JobTrackedSchedule(jobName = "Index Searcher Cache Refresh", description = "Job to refresh index shard searchers in the cache")
    public void refresh() {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();

        getCache().asMap().values().forEach(v -> {
            if (v != null) {
                try {
                    v.getSearcherManager().maybeRefresh();
                } catch (final IOException e) {
                    LOGGER.error(e::getMessage, e);
                }
            }
        });

        LOGGER.debug(() -> "refresh() - Completed in " + logExecutionTime);
    }

    public static class Key {
        private final long indexShardId;
        private final IndexWriter indexWriter;

        Key(final long indexShardId, final IndexWriter indexWriter) {
            this.indexShardId = indexShardId;
            this.indexWriter = indexWriter;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            final Key key = (Key) o;

            if (indexShardId != key.indexShardId) return false;
            return indexWriter != null ? indexWriter.equals(key.indexWriter) : key.indexWriter == null;
        }

        @Override
        public int hashCode() {
            int result = (int) (indexShardId ^ (indexShardId >>> 32));
            result = 31 * result + (indexWriter != null ? indexWriter.hashCode() : 0);
            return result;
        }
    }
}
