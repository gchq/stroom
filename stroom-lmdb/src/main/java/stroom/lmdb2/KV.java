package stroom.lmdb2;

import stroom.util.shared.AbstractBuilder;

import com.fasterxml.jackson.annotation.JsonProperty;

public abstract class KV<K, V> {

    @JsonProperty
    private final K key;
    @JsonProperty
    private final V value;

    public KV(final K key, final V value) {
        this.key = key;
        this.value = value;
    }

    public K key() {
        return key;
    }

    public V val() {
        return value;
    }

    @Override
    public String toString() {
        return "KV{" +
               "key=" + key +
               ", value=" + value +
               '}';
    }


    // --------------------------------------------------------------------------------


    public abstract static class AbstractKVBuilder<T, B extends AbstractKVBuilder<T, ?, K, V>, K, V>
            extends AbstractBuilder<T, B> {

        protected K key;
        protected V value;

        public AbstractKVBuilder() {
        }

        public AbstractKVBuilder(final KV<K, V> kv) {
            this.key = kv.key();
            this.value = kv.val();
        }

        public B key(final K key) {
            this.key = key;
            return self();
        }

        public B value(final V value) {
            this.value = value;
            return self();
        }
    }
}
