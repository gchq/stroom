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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.shared.Clearable;
import stroom.util.cache.CacheManager;
import stroom.util.cache.CacheUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public abstract class AbstractPoolCache<K, V> implements Clearable {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractPoolCache.class);

    private static final int MAX_CACHE_ENTRIES = 1000;

    private final LoadingCache<PoolKey<K>, PoolItem<V>> cache;
    private final Map<K, LinkedBlockingDeque<PoolKey<K>>> keyMap = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public AbstractPoolCache(final CacheManager cacheManager, final String name) {
        final RemovalListener<PoolKey<K>, PoolItem<V>> removalListener = notification -> destroy(notification.getKey());
        final CacheLoader<PoolKey<K>, PoolItem<V>> cacheLoader = CacheLoader.from(k -> {
            final V value = internalCreateValue(k.getKey());
            return new PoolItem<>(k, value);
        });
        final CacheBuilder cacheBuilder = CacheBuilder.newBuilder()
                .maximumSize(MAX_CACHE_ENTRIES)
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .removalListener(removalListener);
        cache = cacheBuilder.build(cacheLoader);
        cacheManager.registerCache(name, cacheBuilder, cache);
    }

    protected abstract V internalCreateValue(Object key);


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
            return cache.getUnchecked(poolKey);

        } catch (final RuntimeException e) {
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

            } catch (final RuntimeException e) {
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

    public void clear() {
        CacheUtil.clear(cache);
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
