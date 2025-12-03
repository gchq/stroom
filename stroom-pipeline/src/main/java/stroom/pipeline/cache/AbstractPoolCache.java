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

package stroom.pipeline.cache;

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.util.cache.CacheConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Clearable;
import stroom.util.shared.NullSafe;
import stroom.util.sysinfo.HasSystemInfo;
import stroom.util.sysinfo.SystemInfoResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public abstract class AbstractPoolCache<K, V> implements Clearable, HasSystemInfo {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractPoolCache.class);
    private static final String PARAM_NAME_LIMIT = "limit";

    // Holds all 1-* pooled items for each K. PoolKey hashed on object instance not content.
    private final LoadingStroomCache<PoolKey<K>, PoolItem<V>> cache;
    // Provides all the cache keys (PoolKey) for each K
    private final Map<K, LinkedBlockingDeque<PoolKey<K>>> keyMap = new ConcurrentHashMap<>();

    public AbstractPoolCache(final CacheManager cacheManager,
                             final String cacheName,
                             final Supplier<CacheConfig> cacheConfigSupplier) {
        cache = cacheManager.createLoadingCache(
                cacheName,
                cacheConfigSupplier,
                this::create,
                this::destroy);
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

    /**
     * Forces the eviction of all pooled items associated with this key
     *
     * @param key The key of the entry to evict.
     */
    public void invalidate(final K key) {
        LOGGER.debug("Invalidating key {}", key);
        // Get all the cache key instances for our key K
        final LinkedBlockingDeque<PoolKey<K>> poolKeys = keyMap.get(key);
        if (poolKeys != null && !poolKeys.isEmpty()) {
            final List<PoolKey<K>> drainedPoolKeys = new ArrayList<>();
            final int drainCount = poolKeys.drainTo(drainedPoolKeys);
            LOGGER.debug("Invalidating {} poolKeys for key {}", drainCount, key);

            // Now remove the found cache keys from the cache
            drainedPoolKeys.forEach(cache::invalidate);
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
            final PoolItem<V> val = cache.get(poolKey);

            return val;

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

                if (cache.getIfPresent(poolKey).isEmpty()) {
                    // Item no longer in the cache so no point returning it to the deque
                    // Cache may have been cleared/rebuilt or the item aged off.
                    LOGGER.debug("Returning item {} whose poolKey is not in the cache, dropping it.", item);
                } else {
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
                }
            } catch (final RuntimeException e) {
                LOGGER.debug(e.getMessage(), e);
                throw new RuntimeException(e.getMessage(), e);
            }
        }
    }

    public void clear() {
        keyMap.clear();
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

    /**
     * @return The number of keys in the pool cache
     */
    public long size() {
        return cache.size();
    }

    public Set<K> getKeys() {
        return new HashSet<>(keyMap.keySet());
    }

    abstract Object mapKeyForSystemInfo(final K key);

    @Override
    public SystemInfoResult getSystemInfo(final Map<String, String> params) {
        final Integer limit = NullSafe.getOrElse(
                params.get(PARAM_NAME_LIMIT),
                Integer::valueOf,
                Integer.MAX_VALUE);

        final List<Object> mappedKeys = keyMap
                .entrySet()
                .stream()
                .limit(limit)
                .map(entry ->
                        Map.of(
                                "key", mapKeyForSystemInfo(entry.getKey()),
                                "poolItemCount", NullSafe.get(entry.getValue(), Collection::size)))
                .collect(Collectors.toList());

        final int totalCount = keyMap.values()
                .stream()
                .mapToInt(Collection::size)
                .sum();

        return SystemInfoResult.builder(this)
                .description("List of pool keys")
                .addDetail("keys", mappedKeys)
                .addDetail("totalItemCount", totalCount)
                .addDetail("keyCount", keyMap.size())
                .build();
    }

    @Override
    public SystemInfoResult getSystemInfo() {
        return getSystemInfo(Collections.emptyMap());

    }

    @Override
    public List<ParamInfo> getParamInfo() {
        return List.of(
                ParamInfo.optionalParam(PARAM_NAME_LIMIT,
                        "A limit on the number of keys to return, default is unlimited."));
    }
}
