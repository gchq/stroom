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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class AbstractCacheBean<K, V> implements CacheBean<K, V> {
    private final Ehcache ehcache;
    private final Ehcache selfPopulatingCache;

    private static class Mapping<K, V> {
        private final K key;
        private final Function<? super K, ? extends V> mappingFunction;

        Mapping(final K key, final Function<? super K, ? extends V> mappingFunction) {
            this.key = key;
            this.mappingFunction = mappingFunction;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            final Mapping innerKey = (Mapping) o;

            return key.equals(innerKey.key);
        }

        @Override
        public int hashCode() {
            return key.hashCode();
        }
    }

    public AbstractCacheBean(final CacheManager cacheManager, final String name, final int maxCacheEntries) {
        this(cacheManager, new CacheConfiguration(name, maxCacheEntries));
    }

    @SuppressWarnings("unchecked")
    public AbstractCacheBean(final CacheManager cacheManager, final CacheConfiguration cacheConfiguration) {
        ehcache = new Cache(cacheConfiguration);
        selfPopulatingCache = new SelfPopulatingCache(ehcache, key -> {
            try {
                final Mapping mapping = (Mapping) key;
                return mapping.mappingFunction.apply(mapping.key);
            } catch (final Throwable t) {
                return t;
            }
        });
        ehcache.getCacheEventNotificationService().registerListener(new CacheListener(this));
        cacheManager.addCache(ehcache);
    }

    protected long getMaxCacheEntries() {
        return selfPopulatingCache.getCacheConfiguration().getMaxEntriesLocalHeap();
    }

    protected void setMaxCacheEntries(final long maxCacheEntries) {
        selfPopulatingCache.getCacheConfiguration().setMaxEntriesLocalHeap(maxCacheEntries);
    }

    public V computeIfAbsent(final K key, final Function<? super K, ? extends V> mappingFunction) {
        return getValue(selfPopulatingCache.get(new Mapping<K, V>(key, mappingFunction)));
    }

    protected V computeIfAbsentQuiet(final K key, final Function<? super K, ? extends V> mappingFunction) {
        return getValue(selfPopulatingCache.getQuiet(new Mapping<K, V>(key, mappingFunction)));
    }

    protected V getQuiet(final K key) {
        return getValue(ehcache.getQuiet(new Mapping<K, V>(key, null)));
    }

//    protected V get(final K key) {
//        return getValue(ehcache.get(new Mapping<K, V>(key, null)));
//    }

    @SuppressWarnings("unchecked")
    protected List<K> getKeys() {
        final List list = selfPopulatingCache.getKeys();
        final List<K> keys = new ArrayList<>(list.size());
        for (final Object item : list) {
            keys.add((K) ((Mapping) item).key);
        }
        return keys;
    }

    @SuppressWarnings("unchecked")
    private V getValue(final Element element) {
        try {
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

    public void remove(final K key) {
        selfPopulatingCache.remove(new Mapping<K, V>(key, null));
    }

    public void setMaxIdleTime(final long maxIdleTime, final TimeUnit unit) {
        setMaxIdleTime(TimeUnit.SECONDS.convert(maxIdleTime, unit));
    }

    public void setMaxLiveTime(final long maxLiveTime, final TimeUnit unit) {
        setMaxLiveTime(TimeUnit.SECONDS.convert(maxLiveTime, unit));
    }

    public void setMaxIdleTime(final long maxIdleTime) {
        selfPopulatingCache.getCacheConfiguration().setTimeToIdleSeconds(maxIdleTime);
    }

    public void setMaxLiveTime(final long maxLiveTime) {
        selfPopulatingCache.getCacheConfiguration().setTimeToLiveSeconds(maxLiveTime);
    }

    public void evictExpiredElements() {
        selfPopulatingCache.evictExpiredElements();
    }

    @Override
    public void clear() {
        CacheUtil.clear(ehcache);
    }

    public CacheInfo getInfo() {
        return CacheUtil.getInfo(ehcache);
    }

    protected int size() {
        return selfPopulatingCache.getSize();
    }

    @SuppressWarnings("unchecked")
    private void destroy(final Element element) {
        if (element != null) {
            destroy((K) ((Mapping) element.getObjectKey()).key, (V) element.getObjectValue());
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

        CacheListener(final AbstractCacheBean<?, ?> parent) {
            this.parent = parent;
        }

        @Override
        public void notifyRemoveAll(final Ehcache cache) {
            // parent.destroyAll(cache);
            try {
                throw new IllegalStateException("notifyRemoveAll() is not expected");
            } catch (final Exception e) {
                // Hopefully clear will not be called.
                LOGGER.error(e.getMessage(), e);
            }
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
