package stroom.meta.api;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * String hash map that does not care about key case.
 */
class CIStringHashMap implements Map<String, String> {

    private final HashMap<String, KV> map = new HashMap<>();

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public boolean containsKey(final Object key) {
        return map.containsKey(toLowerCase(key));
    }

    @Override
    public boolean containsValue(final Object value) {
        return map.entrySet().stream().anyMatch(entry -> Objects.equals(entry.getValue().getValue(), value));
    }

    private String toLowerCase(final Object key) {
        return ((String) key).toLowerCase(Locale.ROOT);
    }

    private String getKey(final KV kv) {
        if (kv == null) {
            return null;
        }
        return kv.getKey();
    }

    private String getValue(final KV kv) {
        if (kv == null) {
            return null;
        }
        return kv.getValue();
    }

    @Override
    public String get(final Object key) {
        return getValue(map.get(toLowerCase(key)));
    }

    public String getKey(final String key) {
        return getKey(map.get(toLowerCase(key)));
    }

    public KV getEntry(final String key) {
        return map.get(toLowerCase(key));
    }

    @Override
    public String getOrDefault(final Object key, final String defaultVal) {
        final KV kv = map.get(toLowerCase(key));
        return kv == null
                ? defaultVal
                : kv.getKey();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public String put(final String key, String value) {
        if (value != null) {
            value = value.trim();
        }
        return getValue(map.put(toLowerCase(key), new KV(key, value)));
    }

    @Override
    public String remove(final Object key) {
        return getValue(map.remove(toLowerCase(key)));
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
        final Set<Entry<String, String>> rtnSet = new HashSet<>(map.size());
        for (final KV kv : map.values()) {
            rtnSet.add(Map.entry(kv.getKey(), kv.getValue()));
        }
        return rtnSet;
    }

    /**
     * DOESN'T adhere to the contract of {@link Map#keySet()}, so any changes
     * to the returned {@link Set} will NOT affect this map.
     */
    @Override
    public Set<String> keySet() {
        final Set<String> rtnSet = new HashSet<>(map.size());
        for (final KV kv : map.values()) {
            rtnSet.add(kv.getKey());
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
        return map.values().stream().map(KV::getValue).collect(Collectors.toSet());
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

    /**
     * @param normaliseKeys If true, all keys will be converted to lower case, else
     *                      will be left in their original case.
     */
    public Map<String, String> asMap(final boolean normaliseKeys) {
        if (normaliseKeys) {
            return map
                    .values()
                    .stream()
                    .collect(Collectors.toMap(kv -> kv.getKey().toLowerCase(Locale.ROOT), KV::getValue));
        }
        return map
                .values()
                .stream()
                .collect(Collectors.toMap(KV::getKey, KV::getValue));
    }

    public static class KV {

        private final String key;
        private final String value;

        public KV(final String key, final String value) {
            this.key = key;
            this.value = value;
        }

        public String getKey() {
            return key;
        }

        public String getValue() {
            return value;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final KV kv = (KV) o;
            return Objects.equals(value, kv.value);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(value);
        }

        @Override
        public String toString() {
            return value;
        }
    }
}
