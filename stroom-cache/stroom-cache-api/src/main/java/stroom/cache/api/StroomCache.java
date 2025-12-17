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

import stroom.util.shared.PropertyPath;
import stroom.util.shared.cache.CacheInfo;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * A simple cache that must be manually loaded with {@link StroomCache#put(Object, Object)}
 * or {@link StroomCache#get(Object, Function)} calls.
 */
public interface StroomCache<K, V> {

    /**
     * @return The name of the cache instance.
     */
    String name();

    /**
     * @return The base property path for configuring this cache.
     */
    PropertyPath getBasePropertyPath();

    /**
     * Gets a value from the cache or returns null if it is not present in the cache.
     *
     * @return The cached value or null if not found in the cache.
     */
    V get(K key);

    /**
     * Gets a value from the cache or returns an empty {@link Optional} if it is not present
     * in the cache.
     *
     * @return The cached value or an empty {@link Optional} if not found in the cache.
     */
    Optional<V> getIfPresent(K key);

    /**
     * Gets a value from the cache. If key is not found in the cache then valueProvider
     * will be used to provide a value for that key, that if not null, will be
     * entered into the cache. valueProvider must not modify other cache entries.
     */
    V get(K key, Function<K, V> valueProvider);

    /**
     * Puts a new entry into the cache or overwrites an existing one.
     */
    void put(K key, V value);

    /**
     * Returns true if key exists in the cache. Any load function present on the cache
     * will NOT be called.
     */
    boolean containsKey(K key);

    /**
     * @return A read-only view of the keys in the cache.
     * Changes to the items in the set will change the values in the cache.
     */
    Set<K> keySet();

    /**
     * @return A read-only view of the values in the cache.
     * Changes to the items in the collection will change the values in the cache.
     */
    Collection<V> values();

    /**
     * @return A read-only view of the entries in the cache.
     * Changes to the items in the map will change the values in the cache.
     */
    Map<K, V> asMap();

    /**
     * Perform work on each item in the cache. The order in which entries are consumed is not
     * defined.
     */
    void forEach(BiConsumer<K, V> entryConsumer);

    /**
     * Invalidates the entry for the passed key
     */
    void invalidate(K key);

    /**
     * Invalidates all entries that match entryPredicate
     */
    void invalidateEntries(BiPredicate<K, V> entryPredicate);

    /**
     * Removes the entry for the passed key
     */
    void remove(K key);

    /**
     * Triggers the removal of entries that are deemed expired by the expiry settings.
     */
    void evictExpiredElements();

    /**
     * @return The number of entries currently held in the cache.
     */
    long size();

    /**
     * Clear the cache of all entries and rebuild from current config. Used when you
     * need to change the expiry or size limit for the cache. The rebuild will happen
     * under a write-lock. All cache stats will be reset.
     */
    void rebuild();

    /**
     * Clear the cache of all entries.
     */
    void clear();

    /**
     * @return The cache configuration settings and usage statistics.
     */
    CacheInfo getCacheInfo();
}
