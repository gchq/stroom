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

package stroom.pipeline.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.cache.api.CacheManager;
import stroom.cache.api.ICache;
import stroom.util.cache.CacheConfig;
import stroom.util.shared.Clearable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public abstract class AbstractPoolCache<K, V> implements Clearable {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractPoolCache.class);

    private final ICache<PoolKey<K>, PoolItem<V>> cache;
    private final Map<K, LinkedBlockingDeque<PoolKey<K>>> keyMap = new ConcurrentHashMap<>();

    public AbstractPoolCache(final CacheManager cacheManager, final String cacheName, final Supplier<CacheConfig> cacheConfigSupplier) {
        cache = cacheManager.create(cacheName, cacheConfigSupplier, this::create, this::destroy);
    }

    private PoolItem<V> create(final PoolKey<K> poolKey) {
        final V value = internalCreateValue(poolKey.getKey());
        return new PoolItem<>(poolKey, value);
    }

    private void destroy(final PoolKey<K> key, final PoolItem<V> value) {
        if (key != null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Destroy\n" + getKeySizes());
            }

            keyMap.compute(key.getKey(), (k, v) -> {
                if (v == null || v.size() == 0) {
                    return null;
                }
                v.remove(key);
                if (v.size() == 0) {
                    return null;
                }
                return v;
            });
        }
    }

    protected abstract V internalCreateValue(K key);

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
                    LinkedBlockingDeque<PoolKey<K>> deque = v;
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

    public void clear() {
        cache.clear();
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
