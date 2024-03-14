package stroom.proxy.repo.dao.lmdb.serde;

import stroom.bytebuffer.ByteBufferPool;
import stroom.bytebuffer.PooledByteBuffer;

import java.nio.ByteBuffer;

public class LongSerde implements ExtendedSerde<Long> {

    @Override
    public void serialize(final ByteBuffer byteBuffer, final Long val) {
        byteBuffer.putLong(val);
        byteBuffer.flip();
    }

    public void increment(final ByteBuffer byteBuffer) {
        int val = byteBuffer.getInt();
        byteBuffer.flip();
        byteBuffer.putLong(val + 1);
        byteBuffer.flip();
    }

    public void decrement(final ByteBuffer byteBuffer) {
        int val = byteBuffer.getInt();
        byteBuffer.flip();
        byteBuffer.putLong(val - 1);
        byteBuffer.flip();
    }

    @Override
    public PooledByteBuffer serialize(final Long value, final ByteBufferPool byteBufferPool) {
        final PooledByteBuffer pooledByteBuffer = byteBufferPool.getPooledByteBuffer(Long.BYTES);
        pooledByteBuffer.getByteBuffer().putLong(value);
        pooledByteBuffer.getByteBuffer().flip();
        return pooledByteBuffer;
    }

    @Override
    public Long deserialize(final ByteBuffer byteBuffer) {
        return byteBuffer.getLong();
    }
}
