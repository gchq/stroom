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

package stroom.util;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * String hash map that does not care about key case.
 */
public class CIStringHashMap implements Serializable, Map<String, String> {

    private static final long serialVersionUID = 4877407570072403322L;
    protected Map<CIString, String> realMap = new HashMap<>();

    @Override
    public void clear() {
        realMap.clear();
    }

    @Override
    public boolean containsKey(final Object key) {
        return realMap.containsKey(new CIString((String) key));
    }

    @Override
    public boolean containsValue(final Object value) {
        return realMap.containsValue(value);
    }

    @Override
    public String get(final Object key) {
        return realMap.get(new CIString((String) key));
    }

    @Override
    public String getOrDefault(final Object key, final String defaultVal) {
        final String val = realMap.get(new CIString((String) key));
        return val == null
                ? defaultVal
                : val;
    }

    @Override
    public boolean isEmpty() {
        return realMap.isEmpty();
    }

    public String computeIfAbsent(final String key, final Function<String, String> mappingFunction) {
        return realMap.computeIfAbsent(new CIString(key), k -> mappingFunction.apply(k.key));
    }

    @Override
    public String put(final String key, String value) {
        if (value != null) {
            value = value.trim();
        }
        final CIString newKey = new CIString(key);
        final String oldValue = realMap.remove(newKey);
        realMap.put(newKey, value);
        return oldValue;
    }

    @Override
    public String remove(final Object key) {
        return realMap.remove(new CIString((String) key));
    }

    @Override
    public int size() {
        return realMap.size();
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public Set<Entry<String, String>> entrySet() {
        final Set<Entry<String, String>> rtnSet = new HashSet<>();
        for (final Entry<CIString, String> entry : realMap.entrySet()) {
            rtnSet.add(new CIEntryAdaptor(entry));
        }
        return rtnSet;
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public Set<String> keySet() {
        final Set<String> rtnSet = new HashSet<>();
        for (final CIString entry : realMap.keySet()) {
            rtnSet.add(entry.key);
        }
        return rtnSet;
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public void putAll(final Map<? extends String, ? extends String> m) {
        for (final Entry<? extends String, ? extends String> entry : m.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public Collection<String> values() {
        return realMap.values();
    }

    @Override
    public String toString() {
        return realMap.toString();
    }

    protected static class CIString implements Comparable<CIString>, Serializable {

        private static final long serialVersionUID = 550532045010691235L;

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
        public int hashCode() {
            return lowerKey.hashCode();
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == null) {
                return false;
            }
            if (!(obj instanceof CIString)) {
                return false;
            }
            return key.equalsIgnoreCase(((CIString) obj).key);
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
