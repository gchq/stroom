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

package stroom.search.server.shard;

import net.sf.ehcache.CacheManager;
import org.apache.lucene.index.IndexWriter;
import org.springframework.stereotype.Component;
import stroom.cache.AbstractCacheBean;
import stroom.index.server.IndexShardService;
import stroom.index.server.IndexShardWriter;
import stroom.index.server.IndexShardWriterCache;
import stroom.index.shared.IndexShard;
import stroom.jobsystem.server.JobTrackedSchedule;
import stroom.search.server.SearchException;
import stroom.task.server.ExecutorProvider;
import stroom.task.server.TaskContext;
import stroom.task.server.ThreadPoolImpl;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;
import stroom.util.shared.ThreadPool;
import stroom.util.spring.StroomFrequencySchedule;
import stroom.util.thread.ThreadUtil;

import javax.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class IndexShardSearcherCacheImpl extends AbstractCacheBean<IndexShardSearcherCacheImpl.Key, IndexShardSearcherImpl> implements IndexShardSearcherCache {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexShardSearcherCacheImpl.class);
    private static final int MAX_CACHE_ENTRIES = 2;

    private final IndexShardService indexShardService;
    private final IndexShardWriterCache indexShardWriterCache;
    private final Executor executor;
    private final AtomicLong closing = new AtomicLong();
    private final TaskContext taskContext;

    @Inject
    IndexShardSearcherCacheImpl(final CacheManager cacheManager,
                                final IndexShardService indexShardService,
                                final IndexShardWriterCache indexShardWriterCache,
                                final ExecutorProvider executorProvider,
                                final TaskContext taskContext) {
        super(cacheManager, "Index Shard Searcher Cache", MAX_CACHE_ENTRIES);
        this.indexShardService = indexShardService;
        this.indexShardWriterCache = indexShardWriterCache;

        setMaxIdleTime(60, TimeUnit.SECONDS);
        setMaxLiveTime(120, TimeUnit.SECONDS);

        final ThreadPool threadPool = new ThreadPoolImpl("Index Shard Writer Cache", 3, 0, Integer.MAX_VALUE);
        executor = executorProvider.getExecutor(threadPool);

        this.taskContext = taskContext;
    }

    @Override
    public IndexShardSearcher get(final Long indexShardId) {
        final IndexWriter indexWriter = getWriter(indexShardId);
        final Key key = new Key(indexShardId, indexWriter);
        return computeIfAbsent(key, this::create);
    }

    @Override
    public boolean isCached(final Long indexShardId) {
        final IndexWriter indexWriter = getWriter(indexShardId);
        final Key key = new Key(indexShardId, indexWriter);
        IndexShardSearcherImpl result = getQuiet(key);

        return result != null;
    }

    private IndexShardSearcherImpl create(final Key key) {
        try {
            final IndexShard indexShard = indexShardService.loadById(key.indexShardId);
            if (indexShard == null) {
                throw new SearchException("Unable to find index shard with id = " + key.indexShardId);
            }

            return new IndexShardSearcherImpl(indexShard, key.indexWriter);
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }
    }

    @Override
    protected void destroy(final Key key, final Object value) {
        if (value != null && value instanceof IndexShardSearcherImpl) {
            final IndexShardSearcherImpl indexShardSearcher = (IndexShardSearcherImpl) value;

            closing.incrementAndGet();
            executor.execute(() -> {
                try {
                    taskContext.setName("Closing searcher");
                    taskContext.setInfo("Closing searcher for index shard " + key.indexShardId);

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
            super.clear();

            // Report on closing progress.
            if (closing.get() > 0) {
                // Create a scheduled executor for us to continually log close progress.
                executor = Executors.newSingleThreadScheduledExecutor();
                // Start logging action progress.
                executor.scheduleAtFixedRate(() -> LOGGER.info(() -> "Waiting for " + closing.get() + " readers to close"), 10, 10, TimeUnit.SECONDS);

                while (closing.get() > 0) {
                    ThreadUtil.sleep(500);
                }
            }

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
        return getMaxCacheEntries();
    }

    @Override
    public void setMaxOpenShards(final long maxOpenShards) {
        setMaxCacheEntries(maxOpenShards);
    }

    /**
     * This is called by the lifecycle service and remove writers that are past their TTL, TTI or LRU items that exceed the capacity.
     */
    @StroomFrequencySchedule("10m")
    @JobTrackedSchedule(jobName = "Index Searcher Cache Refresh", description = "Job to refresh index shard searchers in the cache")
    public void refresh() {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();

        final List<Key> keys = getKeysWithExpiryCheck();
        keys.forEach(key -> {
            final IndexShardSearcher indexShardSearcher = getQuiet(key);
            if (indexShardSearcher != null) {
                try {
                    indexShardSearcher.getSearcherManager().maybeRefresh();
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

        public Key(final long indexShardId, final IndexWriter indexWriter) {
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
