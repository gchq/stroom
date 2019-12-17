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

package stroom.cache.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.RemovalListener;
import stroom.cache.api.CacheManager;
import stroom.cache.api.ICache;
import stroom.util.cache.CacheConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Singleton;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

@Singleton
public class CacheManagerImpl implements CacheManager {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CacheManagerImpl.class);

    private final Map<String, CacheHolder> caches = new ConcurrentHashMap<>();

    @Override
    public synchronized void close() {
        caches.forEach((k, v) -> CacheUtil.clear(v.getCache()));
    }

    @Override
    public <K, V> ICache<K, V> create(final String name, final Supplier<CacheConfig> cacheConfigSupplier) {
        return create(name, cacheConfigSupplier, null, null);
    }

    @Override
    public <K, V> ICache<K, V> create(final String name, final Supplier<CacheConfig> cacheConfigSupplier, final Function<K, V> loadFunction) {
        return create(name, cacheConfigSupplier, loadFunction, null);
    }

    @Override
    public <K, V> ICache<K, V> create(final String name, final Supplier<CacheConfig> cacheConfigSupplier, final Function<K, V> loadFunction, final BiConsumer<K, V> removalNotificationConsumer) {
        final CacheConfig cacheConfig = cacheConfigSupplier.get();

        final Caffeine cacheBuilder = Caffeine.newBuilder();
        if (cacheConfig.getMaximumSize() != null) {
            cacheBuilder.maximumSize(cacheConfig.getMaximumSize());
        }
        if (cacheConfig.getExpireAfterAccess() != null) {
            cacheBuilder.expireAfterAccess(cacheConfig.getExpireAfterAccess(), TimeUnit.MILLISECONDS);
        }
        if (cacheConfig.getExpireAfterWrite() != null) {
            cacheBuilder.expireAfterWrite(cacheConfig.getExpireAfterWrite(), TimeUnit.MILLISECONDS);
        }
        if (removalNotificationConsumer != null) {
            final RemovalListener<K, V> removalListener = (key, value, cause) -> {
                LOGGER.debug(() -> "Removal notification for key " + key + ", value " + value + ", cause " + cause);
                removalNotificationConsumer.accept(key, value);
            };
            cacheBuilder.removalListener(removalListener);
        }

        if (loadFunction != null) {
            final CacheLoader<K, V> cacheLoader = loadFunction::apply;
            final LoadingCache<K, V> cache = cacheBuilder.build(cacheLoader);
            registerCache(name, cacheBuilder, cache);

            return new ICache<K, V>() {
                @Override
                public V get(final K key) {
                    return cache.get(key);
                }

                @Override
                public void put(final K key, final V value) {
                    cache.put(key, value);
                }

                @Override
                public Optional<V> getOptional(final K key) {
                    return Optional.ofNullable(cache.getIfPresent(key));
                }

                @Override
                public Map<K, V> asMap() {
                    return cache.asMap();
                }

                @Override
                public Collection<V> values() {
                    return cache.asMap().values();
                }

                @Override
                public void invalidate(final K key) {
                    cache.invalidate(key);
                }

                @Override
                public void remove(final K key) {
                    cache.invalidate(key);
                    cache.cleanUp();
                }

                @Override
                public void evictExpiredElements() {
                    cache.cleanUp();
                }

                @Override
                public long size() {
                    return cache.estimatedSize();
                }

                @Override
                public void clear() {
                    CacheUtil.clear(cache);
                }
            };

        } else {

            final Cache<K, V> cache = cacheBuilder.build();
            registerCache(name, cacheBuilder, cache);

            return new ICache<K, V>() {
                @Override
                public V get(final K key) {
                    return cache.getIfPresent(key);
                }

                @Override
                public void put(final K key, final V value) {
                    cache.put(key, value);
                }

                @Override
                public Optional<V> getOptional(final K key) {
                    return Optional.ofNullable(cache.getIfPresent(key));
                }

                @Override
                public Map<K, V> asMap() {
                    return cache.asMap();
                }

                @Override
                public Collection<V> values() {
                    return cache.asMap().values();
                }

                @Override
                public void invalidate(final K key) {
                    cache.invalidate(key);
                }

                @Override
                public void remove(final K key) {
                    cache.invalidate(key);
                    cache.cleanUp();
                }

                @Override
                public void evictExpiredElements() {
                    cache.cleanUp();
                }

                @Override
                public long size() {
                    return cache.estimatedSize();
                }

                @Override
                public void clear() {
                    CacheUtil.clear(cache);
                }
            };
        }
    }

//    @Override
//    public void clear(final String name) {
//        final CacheHolder cacheHolder = caches.get(name);
//        if (cacheHolder != null) {
//            CacheUtil.clear(cacheHolder.getCache());
//        }
//    }

    //    @Override
    public void registerCache(final String alias, final Caffeine cacheBuilder, final Cache cache) {
        if (caches.containsKey(alias)) {
            throw new RuntimeException("A cache called '" + alias + "' already exists");
        }

        replaceCache(alias, cacheBuilder, cache);
    }

    //    @Override
    public void replaceCache(final String alias, final Caffeine cacheBuilder, final Cache cache) {
        final CacheHolder existing = caches.put(alias, new CacheHolder(cacheBuilder, cache));
        if (existing != null) {
            CacheUtil.clear(existing.getCache());
        }
    }

    public Map<String, CacheHolder> getCaches() {
        return caches;
    }
}
