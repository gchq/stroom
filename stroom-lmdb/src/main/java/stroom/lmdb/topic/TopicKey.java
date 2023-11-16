package stroom.lmdb.topic;

class TopicKey<K> {

    private final long offset;
    private final K key;

    public TopicKey(final long offset, final K key) {
        this.offset = offset;
        this.key = key;
    }

    public static TopicKey<?> asStartKey(final long offset) {
        return new TopicKey<>(offset, null);
    }

    public long getOffset() {
        return offset;
    }

    public K getKey() {
        return key;
    }

    @Override
    public String toString() {
        return "TopicKey{" +
                "offset=" + offset +
                ", key=" + key +
                '}';
    }
}
