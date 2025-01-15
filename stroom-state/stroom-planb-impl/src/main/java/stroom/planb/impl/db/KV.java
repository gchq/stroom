package stroom.planb.impl.db;

public interface KV<K, V> {

    K key();

    V value();
}
