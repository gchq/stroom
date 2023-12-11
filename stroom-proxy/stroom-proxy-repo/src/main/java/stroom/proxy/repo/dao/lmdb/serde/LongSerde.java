package stroom.proxy.repo.dao.lmdb.serde;

import java.nio.ByteBuffer;

public class LongSerde implements Serde<Long> {

    private static final ByteBufferPool BUFFER_POOL = new ByteBufferPool(Long.BYTES);

    public ByteBuffer serialise(final ByteBuffer byteBuffer, final Long value) {
        byteBuffer.putLong(value);
        byteBuffer.flip();
        return byteBuffer;
    }

    @Override
    public PooledByteBuffer serialise(final Long value) {
        final PooledByteBuffer pooledByteBuffer = BUFFER_POOL.createOrBorrowBuffer();
        serialise(pooledByteBuffer.get(), value);
        return pooledByteBuffer;
    }

    @Override
    public Long deserialise(final ByteBuffer byteBuffer) {
        return byteBuffer.getLong();
    }
}
