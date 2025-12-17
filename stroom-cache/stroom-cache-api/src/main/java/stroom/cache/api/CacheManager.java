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

package stroom.cache.api;

import stroom.util.cache.CacheConfig;

import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public interface CacheManager extends AutoCloseable {

    /**
     * Create a simple cache that must be manually loaded with
     * {@link StroomCache#put(Object, Object)} or {@link StroomCache#get(Object, Function)}
     * calls.
     * Also registers the cache with the {@link CacheManager} so {@link CacheManager#close()} can
     * be used to clear down all caches.
     *
     * @param name                        Name of the cache
     * @param cacheConfigSupplier         Supplier of config for the cache. When {@link Supplier#get()} is
     *                                    called it must supply the latest state of the config as it will
     *                                    be used to rebuild the cache from updated config.
     * @param removalNotificationConsumer A listener that is called each time an entry is removed.
     * @return The cache object.
     */
    <K, V> StroomCache<K, V> create(
            final String name,
            final Supplier<CacheConfig> cacheConfigSupplier,
            final BiConsumer<K, V> removalNotificationConsumer);

    /**
     * Create a simple cache that must be manually loaded with
     * {@link StroomCache#put(Object, Object)} or {@link StroomCache#get(Object, Function)}
     * calls.
     * Also registers the cache with the {@link CacheManager} so {@link CacheManager#close()} can
     * be used to clear down all caches.
     *
     * @param name                Name of the cache
     * @param cacheConfigSupplier Supplier of config for the cache. When {@link Supplier#get()} is
     *                            called it must supply the latest state of the config as it will
     *                            be used to rebuild the cache from updated config.
     * @return The cache object.
     */
    default <K, V> StroomCache<K, V> create(
            final String name,
            final Supplier<CacheConfig> cacheConfigSupplier) {

        return create(
                name,
                cacheConfigSupplier,
                null);
    }

    /**
     * Create a loading cache that will attempt to load entries into the cache on demand.
     * Also registers the cache with the {@link CacheManager} so {@link CacheManager#close()} can
     * be used to clear down all caches.
     *
     * @param name                        Name of the cache
     * @param cacheConfigSupplier         Supplier of config for the cache. When {@link Supplier#get()} is
     *                                    called it must supply the latest state of the config as it will
     *                                    be used to rebuild the cache from updated config.
     * @param loadFunction                A function to load an entry into the cache for a given key. Function should
     *                                    return null if the key is not know to the load function.
     * @param removalNotificationConsumer A listener that is called each time an entry is removed.
     * @return The cache object.
     */
    <K, V> LoadingStroomCache<K, V> createLoadingCache(
            final String name,
            final Supplier<CacheConfig> cacheConfigSupplier,
            final Function<K, V> loadFunction,
            final BiConsumer<K, V> removalNotificationConsumer);

    /**
     * Create a loading cache that will attempt to load entries into the cache on demand.
     * Also registers the cache with the {@link CacheManager} so {@link CacheManager#close()} can
     * be used to clear down all caches.
     *
     * @param name                Name of the cache
     * @param cacheConfigSupplier Supplier of config for the cache. When {@link Supplier#get()} is
     *                            called it must supply the latest state of the config as it will
     *                            be used to rebuild the cache from updated config.
     * @param loadFunction        A function to load an entry into the cache for a given key. Function should
     *                            return null if the key is not know to the load function.
     * @return The cache object.
     */
    default <K, V> LoadingStroomCache<K, V> createLoadingCache(
            final String name,
            final Supplier<CacheConfig> cacheConfigSupplier, final Function<K, V> loadFunction) {

        return createLoadingCache(
                name,
                cacheConfigSupplier,
                loadFunction,
                null);
    }

    boolean exists(final String name);

    <K, V> StroomCache<K, V> getCache(final String name);

    <K, V> LoadingStroomCache<K, V> getLoadingCache(final String name);

    /**
     * Clears down all caches registered with {@link CacheManager}.
     */
    void close();

    Set<String> getCacheNames();
}
