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

package stroom.util.collections;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class TypedMap<K, V> {
    private final Map<K, V> map;

    public static <K, V> TypedMap<K, V> fromMap(final Map<K, V> map) {
        return new TypedMap<>(map);
    }

    private TypedMap(final Map<K, V> map) {
        this.map = map;
    }

    public int size() {
        return map.size();
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public boolean containsKey(final K key) {
        return map.containsKey(key);
    }

    public boolean containsValue(final V value) {
        return map.containsValue(value);
    }

    public V get(final K key) {
        return map.get(key);
    }

    public V put(final K key, final V value) {
        return map.put(key, value);
    }

    public V remove(final K key) {
        return map.remove(key);
    }

    public void putAll(final Map<? extends K, ? extends V> m) {
        map.putAll(m);
    }

    public void clear() {
        map.clear();
    }

    public Set<K> keySet() {
        return map.keySet();
    }

    public Collection<V> values() {
        return map.values();
    }

    public Set<Entry<K, V>> entrySet() {
        return map.entrySet();
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj instanceof TypedMap) {
            return ((TypedMap<?, ?>) obj).map.equals(map);
        }

        return map.equals(obj);
    }

    @Override
    public int hashCode() {
        return map.hashCode();
    }

    @Override
    public String toString() {
        return map.toString();
    }
}
