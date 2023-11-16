package stroom.lmdb.topic;

import stroom.lmdb.serde.Serde;

import java.nio.ByteBuffer;

class SignedLongSerde implements Serde<Long> {

    @Override
    public Long deserialize(final ByteBuffer byteBuffer) {
        final long val = byteBuffer.getLong();
        byteBuffer.rewind();
        return val;
    }

    @Override
    public void serialize(final ByteBuffer byteBuffer, final Long val) {
        byteBuffer.putLong(val);
        byteBuffer.flip();
    }
}
