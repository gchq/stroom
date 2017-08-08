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

package stroom.search.server.shard;

import net.sf.ehcache.CacheManager;
import org.apache.lucene.index.IndexWriter;
import org.springframework.stereotype.Component;
import stroom.cache.AbstractCacheBean;
import stroom.index.server.IndexShardWriter;
import stroom.index.server.IndexShardWriterCache;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShardService;
import stroom.search.server.SearchException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class IndexShardSearcherCache extends AbstractCacheBean<IndexShardSearcherCache.Key, IndexShardSearcherImpl> {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexShardSearcherCache.class);
    private static final int MAX_CACHE_ENTRIES = 2;

    private final IndexShardService indexShardService;
    private final IndexShardWriterCache indexShardWriterCache;

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

    @Inject
    IndexShardSearcherCache(final CacheManager cacheManager,
                            final IndexShardService indexShardService,
                            final IndexShardWriterCache indexShardWriterCache) {
        super(cacheManager, "Index Shard Searcher Cache", MAX_CACHE_ENTRIES);
        this.indexShardService = indexShardService;
        this.indexShardWriterCache = indexShardWriterCache;

        setMaxIdleTime(60, TimeUnit.SECONDS);
        setMaxLiveTime(120, TimeUnit.SECONDS);
    }

    IndexShardSearcher borrowSearcher(final Long indexShardId) {
        IndexShardSearcherImpl result = null;

        while (result == null) {
            final IndexWriter indexWriter = getWriter(indexShardId);
            final Key key = new Key(indexShardId, indexWriter);
            result = computeIfAbsent(key, this::create);

            if (!result.incrementInUse()) {
                remove(key);
                result = null;
            }
        }

        return result;
    }

    void returnSearcher(final IndexShardSearcher indexShardSearcher) {
        ((IndexShardSearcherImpl) indexShardSearcher).decrementInUse();
    }

    boolean isCached(final Long indexShardId) {
        final IndexWriter indexWriter = getWriter(indexShardId);
        final Key key = new Key(indexShardId, indexWriter);
        IndexShardSearcherImpl result = getQuiet(key);

        return result != null && !result.destroyed();
    }

    private IndexShardSearcherImpl create(final Key key) {
        final IndexShard indexShard = indexShardService.loadById(key.indexShardId);
        if (indexShard == null) {
            throw new SearchException("Unable to find index shard with id = " + key.indexShardId);
        }

        return new IndexShardSearcherImpl(indexShard, key.indexWriter);
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
        ScheduledExecutorService executor = null;

        try {
            if (size() > 0) {
                // Create a scheduled executor for us to continually log index shard writer action progress.
                executor = Executors.newSingleThreadScheduledExecutor();
                // Start logging action progress.
                executor.scheduleAtFixedRate(() -> LOGGER.info(() -> "Waiting for " + size() + " index shards to close"), 10, 10, TimeUnit.SECONDS);
            }

            super.clear();

        } finally {
            if (executor != null) {
                // Shut down the progress logging executor.
                executor.shutdown();
            }
        }

        LOGGER.info(() -> "Finished clearing index shard searcher cache");
    }

    public long getMaxOpenShards() {
        return getMaxCacheEntries();
    }

    public void setMaxOpenShards(final long maxOpenShards) {
        setMaxCacheEntries(maxOpenShards);
    }
}
