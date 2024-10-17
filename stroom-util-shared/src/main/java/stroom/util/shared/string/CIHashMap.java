/*
 * Copyright 2024 Crown Copyright
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

package stroom.util.shared.string;

import stroom.util.shared.GwtNullSafe;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A standard {@link HashMap} keyed with case-insensitive {@link CIKey} keys.
 * <p>
 * Various methods of {@link HashMap} are overridden so you can interact with the
 * map using {@link String} or {@link CIKey} keys, with {@link String} keys getting
 * converted to {@link CIKey} on the fly.
 * </p>
 */
public class CIHashMap<V> extends HashMap<CIKey, V> {

    @SuppressWarnings("rawtypes")
    private static final Map<CIKey, ?> EMPTY = Collections.emptyMap();

    public static <V> CIHashMap<V> empty() {
        //noinspection unchecked
        return (CIHashMap<V>) EMPTY;
    }

    public CIHashMap(final int initialCapacity, final float loadFactor) {
        super(initialCapacity, loadFactor);
    }

    public CIHashMap(final int initialCapacity) {
        super(initialCapacity);
    }

    public CIHashMap() {
        super();
    }

    public CIHashMap(final Map<? extends CIKey, ? extends V> m) {
        super(m);
    }

    public V put(final String key, final V value) {
        // CIString used to trim all keys
        return super.put(CIKey.of(key), value);
    }

    /**
     * Converts key into a {@link CIKey} before calling {@link CIHashMap#get(Object)}
     */
    // Overload all the super methods that take key as an Object as the compiler
    // won't spot people using a String key.
    public V get(final String key) {
        return super.get(CIKey.of(key));
    }

    public V get(final CIKey key) {
        return super.get(key);
    }

    public boolean replace(final String key, final V oldValue, final V newValue) {
        return super.replace(CIKey.of(key), oldValue, newValue);
    }

    public boolean replace(final CIKey key, final V oldValue, final V newValue) {
        return super.replace(key, oldValue, newValue);
    }

    public V replace(final String key, final V value) {
        return super.replace(CIKey.of(key), value);
    }

    public V replace(final CIKey key, final V value) {
        return super.replace(key, value);
    }

    /**
     * Converts key into a {@link CIKey} before calling {@link CIHashMap#getOrDefault(Object, Object)}
     */
    public V getOrDefault(final String key, final V defaultValue) {
        return super.getOrDefault(CIKey.of(key), defaultValue);
    }

    /**
     * Converts key into a {@link CIKey} before calling {@link CIHashMap#remove(String, Object)}
     */
    public boolean remove(final String key, final Object value) {
        return super.remove(CIKey.of(key), value);
    }

    public boolean remove(final CIKey key, final Object value) {
        return super.remove(key, value);
    }

    /**
     * Converts key into a {@link CIKey} before calling {@link CIHashMap#remove(Object)}
     */
    public V remove(final String key) {
        return super.remove(CIKey.of(key));
    }

    public V remove(final CIKey key) {
        return super.remove(key);
    }

    public boolean containsKey(final String key) {
        return super.containsKey(CIKey.of(key));
    }

    public boolean containsKey(final CIKey key) {
        return super.containsKey(key);
    }

    @Override
    public void putAll(final Map<? extends CIKey, ? extends V> m) {
        super.putAll(m);
    }

    /**
     * Equivalent to {@link Map#putAll(Map)} but for a map keyed by strings.
     */
    public void putAllWithStringKeys(final Map<String, V> map) {
        GwtNullSafe.map(map)
                .forEach(this::put);
    }

    public Set<String> keySetAsStrings() {
        return keySet().stream()
                .map(CIKey::get)
                .collect(Collectors.toSet());
    }

    /**
     * Create a {@link CIKey} keyed map
     */
    public static <V> CIHashMap<V> of(String k1, V v1) {
        return new CIHashMap<>(CIKey.mapOf(k1, v1));
    }

    /**
     * Create a {@link CIKey} keyed map
     */
    public static <V> CIHashMap<V> of(String k1, V v1, String k2, V v2) {
        return new CIHashMap<>(CIKey.mapOf(
                k1, v1,
                k2, v2));
    }

    /**
     * Create a {@link CIKey} keyed map
     */
    public static <V> CIHashMap<V> of(String k1, V v1,
                                      String k2, V v2,
                                      String k3, V v3) {
        return new CIHashMap<>(CIKey.mapOf(
                k1, v1,
                k2, v2,
                k3, v3));
    }

    /**
     * Create a {@link CIKey} keyed map
     */
    public static <V> CIHashMap<V> of(String k1, V v1,
                                      String k2, V v2,
                                      String k3, V v3,
                                      String k4, V v4) {
        return new CIHashMap<>(CIKey.mapOf(
                k1, v1,
                k2, v2,
                k3, v3,
                k4, v4));
    }

    /**
     * Create a {@link CIKey} keyed map
     */
    public static <V> CIHashMap<V> of(String k1, V v1,
                                      String k2, V v2,
                                      String k3, V v3,
                                      String k4, V v4,
                                      String k5, V v5) {
        return new CIHashMap<>(CIKey.mapOf(
                k1, v1,
                k2, v2,
                k3, v3,
                k4, v4,
                k5, v5));
    }

    /**
     * Create a {@link CIKey} keyed map
     */
    public static <V> CIHashMap<V> of(String k1, V v1,
                                      String k2, V v2,
                                      String k3, V v3,
                                      String k4, V v4,
                                      String k5, V v5,
                                      String k6, V v6) {
        return new CIHashMap<>(CIKey.mapOf(
                k1, v1,
                k2, v2,
                k3, v3,
                k4, v4,
                k5, v5,
                k6, v6));
    }

    /**
     * Create a {@link CIKey} keyed map
     */
    public static <V> CIHashMap<V> of(String k1, V v1,
                                      String k2, V v2,
                                      String k3, V v3,
                                      String k4, V v4,
                                      String k5, V v5,
                                      String k6, V v6,
                                      String k7, V v7) {
        return new CIHashMap<>(CIKey.mapOf(
                k1, v1,
                k2, v2,
                k3, v3,
                k4, v4,
                k5, v5,
                k6, v6,
                k7, v7));
    }

    /**
     * Create a {@link CIKey} keyed map
     */
    public static <V> CIHashMap<V> of(String k1, V v1,
                                      String k2, V v2,
                                      String k3, V v3,
                                      String k4, V v4,
                                      String k5, V v5,
                                      String k6, V v6,
                                      String k7, V v7,
                                      String k8, V v8) {
        return new CIHashMap<>(CIKey.mapOf(
                k1, v1,
                k2, v2,
                k3, v3,
                k4, v4,
                k5, v5,
                k6, v6,
                k7, v7,
                k8, v8));
    }

    /**
     * Convert an array of {@link String} keyed entries into a {@link CIKey} keyed map.
     */
    @SafeVarargs
    public static <V> CIHashMap<V> ofEntries(final Entry<String, ? extends V>... entries) {
        return GwtNullSafe.stream(entries)
                .collect(Collectors.toMap(
                        entry ->
                                CIKey.of(entry.getKey()),
                        Entry::getValue,
                        (object, object2) -> object,
                        CIHashMap::new));
    }

    /**
     * Convert a {@link String} keyed map into a {@link CIKey} keyed map.
     * Accepts nulls and never returns a null.
     */
    public static <V> CIHashMap<V> of(final Map<String, ? extends V> map) {
        return GwtNullSafe.map(map)
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        entry ->
                                CIKey.of(entry.getKey()),
                        Entry::getValue,
                        (object, object2) -> object,
                        CIHashMap::new));
    }
}
