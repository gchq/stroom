package stroom.lmdb2;

public class KV<K, V> {

    private final K key;
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

    public static <K, V> Builder<K, V> builder() {
        return new Builder<>();
    }

    public static class Builder<K, V> {

        private K key;
        private V value;

        public Builder() {
        }

        public Builder(final KV<K, V> kv) {
            this.key = kv.key();
            this.value = kv.val();
        }

        public Builder<K, V> key(final K key) {
            this.key = key;
            return this;
        }

        public Builder<K, V> value(final V value) {
            this.value = value;
            return this;
        }

        public KV<K, V> build() {
            return new KV<>(key, value);
        }
    }
}
