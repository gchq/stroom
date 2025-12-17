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

package stroom.meta.api;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * String hash map that does not care about key case.
 */
class CIStringHashMap implements Map<String, String> {

    private final Map<CIString, String> map;

    public CIStringHashMap() {
        map = new HashMap<>();
    }

    public CIStringHashMap(final int initialCapacity) {
        map = new HashMap<>(initialCapacity);
    }

    public CIStringHashMap(final CIStringHashMap ciStringHashMap) {
        if (ciStringHashMap == null || ciStringHashMap.isEmpty()) {
            map = new HashMap<>();
        } else {
            map = new HashMap<>(ciStringHashMap.map);
        }
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public boolean containsKey(final Object key) {
        return map.containsKey(new CIString((String) key));
    }

    @Override
    public boolean containsValue(final Object value) {
        return map.containsValue(value);
    }

    /**
     * Get the value corresponding to the key.
     * If the value is non-null then it will be already trimmed
     * as all values are trimmed on entry into the map.
     *
     * @param key the key whose associated value is to be returned
     * @return The trimmed value or null.
     */
    @Override
    public String get(final Object key) {
        return map.get(new CIString((String) key));
    }

    /**
     * Get the value corresponding to the key.
     * If the value is non-null then it will be already trimmed
     * as all values are trimmed on entry into the map.
     *
     * @param key the key whose associated value is to be returned
     * @return The trimmed value from the map or defaultVal
     */
    @Override
    public String getOrDefault(final Object key, final String defaultVal) {
        final String val = map.get(new CIString((String) key));
        return val == null
                ? defaultVal
                : val;
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    public String computeIfAbsent(final String key, final Function<String, String> mappingFunction) {
        return map.computeIfAbsent(new CIString(key), k -> mappingFunction.apply(k.key));
    }

    @Override
    public String put(final String key, String value) {
        if (value != null) {
            value = value.trim();
        }
        final CIString newKey = new CIString(key);
        final String oldValue = map.remove(newKey);
        map.put(newKey, value);
        return oldValue;
    }

    @Override
    public String remove(final Object key) {
        return map.remove(new CIString((String) key));
    }

    @Override
    public int size() {
        return map.size();
    }

    /**
     * DOESN'T adhere to the contract of {@link Map#entrySet()}, so any changes
     * to the returned {@link Set} will NOT affect this map.
     */
    @Override
    public Set<Entry<String, String>> entrySet() {
        final Set<Entry<String, String>> rtnSet = new HashSet<>();
        for (final Entry<CIString, String> entry : map.entrySet()) {
            rtnSet.add(new CIEntryAdaptor(entry));
        }
        return rtnSet;
    }

    /**
     * DOESN'T adhere to the contract of {@link Map#keySet()}, so any changes
     * to the returned {@link Set} will NOT affect this map.
     */
    @Override
    public Set<String> keySet() {
        final Set<String> rtnSet = new HashSet<>();
        for (final CIString entry : map.keySet()) {
            rtnSet.add(entry.key);
        }
        return rtnSet;
    }

    @Override
    public void putAll(final Map<? extends String, ? extends String> m) {
        for (final Entry<? extends String, ? extends String> entry : m.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public Collection<String> values() {
        return map.values();
    }

    @Override
    public String toString() {
        return map.toString();
    }

    /**
     * @return A {@link Map} that maps the lower-case key to the key in its original case (whatever that
     * may be).
     */
    public Map<String, String> getKeyMap() {
        return map.keySet()
                .stream()
                .collect(Collectors.toMap(CIString::getLowerKey, CIString::getKey));
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final CIStringHashMap that = (CIStringHashMap) o;
        return Objects.equals(map, that.map);
    }

    @Override
    public int hashCode() {
        return Objects.hash(map);
    }

    /**
     * @param normaliseKeys If true, all keys will be converted to lower case, else
     *                      will be left in their original case.
     */
    public Map<String, String> asMap(final boolean normaliseKeys) {
        final Function<Entry<CIString, String>, String> keyMapper = normaliseKeys
                ? entry -> entry.getKey().lowerKey
                : entry -> entry.getKey().key;

        return map.entrySet()
                .stream()
                .collect(Collectors.toMap(keyMapper, Entry::getValue));
    }

    // --------------------------------------------------------------------------------


    protected static class CIString implements Comparable<CIString>, Serializable {

        private final String key;
        private final String lowerKey;

        CIString(final String key) {
            this.key = key.trim();
            this.lowerKey = this.key.toLowerCase(Locale.ENGLISH);
        }

        public String getKey() {
            return key;
        }

        public String getLowerKey() {
            return lowerKey;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final CIString ciString = (CIString) o;
            return lowerKey.equals(ciString.lowerKey);
        }

        @Override
        public int hashCode() {
            return lowerKey.hashCode();
        }

        @Override
        public int compareTo(final CIString o) {
            return lowerKey.compareTo(o.lowerKey);
        }

        @Override
        public String toString() {
            return key;
        }
    }


    // --------------------------------------------------------------------------------


    private static class CIEntryAdaptor implements Entry<String, String> {

        private final Entry<CIString, String> realEntry;

        private CIEntryAdaptor(final Entry<CIString, String> realEntry) {
            this.realEntry = realEntry;
        }

        @Override
        public String getKey() {
            return realEntry.getKey().key;
        }

        @Override
        public String getValue() {
            return realEntry.getValue();
        }

        @Override
        public String setValue(final String value) {
            return realEntry.setValue(value);
        }
    }
}
