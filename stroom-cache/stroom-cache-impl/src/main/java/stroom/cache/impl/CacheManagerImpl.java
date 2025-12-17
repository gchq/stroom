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

package stroom.cache.impl;

import stroom.cache.api.CacheExistsException;
import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.cache.api.StroomCache;
import stroom.util.cache.CacheConfig;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LogUtil;
import stroom.util.metrics.Metrics;
import stroom.util.shared.NullSafe;
import stroom.util.shared.cache.CacheIdentity;
import stroom.util.sysinfo.HasSystemInfo;
import stroom.util.sysinfo.SystemInfoResult;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton
public class CacheManagerImpl implements CacheManager, HasSystemInfo {

    private static final String PARAM_NAME_LIMIT = "limit";
    private static final String PARAM_NAME_CACHE_NAME = "name";

    private final Map<String, StroomCache<?, ?>> caches = new ConcurrentHashMap<>();
    private final Provider<Metrics> metricsProvider;

    @Inject
    public CacheManagerImpl(final Provider<Metrics> metricsProvider) {
        this.metricsProvider = metricsProvider;
    }

    /**
     * This is only for tests to save providing a {@link Metrics} instance
     */
    public CacheManagerImpl() {
        this.metricsProvider = null;
    }

    @Override
    public synchronized void close() {
        caches.forEach((name, cache) -> cache.clear());
    }

    @Override
    public <K, V> StroomCache<K, V> create(final String name,
                                           final Supplier<CacheConfig> cacheConfigSupplier,
                                           final BiConsumer<K, V> removalNotificationConsumer) {
        final StroomCache<K, V> cache = new StroomCacheImpl<>(
                name,
                cacheConfigSupplier,
                removalNotificationConsumer,
                metricsProvider);
        registerCache(name, cache);
        return cache;
    }

    @Override
    public <K, V> LoadingStroomCache<K, V> createLoadingCache(
            final String name,
            final Supplier<CacheConfig> cacheConfigSupplier,
            final Function<K, V> loadFunction,
            final BiConsumer<K, V> removalNotificationConsumer) {

        Objects.requireNonNull(loadFunction);
        final LoadingStroomCache<K, V> cache;
        cache = new LoadingStroomCacheImpl<>(
                name,
                cacheConfigSupplier,
                loadFunction,
                removalNotificationConsumer,
                metricsProvider);
        registerCache(name, cache);
        return cache;
    }


    @Override
    public boolean exists(final String name) {
        return caches.containsKey(name);
    }

    public void registerCache(final String name, final StroomCache<?, ?> cache) {
        if (exists(name)) {
            throw new CacheExistsException(name);
        }

        final StroomCache<?, ?> existing = caches.put(name, cache);
        if (existing != null) {
            cache.clear();
        }
    }

    @Override
    public <K, V> StroomCache<K, V> getCache(final String name) {
        try {
            return (StroomCache<K, V>) caches.get(name);
        } catch (final ClassCastException e) {
            throw new RuntimeException(LogUtil.message("Cache {} is not of the expected type: {}", e.getMessage(), e));
        }
    }

    @Override
    public <K, V> LoadingStroomCache<K, V> getLoadingCache(final String name) {
        try {
            return (LoadingStroomCache<K, V>) caches.get(name);
        } catch (final ClassCastException e) {
            throw new RuntimeException(LogUtil.message("Cache {} is not of the expected type: {}", e.getMessage(), e));
        }
    }

    public Map<String, StroomCache<?, ?>> getCaches() {
        return caches;
    }

    @Override
    public Set<String> getCacheNames() {
        return caches.keySet();
    }

    public Set<CacheIdentity> getCacheIdentities() {
        // Note: it is possible to have two caches with the same config, e.g. StatisticsDataSourceCacheImpl
        return caches.values()
                .stream()
                .map(cache ->
                        new CacheIdentity(cache.name(), cache.getBasePropertyPath()))
                .collect(Collectors.toSet());
    }

    @Override
    public SystemInfoResult getSystemInfo(final Map<String, String> params) {
        final Integer limit = NullSafe.getOrElse(
                params.get(PARAM_NAME_LIMIT),
                Integer::valueOf,
                Integer.MAX_VALUE);

        final String cacheName = params.get(PARAM_NAME_CACHE_NAME);

        if (cacheName != null) {
            final StroomCache<?, ?> cache = caches.get(cacheName);

            if (cache != null) {
                final Set<?> keySet = cache.keySet();

                Stream<?> stream = keySet
                        .stream()
                        .limit(limit);

                final List<?> keyList;
                if (!keySet.isEmpty()) {
                    final Object aKey = keySet.iterator().next();

                    if (aKey instanceof Comparable) {
                        stream = stream
                                .sorted();
                    }
                    keyList = stream
                            .map(key -> {
                                try {
                                    // Try and serialise it
                                    JsonUtil.writeValueAsString(key);
                                } catch (final Exception e) {
                                    return "Unable to serialise Key as JSON, dumping as string: "
                                           + key.toString().substring(0, 1_000);
                                }
                                return key;
                            })
                            .collect(Collectors.toList());

                } else {
                    keyList = Collections.emptyList();
                }

                return SystemInfoResult.builder(this)
                        .description("List of cache keys")
                        .addDetail("cacheName", cacheName)
                        .addDetail("keys", keyList)
                        .addDetail("keyCount", keySet.size())
                        .build();
            } else {
                throw new RuntimeException(LogUtil.message("Unknown cache name {}", cacheName));
            }
        } else {
            final List<String> cacheNames = caches.keySet()
                    .stream()
                    .sorted()
                    .limit(limit)
                    .collect(Collectors.toList());


            return SystemInfoResult.builder(this)
                    .description("List of cache names")
                    .addDetail("cacheNames", cacheNames)
                    .addDetail("cacheCount", caches.size())
                    .build();
        }
    }

    @Override
    public SystemInfoResult getSystemInfo() {
        return getSystemInfo(Collections.emptyMap());

    }

    @Override
    public List<ParamInfo> getParamInfo() {
        return List.of(
                ParamInfo.optionalParam(PARAM_NAME_LIMIT,
                        "A limit on the number of keys to return, default is unlimited."),
                ParamInfo.optionalParam(PARAM_NAME_CACHE_NAME,
                        "The name of the cache to see the list of keys for. " +
                        "If not supplied a list of cache names will be returned"));
    }
}
