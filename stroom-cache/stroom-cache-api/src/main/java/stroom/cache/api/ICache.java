package stroom.cache.api;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface ICache<K, V> {
    V get(K key);

    void put(K key, V value);

    Optional<V> getOptional(K key);

    Map<K, V> asMap();

    Collection<V> values();

    void invalidate(K key);

    void remove(K key);

    void evictExpiredElements();

    long size();

    void clear();
}
