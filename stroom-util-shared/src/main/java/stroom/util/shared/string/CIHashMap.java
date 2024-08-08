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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A {@link HashMap} keyed with case-insensitive {@link CIKey} keys.
 */
public class CIHashMap<V> extends HashMap<CIKey, V> {

    public V put(final String key, final V value) {
        // CIString used to trim all keys
        return super.put(CIKey.of(key), value);
    }

    // Overload all the super methods that take key as an Object as the compiler
    // won't spot people using a String key.
    public V get(final String key) {
        return super.get(CIKey.of(key));
    }

    public V getOrDefault(final String key, final V defaultValue) {
        return super.getOrDefault(CIKey.of(key), defaultValue);
    }

    public boolean remove(final String key, final Object value) {
        return super.remove(CIKey.of(key), value);
    }

    public boolean containsKey(final String key) {
        return super.containsKey(CIKey.of(key));
    }

    /**
     * @return True if the key is in the map (ignoring case)
     */
    public boolean containsStringKey(final String key) {
        return super.containsKey(CIKey.of(key));
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
}
