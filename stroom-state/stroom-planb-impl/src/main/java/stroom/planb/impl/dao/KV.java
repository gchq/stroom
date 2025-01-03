package stroom.planb.impl.dao;

public interface KV<K, V> {

    K key();

    V value();
}
