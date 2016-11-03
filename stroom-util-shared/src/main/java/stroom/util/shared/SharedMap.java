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

package stroom.util.shared;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class SharedMap<K extends SharedObject, V extends SharedObject> implements Map<K, V>, SharedObject {
    private static final long serialVersionUID = 3481789353378918333L;

    private Map<K, V> map;

    public SharedMap() {
        this.map = new HashMap<K, V>();
    }

    public SharedMap(final int initialCapacity) {
        this.map = new HashMap<K, V>(initialCapacity);
    }

    public SharedMap(final Map<K, V> map) {
        this.map = map;
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public boolean containsKey(final Object key) {
        return map.containsKey(key);
    }

    @Override
    public boolean containsValue(final Object value) {
        return map.containsValue(value);
    }

    @Override
    public V get(final Object key) {
        return map.get(key);
    }

    @Override
    public V put(final K key, final V value) {
        return map.put(key, value);
    }

    @Override
    public V remove(final Object key) {
        return map.remove(key);
    }

    @Override
    public void putAll(final Map<? extends K, ? extends V> m) {
        map.putAll(m);
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public Set<K> keySet() {
        return map.keySet();
    }

    @Override
    public Collection<V> values() {
        return map.values();
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return map.entrySet();
    }

    @Override
    public int hashCode() {
        return map.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null || !(obj instanceof SharedMap)) {
            return false;
        }

        final SharedMap<?, ?> m = (SharedMap<?, ?>) obj;
        return map.equals(m.map);
    }

    @Override
    public String toString() {
        return map.toString();
    }

    /**
     * Added to ensure map is not made final which would break GWT
     * serialisation.
     */
    public void setMap(final Map<K, V> map) {
        this.map = map;
    }
}
