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

/**
 * String hash map that does not care about key case.
 */
class CIStringHashMap implements Map<String, String> {

    private final HashMap<CIString, String> map = new HashMap<>();

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

    @Override
    public String get(final Object key) {
        return map.get(new CIString((String) key));
    }

    @Override
    public String getOrDefault(Object key, String defaultVal) {
        String val = map.get(new CIString((String) key));
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

    @Override
    public Set<Entry<String, String>> entrySet() {
        final Set<Entry<String, String>> rtnSet = new HashSet<>();
        for (final Entry<CIString, String> entry : map.entrySet()) {
            rtnSet.add(new CIEntryAdaptor(entry));
        }
        return rtnSet;
    }

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
            return Objects.equals(lowerKey, ciString.lowerKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(lowerKey);
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
