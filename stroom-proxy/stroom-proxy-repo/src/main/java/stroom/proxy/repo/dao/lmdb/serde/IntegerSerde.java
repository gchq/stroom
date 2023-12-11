package stroom.proxy.repo.dao.lmdb.serde;

import java.nio.ByteBuffer;

public class IntegerSerde implements Serde<Integer> {

    private static final ByteBufferPool BUFFER_POOL = new ByteBufferPool(Integer.BYTES);

    public ByteBuffer serialise(final ByteBuffer byteBuffer, final Integer value) {
        byteBuffer.putInt(value);
        byteBuffer.flip();
        return byteBuffer;
    }

    @Override
    public PooledByteBuffer serialise(final Integer value) {
        final PooledByteBuffer pooledByteBuffer = BUFFER_POOL.createOrBorrowBuffer();
        serialise(pooledByteBuffer.get(), value);
        return pooledByteBuffer;
    }

    @Override
    public Integer deserialise(final ByteBuffer byteBuffer) {
        return byteBuffer.getInt();
    }
}
