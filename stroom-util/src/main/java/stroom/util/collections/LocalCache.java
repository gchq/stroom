/*
 * Copyright 2026 Crown Copyright
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


import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/// A simple non-thread-safe self-loading cache for local single thread use.
/// Keys must not be null. Null values are treated as no mapping.
public class LocalCache<K, V> {

    private final Map<K, V> map;
    private final Function<K, V> loadFunction;

    public LocalCache(final Function<K, V> loadFunction) {
        this.loadFunction = Objects.requireNonNull(loadFunction);
        this.map = new HashMap<>();
    }

    /// Gets the value corresponding to the given key, loading it if necessary.
    ///
    /// @param key The key to look up.
    /// @return The cached value if present and non-null, else the result of the loadFunction.
    public Optional<V> get(final K key) {
        Objects.requireNonNull(key);
        return Optional.ofNullable(map.computeIfAbsent(key, loadFunction));
    }

    /// Gets the value corresponding to the given key. Does not call the loadFunction.
    ///
    /// @param key The key to look up.
    /// @return The value if present and non-null
    public Optional<V> getIfPresent(final K key) {
        Objects.requireNonNull(key);
        return Optional.ofNullable(map.get(key));
    }

    public void clear() {
        map.clear();
    }

    public void invalidate(final K key) {
        map.remove(key);
    }

    public int size() {
        return map.size();
    }

    @Override
    public String toString() {
        return map.toString();
    }
}
