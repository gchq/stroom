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

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheException;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.constructs.blocking.SelfPopulatingCache;
import net.sf.ehcache.event.CacheEventListenerAdapter;
import org.apache.lucene.index.IndexWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import stroom.index.server.IndexShardWriter;
import stroom.index.server.IndexShardWriterCache;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShardService;
import stroom.search.server.IndexShardSearcher;
import stroom.search.server.IndexShardSearcherImpl;

import javax.inject.Inject;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class IndexShardSearcherCache implements InitializingBean {
    public static final int MAX_OPEN_SHARDS = 2;
    private static final Logger LOGGER = LoggerFactory.getLogger(IndexShardSearcherCache.class);
    private final IndexShardWriterCache indexShardWriterPool;
    private final CacheManager cacheManager;
    private final Cache cache;
    private final SelfPopulatingCache selfPopulatingCache;

    @Inject
    public IndexShardSearcherCache(final IndexShardWriterCache indexShardWriterPool,
            final IndexShardService indexShardService, final CacheManager cacheManager) {
        this.indexShardWriterPool = indexShardWriterPool;
        this.cacheManager = cacheManager;

        final CacheConfiguration cacheConfiguration = new CacheConfiguration("Index Shard Searcher Cache",
                MAX_OPEN_SHARDS);
        cacheConfiguration.setEternal(false);
        cacheConfiguration.setOverflowToOffHeap(false);
        // Allow readers to idle for 1 minute.
        cacheConfiguration.setTimeToIdleSeconds(60);
        // Allow readers to live for 2 minutes.
        cacheConfiguration.setTimeToLiveSeconds(120);
        cache = new Cache(cacheConfiguration) {
            @Override
            public void removeAll() throws IllegalStateException, CacheException {
                @SuppressWarnings("rawtypes")
                final List keys = cache.getKeys();
                for (final Object key : keys) {
                    cache.remove(key);
                }
            }
        };

        cache.getCacheEventNotificationService().registerListener(new CacheEventListenerAdapter() {
            @Override
            public void notifyElementRemoved(final Ehcache cache, final Element element) throws CacheException {
                destroyPool(element);
            }

            @Override
            public void notifyElementEvicted(final Ehcache cache, final Element element) {
                destroyPool(element);
            }

            @Override
            public void notifyElementExpired(final Ehcache cache, final Element element) {
                destroyPool(element);
            }

            @Override
            public void notifyRemoveAll(final Ehcache cache) {
                LOGGER.error("Unexpected call to remove all index shard searchers");
            }
        });

        selfPopulatingCache = new SelfPopulatingCache(cache, key -> {
            final Long shardId = (Long) key;
            if (shardId != null) {
                final IndexShard indexShard = indexShardService.loadById(shardId);
                if (indexShard != null) {
                    final IndexShardSearcherPool pool = createPool(indexShard);
                    pool.setCached(true);
                    return pool;
                }
            }

            return null;
        });
    }

    public IndexShardSearcherPool get(final Long shardId) {
        return getPool(cache.get(shardId));
    }

    public IndexShardSearcherPool getOrCreate(final Long shardId) {
        return getPool(selfPopulatingCache.get(shardId));
    }

    public long getMaxOpenShards() {
        return cache.getCacheConfiguration().getMaxEntriesLocalHeap();
    }

    public void setMaxOpenShards(final long maxOpenShards) {
        cache.getCacheConfiguration().setMaxEntriesLocalHeap(maxOpenShards);
    }

    private IndexShardSearcherPool createPool(final IndexShard indexShard) {
        return new IndexShardSearcherPool(indexShard);
    }

    private void destroyPool(final Element element) {
        final IndexShardSearcherPool pool = getPool(element);
        if (pool != null) {
            pool.setCached(false);
        }
    }

    private IndexShardSearcherPool getPool(final Element element) {
        if (element == null) {
            return null;
        }
        return (IndexShardSearcherPool) element.getObjectValue();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        cacheManager.addCache(cache);
    }

    public interface Pool<T> {
        T borrowObject();

        void returnObject(T object);
    }

    public class IndexShardSearcherPool implements Pool<IndexShardSearcher> {
        private final IndexShard indexShard;
        private final AtomicInteger openCount = new AtomicInteger();
        private volatile IndexShardSearcher indexShardSearcher;
        private volatile ConcurrentLinkedQueue<Throwable> exceptions;
        private volatile boolean cached;
        private volatile boolean open;

        public IndexShardSearcherPool(final IndexShard indexShard) {
            this.indexShard = indexShard;
        }

        @Override
        public IndexShardSearcher borrowObject() {
            openCount.incrementAndGet();
            open();
            return indexShardSearcher;
        }

        @Override
        public void returnObject(final IndexShardSearcher object) {
            openCount.decrementAndGet();
            tryClose();
        }

        private void addException(final Throwable e) {
            if (exceptions == null) {
                exceptions = new ConcurrentLinkedQueue<>();
            }
            exceptions.add(e);
        }

        public Collection<Throwable> getExceptions() {
            return exceptions;
        }

        public boolean hasExceptions() {
            return exceptions != null && exceptions.size() > 0;
        }

        public void setCached(final boolean cached) {
            this.cached = cached;
            if (!cached) {
                tryClose();
            }
        }

        private synchronized void open() {
            if (!open && !hasExceptions()) {
                try {
                    IndexWriter indexWriter = null;
                    Exception writerException = null;
                    try {
                        final IndexShardWriter indexShardWriter = indexShardWriterPool.getWriter(indexShard);
                        if (indexShardWriter != null) {
                            indexWriter = indexShardWriter.getWriter();
                        }
                    } catch (final Exception e) {
                        writerException = e;
                    }

                    indexShardSearcher = new IndexShardSearcherImpl(indexShard, indexWriter);

                    if (writerException != null) {
                        addException(writerException);
                    }

                    // Open the index.
                    indexShardSearcher.open();

                } catch (final Throwable t) {
                    addException(t);
                } finally {
                    open = true;
                }
            }
        }

        private synchronized void close() {
            if (open) {
                try {
                    indexShardSearcher.close();
                } catch (final Exception e) {
                    addException(e);
                } finally {
                    open = false;
                }
            }
        }

        private synchronized void tryClose() {
            if (!cached && openCount.get() == 0) {
                close();
            }
        }
    }
}
