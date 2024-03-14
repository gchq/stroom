package stroom.proxy.repo.dao.lmdb.serde;

import stroom.bytebuffer.ByteBufferPool;
import stroom.bytebuffer.PooledByteBuffer;
import stroom.proxy.repo.dao.lmdb.AggregateValue;

import java.nio.ByteBuffer;

public class AggregateValueSerde implements ExtendedSerde<AggregateValue> {

    @Override
    public void serialize(final ByteBuffer byteBuffer, final AggregateValue object) {
        throw new UnsupportedOperationException("Not implemented for this serializer");
    }

    @Override
    public PooledByteBuffer serialize(final AggregateValue value, final ByteBufferPool byteBufferPool) {
        return ByteBuffers.write(byteBufferPool, output -> {
            output.writeLong(value.byteSize());
            output.writeLong(value.items());
            output.writeBoolean(value.complete());
        });
    }

    @Override
    public AggregateValue deserialize(final ByteBuffer byteBuffer) {
        return ByteBuffers.read(byteBuffer, input -> {
            final long byteSize = input.readLong();
            final long items = input.readLong();
            final boolean complete = input.readBoolean();
            return new AggregateValue(byteSize, items, complete);
        });
    }
}
