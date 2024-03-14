package stroom.proxy.repo.dao.lmdb.serde;

import stroom.bytebuffer.ByteBufferPool;
import stroom.bytebuffer.PooledByteBuffer;
import stroom.proxy.repo.dao.lmdb.RepoSourceValue;

import java.nio.ByteBuffer;

public class RepoSourceValueSerde implements ExtendedSerde<RepoSourceValue> {

    @Override
    public void serialize(final ByteBuffer byteBuffer, final RepoSourceValue value) {
        byteBuffer.putLong(value.feedId());
        byteBuffer.putLong(value.itemCount());
        byteBuffer.flip();
    }

    @Override
    public PooledByteBuffer serialize(final RepoSourceValue value, final ByteBufferPool byteBufferPool) {
        final PooledByteBuffer pooledByteBuffer = byteBufferPool.getPooledByteBuffer(Long.BYTES * 2);
        serialize(pooledByteBuffer.getByteBuffer(), value);
        return pooledByteBuffer;
    }

    @Override
    public RepoSourceValue deserialize(final ByteBuffer byteBuffer) {
        final long feedId = byteBuffer.getLong();
        final long itemCount = byteBuffer.getLong();
        return new RepoSourceValue(feedId, itemCount);
    }
}
