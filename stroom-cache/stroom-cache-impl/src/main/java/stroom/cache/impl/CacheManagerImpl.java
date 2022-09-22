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

import stroom.cache.api.CacheManager;
import stroom.cache.api.ICache;
import stroom.cache.api.LoadingICache;
import stroom.util.NullSafe;
import stroom.util.cache.CacheConfig;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.sysinfo.HasSystemInfo;
import stroom.util.sysinfo.SystemInfoResult;

import com.fasterxml.jackson.databind.ObjectMapper;

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
import javax.inject.Singleton;

@Singleton
public class CacheManagerImpl implements CacheManager, HasSystemInfo {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CacheManagerImpl.class);

    private static final String PARAM_NAME_LIMIT = "limit";
    private static final String PARAM_NAME_CACHE_NAME = "name";

    private final Map<String, ICache<?, ?>> caches = new ConcurrentHashMap<>();

    @Override
    public synchronized void close() {
        caches.forEach((name, cache) -> cache.clear());
    }

    @Override
    public <K, V> ICache<K, V> create(final String name,
                                      final Supplier<CacheConfig> cacheConfigSupplier,
                                      final BiConsumer<K, V> removalNotificationConsumer) {
        final ICache<K, V> cache = new SimpleICacheImpl<>(
                name,
                cacheConfigSupplier,
                removalNotificationConsumer);
        registerCache(name, cache);
        return cache;
    }

    @Override
    public <K, V> LoadingICache<K, V> createLoadingCache(
            final String name,
            final Supplier<CacheConfig> cacheConfigSupplier,
            final Function<K, V> loadFunction,
            final BiConsumer<K, V> removalNotificationConsumer) {

        Objects.requireNonNull(loadFunction);
        final LoadingICache<K, V> cache;
        cache = new LoadingCacheImpl<>(
                name,
                cacheConfigSupplier,
                loadFunction,
                removalNotificationConsumer);
        registerCache(name, cache);
        return cache;
    }

    public void registerCache(final String name, final ICache<?, ?> cache) {
        if (caches.containsKey(name)) {
            throw new RuntimeException("A cache called '" + name + "' already exists");
        }

        final ICache<?, ?> existing = caches.put(name, cache);
        if (existing != null) {
            cache.clear();
        }
    }

    Map<String, ICache<?, ?>> getCaches() {
        return caches;
    }

    public Set<String> getCacheNames() {
        return caches.keySet();
    }

    @Override
    public SystemInfoResult getSystemInfo(final Map<String, String> params) {
        final Integer limit = NullSafe.getOrElse(
                params.get(PARAM_NAME_LIMIT),
                Integer::valueOf,
                Integer.MAX_VALUE);

        final String cacheName = params.get(PARAM_NAME_CACHE_NAME);

        if (cacheName != null) {
            final ICache<?, ?> cache = caches.get(cacheName);

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
                    final ObjectMapper objectMapper = JsonUtil.getMapper();
                    keyList = stream
                            .map(key -> {
                                try {
                                    // Try and serialise it
                                    objectMapper.writeValueAsString(key);
                                } catch (Exception e) {
                                    return "Unable to serialise Key";
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
