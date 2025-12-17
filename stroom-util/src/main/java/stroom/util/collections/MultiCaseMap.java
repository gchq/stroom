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

package stroom.util.collections;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.string.CIKey;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

/**
 * A map that allows you to perform a lookup in both a case-sensitive and case-insensitive way
 * <p>
 * Not thread safe.
 * </p>
 */
public class MultiCaseMap<V> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(MultiCaseMap.class);

    private static final MultiCaseMap<?> EMPTY = new MultiCaseMap<>(
            Collections.emptyMap(),
            Collections.emptyMap());

    private final Map<String, V> caseSensitiveMap;
    private final Map<CIKey, Map<String, V>> caseinsensitiveMap;

    public MultiCaseMap(final Map<String, V> caseSensitiveMap,
                        final Map<CIKey, Map<String, V>> caseinsensitiveMap) {
        this.caseSensitiveMap = caseSensitiveMap;
        this.caseinsensitiveMap = caseinsensitiveMap;
    }

    public MultiCaseMap() {
        this.caseSensitiveMap = new HashMap<>();
        this.caseinsensitiveMap = new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    public static <V> MultiCaseMap<V> empty() {
        return (MultiCaseMap<V>) EMPTY;
    }

    public static <V> MultiCaseMap<V> fromStringKeyedMap(final Map<String, V> map) {
        final MultiCaseMap<V> multiCaseMap = new MultiCaseMap<>();
        NullSafe.map(map)
                .forEach(multiCaseMap::put);
        return multiCaseMap;
    }

    public static <V> MultiCaseMap<V> fromCIKeyedMap(final Map<CIKey, V> map) {
        final MultiCaseMap<V> multiCaseMap = new MultiCaseMap<>();
        NullSafe.map(map)
                .forEach(multiCaseMap::put);
        return multiCaseMap;
    }

    public int size() {
        return caseSensitiveMap.size();
    }

    public boolean isEmpty() {
        return caseSensitiveMap.isEmpty();
    }

    public V getCaseSensitive(final String key) {
        return caseSensitiveMap.get(Objects.requireNonNull(key));
    }

    public V getCaseSensitive(final CIKey ciKey) {
        return caseSensitiveMap.get(Objects.requireNonNull(ciKey).get());
    }

    public V get(final String key) {
        Objects.requireNonNull(key);
        return get(null, key);
    }

    public V get(final CIKey ciKey) {
        Objects.requireNonNull(ciKey);
        return get(ciKey, null);
    }

    private V get(final CIKey ciKey, final String key) {
        // We should have either ciKey or key
        final V val = key != null
                ? caseSensitiveMap.get(key)
                : caseSensitiveMap.get(ciKey.get());
        if (val == null) {
            final Map<String, V> subMap = caseinsensitiveMap.get(Objects.requireNonNullElseGet(
                    ciKey, () -> CIKey.of(key)));
            if (subMap == null) {
                return null;
            } else {
                if (subMap.size() > 1) {
                    throw new MultipleMatchException(
                            "Multiple keys " + subMap.keySet()
                            + " exist for case-insensitive key '"
                            + Objects.requireNonNullElseGet(key, ciKey::get)
                            + "' and there is no " +
                            "exact match.");
                } else {
                    return subMap.values().iterator().next();
                }
            }

        } else {
            return val;
        }
    }

    public void put(final String key, final V value) {
        Objects.requireNonNull(key);
        put(CIKey.of(key), value);
    }

    public void put(final CIKey ciKey, final V value) {
        Objects.requireNonNull(ciKey);
        final String key = ciKey.get();
        caseSensitiveMap.put(key, value);
        final Map<String, V> subMap = caseinsensitiveMap.computeIfAbsent(ciKey, k -> new HashMap<>());
        if (subMap.isEmpty()) {
            subMap.put(key, value);
        } else {
            if (subMap.containsValue(value)) {
                // Already have this value so don't bother adding it
                LOGGER.debug("Dup value {} for key {}", value, key);
            } else {
                subMap.put(key, value);
            }
        }
    }

    public boolean containsKey(final String key) {
        Objects.requireNonNull(key);
        if (caseSensitiveMap.containsKey(key)) {
            return true;
        } else {
            return caseinsensitiveMap.containsKey(CIKey.of(key));
        }
    }

    public boolean containsKey(final CIKey ciKey) {
        Objects.requireNonNull(ciKey);
        if (caseSensitiveMap.containsKey(ciKey.get())) {
            return true;
        } else {
            return caseinsensitiveMap.containsKey(ciKey);
        }
    }

    public Set<String> keySet() {
        return caseSensitiveMap.keySet();
    }

    public Collection<V> values() {
        return caseSensitiveMap.values();
    }

    public Set<Entry<String, V>> entrySet() {
        return caseSensitiveMap.entrySet();
    }

    public void clear() {
        caseSensitiveMap.clear();
        caseinsensitiveMap.clear();
    }
}
