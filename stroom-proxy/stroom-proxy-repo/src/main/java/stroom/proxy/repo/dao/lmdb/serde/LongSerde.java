package stroom.proxy.repo.dao.lmdb.serde;

import java.nio.ByteBuffer;

public class LongSerde implements Serde<Long> {

    @Override
    public ByteBuffer serialise(final Long value) {
        final ByteBuffer hashByteBuffer = ByteBuffer.allocateDirect(Long.BYTES);
        hashByteBuffer.putLong(value);
        hashByteBuffer.flip();
        return hashByteBuffer;
    }

    @Override
    public Long deserialise(final ByteBuffer byteBuffer) {
        return byteBuffer.getLong();
    }
}
