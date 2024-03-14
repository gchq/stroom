package stroom.proxy.repo.dao.lmdb.serde;

import stroom.bytebuffer.ByteBufferPool;
import stroom.bytebuffer.PooledByteBuffer;
import stroom.lmdb.serde.Serde;

import com.google.inject.TypeLiteral;
import jakarta.inject.Inject;

import java.nio.ByteBuffer;

public class IntegerSerde implements ExtendedSerde<Integer> {

    @Override
    public void serialize(final ByteBuffer byteBuffer, final Integer val) {
        byteBuffer.putInt(val);
        byteBuffer.flip();
    }

    public void increment(final ByteBuffer byteBuffer) {
        int val = byteBuffer.getInt();
        byteBuffer.flip();
        byteBuffer.putInt(val + 1);
        byteBuffer.flip();
    }

    public void decrement(final ByteBuffer byteBuffer) {
        int val = byteBuffer.getInt();
        byteBuffer.flip();
        byteBuffer.putInt(val - 1);
        byteBuffer.flip();
    }

    @Override
    public PooledByteBuffer serialize(final Integer value, final ByteBufferPool byteBufferPool) {
        final PooledByteBuffer pooledByteBuffer = byteBufferPool.getPooledByteBuffer(Integer.BYTES);
        pooledByteBuffer.getByteBuffer().putInt(value);
        pooledByteBuffer.getByteBuffer().flip();
        return pooledByteBuffer;
    }

    @Override
    public Integer deserialize(final ByteBuffer byteBuffer) {
        return byteBuffer.getInt();
    }
}
