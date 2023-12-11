package stroom.proxy.repo.dao.lmdb.serde;

import stroom.proxy.repo.dao.lmdb.RepoSourceValue;

import java.nio.ByteBuffer;

public class RepoSourcePartSerde implements Serde<RepoSourceValue> {

    private static final ByteBufferPool BUFFER_POOL = new ByteBufferPool(Long.BYTES * 2);

    public ByteBuffer serialise(final ByteBuffer byteBuffer, final RepoSourceValue value) {
        byteBuffer.putLong(value.feedId());
        byteBuffer.putLong(value.itemCount());
        byteBuffer.flip();
        return byteBuffer;
    }

    @Override
    public PooledByteBuffer serialise(final RepoSourceValue value) {
        final PooledByteBuffer pooledByteBuffer = BUFFER_POOL.createOrBorrowBuffer();
        serialise(pooledByteBuffer.get(), value);
        return pooledByteBuffer;
    }

    @Override
    public RepoSourceValue deserialise(final ByteBuffer byteBuffer) {
        final long feedId = byteBuffer.getLong();
        final long itemCount = byteBuffer.getLong();
        return new RepoSourceValue(feedId, itemCount);
    }
}
