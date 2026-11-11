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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public abstract class AbstractPoolCache<K, V> implements Clearable, HasSystemInfo {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractPoolCache.class);
    private static final String PARAM_NAME_LIMIT = "limit";

    // Holds all 1-* pooled items for each K. PoolKey hashed on object instance not content.
    private final LoadingStroomCache<PoolKey<K>, PoolItem<V>> cache;
    // The pool of items for each K that has items borrowed or waiting to be borrowed.
    // A KeyPool is only changed inside keyMap.compute() for its K.
    private final Map<K, KeyPool<K>> keyMap = new ConcurrentHashMap<>();
    // Source of the ids of new KeyPools, so an item can only be returned to the pool it was borrowed from.
    private final AtomicLong poolIdSequence = new AtomicLong(PoolKey.NO_POOL_ID);

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

            keyMap.computeIfPresent(key.getKey(), (k, pool) -> {
                pool.removeIdle(key);
                return pool.isUnused()
                        ? null
                        : pool;
            });
        }
    }

    /**
     * Forces the eviction of all pooled items associated with this key.
     * Items that are currently borrowed will be dropped rather than pooled when they are returned,
     * as will any item that is being created when this is called.
     *
     * @param key The key of the entry to evict.
     */
    public void invalidate(final K key) {
        LOGGER.debug("Invalidating key {}", key);
        // Removing the pool means the next borrow creates a new one with a new id, and borrowed items
        // from this one won't be accepted back into it.
        final KeyPool<K> pool = keyMap.remove(key);
        if (pool != null) {
            final List<PoolKey<K>> drainedPoolKeys = pool.drainIdle();
            LOGGER.debug("Invalidating {} idle poolKeys for key {}", drainedPoolKeys.size(), key);

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

            if (!usePool) {
                return new PoolItem<>(new PoolKey<>(key), internalCreateValue(key));
            }

            // Take an idle pool key from the pool for this key, or a new one if there are none idle.
            // The pool is created here rather than on return so the key is visible to invalidate()
            // while its first item is being created or is borrowed.
            final AtomicReference<PoolKey<K>> poolKeyRef = new AtomicReference<>();
            keyMap.compute(key, (k, pool) -> {
                final KeyPool<K> keyPool = pool != null
                        ? pool
                        : new KeyPool<>(poolIdSequence.incrementAndGet());
                poolKeyRef.set(keyPool.lease(k));
                return keyPool;
            });
            final PoolKey<K> poolKey = poolKeyRef.get();

            // Get an item from the cache using the pool key.
            try {
                return cache.get(poolKey);
            } catch (final RuntimeException e) {
                // The item was never borrowed so give its lease back.
                release(poolKey, false);
                throw e;
            }
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

                // If the item is no longer in the cache there is no point making it available again.
                // The cache may have been cleared/rebuilt or the item aged off.
                final boolean isInCache = cache.getIfPresent(poolKey).isPresent();
                final boolean isPooled = release(poolKey, isInCache);
                if (!isPooled) {
                    LOGGER.debug("Returned item {} was not pooled, dropping it.", item);
                    if (isInCache) {
                        // The pool it came from has been invalidated so the value may be stale.
                        cache.invalidate(poolKey);
                    }
                }
            } catch (final RuntimeException e) {
                LOGGER.debug(e.getMessage(), e);
                throw new RuntimeException(e.getMessage(), e);
            }
        }
    }

    /// Gives the lease on a borrowed pool key back to the pool for its key.
    ///
    /// @return True if the pool key was made available to borrow again.
    private boolean release(final PoolKey<K> poolKey, final boolean makeAvailable) {
        final AtomicBoolean isPooled = new AtomicBoolean(false);
        keyMap.computeIfPresent(poolKey.getKey(), (k, pool) -> {
            isPooled.set(pool.release(poolKey, makeAvailable));
            return pool.isUnused()
                    ? null
                    : pool;
        });
        return isPooled.get();
    }

    public void clear() {
        keyMap.clear();
        cache.clear();
    }

    private String getKeySizes() {
        final StringBuilder sb = new StringBuilder();
        final AtomicLong size = new AtomicLong();
        keyMap.forEach((k, pool) -> {
            sb.append("\t");
            sb.append(k);
            sb.append(" = ");
            sb.append(pool.getIdleCount());
            sb.append("\n");
            size.getAndAdd(pool.getIdleCount());
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

    /**
     * @return The keys that have items in the pool, whether they are borrowed or waiting to be borrowed.
     */
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
                                "poolItemCount", entry.getValue().getIdleCount(),
                                "borrowedItemCount", entry.getValue().getBorrowedCount()))
                .collect(Collectors.toList());

        final int totalCount = keyMap.values()
                .stream()
                .mapToInt(KeyPool::getIdleCount)
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


    // --------------------------------------------------------------------------------


    /// The pool of items for one key. Each pool has a unique id and only accepts back the pool keys
    /// it leased, so items borrowed before the key was invalidated are not pooled again.
    /// Only changed inside `keyMap.compute()` for its key.
    private static final class KeyPool<K> {

        private final long id;
        // Pool keys of the items waiting to be borrowed
        private final LinkedBlockingDeque<PoolKey<K>> idlePoolKeys = new LinkedBlockingDeque<>();
        private final AtomicInteger borrowedCount = new AtomicInteger();

        private KeyPool(final long id) {
            this.id = id;
        }

        private PoolKey<K> lease(final K key) {
            PoolKey<K> poolKey = idlePoolKeys.poll();
            if (poolKey == null) {
                poolKey = new PoolKey<>(key, id);
            }
            borrowedCount.incrementAndGet();
            return poolKey;
        }

        /// @return True if the pool key was made available to borrow again. False if it was leased by
        /// a different pool, or makeAvailable is false.
        private boolean release(final PoolKey<K> poolKey, final boolean makeAvailable) {
            if (poolKey.getPoolId() != id) {
                LOGGER.debug("Ignoring return of poolKey from pool {} to pool {}", poolKey.getPoolId(), id);
                return false;
            }
            borrowedCount.decrementAndGet();
            if (makeAvailable) {
                idlePoolKeys.offer(poolKey);
            }
            return makeAvailable;
        }

        private void removeIdle(final PoolKey<K> poolKey) {
            idlePoolKeys.remove(poolKey);
        }

        private List<PoolKey<K>> drainIdle() {
            final List<PoolKey<K>> drainedPoolKeys = new ArrayList<>();
            idlePoolKeys.drainTo(drainedPoolKeys);
            return drainedPoolKeys;
        }

        private boolean isUnused() {
            return idlePoolKeys.isEmpty() && borrowedCount.get() <= 0;
        }

        private int getIdleCount() {
            return idlePoolKeys.size();
        }

        private int getBorrowedCount() {
            return borrowedCount.get();
        }
    }
}
