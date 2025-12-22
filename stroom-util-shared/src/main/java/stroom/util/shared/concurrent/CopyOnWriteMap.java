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

package stroom.util.shared.concurrent;

import stroom.util.shared.NullSafe;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * A {@link ConcurrentMap} implementation that performs a copy of the underlying {@link Map}
 * for every write operation. All read operations are delegated to the underlying {@link Map}
 * with no locking or concurrency overhead at all.
 * All concurrency overhead is on the write operations.
 * All methods that directly mutate the map are synchronized, even ones like putIfAbsent.
 * Intended for 'write once (or very few times) and read many' type use cases.
 *
 * <p>
 * Note: All read access to the underlying {@link Map} is via an unmodifiable view so methods like
 * {@link ConcurrentMap#entrySet()} cannot be used to indirectly mutate the underlying map.
 * </p>
 *
 * @param <K>
 * @param <V>
 */
public class CopyOnWriteMap<K, V> implements ConcurrentMap<K, V> {

    private final IntFunction<Map<K, V>> mapSupplier;
    private volatile Map<K, V> map;

    /**
     * Creates a new {@link CopyOnWriteMap} that delegates to a {@link HashMap}.
     */
    public CopyOnWriteMap() {
        this(HashMap::new, null);
    }

    /**
     * Creates a new {@link CopyOnWriteMap} that delegates to the Map implementation supplied by mapSupplier.
     *
     * @param mapSupplier An {@link IntFunction} that accepts a value for the initial size of the map
     *                    and returns a new {@link Map} instance. The returned {@link Map} must be
     *                    mutable and mapSupplier must NOT hold onto the supplied instance.
     */
    public CopyOnWriteMap(final IntFunction<Map<K, V>> mapSupplier) {
        this(mapSupplier, null);
    }

    /**
     * Creates a new {@link CopyOnWriteMap} populated with the entries from map and
     * that delegates to a {@link HashMap}
     *
     * @param map The map to copy values from.
     */
    public CopyOnWriteMap(final Map<K, V> map) {
        this(HashMap::new, map);
    }

    /**
     * @param mapSupplier An {@link IntFunction} that accepts a value for the initial size of the map
     *                    and returns a new {@link Map} instance. The returned {@link Map} must be
     *                    mutable and mapSupplier must NOT hold onto the supplied instance.
     * @param map         The map to copy values from.
     */
    public CopyOnWriteMap(final IntFunction<Map<K, V>> mapSupplier,
                          final Map<K, V> map) {
        this.mapSupplier = mapSupplier != null
                ? mapSupplier
                : HashMap::new;
        this.map = unmodifiableCopyOf(this.mapSupplier, map);
    }

    private Map<K, V> unmodifiableCopyOf(final Map<K, V> map) {
        return unmodifiableCopyOf(this.mapSupplier, map);
    }

    private static <K, V> Map<K, V> unmodifiableCopyOf(final IntFunction<Map<K, V>> mapSupplier,
                                                       final Map<K, V> map) {
        Objects.requireNonNull(mapSupplier);
        if (NullSafe.isEmptyMap(map)) {
            return Collections.emptyMap();
        } else {
            // Create a new map in case map is not the same impl as mapSupplier supplies.
            final Map<K, V> copy = mapSupplier.apply(map.size());
            copy.putAll(map);
            return Collections.unmodifiableMap(copy);
        }
    }

    @Override
    public boolean containsKey(final Object k) {
        return map.containsKey(k);
    }

    @Override
    public boolean containsValue(final Object v) {
        return map.containsValue(v);
    }

    /**
     * Unlink {@link Map#entrySet()} this does NOT allow mutation of the returned {@link Set}.
     */
    @Override
    public Set<Entry<K, V>> entrySet() {
        return map.entrySet();
    }

    @Override
    public V get(final Object k) {
        return map.get(k);
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    /**
     * Unlink {@link Map#keySet()} this does NOT allow mutation of the returned {@link Set}.
     */
    @Override
    public Set<K> keySet() {
        return map.keySet();
    }

    @Override
    public int size() {
        return map.size();
    }

    /**
     * Unlink {@link Map#values()} this does NOT allow mutation of the returned {@link Set}.
     */
    @Override
    public Collection<V> values() {
        return map.values();
    }

    @Override
    public synchronized void clear() {
        this.map = Collections.emptyMap();
    }

    private synchronized Map<K, V> copyDelegateMap(final int additionalInitialSize) {
        if (additionalInitialSize < 0) {
            throw new IllegalArgumentException("additionalInitialSize must be >= 0");
        }
        final Map<K, V> copy = mapSupplier.apply(map.size() + additionalInitialSize);
        copy.putAll(map);
        return copy;
    }

    @Override
    public synchronized V put(final K k, final V v) {
        // Assume that the copy will be size of delegate + 1 for this put
        final Map<K, V> copy = copyDelegateMap(1);
        final V prev = copy.put(k, v);
        this.map = Collections.unmodifiableMap(copy);
        return prev;
    }

    @Override
    public synchronized void putAll(final Map<? extends K, ? extends V> map) {
        // Assume that the copy will be size of delegate + size of map
        final Map<K, V> copy = copyDelegateMap(map.size());
        copy.putAll(map);
        this.map = Collections.unmodifiableMap(copy);
    }

    @Override
    public synchronized V remove(final Object key) {
        final Map<K, V> copy = copyDelegateMap(0);
        final V prev = copy.remove(key);
        this.map = Collections.unmodifiableMap(copy);
        return prev;
    }

    @Override
    public synchronized V putIfAbsent(final K k, final V v) {
        if (!containsKey(k)) {
            return put(k, v);
        } else {
            return get(k);
        }
    }

    @Override
    public synchronized V computeIfAbsent(final K key,
                                          final Function<? super K, ? extends V> mappingFunction) {
        return getWithCopy(copy ->
                copy.computeIfAbsent(key, mappingFunction));
    }

    @Override
    public synchronized V computeIfPresent(final K key,
                                           final BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        return getWithCopy(copy ->
                copy.computeIfPresent(key, remappingFunction));
    }

    @Override
    public synchronized V compute(final K key,
                                  final BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        return getWithCopy(copy ->
                copy.compute(key, remappingFunction));
    }

    @Override
    public V merge(final K key,
                   final V value,
                   final BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        return getWithCopy(copy ->
                copy.merge(key, value, remappingFunction));
    }

    @Override
    public synchronized boolean remove(final Object k, final Object v) {
        if (containsKey(k) && get(k).equals(v)) {
            remove(k);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public synchronized boolean replace(final K k, final V original, final V replacement) {
        if (containsKey(k) && get(k).equals(original)) {
            put(k, replacement);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public synchronized V replace(final K k, final V v) {
        if (containsKey(k)) {
            return put(k, v);
        } else {
            return null;
        }
    }

    @Override
    public synchronized void replaceAll(final BiFunction<? super K, ? super V, ? extends V> function) {
        getWithCopy(copy -> {
            copy.replaceAll(function);
            return null;
        });
    }

    private synchronized <T> T getWithCopy(final Function<Map<K, V>, T> copyConsumer) {
        Objects.requireNonNull(copyConsumer);
        final Map<K, V> copy = copyDelegateMap(0);
        final T result = copyConsumer.apply(copy);
        // Re-copy in case the caller is holding the reference to the original copy
        // such that they can mutate it.
        //noinspection Java9CollectionFactory
        this.map = Collections.unmodifiableMap(new HashMap<>(copy));
        return result;
    }

    /**
     * Method for performing bulk write operations on the map that result in the underlying
     * map only being copied once.
     *
     * @param writer A {@link Consumer} of a temporary copy of the underlying map.
     *               This map can be mutated in any way the caller wishes, but the caller
     *               must NOT try to use the map passed to the consumer outside the consumer
     *               or hold onto a reference to it.
     */
    public synchronized void bulkWrite(final Consumer<Map<K, V>> writer) {
        if (writer != null) {
            // No way of knowing how big the map will get
            final Map<K, V> copy = copyDelegateMap(0);
            writer.accept(copy);
            // Re-copy in case the caller is holding the reference to the original copy
            // such that they can mutate it.
            this.map = unmodifiableCopyOf(copy);
        }
    }
}
