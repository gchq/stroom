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

package stroom.pool;

import org.ehcache.Cache;
import org.ehcache.config.CacheConfiguration;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheEventListenerConfigurationBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.event.CacheEventListener;
import org.ehcache.event.EventType;
import org.ehcache.expiry.Duration;
import org.ehcache.expiry.Expirations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.cache.Loader;
import stroom.util.cache.CentralCacheManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public abstract class AbstractPoolCache<K, V> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractPoolCache.class);

    private static final int MAX_CACHE_ENTRIES = 1000;

    private final Cache<PoolKey, PoolItem> cache;
    private final Map<K, LinkedBlockingDeque<PoolKey<K>>> keyMap = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public AbstractPoolCache(final CentralCacheManager cacheManager, final String name) {
        final Loader<PoolKey, PoolItem> loader = new Loader<PoolKey, PoolItem>() {
            @Override
            public PoolItem load(final PoolKey key) throws Exception {
                final V value = internalCreateValue(key.getKey());
                return new PoolItem<>(key, value);
            }
        };

        final CacheEventListener<PoolKey, PoolItem> cacheEventListener = event -> destroy(event.getKey());
        final CacheEventListenerConfigurationBuilder cacheEventListenerConfigurationBuilder = CacheEventListenerConfigurationBuilder.newEventListenerConfiguration(cacheEventListener, EventType.EVICTED, EventType.EXPIRED, EventType.REMOVED);

        final CacheConfiguration<PoolKey, PoolItem> cacheConfiguration = CacheConfigurationBuilder.newCacheConfigurationBuilder(PoolKey.class, PoolItem.class,
                ResourcePoolsBuilder.heap(MAX_CACHE_ENTRIES))
                .withExpiry(Expirations.timeToIdleExpiration(Duration.of(10, TimeUnit.MINUTES)))
                .withLoaderWriter(loader)
                .add(cacheEventListenerConfigurationBuilder.build())
                .build();

        cache = cacheManager.createCache(name, cacheConfiguration);
    }

    protected abstract V internalCreateValue(Object key);

    @SuppressWarnings("unchecked")
    protected PoolItem<V> internalBorrowObject(final K key, final boolean usePool) {
        try {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Borrow Object\n" + getKeySizes());
            }

            PoolKey<K> poolKey = null;

            if (!usePool) {
                return new PoolItem<>(new PoolKey<>(key), internalCreateValue(key));
            }

            // Get the current deque associated with the key.
            final LinkedBlockingDeque<PoolKey<K>> deque = keyMap.get(key);
            if (deque != null) {
                // Pull a pool key off the deque if there is one.
                poolKey = deque.poll();
            }

            // If there isn't a pool key on the deque then create a new one.
            if (poolKey == null) {
                poolKey = new PoolKey<>(key);
            }

            // Get an item from the cache using the pool key.
            return cache.get(poolKey);

        } catch (final Exception e) {
            LOGGER.debug(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    protected void internalReturnObject(final PoolItem<V> item, final boolean usePool) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Return Object\n" + getKeySizes());
        }

        if (usePool) {
            try {
                final PoolKey<K> poolKey = item.getKey();

                // Make this key available again to other threads.
                // Get the current deque associated with the key.
                keyMap.compute(poolKey.getKey(), (k, v) -> {
                    LinkedBlockingDeque deque = v;
                    if (deque == null) {
                        deque = new LinkedBlockingDeque<>();
                    }
                    // Put the returned item onto the deque.
                    deque.offer(poolKey);
                    return deque;
                });

            } catch (final Exception e) {
                LOGGER.debug(e.getMessage(), e);
                throw new RuntimeException(e.getMessage(), e);
            }
        }
    }

    private void destroy(final PoolKey<K> poolKey) {
        if (poolKey != null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Destroy\n" + getKeySizes());
            }

            keyMap.compute(poolKey.getKey(), (k, v) -> {
                if (v == null || v.size() == 0) {
                    return null;
                }
                v.remove(poolKey);
                if (v.size() == 0) {
                    return null;
                }
                return v;
            });
        }
    }

    protected void clear() {
        CacheUtil.removeAll(cache);
    }

    private String getKeySizes() {
        final StringBuilder sb = new StringBuilder();
        final AtomicLong size = new AtomicLong();
        keyMap.forEach((k, v) -> {
            sb.append("\t");
            sb.append(k);
            sb.append(" = ");
            sb.append(v.size());
            sb.append("\n");
            size.getAndAdd(v.size());
        });
        sb.append("total = ");
        sb.append(size.get());

        return sb.toString();
    }
}
