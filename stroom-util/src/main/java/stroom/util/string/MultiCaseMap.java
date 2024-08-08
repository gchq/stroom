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

package stroom.util.string;

import stroom.util.NullSafe;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.string.CIKey;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Wraps a nested map that allows you to perform both case-sensitive and
 * case-insensitive look-ups.
 */
public class MultiCaseMap<T> {

    @SuppressWarnings("rawtypes")
    private static final MultiCaseMap EMPTY = new MultiCaseMap<>(Collections.emptyMap());

    private final Map<CIKey, Map<String, T>> map;

    public MultiCaseMap() {
        this.map = new HashMap<>();
    }

    private MultiCaseMap(final Map<CIKey, Map<String, T>> map) {
        this.map = map;
    }

    public static <T> MultiCaseMap<T> fromStringKeyedMap(final Map<String, T> map) {
        final MultiCaseMap<T> multiCaseMap = new MultiCaseMap<>();
        NullSafe.map(map)
                .forEach(multiCaseMap::put);
        return multiCaseMap;
    }

    @SuppressWarnings("unchecked")
    public static <T> MultiCaseMap<T> emptyMap() {
        return (MultiCaseMap<T>) EMPTY;
    }

    /**
     * Case-sensitive match on key
     */
    public boolean containsKey(final String key) {
        return GwtNullSafe.test(
                map.get(CIKey.of(key)),
                subMap -> subMap.containsKey(key));
    }

    /**
     * Case-insensitive match on ciKey
     */
    public boolean containsKeys(final CIKey key) {
        final Map<String, T> subMap = map.get(key);
        return subMap != null && !subMap.isEmpty();
    }

    public int size() {
        return map.size();
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public void clear() {
        map.clear();
    }

    /**
     * Case-sensitive match on key
     */
    public T get(final String key) {
        return GwtNullSafe.get(
                map.get(CIKey.of(key)),
                subMap -> subMap.get(key));
    }

    /**
     * Case-insensitive match on {@link CIKey}.
     *
     * @return A map of values keyed on a case-sensitive key.
     */
    public Map<String, T> get(final CIKey key) {
        return GwtNullSafe.requireNonNullElseGet(map.get(key), Collections::emptyMap);
    }

    /**
     * @return The value associated with ciKey (case-insensitive) or null if there
     * is no associated value. If ciKey matches multiple keys with different case then
     * it will attempt to do a case-sensitive match. If there is no case-sensitive match
     * it will throw a {@link MultipleMatchException}.
     * @throws MultipleMatchException if multiple values are associated with ciKey
     */
    public T getCaseSensitive(final CIKey ciKey) {
        final Map<String, T> subMap = map.get(ciKey);
        if (NullSafe.isEmptyMap(subMap)) {
            return null;
        } else {
            final int count = subMap.size();
            if (count == 0) {
                // Should never happen.  Shouldn't have an empty subMap
                throw new RuntimeException("Empty subMap");
            } else if (count == 1) {
                // There is only one, so we don't need to check for an exact match
                return subMap.values().iterator().next();
            } else {
                final T exactMatch = subMap.get(ciKey.get());
                if (exactMatch != null) {
                    return exactMatch;
                } else {
                    throw new MultipleMatchException("Multiple values (" + subMap.size()
                            + ") exist for case-insensitive key '" + ciKey.get() + "'");
                }
            }
        }
    }

    /**
     * Put an entry into the map
     */
    public T put(final String key, final T value) {
        return map.computeIfAbsent(CIKey.of(key), k -> new HashMap<>())
                .put(key, value);
    }

    /**
     * Put an entry into the map
     */
    public T put(final CIKey ciKey, final T value) {
        return map.computeIfAbsent(ciKey, k -> new HashMap<>())
                .put(ciKey.get(), value);
    }

    /**
     * Iterate over case-sensitive entry keys
     */
    public void forEach(final BiConsumer<String, T> action) {
        if (action != null) {
            map.values()
                    .stream()
                    .filter(Objects::nonNull)
                    .flatMap(subMap ->
                            subMap.entrySet()
                                    .stream())
                    .forEach(entry -> action.accept(entry.getKey(), entry.getValue()));
        }
    }

    public Set<Entry<String, T>> entrySet() {
        return map.values()
                .stream()
                .filter(Objects::nonNull)
                .flatMap(subMap ->
                        subMap.entrySet()
                                .stream())
                .collect(Collectors.toSet());
    }


    // --------------------------------------------------------------------------------


    public static class MultipleMatchException extends RuntimeException {

        public MultipleMatchException(final String message) {
            super(message);
        }
    }
}
