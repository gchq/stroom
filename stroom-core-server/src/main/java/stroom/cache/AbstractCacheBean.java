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

package stroom.cache;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheException;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import net.sf.ehcache.Statistics;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.constructs.blocking.SelfPopulatingCache;
import net.sf.ehcache.event.CacheEventListenerAdapter;
import stroom.cache.shared.CacheInfo;
import stroom.util.logging.StroomLogger;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public abstract class AbstractCacheBean<K, V> implements CacheBean<K, V> {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(AbstractCacheBean.class);
    private final Ehcache cache;

    public AbstractCacheBean(final CacheManager cacheManager, final String name, final int maxCacheEntries) {
        this(cacheManager, new CacheConfiguration(name, maxCacheEntries));
    }

    @SuppressWarnings("unchecked")
    public AbstractCacheBean(final CacheManager cacheManager, final CacheConfiguration cacheConfiguration) {
        Ehcache cache = new Cache(cacheConfiguration);
        cache = new SelfPopulatingCache(cache, key -> {
            try {
                return create((K) key);
            } catch (final Throwable t) {
                return t;
            }
        });
        cache.getCacheEventNotificationService().registerListener(new CacheListener(this));

        this.cache = cache;
        cacheManager.addCache(cache);
    }

    protected abstract V create(K key);

    @SuppressWarnings("unchecked")
    @Override
    public V get(final K k) {
        try {
            final Element element = cache.get(k);
            if (element == null) {
                return null;
            }

            final Object object = element.getObjectValue();
            if (object instanceof RuntimeException) {
                throw (RuntimeException) object;
            }
            if (object instanceof Throwable) {
                final Throwable throwable = (Throwable) object;
                throw new RuntimeException(throwable.getMessage(), throwable);
            }

            return (V) object;

        } catch (final CacheException e) {
            final Throwable cause = e.getCause();
            if (cause != null && cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }

            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    protected V getQuiet(final K k) {
        final Element element = cache.getQuiet(k);
        if (element == null) {
            return null;
        }
        return (V) element.getObjectValue();
    }

    @Override
    public void remove(final K k) {
        cache.remove(k);
    }

    public void setMaxIdleTime(final long maxIdleTime, final TimeUnit unit) {
        setMaxIdleTime(TimeUnit.SECONDS.convert(maxIdleTime, unit));
    }

    public void setMaxLiveTime(final long maxLiveTime, final TimeUnit unit) {
        setMaxLiveTime(TimeUnit.SECONDS.convert(maxLiveTime, unit));
    }

    public void setMaxIdleTime(final long maxIdleTime) {
        cache.getCacheConfiguration().setTimeToIdleSeconds(maxIdleTime);
    }

    public void setMaxLiveTime(final long maxLiveTime) {
        cache.getCacheConfiguration().setTimeToLiveSeconds(maxLiveTime);
    }

    public void evictExpiredElements() {
        cache.evictExpiredElements();
    }

    @Override
    public void clear() {
        cache.removeAll();
    }

    public CacheInfo getInfo() {
        final Statistics stats = cache.getStatistics();

        final CacheInfo info = new CacheInfo(stats.getAssociatedCacheName(), stats.getCacheHits(),
                stats.getOnDiskHits(), stats.getOffHeapHits(), stats.getInMemoryHits(), stats.getCacheMisses(),
                stats.getOnDiskMisses(), stats.getOffHeapMisses(), stats.getInMemoryMisses(), stats.getObjectCount(),
                stats.getAverageGetTime(), stats.getEvictionCount(), stats.getMemoryStoreObjectCount(),
                stats.getOffHeapStoreObjectCount(), stats.getDiskStoreObjectCount(), stats.getSearchesPerSecond(),
                stats.getAverageSearchTime(), stats.getWriterQueueSize());
        return info;
    }

    protected Ehcache getCache() {
        return cache;
    }

    @SuppressWarnings("rawtypes")
    private void destroyAll(final Ehcache cache) {
        try {
            final List keys = cache.getKeys();
            for (final Object key : keys) {
                try {
                    // Try and get the element quietly as we don't want this
                    // call
                    // top extend the life of sessions that should be dying.
                    final Element element = cache.getQuiet(key);
                    destroy(element);
                } catch (final Throwable t) {
                    LOGGER.error(t.getMessage(), t);
                }
            }
        } catch (final Throwable t) {
            LOGGER.error(t.getMessage(), t);
        }
    }

    @SuppressWarnings("unchecked")
    private void destroy(final Element element) {
        if (element != null) {
            destroy((K) element.getObjectKey(), (V) element.getObjectValue());
        }
    }

    protected void destroy(final K key, final V value) {
        if (value != null && value instanceof Destroyable) {
            final Destroyable destroyable = (Destroyable) value;
            destroyable.destroy();
        }
    }

    public interface Destroyable {
        void destroy();
    }

    private static class CacheListener extends CacheEventListenerAdapter {
        private static final StroomLogger LOGGER = StroomLogger.getLogger(CacheListener.class);

        private final AbstractCacheBean<?, ?> parent;

        public CacheListener(final AbstractCacheBean<?, ?> parent) {
            this.parent = parent;
        }

        @Override
        public void notifyRemoveAll(final Ehcache cache) {
            // Hopefully clear will not be called.
            LOGGER.trace("notifyRemoveAll()");
            parent.destroyAll(cache);
        }

        @Override
        public void notifyElementUpdated(final Ehcache cache, final Element element) throws CacheException {
            LOGGER.trace("notifyElementUpdated()");
        }

        @Override
        public void notifyElementRemoved(final Ehcache cache, final Element element) throws CacheException {
            LOGGER.trace("notifyElementRemoved()");
            parent.destroy(element);
        }

        @Override
        public void notifyElementPut(final Ehcache cache, final Element element) throws CacheException {
            LOGGER.trace("notifyElementPut()");
        }

        @Override
        public void notifyElementExpired(final Ehcache cache, final Element element) {
            LOGGER.trace("notifyElementExpired()");
            parent.destroy(element);
        }

        @Override
        public void notifyElementEvicted(final Ehcache cache, final Element element) {
            LOGGER.trace("notifyElementEvicted()");
            parent.destroy(element);
        }

        @Override
        public void dispose() {
            LOGGER.trace("dispose()");
            // Do nothing.
        }
    }
}
