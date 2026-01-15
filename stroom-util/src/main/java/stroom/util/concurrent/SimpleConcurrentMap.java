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

package stroom.util.concurrent;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple map that minimising locking
 */
public abstract class SimpleConcurrentMap<K, V> {

    private final Map<K, V> map = new ConcurrentHashMap<>();

    public V get(final K key) {
        V rtn = map.get(key);
        if (rtn == null) {
            final V v = initialValue(key);
            rtn = map.put(key, v);
            if (rtn == null) {
                rtn = v;
            }
        }
        return rtn;
    }

    public void put(final K key, final V value) {
        map.put(key, value);
    }

    public Set<K> keySet() {
        return map.keySet();
    }

    public Collection<V> values() {
        return map.values();
    }

    protected abstract V initialValue(K key);
}
