package stroom.proxy.repo.dao.lmdb.serde;

import stroom.bytebuffer.ByteBufferPool;
import stroom.bytebuffer.PooledByteBuffer;
import stroom.proxy.repo.dao.lmdb.AggregateKey;

import java.nio.ByteBuffer;

public class AggregateKeySerde implements ExtendedSerde<AggregateKey> {

    @Override
    public void serialize(final ByteBuffer byteBuffer, final AggregateKey object) {
        throw new UnsupportedOperationException("Not implemented for this serializer");
    }

    @Override
    public PooledByteBuffer serialize(final AggregateKey value, final ByteBufferPool byteBufferPool) {
        return ByteBuffers.write(byteBufferPool, Long.BYTES * 3, output -> {
            output.writeLong(value.feedId());
            output.writeLong(value.createTimeMs());
            output.writeLong(value.idSuffix());
        });
    }

    @Override
    public AggregateKey deserialize(final ByteBuffer byteBuffer) {
        return ByteBuffers.read(byteBuffer, input -> {
            final long feedId = input.readLong();
            final long createTimeMs = input.readLong();
            final long idSuffix = input.readLong();
            return new AggregateKey(feedId, createTimeMs, idSuffix);
        });
    }
}
