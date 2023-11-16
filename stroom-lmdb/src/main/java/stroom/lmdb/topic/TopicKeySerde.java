package stroom.lmdb.topic;

import stroom.lmdb.serde.Serde;

import java.nio.ByteBuffer;

class TopicKeySerde<K> implements Serde<TopicKey<K>> {

    private static final int OFFSET_BYTES = Long.BYTES;
    private final Serde<K> keySerde;

    TopicKeySerde(final Serde<K> keySerde) {
        this.keySerde = keySerde;
    }

    @Override
    public TopicKey<K> deserialize(final ByteBuffer byteBuffer) {
        final long offset = byteBuffer.getLong();
        final K key = keySerde != null
                ? keySerde.deserialize(byteBuffer.slice())
                : null;
        byteBuffer.rewind();
        return new TopicKey<>(offset, key);
    }

    @Override
    public void serialize(final ByteBuffer byteBuffer, final TopicKey<K> topicKey) {
        byteBuffer.putLong(topicKey.getOffset());
        if (keySerde != null) {
            final K key = topicKey.getKey();
            if (key != null) {
                keySerde.serialize(byteBuffer, topicKey.getKey());
            }
        }
        byteBuffer.flip();
    }
}
